// 3D-предпросмотр экрана: Three.js, камера на месте игрока, экран в плоскости z = 0 лицом к +z.
// Локальные оси экрана: x вправо (для игрока), y вверх, z камеры - к игроку. В файле position.z - вдоль взгляда
// игрока (ScreenSupport.resolveLocation: dir·(z − depthOffset)), т.е. ПОЛОЖИТЕЛЬНЫЙ z - дальше от игрока, −0.01 - ближе.
// Управление как в Blockbench: ЛКМ по пустому - вращение, СКМ/ПКМ - сдвиг, колесо - зум к курсору,
// ЛКМ по виджету - выбрать и тащить в плоскости экрана; у выбранного - гизмо (TransformControls);
// в углу - ViewHelper для выравнивания камеры по осям.
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { TransformControls } from 'three/examples/jsm/controls/TransformControls.js';
import { ViewHelper } from 'three/examples/jsm/helpers/ViewHelper.js';
import type { Background, RichText, Screen, Widget } from '../model/screen';
import { calibration } from './metrics';
import { buildTextTexture, layout } from './text';
import { itemTexture } from './items';
import { computeSnap, type Guide, type Rect } from './snap';
import { IDENTITY, isIdentity, targetOf, durationMs, easingOf, tweenAt, type HoverDelta, type HoverTween } from './hoverAnim';
import { planMorph, matchedAt, backgroundAt, type MorphPlan } from './morph';

export interface SceneCallbacks {
  /** additive - Shift/Ctrl зажат: добавить/убрать из выделения, а не заменить. */
  onSelect(uid: string | null, additive?: boolean): void;
  /** Рамка выделения: виджеты, попавшие в прямоугольник. */
  onSelectMany(uids: string[]): void;
  onMove(uid: string, pos: [number, number, number]): void;
  onHover(uid: string | null): void;
}

interface Node {
  uid: string;
  group: THREE.Group;
  /** Якорь сущности (translation): hover-анимации масштабируют/вращают вокруг него, как в игре. */
  pivot: THREE.Group;
  mesh: THREE.Mesh;
  /** Видимые габариты в локальных координатах pivot (до масштаба): у текста с прозрачным фоном - сами глифы, иначе фон/текстура. */
  visual: { l: number; r: number; b: number; t: number };
  /** Базовая трансформация pivot (без hover-дельт). */
  base: { tr: [number, number, number]; scale: [number, number, number]; rotDeg: number; center: [number, number]; item: boolean };
  hit: THREE.LineSegments | null;
  /** Заблокирован в редакторе: не выбирается кликом и не перетаскивается. */
  locked?: boolean;
  dispose(): void;
}

const VIEW_HELPER_SIZE = 128;
const BG_COLOR = 0x37383c;

export class ScreenScene {
  readonly renderer: THREE.WebGLRenderer;
  readonly scene = new THREE.Scene();
  readonly camera: THREE.PerspectiveCamera;
  readonly controls: OrbitControls;
  private gizmo: TransformControls;
  private gizmoHelper: THREE.Object3D;
  private viewHelper: ViewHelper;
  private clock = new THREE.Clock();
  private nodes = new Map<string, Node>();
  private bgNode: Node | null = null;
  private grid: THREE.Group;
  /** Экранный оверлей выделения (SVG поверх canvas): плоский контур по видимым габаритам, как outline в Blender. */
  private overlay: SVGSVGElement;
  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private dragging: { uid: string; plane: THREE.Plane; offset: THREE.Vector3; start: [number, number, number]; moved: boolean; pointerId: number } | null = null;
  private downAt: { x: number; y: number; button: number; onGizmo: boolean } | null = null;
  private gizmoDragging = false;
  private gizmoStart: THREE.Vector3 | null = null;
  private screen: Screen | null = null;
  private selected: string | null = null;
  /** Остальные выделенные (без гизмо, с рамкой). */
  private extraSelected: string[] = [];
  /** Рамка выделения (Shift+драг по пустому): экранные координаты. */
  private marquee: { x0: number; y0: number; x1: number; y1: number; el: HTMLDivElement } | null = null;
  private hovered: string | null = null;
  private frame = 0;
  /** Идущие hover-анимации по uid (переживают перестройку узла). */
  private tweens = new Map<string, HoverTween>();
  /** Идущий превью-morph: экран-цель, план и момент старта. */
  private morph: { to: Screen; plan: MorphPlan; t0: number; lastStep: number; onDone: () => void; extra: Node[]; bgExtra: Node | null } | null = null;
  /** Последняя применённая дельта по uid - чтобы новая анимация стартовала с текущего состояния. */
  private deltas = new Map<string, HoverDelta>();
  showHitAreas = true;
  showGrid = true;
  hoverPreview = true;
  /** Шаг привязки к сетке при перетаскивании (0 - без привязки). */
  snap = 0.05;
  /** Умные направляющие (края/центры/зазоры других виджетов), как в PowerPoint. */
  smartGuides = true;
  private guideGroup = new THREE.Group();

  constructor(readonly canvas: HTMLCanvasElement, private cb: SceneCallbacks) {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false });
    this.renderer.setPixelRatio(Math.min(2, window.devicePixelRatio));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.autoClear = false;
    this.scene.background = new THREE.Color(BG_COLOR);
    this.camera = new THREE.PerspectiveCamera(calibration.fov, 1, 0.05, 200);

    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.15;
    this.controls.zoomToCursor = true;
    this.controls.screenSpacePanning = true;
    this.controls.mouseButtons = { LEFT: THREE.MOUSE.ROTATE, MIDDLE: THREE.MOUSE.PAN, RIGHT: THREE.MOUSE.PAN };
    this.controls.minDistance = 0.1;
    this.controls.maxDistance = 60;

    this.gizmo = new TransformControls(this.camera, canvas);
    this.gizmo.setMode('translate');
    this.gizmo.setSize(0.7);
    this.gizmoHelper = this.gizmo.getHelper();
    this.scene.add(this.gizmoHelper);
    this.gizmo.addEventListener('dragging-changed', (e: any) => {
      this.gizmoDragging = !!e.value;
      this.controls.enabled = !e.value;
      if (e.value) { this.cb.onSelect(this.selected); this.gizmoStart = this.gizmo.object ? this.gizmo.object.position.clone() : null; }
      else this.showGuides([], null);
    });
    this.gizmo.addEventListener('objectChange', () => this.onGizmoMove());

    this.overlay = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    this.overlay.setAttribute('class', 'select-overlay');
    canvas.parentElement?.appendChild(this.overlay);

    this.viewHelper = new ViewHelper(this.camera, canvas);
    this.viewHelper.center = this.controls.target;
    (this.viewHelper as any).setLabels?.('X', 'Y', 'Z');

    this.grid = this.makeGrid();
    this.scene.add(this.grid);
    this.guideGroup.renderOrder = 12;
    this.scene.add(this.guideGroup);
    this.scene.add(this.makeFloor());
    this.resetCamera('player');

    canvas.addEventListener('pointerdown', this.onDown);
    canvas.addEventListener('pointermove', this.onMoveEv);
    canvas.addEventListener('pointerup', this.onUp);
    canvas.addEventListener('pointerleave', this.onLeave);
    // СКМ: не давать браузеру включать автопрокрутку страницы; ПКМ: без контекстного меню
    canvas.addEventListener('mousedown', this.preventAux);
    canvas.addEventListener('auxclick', this.preventAux);
    canvas.addEventListener('contextmenu', this.preventAux);
    // потеря WebGL-контекста (alt-tab, сон, переключение GPU): не падать, восстановить сцену
    canvas.addEventListener('webglcontextlost', this.onContextLost);
    canvas.addEventListener('webglcontextrestored', this.onContextRestored);
    this.loop();
  }

  private onContextLost = (e: Event): void => { e.preventDefault(); cancelAnimationFrame(this.frame); };
  private onContextRestored = (): void => { this.rebuildAll(); this.loop(); };

  /** Сбросить незавершённое взаимодействие (уход со вкладки, alt-tab): перетаскивание, захват курсора, гизмо. */
  cancelInteraction(): void {
    if (this.dragging) { try { this.canvas.releasePointerCapture(this.dragging.pointerId); } catch { /* ignore */ } }
    if (this.marquee) { this.marquee.el.remove(); this.marquee = null; }
    this.dragging = null;
    this.downAt = null;
    this.gizmoDragging = false;
    this.controls.enabled = true;
    this.showGuides([], null);
    this.canvas.style.cursor = 'default';
    this.cb.onHover(null);
    this.clock.getDelta(); // чтобы после паузы ViewHelper не прыгал на огромном dt
  }

  dispose(): void {
    cancelAnimationFrame(this.frame);
    this.overlay.remove();
    this.canvas.removeEventListener('pointerdown', this.onDown);
    this.canvas.removeEventListener('pointermove', this.onMoveEv);
    this.canvas.removeEventListener('pointerup', this.onUp);
    this.canvas.removeEventListener('pointerleave', this.onLeave);
    this.canvas.removeEventListener('mousedown', this.preventAux);
    this.canvas.removeEventListener('auxclick', this.preventAux);
    this.canvas.removeEventListener('contextmenu', this.preventAux);
    this.canvas.removeEventListener('webglcontextlost', this.onContextLost);
    this.canvas.removeEventListener('webglcontextrestored', this.onContextRestored);
    for (const n of this.nodes.values()) n.dispose();
    this.bgNode?.dispose();
    this.gizmo.detach();
    this.gizmo.dispose();
    this.viewHelper.dispose();
    this.controls.dispose();
    this.renderer.dispose();
  }

  private preventAux = (e: MouseEvent): void => {
    if (e.type === 'contextmenu' || e.button === 1) e.preventDefault();
  };

  // ---------------------------------------------------------------- камера

  /** Камера для сохранения на экран: позиция, цель, fov. */
  getCamera(): { pos: [number, number, number]; target: [number, number, number] } {
    const p = this.camera.position, t = this.controls.target;
    return { pos: [p.x, p.y, p.z], target: [t.x, t.y, t.z] };
  }
  setCamera(c: { pos: [number, number, number]; target: [number, number, number] }): void {
    this.camera.position.set(c.pos[0], c.pos[1], c.pos[2]); this.controls.target.set(c.target[0], c.target[1], c.target[2]);
    this.controls.update();
  }

  /** Прямоугольник фона в плоскости экрана (мировые x/y) или null. */
  backgroundRect(): Rect | null {
    return this.bgNode ? this.rectAt(this.bgNode, this.bgNode.group.position.x, this.bgNode.group.position.y) : null;
  }

  resetCamera(mode: 'player' | 'center' | 'front' | 'fit'): void {
    const d = calibration.eyeDistance, h = calibration.eyeAboveCenter;
    this.camera.fov = calibration.fov;
    if (mode === 'player') { this.camera.position.set(0, h, d); this.controls.target.set(0, h, 0); }
    else if (mode === 'center') { this.camera.position.set(0, h, d); this.controls.target.set(0, 0, 0); }
    else if (mode === 'front') { this.camera.position.set(0, 0, d * 1.6); this.controls.target.set(0, 0, 0); }
    else {
      const box = this.contentBox();
      const c = box.getCenter(new THREE.Vector3()), size = box.getSize(new THREE.Vector3());
      const fov = (this.camera.fov * Math.PI) / 180;
      const dist = Math.max(size.y / 2 / Math.tan(fov / 2), size.x / 2 / Math.tan(fov / 2) / this.camera.aspect) * 1.15 + 0.5;
      this.camera.position.set(c.x, c.y, c.z + dist); this.controls.target.set(c.x, c.y, c.z);
    }
    this.camera.updateProjectionMatrix();
    this.controls.update();
  }

  /** Повернуть камеру на ось (как кнопки ViewHelper), сохраняя цель и расстояние. */
  alignTo(axis: 'x' | 'y' | 'z', negative = false): void {
    const t = this.controls.target;
    const dist = this.camera.position.distanceTo(t);
    const dir = new THREE.Vector3(axis === 'x' ? 1 : 0, axis === 'y' ? 1 : 0, axis === 'z' ? 1 : 0);
    if (negative) dir.negate();
    this.camera.position.copy(t).addScaledVector(dir, dist);
    this.camera.up.set(0, 1, 0);
    this.camera.lookAt(t);
    this.controls.update();
  }

  /** Центр камеры на выбранном виджете (или на всём содержимом). */
  /** Проиграть переход на экран `to` как в игре; по окончании - onDone (вызывающий переключает экран). */
  playMorph(to: Screen, onDone: () => void): void {
    if (!this.screen || this.morph) return;
    this.cancelInteraction();
    this.tweens.clear(); this.deltas.clear();
    const plan = planMorph(this.screen, to);
    // fade-in: узлы нового экрана с нулевым масштабом
    const extra: Node[] = [];
    for (const w of plan.fadeIn) {
      const n = w.type === 'ITEM_BUTTON' ? this.buildItem(w) : this.buildText(w);
      n.base.scale = [0, 0, 1]; this.applyDelta(n, IDENTITY);
      this.scene.add(n.group); extra.push(n);
    }
    let bgExtra: Node | null = null;
    if (!plan.bg.from && plan.bg.to) { bgExtra = this.buildBackground(plan.bg.to); bgExtra.base.scale = [0, 0, 1]; this.applyDelta(bgExtra, IDENTITY); this.scene.add(bgExtra.group); }
    this.morph = { to, plan, t0: performance.now(), lastStep: -1, onDone, extra, bgExtra };
    this.gizmo.detach(); this.gizmoHelper.visible = false;
  }

  get morphing(): boolean { return !!this.morph; }

  private tickMorph(): void {
    const m = this.morph; if (!m) return;
    const f = Math.min(1, (performance.now() - m.t0) / m.plan.ms);
    const step = Math.floor(f * m.plan.steps);
    const stepChanged = step !== m.lastStep; m.lastStep = step;
    // matched: геометрия каждый кадр, текст/цвет - по шагам (перестройка узла)
    for (const { from, to } of m.plan.matched) {
      const n = this.nodes.get(from.uid); if (!n) continue;
      const w = matchedAt(from, to, f, m.plan.steps);
      if (stepChanged || f >= 1) {
        // перестроить узел под текущий текст/цвет (размер текстуры может измениться)
        const old = n; this.scene.remove(old.group); old.dispose();
        const nn = w.type === 'ITEM_BUTTON' ? this.buildItem(w) : this.buildText(w);
        nn.locked = true; this.nodes.set(from.uid, nn); this.scene.add(nn.group);
        continue;
      }
      n.group.position.copy(this.worldPos(w.position, w.type === 'ITEM_BUTTON' ? calibration.itemDepthOffset : calibration.textDepthOffset));
      n.base.tr = [w.translation[0], w.translation[1], w.translation[2]];
      n.base.scale = [w.scale[0], w.scale[1], w.scale[2]];
      n.base.rotDeg = w.rotation;
      this.applyDelta(n, IDENTITY);
    }
    // fade-out: масштаб → 0
    for (const w of m.plan.fadeOut) {
      const n = this.nodes.get(w.uid); if (!n) continue;
      n.base.scale = [w.scale[0] * (1 - f), w.scale[1] * (1 - f), 1]; this.applyDelta(n, IDENTITY);
    }
    // fade-in: с тика 1 (1/D пути) масштаб 0 → цель
    const fi = Math.max(0, (f * m.plan.ms - 50) / Math.max(1, m.plan.ms - 50));
    m.extra.forEach((n, i) => { const w = m.plan.fadeIn[i]; n.base.scale = [w.scale[0] * fi, w.scale[1] * fi, 1]; this.applyDelta(n, IDENTITY); });
    // фон
    if (m.plan.bg.from && m.plan.bg.to && this.bgNode && (stepChanged || f >= 1)) {
      this.scene.remove(this.bgNode.group); this.bgNode.dispose();
      this.bgNode = this.buildBackground(backgroundAt(m.plan.bg.from, m.plan.bg.to, Math.min(1, step / m.plan.steps)));
      this.scene.add(this.bgNode.group);
    } else if (m.plan.bg.from && m.plan.bg.to && this.bgNode) {
      const b = backgroundAt(m.plan.bg.from, m.plan.bg.to, f);
      this.bgNode.group.position.copy(this.worldPos(b.position, calibration.textDepthOffset));
      this.bgNode.base.scale = [b.scale[0], b.scale[1], 1]; this.applyDelta(this.bgNode, IDENTITY);
    } else if (m.plan.bg.from && !m.plan.bg.to && this.bgNode) {
      this.bgNode.base.scale = [m.plan.bg.from.scale[0] * (1 - f), m.plan.bg.from.scale[1] * (1 - f), 1]; this.applyDelta(this.bgNode, IDENTITY);
    } else if (m.bgExtra && m.plan.bg.to) {
      m.bgExtra.base.scale = [m.plan.bg.to.scale[0] * fi, m.plan.bg.to.scale[1] * fi, 1]; this.applyDelta(m.bgExtra, IDENTITY);
    }
    if (f >= 1) {
      for (const n of m.extra) { this.scene.remove(n.group); n.dispose(); }
      if (m.bgExtra) { this.scene.remove(m.bgExtra.group); m.bgExtra.dispose(); }
      this.morph = null;
      m.onDone();
    }
  }

  /** Прямоугольники видимых виджетов в плоскости экрана (мировые x/y; x модели = x · positionXSign). */
  widgetRects(uids: string[]): Map<string, Rect> {
    const out = new Map<string, Rect>();
    for (const uid of uids) {
      const n = this.nodes.get(uid); if (!n) continue;
      out.set(uid, this.rectAt(n, n.group.position.x, n.group.position.y));
    }
    return out;
  }

  focusSelected(): void {
    const n = this.selected ? this.nodes.get(this.selected) : null;
    const box = n ? new THREE.Box3().setFromObject(n.group) : this.contentBox();
    const c = box.getCenter(new THREE.Vector3());
    const offset = this.camera.position.clone().sub(this.controls.target);
    this.controls.target.copy(c);
    this.camera.position.copy(c).add(offset);
    this.controls.update();
  }

  private contentBox(): THREE.Box3 {
    const box = new THREE.Box3();
    for (const n of this.nodes.values()) box.expandByObject(n.group);
    if (this.bgNode) box.expandByObject(this.bgNode.group);
    if (box.isEmpty()) box.set(new THREE.Vector3(-1, -1, 0), new THREE.Vector3(1, 1, 0));
    return box;
  }

  private makeGrid(): THREE.Group {
    const g = new THREE.Group();
    // сетка в плоскости экрана (z = 0), шаг 1 блок, тонкие деления 0.25
    const fine = new THREE.GridHelper(8, 32, 0x8a8c93, 0x55575d);
    fine.rotation.x = Math.PI / 2;
    fine.position.z = -0.02;
    (fine.material as THREE.Material).transparent = true;
    (fine.material as THREE.Material).opacity = 0.25;
    g.add(fine);
    const coarse = new THREE.GridHelper(8, 8, 0xa0a3aa, 0x74777e);
    coarse.rotation.x = Math.PI / 2;
    coarse.position.z = -0.019;
    (coarse.material as THREE.Material).transparent = true;
    (coarse.material as THREE.Material).opacity = 0.35;
    g.add(coarse);
    const axes = new THREE.AxesHelper(0.5);
    axes.position.z = -0.015;
    g.add(axes);
    return g;
  }

  /** Пол (ноги игрока, y = -0.9): тёмная плоскость с сеткой, без травы/неба. */
  private makeFloor(): THREE.Group {
    const g = new THREE.Group();
    const geo = new THREE.PlaneGeometry(60, 60);
    const mat = new THREE.MeshBasicMaterial({ color: 0x2c2d31, transparent: true, opacity: 0.85, depthWrite: false });
    const m = new THREE.Mesh(geo, mat);
    m.rotation.x = -Math.PI / 2;
    m.position.y = -0.9;
    g.add(m);
    const grid = new THREE.GridHelper(60, 60, 0x4a4c52, 0x3d3f45);
    grid.position.y = -0.899;
    (grid.material as THREE.Material).transparent = true;
    (grid.material as THREE.Material).opacity = 0.6;
    g.add(grid);
    return g;
  }

  // ---------------------------------------------------------------- экран

  setScreen(s: Screen | null): void {
    if (this.screen !== s) { this.tweens.clear(); this.deltas.clear(); }
    this.screen = s;
    this.rebuildAll();
  }

  setSelection(primary: string | null, extra: string[]): void {
    this.extraSelected = extra.filter((u) => u !== primary);
    this.setSelected(primary);
  }

  setSelected(uid: string | null): void {
    this.selected = uid;
    this.updateSelectionBox();
    this.attachGizmo();
  }

  setHovered(uid: string | null): void {
    if (this.hovered === uid) return;
    const prev = this.hovered;
    this.hovered = uid;
    if (prev) { this.startTween(prev, false); this.rebuildWidget(prev); }
    if (uid) { this.startTween(uid, true); this.rebuildWidget(uid); }
  }

  /** Запустить hover-анимацию виджета (или откат): цель - из его hoverAnimation, старт - с текущей дельты. */
  private startTween(uid: string, hovering: boolean): void {
    const w = this.screen?.widgets.find((x) => x.uid === uid);
    const a = w?.hoverAnimation ?? null;
    if (!w || !this.hoverPreview || !a) { this.tweens.delete(uid); this.deltas.delete(uid); return; }
    const from = this.currentDelta(uid);
    if (!hovering && !a.reverseOnExit) { this.tweens.delete(uid); return; } // плагин так и оставляет
    const to = hovering ? targetOf(a) : IDENTITY;
    this.tweens.set(uid, { t0: performance.now(), ms: durationMs(a), from, to, ease: easingOf(a) });
  }

  private currentDelta(uid: string): HoverDelta {
    const tw = this.tweens.get(uid);
    return tw ? tweenAt(tw, performance.now()).d : (this.deltas.get(uid) ?? IDENTITY);
  }

  /** Наложить hover-дельту на pivot узла поверх базовой трансформации (translation · leftRotation · scale, вокруг якоря). */
  private applyDelta(n: Node, d: HoverDelta): void {
    const b = n.base;
    const sx = b.scale[0] * d.mul[0], sy = b.scale[1] * d.mul[1];
    const xs = calibration.translationXSign;
    // текст: якорь - низ по центру; базовый поворот плагин делает вокруг центра, сдвигая якорь на c − R·c
    const hc = b.center[1] * b.scale[1];
    const a = THREE.MathUtils.degToRad(b.rotDeg);
    const comp: [number, number] = b.item || b.rotDeg === 0 ? [0, 0] : [hc * Math.sin(a), hc - hc * Math.cos(a)];
    n.pivot.position.set((b.tr[0] + d.offset[0]) * xs + comp[0] * xs, b.tr[1] + d.offset[1] + comp[1], b.tr[2] + d.offset[2]);
    n.pivot.scale.set(sx, sy, 1);
    const q = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 0, 1), a);
    if (d.angle) q.multiply(new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(d.axis[0] * xs, d.axis[1], d.axis[2]).normalize(), THREE.MathUtils.degToRad(d.angle)));
    n.pivot.quaternion.copy(q);
  }

  /** Продвинуть hover-анимации (раз в кадр). */
  private tickTweens(): void {
    if (!this.tweens.size) return;
    const now = performance.now();
    for (const [uid, tw] of this.tweens) {
      const { d, done } = tweenAt(tw, now);
      this.deltas.set(uid, d);
      const n = this.nodes.get(uid); if (n) this.applyDelta(n, d);
      if (done) { this.tweens.delete(uid); if (isIdentity(d)) this.deltas.delete(uid); }
    }
  }

  private attachGizmo(): void {
    const sel = this.selected ? this.nodes.get(this.selected) : null;
    const n = sel && !sel.locked ? sel : null; // у заблокированного гизмо нет
    if (n) { if (this.gizmo.object !== n.group) this.gizmo.attach(n.group); }
    else this.gizmo.detach();
    this.gizmoHelper.visible = !!n;
  }

  /** Полная перестройка (смена экрана, калибровка, фон). */
  rebuildAll(): void {
    this.gizmo.detach();
    for (const n of this.nodes.values()) { this.scene.remove(n.group); n.dispose(); }
    this.nodes.clear();
    if (this.bgNode) { this.scene.remove(this.bgNode.group); this.bgNode.dispose(); this.bgNode = null; }
    this.grid.visible = this.showGrid;
    if (this.screen) {
      if (this.screen.background) {
        this.bgNode = this.buildBackground(this.screen.background);
        this.scene.add(this.bgNode.group);
      }
      for (const w of this.screen.widgets) this.rebuildWidget(w.uid);
    }
    this.updateSelectionBox();
    this.attachGizmo();
  }

  rebuildBackground(): void {
    if (this.bgNode) { this.scene.remove(this.bgNode.group); this.bgNode.dispose(); this.bgNode = null; }
    if (this.screen?.background) { this.bgNode = this.buildBackground(this.screen.background); this.scene.add(this.bgNode.group); }
  }

  /** Перестроить один виджет (изменились его свойства). */
  rebuildWidget(uid: string): void {
    const old = this.nodes.get(uid);
    if (old) { if (this.gizmo.object === old.group) this.gizmo.detach(); this.scene.remove(old.group); old.dispose(); this.nodes.delete(uid); }
    const w = this.screen?.widgets.find((x) => x.uid === uid);
    // скрытый виджет не строится вовсе: его нет ни в сцене, ни в выборе, ни среди привязок
    if (!w || w.hidden) { if (this.selected === uid) { this.updateSelectionBox(); this.attachGizmo(); } return; }
    const n = w.type === 'ITEM_BUTTON' ? this.buildItem(w) : this.buildText(w);
    n.locked = !!w.locked;
    const d = this.deltas.get(uid); if (d) this.applyDelta(n, d);
    this.nodes.set(uid, n);
    this.scene.add(n.group);
    if (this.selected === uid) { this.updateSelectionBox(); this.attachGizmo(); }
  }

  /** Только позиция (перетаскивание) - без перестройки текстуры. */
  updatePosition(uid: string): void {
    const w = this.screen?.widgets.find((x) => x.uid === uid);
    const n = this.nodes.get(uid);
    if (!w || !n) return;
    n.group.position.copy(this.worldPos(w.position, w.type === 'ITEM_BUTTON' ? calibration.itemDepthOffset : calibration.textDepthOffset));
    if (this.selected === uid) this.updateSelectionBox();
  }

  private worldPos(p: [number, number, number], depthOffset: number): THREE.Vector3 {
    return new THREE.Vector3(p[0] * calibration.positionXSign, p[1], -p[2] + depthOffset);
  }

  /** Обратно: мировая позиция группы -> position модели. */
  private modelPos(v: THREE.Vector3, w: Widget): [number, number, number] {
    const depth = w.type === 'ITEM_BUTTON' ? calibration.itemDepthOffset : calibration.textDepthOffset;
    return [round(v.x * calibration.positionXSign), round(v.y), round(depth - v.z)];
  }

  private buildText(w: Widget): Node {
    const hovered = this.hoverPreview && this.hovered === w.uid;
    const text = widgetText(w, hovered);
    const bg = hovered ? w.hoveredBackgroundColor : w.backgroundColor;
    const alpha = hovered ? w.hoveredBackgroundAlpha : w.backgroundAlpha;
    const { texture, layout: L } = buildTextTexture(text, w.alignment, bg, alpha, 4, w.lineWidth);
    const objectOnly = L.lines.length === 1 && L.lines[0].length > 0 && L.lines[0].every((r) => !!r.sprite);
    // видимые габариты без фона: одиночный спрайт/голова - 8 px (верх строки), текст - без пустой нижней строки-отступа (l - 1)
    const inkH = objectOnly ? 8 : Math.max(1, L.height - 1);
    return this.textNode(w.uid, texture, L.bgW, L.bgH, w.scale, w.translation, w.position, calibration.textDepthOffset, w.tolerance, true, w.rotation, alpha < 26 ? { width: L.width, height: inkH } : null, objectOnly);
  }

  private buildBackground(b: Background): Node {
    const { texture, layout: L } = buildTextTexture(b.text, 'CENTERED', b.color, b.alpha);
    // плагин: translation.x = -scaleX/80 + user.x (TransformationUtil.createAlignedTranslation);
    // якорь: точка (ax, ay) прямоугольника фона ставится в начало координат (ScreenSupport.backgroundConfig)
    const wBlocks = L.bgW * calibration.px * b.scale[0], hBlocks = L.bgH * calibration.px * b.scale[1];
    const ax = -(b.anchor[0] - 0.5) * wBlocks, ay = -b.anchor[1] * hBlocks;
    const tr: [number, number, number] = [-b.scale[0] / 80 + b.translation[0] + ax, b.translation[1] + ay, b.translation[2]];
    return this.textNode('__background', texture, L.bgW, L.bgH, b.scale, tr, b.position, calibration.textDepthOffset, null, false);
  }

  private textNode(uid: string, texture: THREE.Texture, bgW: number, bgH: number, scale: [number, number, number], tr: [number, number, number],
    pos: [number, number, number], depth: number, tol: [number, number] | null, selectable: boolean, rotationDeg = 0, ink: { width: number; height: number } | null = null, objectOnly = false): Node {
    const px = calibration.px;
    const wBlocks = bgW * px, hBlocks = bgH * px;
    const geo = new THREE.PlaneGeometry(wBlocks, hBlocks);
    const mat = new THREE.MeshBasicMaterial({ map: texture, transparent: true, side: THREE.FrontSide, depthWrite: true, alphaTest: 0.01 });
    const mesh = new THREE.Mesh(geo, mat);
    // якорь - низ по центру текста. Локальный x фона: [1 - k/2 - bgLeft, 1 + k/2 + bgRight] px
    // (translate(1 - k/2) клиента), центр = 1 + (bgRight - bgLeft)/2 = +0.5 px; по y фон от 0 до bgH.
    const centerX = (1 + (calibration.bgRight - calibration.bgLeft) / 2) * px;
    const centerY = (bgH / 2) * px;
    mesh.position.set(centerX, centerY, 0);
    mesh.userData.uid = selectable ? uid : null;
    // pivot = якорь сущности: translation · поворот · масштаб, как Transformation у Display; hover-дельты ложатся сюда же
    const pivot = new THREE.Group();
    pivot.add(mesh);
    const group = new THREE.Group();
    group.add(pivot);
    group.position.copy(this.worldPos(pos, depth));
    // зона наведения: плагин поднимает центр на половину высоты фона (TextDisplayButtonWidget.hitCenterOffsetY)
    // как плагин: центр зоны на 1 px правее якоря (клиент рисует текст от 1 - k/2) и на l/2 - 1 px выше
    // (строки текста локально [-1, l-1]); одиночный спрайт/голова - верхние 8 px строки, центр ровно на l/2
    const hit = tol ? this.hitRect(tol, tr, (bgH / 2 - (objectOnly ? 0 : 1)) * px * scale[1], 1 * px * scale[0], rotationDeg) : null;
    if (hit) group.add(hit);
    // видимые габариты: при невидимом фоне (альфа < 26 клиент фон не рисует) - прямоугольник глифов, иначе фон
    const planeL = centerX - wBlocks / 2;
    const visual = ink
      ? { l: planeL + calibration.bgLeft * px, r: planeL + (calibration.bgLeft + ink.width) * px, b: hBlocks - (calibration.bgTop + ink.height) * px, t: hBlocks - calibration.bgTop * px }
      : { l: planeL, r: planeL + wBlocks, b: 0, t: hBlocks };
    const n: Node = { uid, group, pivot, mesh, hit, visual, base: { tr: [tr[0], tr[1], tr[2]], scale: [scale[0], scale[1], scale[2]], rotDeg: rotationDeg, center: [centerX, centerY], item: false },
      dispose: () => { geo.dispose(); mat.dispose(); texture.dispose(); hit?.geometry.dispose(); } };
    this.applyDelta(n, IDENTITY);
    return n;
  }

  private buildItem(w: Widget): Node {
    const tex = itemTexture(w.material);
    const size = calibration.itemSize;
    const geo = new THREE.PlaneGeometry(size, size);
    const mat = tex
      ? new THREE.MeshBasicMaterial({ map: tex, transparent: true, alphaTest: 0.05 })
      : new THREE.MeshBasicMaterial({ color: 0xff00ff, transparent: true, opacity: 0.6 });
    const mesh = new THREE.Mesh(geo, mat);
    const hovered = this.hoverPreview && this.hovered === w.uid && w.glowOnHover;
    mesh.userData.uid = w.uid;
    const pivot = new THREE.Group();
    pivot.add(mesh);
    const group = new THREE.Group();
    group.add(pivot);
    if (hovered) {
      const gc = w.glowColor ? new THREE.Color(w.glowColor[0] / 255, w.glowColor[1] / 255, w.glowColor[2] / 255) : new THREE.Color(0xffffff);
      const edge = new THREE.LineSegments(new THREE.EdgesGeometry(geo), new THREE.LineBasicMaterial({ color: gc }));
      pivot.add(edge);
    }
    group.position.copy(this.worldPos(w.position, calibration.itemDepthOffset));
    const hit = this.hitRect(w.tolerance, w.translation, 0, 0, w.rotation);
    group.add(hit);
    const n: Node = { uid: w.uid, group, pivot, mesh, hit, visual: { l: -size / 2, r: size / 2, b: -size / 2, t: size / 2 }, base: { tr: [w.translation[0], w.translation[1], w.translation[2]], scale: [w.scale[0], w.scale[1], w.scale[2]], rotDeg: w.rotation, center: [0, 0], item: true },
      dispose: () => { geo.dispose(); mat.dispose(); hit.geometry.dispose(); } };
    this.applyDelta(n, IDENTITY);
    return n;
  }

  /**
   * Зона наведения: прямоугольник tolerance с центром в позиции + translation (+ половина высоты текста у TextDisplay).
   * Поворачивается вместе с виджетом, как в плагине (HitArea с roll): вокруг центра поворота
   * translation + (0, offsetY); смещение offsetX (1 px у текста) уходит по дуге.
   */
  private hitRect(tol: [number, number], tr: [number, number, number], offsetY = 0, offsetX = 0, rotDeg = 0): THREE.LineSegments {
    const g = new THREE.BufferGeometry();
    const hw = tol[0], hh = tol[1];
    const v = new Float32Array([-hw, -hh, 0, hw, -hh, 0, hw, -hh, 0, hw, hh, 0, hw, hh, 0, -hw, hh, 0, -hw, hh, 0, -hw, -hh, 0]);
    g.setAttribute('position', new THREE.BufferAttribute(v, 3));
    const l = new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: 0x00e5ff, transparent: true, opacity: 0.7, depthTest: false }));
    const a = THREE.MathUtils.degToRad(rotDeg);
    l.position.set(tr[0] * calibration.translationXSign + offsetX * Math.cos(a), tr[1] + offsetY + offsetX * Math.sin(a), tr[2] + 0.002);
    l.rotation.z = a;
    l.visible = this.showHitAreas;
    l.renderOrder = 10;
    return l;
  }

  setShowHitAreas(on: boolean): void { this.showHitAreas = on; for (const n of this.nodes.values()) if (n.hit) n.hit.visible = on; }
  setShowGrid(on: boolean): void { this.showGrid = on; this.grid.visible = on; }
  setSnap(step: number): void { this.snap = step; }

  /** Контур выделения рисуется каждый кадр в SVG-оверлее (drawSelection); тут - только совместимость с вызовами. */
  private updateSelectionBox(): void { this.drawSelection(); }

  /** Экранные координаты углов видимого прямоугольника узла (с поворотом и hover-дельтами); null - за камерой. */
  private screenQuad(n: Node): [number, number][] | null {
    n.group.updateMatrixWorld(true);
    const W = this.canvas.clientWidth, H = this.canvas.clientHeight;
    const v = n.visual; const out: [number, number][] = [];
    for (const [x, y] of [[v.l, v.b], [v.r, v.b], [v.r, v.t], [v.l, v.t]]) {
      const p = new THREE.Vector3(x, y, 0).applyMatrix4(n.pivot.matrixWorld).project(this.camera);
      if (p.z > 1) return null;
      out.push([((p.x + 1) / 2) * W, ((1 - p.y) / 2) * H]);
    }
    return out;
  }

  private drawSelection(): void {
    const W = this.canvas.clientWidth, H = this.canvas.clientHeight;
    this.overlay.setAttribute('viewBox', `0 0 ${W} ${H}`);
    let html = '';
    const poly = (n: Node, primary: boolean) => {
      const q = this.screenQuad(n); if (!q) return;
      // контур чуть снаружи габаритов: сдвиг каждой вершины от центра на 1.5 px
      const cx = q.reduce((a, p) => a + p[0], 0) / 4, cy = q.reduce((a, p) => a + p[1], 0) / 4;
      const pts = q.map(([x, y]) => { const dx = x - cx, dy = y - cy, l = Math.hypot(dx, dy) || 1; return `${(x + (dx / l) * 1.5).toFixed(1)},${(y + (dy / l) * 1.5).toFixed(1)}`; }).join(' ');
      html += `<polygon points="${pts}" class="${primary ? 'sel' : 'sel extra'}"/>`;
    };
    for (const uid of this.extraSelected) { const n = this.nodes.get(uid); if (n) poly(n, false); }
    const n = this.selected ? this.nodes.get(this.selected) : null;
    if (n) poly(n, true);
    if (this.overlay.innerHTML !== html) this.overlay.innerHTML = html;
  }

  // ---------------------------------------------------------------- мышь

  private setPointer(e: PointerEvent | MouseEvent): void {
    const r = this.canvas.getBoundingClientRect();
    this.pointer.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
    this.raycaster.setFromCamera(this.pointer, this.camera);
  }

  private pick(): string | null {
    const meshes = [...this.nodes.values()].filter((n) => !n.locked).map((n) => n.mesh);
    const hits = this.raycaster.intersectObjects(meshes, false);
    for (const h of hits) { const uid = h.object.userData.uid as string | null; if (uid) return uid; }
    return null;
  }

  private inViewHelper(e: MouseEvent): boolean {
    const r = this.canvas.getBoundingClientRect();
    return e.clientX - r.left > r.width - VIEW_HELPER_SIZE && e.clientY - r.top > r.height - VIEW_HELPER_SIZE;
  }

  private onGizmoMove(): void {
    const uid = this.selected; if (!uid) return;
    const w = this.screen?.widgets.find((x) => x.uid === uid);
    const n = this.nodes.get(uid);
    if (!w || !n) return;
    // привязка к сетке и направляющим - своя, только по осям, которые двигает гизмо
    let axis = String((this.gizmo as any).axis ?? '');
    if (axis === 'XYZ') axis = 'XY'; // центральная ручка: двигаем в плоскости экрана
    const pos = n.group.position, st = this.gizmoStart ?? pos.clone();
    const step = this.snap;
    const sn = (v: number) => (step > 0 ? Math.round(v / step) * step : v);
    if (!axis.includes('X')) pos.x = st.x;
    if (!axis.includes('Y')) pos.y = st.y;
    if (axis.includes('Z')) pos.z = sn(pos.z); else pos.z = st.z;
    let guides: Guide[] = [];
    let sx = false, sy = false;
    if (this.smartGuides && !axis.includes('Z') && axis.length) {
      const plane = new THREE.Plane(new THREE.Vector3(0, 0, 1), -pos.z);
      const m = this.rectAt(n, pos.x, pos.y);
      const r = computeSnap(m, this.snapPartners(uid, plane), this.worldPerPixel(plane) * 8, this.snapAxes());
      if (axis.includes('X') && r.sx) { pos.x += r.dx; sx = true; }
      if (axis.includes('Y') && r.sy) { pos.y += r.dy; sy = true; }
      guides = r.guides.filter((g) => (g.dir === 'v' ? axis.includes('X') : axis.includes('Y')));
      this.showGuides(guides, plane);
    }
    // сетка - только по осям без сработавшей направляющей
    if (axis.includes('X') && !sx) pos.x = sn(pos.x);
    if (axis.includes('Y') && !sy) pos.y = sn(pos.y);
    this.cb.onMove(uid, this.modelPos(pos, w));
    this.updateSelectionBox();
  }

  private onDown = (e: PointerEvent): void => {
    this.downAt = { x: e.clientX, y: e.clientY, button: e.button, onGizmo: this.gizmoDragging || (this.gizmo as any).axis != null };
    if (e.button !== 0) return;
    if (this.downAt.onGizmo) { this.controls.enabled = false; return; }
    if (this.inViewHelper(e)) { this.controls.enabled = false; return; }
    this.setPointer(e);
    const uid = this.pick();
    const additive = e.shiftKey || e.ctrlKey || e.metaKey;
    if (!uid) {
      // пустое место: Shift - рамка выделения, иначе OrbitControls вращает; выделение снимется по клику без движения
      if (e.shiftKey) { this.startMarquee(e); this.controls.enabled = false; this.canvas.setPointerCapture(e.pointerId); }
      return;
    }
    if (additive) { this.cb.onSelect(uid, true); this.controls.enabled = false; return; } // добавить к выделению без перетаскивания
    if (!this.isSelected(uid)) this.cb.onSelect(uid);
    const w = this.screen?.widgets.find((x) => x.uid === uid);
    const n = this.nodes.get(uid);
    if (!w || !n) return;
    // плоскость перетаскивания - плоскость виджета (z = его глубина)
    const plane = new THREE.Plane(new THREE.Vector3(0, 0, 1), -n.group.position.z);
    const p = new THREE.Vector3();
    if (!this.raycaster.ray.intersectPlane(plane, p)) return;
    this.dragging = { uid, plane, offset: p.clone().sub(n.group.position), start: [...w.position] as [number, number, number], moved: false, pointerId: e.pointerId };
    this.controls.enabled = false;
    this.canvas.setPointerCapture(e.pointerId);
    // Тащим виджет в обычном (не hover) виде: привязки считаются по его размеру в покое
    if (this.hovered === uid) this.cb.onHover(null);
    this.tweens.delete(uid); this.deltas.delete(uid); this.applyDelta(n, IDENTITY);
  };

  private isSelected(uid: string): boolean { return this.selected === uid || this.extraSelected.includes(uid); }

  private startMarquee(e: PointerEvent): void {
    const r = this.canvas.getBoundingClientRect();
    const el = document.createElement('div');
    el.className = 'marquee';
    this.canvas.parentElement?.appendChild(el);
    this.marquee = { x0: e.clientX - r.left, y0: e.clientY - r.top, x1: e.clientX - r.left, y1: e.clientY - r.top, el };
    this.drawMarquee();
  }
  private drawMarquee(): void {
    const m = this.marquee; if (!m) return;
    const l = Math.min(m.x0, m.x1), t = Math.min(m.y0, m.y1);
    Object.assign(m.el.style, { left: l + 'px', top: t + 'px', width: Math.abs(m.x1 - m.x0) + 'px', height: Math.abs(m.y1 - m.y0) + 'px' });
  }
  /** Виджеты, чей центр (проекция mesh) попал в рамку. */
  private finishMarquee(): void {
    const m = this.marquee; if (!m) return;
    m.el.remove(); this.marquee = null;
    const l = Math.min(m.x0, m.x1), r = Math.max(m.x0, m.x1), t = Math.min(m.y0, m.y1), b = Math.max(m.y0, m.y1);
    if (r - l < 3 && b - t < 3) return;
    const W = this.canvas.clientWidth, H = this.canvas.clientHeight;
    const out: string[] = [];
    for (const n of this.nodes.values()) {
      if (n.locked) continue;
      n.mesh.updateWorldMatrix(true, false);
      const c = this.visualBox(n).getCenter(new THREE.Vector3()).project(this.camera);
      if (c.z > 1) continue;
      const x = (c.x + 1) / 2 * W, y = (1 - c.y) / 2 * H;
      if (x >= l && x <= r && y >= t && y <= b) out.push(n.uid);
    }
    // порядок как в списке виджетов
    const order = new Map((this.screen?.widgets ?? []).map((w, i) => [w.uid, i]));
    out.sort((a, b2) => (order.get(a) ?? 0) - (order.get(b2) ?? 0));
    this.cb.onSelectMany(out);
  }

  private onMoveEv = (e: PointerEvent): void => {
    this.setPointer(e);
    if (this.marquee) { const r = this.canvas.getBoundingClientRect(); this.marquee.x1 = e.clientX - r.left; this.marquee.y1 = e.clientY - r.top; this.drawMarquee(); return; }
    if (this.dragging) {
      const d = this.dragging;
      if (!d.moved && Math.hypot(e.clientX - this.downAt!.x, e.clientY - this.downAt!.y) < 3) return;
      d.moved = true;
      const p = new THREE.Vector3();
      if (!this.raycaster.ray.intersectPlane(d.plane, p)) return;
      let gx = p.x - d.offset.x, gy = p.y - d.offset.y; // мировые координаты группы
      const step = e.shiftKey ? 0.5 : e.altKey ? 0.01 : this.snap;
      let guides: Guide[] = [];
      // Умные направляющие считаются от «сырой» позиции курсора, а сетка применяется только по тем осям,
      // где направляющая не сработала: иначе округление к сетке (0.05) уводило точку выравнивания за порог
      // прилипания (~8 px) и привязка срабатывала через раз.
      let sx = false, sy = false;
      if (this.smartGuides && !e.ctrlKey) {
        const thr = this.worldPerPixel(d.plane) * 8;
        const n = this.nodes.get(d.uid)!;
        const m = this.rectAt(n, gx, gy);
        const r = computeSnap(m, this.snapPartners(d.uid, d.plane), thr, this.snapAxes());
        if (r.sx) { gx += r.dx; sx = true; }
        if (r.sy) { gy += r.dy; sy = true; }
        guides = r.guides;
      }
      if (step > 0) { if (!sx) gx = Math.round(gx / step) * step; if (!sy) gy = Math.round(gy / step) * step; }
      this.showGuides(guides, d.plane);
      this.cb.onMove(d.uid, [round(gx * calibration.positionXSign), round(gy), d.start[2]]);
      return;
    }
    if (this.gizmoDragging) return;
    if ((this.gizmo as any).axis != null) { this.canvas.style.cursor = 'move'; return; }
    const uid = this.downAt ? null : this.pick();
    this.canvas.style.cursor = uid ? 'grab' : this.downAt ? (this.downAt.button === 0 ? 'grabbing' : 'move') : 'default';
    this.cb.onHover(uid);
  };

  private onUp = (e: PointerEvent): void => {
    const down = this.downAt;
    this.downAt = null;
    if (this.marquee) { try { this.canvas.releasePointerCapture(e.pointerId); } catch { /* ignore */ } this.finishMarquee(); this.controls.enabled = true; return; }
    if (this.dragging) {
      try { this.canvas.releasePointerCapture(e.pointerId); } catch { /* ignore */ }
      this.dragging = null;
      this.showGuides([], null);
      this.controls.enabled = true;
      return;
    }
    if (!this.gizmoDragging) this.controls.enabled = true;
    if (!down || down.button !== 0 || down.onGizmo) return;
    const moved = Math.hypot(e.clientX - down.x, e.clientY - down.y) >= 3;
    if (moved) return;
    if (this.inViewHelper(e)) { this.viewHelper.handleClick(e as unknown as MouseEvent); return; }
    // клик по пустому месту без движения - снять выделение (с Shift - оставить)
    this.setPointer(e);
    if (!this.pick() && !e.shiftKey && !e.ctrlKey && !e.metaKey) this.cb.onSelect(null);
  };

  private onLeave = (e: PointerEvent): void => {
    if (this.dragging) this.onUp(e);
    this.cb.onHover(null);
  };

  // ---------------------------------------------------------------- направляющие

  /** Размер одного экранного пикселя в мировых единицах на плоскости перетаскивания. */
  private worldPerPixel(plane: THREE.Plane): number {
    const dist = Math.abs(plane.distanceToPoint(this.camera.position));
    return (2 * dist * Math.tan((this.camera.fov * Math.PI) / 360)) / Math.max(1, this.canvas.clientHeight);
  }

  /** Мировой бокс видимых габаритов узла (visual через матрицу pivot: масштаб, поворот, hover-дельты). */
  private visualBox(n: Node): THREE.Box3 {
    n.group.updateMatrixWorld(true);
    const v = n.visual, z = 0;
    const box = new THREE.Box3();
    for (const [x, y] of [[v.l, v.b], [v.r, v.b], [v.l, v.t], [v.r, v.t]]) box.expandByPoint(new THREE.Vector3(x, y, z).applyMatrix4(n.pivot.matrixWorld));
    return box;
  }

  /** Прямоугольник видимой части узла в плоскости экрана, если группа стоит в (x, y). Им же пользуются привязки, выравнивание и рамка выделения. */
  private rectAt(n: Node, x: number, y: number): Rect {
    const box = this.visualBox(n);
    const ox = n.group.position.x, oy = n.group.position.y;
    return { l: box.min.x - ox + x, r: box.max.x - ox + x, b: box.min.y - oy + y, t: box.max.y - oy + y };
  }

  /**
   * Прямоугольники, к которым можно прилипать: остальные виджеты и фон, но только те, что сейчас
   * видны в окне (хотя бы частично). Привязка к объекту за кадром сбивает с толку - непонятно,
   * к чему прилипло; дальность среди видимых учитывает сам snap (ближний выигрывает ничью).
   */
  private snapPartners(exclude: string, plane: THREE.Plane): Rect[] {
    const out: Rect[] = [];
    const z = -plane.constant;
    const visible = (r: Rect): boolean => {
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity, behind = 0;
      for (const [x, y] of [[r.l, r.b], [r.r, r.b], [r.l, r.t], [r.r, r.t]]) {
        const v = new THREE.Vector3(x, y, z).project(this.camera);
        if (v.z > 1) { behind++; continue; }
        minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x); minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
      }
      if (behind === 4) return false;
      const pad = 0.05; // чуть-чуть за край окна - тоже считается видимым
      return maxX >= -1 - pad && minX <= 1 + pad && maxY >= -1 - pad && minY <= 1 + pad;
    };
    for (const [uid, o] of this.nodes) {
      if (uid === exclude) continue;
      const r = this.rectAt(o, o.group.position.x, o.group.position.y);
      if (visible(r)) out.push(r);
    }
    if (this.bgNode) {
      const r = this.rectAt(this.bgNode, this.bgNode.group.position.x, this.bgNode.group.position.y);
      if (visible(r)) out.push(r);
    }
    return out;
  }

  /** Оси экрана для прилипания и зеркала: центр координат и центр фона, если он смещён. */
  private snapAxes(): { x: number[]; y: number[] } {
    const x = [0], y = [0];
    if (this.bgNode) {
      const r = this.rectAt(this.bgNode, this.bgNode.group.position.x, this.bgNode.group.position.y);
      const bx = (r.l + r.r) / 2, by = (r.b + r.t) / 2;
      if (Math.abs(bx) > 1e-6) x.push(bx);
      if (Math.abs(by) > 1e-6) y.push(by);
    }
    return { x, y };
  }

  private guideMat = new THREE.LineBasicMaterial({ color: 0xff3fa4, depthTest: false, transparent: true, opacity: 0.95 });
  private gapMat = new THREE.LineBasicMaterial({ color: 0xffb020, depthTest: false, transparent: true, opacity: 0.95 });

  private showGuides(gs: Guide[], plane: THREE.Plane | null): void {
    for (const c of [...this.guideGroup.children]) { this.guideGroup.remove(c); (c as THREE.Line).geometry.dispose(); }
    if (!gs.length || !plane) return;
    const z = -plane.constant + 0.003;
    const tick = plane ? this.worldPerPixel(plane) * 5 : 0.02;
    const pts: number[] = [], gaps: number[] = [];
    for (const g of gs) {
      if (g.kind === 'align') {
        if (g.dir === 'v') pts.push(g.at, g.from, z, g.at, g.to, z); else pts.push(g.from, g.at, z, g.to, g.at, z);
      } else {
        // зазоры: отрезок с засечками на концах, поперёк - линия at
        for (const [a, b] of g.gaps ?? []) {
          if (g.dir === 'h') { // зазор вдоль x на высоте at
            gaps.push(a, g.at, z, b, g.at, z, a, g.at - tick, z, a, g.at + tick, z, b, g.at - tick, z, b, g.at + tick, z);
          } else {
            gaps.push(g.at, a, z, g.at, b, z, g.at - tick, a, z, g.at + tick, a, z, g.at - tick, b, z, g.at + tick, b, z);
          }
        }
      }
    }
    const add = (arr: number[], mat: THREE.Material) => {
      if (!arr.length) return;
      const geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(arr), 3));
      const l = new THREE.LineSegments(geo, mat); l.renderOrder = 12; this.guideGroup.add(l);
    };
    add(pts, this.guideMat); add(gaps, this.gapMat);
  }

  // ---------------------------------------------------------------- цикл

  /** Последний показанный кадр по виджету - перестраиваем только когда кадр сменился. */
  private frameShown = new Map<string, number>();
  private tickFrames(): void {
    if (!this.screen) return;
    const tick = editorTick();
    for (const w of this.screen.widgets) {
      if (w.type !== 'TEXT_BUTTON' || (!w.frames.length && !w.hoveredFrames.length)) continue;
      if (this.dragging?.uid === w.uid) continue; // во время перетаскивания кадр не меняем - размер должен быть стабильным
      const fi = frameIndexAt(w, this.hoverPreview && this.hovered === w.uid, tick);
      if (this.frameShown.get(w.uid) !== fi) { this.frameShown.set(w.uid, fi); if (this.nodes.has(w.uid)) this.rebuildWidget(w.uid); }
    }
  }

  private loop = (): void => {
    this.frame = requestAnimationFrame(this.loop);
    this.tickFrames();
    this.tickTweens();
    this.tickMorph();
    if (this.selected || this.extraSelected.length || this.overlay.innerHTML) this.drawSelection();
    const w = this.canvas.clientWidth, h = this.canvas.clientHeight;
    const pr = this.renderer.getPixelRatio();
    if (w && h && (this.canvas.width !== Math.floor(w * pr) || this.canvas.height !== Math.floor(h * pr))) {
      this.renderer.setSize(w, h, false);
      this.camera.aspect = w / h;
      this.camera.updateProjectionMatrix();
    }
    const dt = Math.min(0.1, this.clock.getDelta());
    if (this.viewHelper.animating) this.viewHelper.update(dt);
    else this.controls.update();
    this.renderer.clear();
    this.renderer.render(this.scene, this.camera);
    this.viewHelper.render(this.renderer);
  };
}

function round(v: number): number { return Math.round(v * 1000) / 1000; }

/** Текущий серверный «тик» редактора (50 мс) - для кадровой анимации текста в предпросмотре. */
export function editorTick(): number { return Math.floor(performance.now() / 50); }

/** Индекс кадра виджета в этот тик: активный набор - hoveredFrames при наведении, иначе frames; -1 - кадров нет. */
export function frameIndexAt(w: Widget, hovered: boolean, tick: number): number {
  if (w.type !== 'TEXT_BUTTON') return -1;
  const set = hovered && w.hoveredFrames.length ? w.hoveredFrames : (!hovered || w.hoveredText == null) ? w.frames : [];
  if (set.length < 2) return -1;
  const interval = set === w.hoveredFrames ? (w.hoveredFrameInterval > 0 ? w.hoveredFrameInterval : w.frameInterval) : w.frameInterval;
  return Math.floor(tick / Math.max(1, interval)) % set.length;
}

/** Что показывает TextDisplay виджета: текст (или hoveredText), кадр анимации, либо спрайт SPRITE_BUTTON. */
export function widgetText(w: Widget, hovered = false, tick = editorTick()): RichText {
  if (w.type === 'SPRITE_BUTTON') {
    if (w.player) { const pl = hovered && w.hoveredPlayer ? w.hoveredPlayer : w.player; return [{ text: '', player: pl, hat: w.hat, color: (hovered && w.hoveredSpriteColor) || w.spriteColor || undefined }]; }
    const sp = hovered && w.hoveredSprite ? w.hoveredSprite : w.sprite;
    const col = (hovered && w.hoveredSpriteColor) || w.spriteColor || undefined;
    return [{ text: '', sprite: sp || 'item/barrier', atlas: w.atlas ?? undefined, color: col }];
  }
  const fi = frameIndexAt(w, hovered, tick);
  if (fi >= 0) return (hovered && w.hoveredFrames.length ? w.hoveredFrames : w.frames)[fi];
  if (hovered && w.hoveredText != null) return w.hoveredText;
  return w.frames.length ? w.frames[0] : w.text;
}

/**
 * Мировое смещение (x, y) центра вращения виджета от его якоря (worldPos(position)) при повороте deg.
 * Повторяет applyDelta без hover-дельты: pivot = translation (+ компенсация у текста), центр = pivot + R·(centerX·sx, centerY·sy).
 * Нужен групповому повороту, чтобы крутить группу как одно целое - вокруг реальных визуальных центров.
 */
export function rotationCenterOffset(w: Widget, deg: number): [number, number] {
  const xs = calibration.translationXSign, tr = w.translation;
  if (w.type === 'ITEM_BUTTON') return [tr[0] * xs, tr[1]];
  const px = calibration.px, L = layout(widgetText(w), w.lineWidth);
  const cx = (1 + (calibration.bgRight - calibration.bgLeft) / 2) * px * w.scale[0];
  const hc = (L.bgH / 2) * px * w.scale[1];
  const a = THREE.MathUtils.degToRad(deg), cos = Math.cos(a), sin = Math.sin(a);
  const comp: [number, number] = deg === 0 ? [0, 0] : [hc * sin, hc - hc * cos];
  return [(tr[0] + comp[0]) * xs + cx * cos - hc * sin, tr[1] + comp[1] + cx * sin + hc * cos];
}

/** Размер текстового виджета в блоках (для инспектора). */
export function textSizeBlocks(w: Widget): { w: number; h: number } {
  const L = layout(widgetText(w), w.lineWidth);
  return { w: L.bgW * calibration.px * w.scale[0], h: L.bgH * calibration.px * w.scale[1] };
}

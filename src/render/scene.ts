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

export interface SceneCallbacks {
  onSelect(uid: string | null): void;
  onMove(uid: string, pos: [number, number, number]): void;
  onHover(uid: string | null): void;
}

interface Node {
  uid: string;
  group: THREE.Group;
  mesh: THREE.Mesh;
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
  private selectionBox: THREE.LineSegments | null = null;
  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private dragging: { uid: string; plane: THREE.Plane; offset: THREE.Vector3; start: [number, number, number]; moved: boolean; pointerId: number } | null = null;
  private downAt: { x: number; y: number; button: number; onGizmo: boolean } | null = null;
  private gizmoDragging = false;
  private gizmoStart: THREE.Vector3 | null = null;
  private screen: Screen | null = null;
  private selected: string | null = null;
  private hovered: string | null = null;
  private frame = 0;
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
    this.screen = s;
    this.rebuildAll();
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
    if (prev) this.rebuildWidget(prev);
    if (uid) this.rebuildWidget(uid);
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
    const { texture, layout: L } = buildTextTexture(text, w.alignment, bg, alpha);
    return this.textNode(w.uid, texture, L.bgW, L.bgH, w.scale, w.translation, w.position, calibration.textDepthOffset, w.tolerance, true);
  }

  private buildBackground(b: Background): Node {
    const { texture, layout: L } = buildTextTexture(b.text, 'CENTERED', b.color, b.alpha);
    // плагин: translation.x = -scaleX/80 + user.x (TransformationUtil.createAlignedTranslation)
    const tr: [number, number, number] = [-b.scale[0] / 80 + b.translation[0], b.translation[1], b.translation[2]];
    return this.textNode('__background', texture, L.bgW, L.bgH, b.scale, tr, b.position, calibration.textDepthOffset, null, false);
  }

  private textNode(uid: string, texture: THREE.Texture, bgW: number, bgH: number, scale: [number, number, number], tr: [number, number, number],
    pos: [number, number, number], depth: number, tol: [number, number] | null, selectable: boolean): Node {
    const px = calibration.px;
    const wBlocks = bgW * px, hBlocks = bgH * px;
    const geo = new THREE.PlaneGeometry(wBlocks, hBlocks);
    const mat = new THREE.MeshBasicMaterial({ map: texture, transparent: true, side: THREE.FrontSide, depthWrite: true, alphaTest: 0.01 });
    const mesh = new THREE.Mesh(geo, mat);
    // якорь - низ по центру текста. Локальный x фона: [1 - k/2 - bgLeft, 1 + k/2 + bgRight] px
    // (translate(1 - k/2) клиента), центр = 1 + (bgRight - bgLeft)/2 = +0.5 px; по y фон от 0 до bgH.
    const centerX = (1 + (calibration.bgRight - calibration.bgLeft) / 2) * px;
    const centerY = (bgH / 2) * px;
    mesh.position.set(centerX * scale[0] + tr[0] * calibration.translationXSign, centerY * scale[1] + tr[1], tr[2]);
    mesh.scale.set(scale[0], scale[1], 1);
    mesh.userData.uid = selectable ? uid : null;
    const group = new THREE.Group();
    group.add(mesh);
    group.position.copy(this.worldPos(pos, depth));
    // зона наведения: плагин поднимает центр на половину высоты фона (TextDisplayButtonWidget.hitCenterOffsetY)
    const hit = tol ? this.hitRect(tol, tr, (bgH / 2) * px * scale[1]) : null;
    if (hit) group.add(hit);
    return { uid, group, mesh, hit, dispose: () => { geo.dispose(); mat.dispose(); texture.dispose(); hit?.geometry.dispose(); } };
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
    mesh.position.set(w.translation[0] * calibration.translationXSign, w.translation[1], w.translation[2]);
    mesh.scale.set(w.scale[0], w.scale[1], 1);
    mesh.userData.uid = w.uid;
    const group = new THREE.Group();
    group.add(mesh);
    if (hovered) {
      const gc = w.glowColor ? new THREE.Color(w.glowColor[0] / 255, w.glowColor[1] / 255, w.glowColor[2] / 255) : new THREE.Color(0xffffff);
      const edge = new THREE.LineSegments(new THREE.EdgesGeometry(geo), new THREE.LineBasicMaterial({ color: gc }));
      edge.position.copy(mesh.position); edge.scale.copy(mesh.scale);
      group.add(edge);
    }
    group.position.copy(this.worldPos(w.position, calibration.itemDepthOffset));
    const hit = this.hitRect(w.tolerance, w.translation);
    group.add(hit);
    return { uid: w.uid, group, mesh, hit, dispose: () => { geo.dispose(); mat.dispose(); hit.geometry.dispose(); } };
  }

  /** Зона наведения: прямоугольник tolerance с центром в позиции + translation (+ половина высоты текста у TextDisplay). */
  private hitRect(tol: [number, number], tr: [number, number, number], offsetY = 0): THREE.LineSegments {
    const g = new THREE.BufferGeometry();
    const hw = tol[0], hh = tol[1];
    const v = new Float32Array([-hw, -hh, 0, hw, -hh, 0, hw, -hh, 0, hw, hh, 0, hw, hh, 0, -hw, hh, 0, -hw, hh, 0, -hw, -hh, 0]);
    g.setAttribute('position', new THREE.BufferAttribute(v, 3));
    const l = new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: 0x00e5ff, transparent: true, opacity: 0.7, depthTest: false }));
    l.position.set(tr[0] * calibration.translationXSign, tr[1] + offsetY, tr[2] + 0.002);
    l.visible = this.showHitAreas;
    l.renderOrder = 10;
    return l;
  }

  setShowHitAreas(on: boolean): void { this.showHitAreas = on; for (const n of this.nodes.values()) if (n.hit) n.hit.visible = on; }
  setShowGrid(on: boolean): void { this.showGrid = on; this.grid.visible = on; }
  setSnap(step: number): void { this.snap = step; }

  private updateSelectionBox(): void {
    if (this.selectionBox) { this.scene.remove(this.selectionBox); this.selectionBox.geometry.dispose(); this.selectionBox = null; }
    const n = this.selected ? this.nodes.get(this.selected) : null;
    if (!n) return;
    n.mesh.updateWorldMatrix(true, false);
    const box = new THREE.Box3().setFromObject(n.mesh);
    box.expandByScalar(0.01);
    const helper = new THREE.Box3Helper(box, new THREE.Color(0xffc400));
    (helper.material as THREE.LineBasicMaterial).depthTest = false;
    helper.renderOrder = 11;
    this.selectionBox = helper as unknown as THREE.LineSegments;
    this.scene.add(helper);
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
    if (axis.includes('X')) pos.x = sn(pos.x); else pos.x = st.x;
    if (axis.includes('Y')) pos.y = sn(pos.y); else pos.y = st.y;
    if (axis.includes('Z')) pos.z = sn(pos.z); else pos.z = st.z;
    let guides: Guide[] = [];
    if (this.smartGuides && !axis.includes('Z') && axis.length) {
      const plane = new THREE.Plane(new THREE.Vector3(0, 0, 1), -pos.z);
      const m = this.rectAt(n, pos.x, pos.y);
      const others: Rect[] = [];
      for (const [id, o] of this.nodes) if (id !== uid) others.push(this.rectAt(o, o.group.position.x, o.group.position.y));
      if (this.bgNode) others.push(this.rectAt(this.bgNode, this.bgNode.group.position.x, this.bgNode.group.position.y));
      const r = computeSnap(m, others, this.worldPerPixel(plane) * 8);
      if (axis.includes('X')) pos.x += r.dx;
      if (axis.includes('Y')) pos.y += r.dy;
      guides = r.guides.filter((g) => (g.dir === 'v' ? axis.includes('X') : axis.includes('Y')));
      this.showGuides(guides, plane);
    }
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
    if (!uid) return; // пустое место: OrbitControls вращает; выделение снимется по клику без движения
    this.cb.onSelect(uid);
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
  };

  private onMoveEv = (e: PointerEvent): void => {
    this.setPointer(e);
    if (this.dragging) {
      const d = this.dragging;
      if (!d.moved && Math.hypot(e.clientX - this.downAt!.x, e.clientY - this.downAt!.y) < 3) return;
      d.moved = true;
      const p = new THREE.Vector3();
      if (!this.raycaster.ray.intersectPlane(d.plane, p)) return;
      let gx = p.x - d.offset.x, gy = p.y - d.offset.y; // мировые координаты группы
      const step = e.shiftKey ? 0.5 : e.altKey ? 0.01 : this.snap;
      if (step > 0) { gx = Math.round(gx / step) * step; gy = Math.round(gy / step) * step; }
      let guides: Guide[] = [];
      if (this.smartGuides && !e.ctrlKey) {
        const thr = this.worldPerPixel(d.plane) * 8;
        const n = this.nodes.get(d.uid)!;
        const m = this.rectAt(n, gx, gy);
        const others: Rect[] = [];
        for (const [uid, o] of this.nodes) if (uid !== d.uid) others.push(this.rectAt(o, o.group.position.x, o.group.position.y));
        if (this.bgNode) others.push(this.rectAt(this.bgNode, this.bgNode.group.position.x, this.bgNode.group.position.y));
        const r = computeSnap(m, others, thr);
        gx += r.dx; gy += r.dy; guides = r.guides;
      }
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
    // клик по пустому месту без движения - снять выделение
    this.setPointer(e);
    if (!this.pick()) this.cb.onSelect(null);
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

  /** Прямоугольник видимой части узла (mesh) в плоскости экрана, если группа стоит в (x, y). */
  private rectAt(n: Node, x: number, y: number): Rect {
    n.group.updateMatrixWorld(true);
    const box = new THREE.Box3().setFromObject(n.mesh); // мировой бокс при текущей позиции группы
    const ox = n.group.position.x, oy = n.group.position.y;
    return { l: box.min.x - ox + x, r: box.max.x - ox + x, b: box.min.y - oy + y, t: box.max.y - oy + y };
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

  private loop = (): void => {
    this.frame = requestAnimationFrame(this.loop);
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

/** Что показывает TextDisplay виджета: текст (или hoveredText) либо спрайт SPRITE_BUTTON. */
export function widgetText(w: Widget, hovered = false): RichText {
  if (w.type === 'SPRITE_BUTTON') {
    const sp = hovered && w.hoveredSprite ? w.hoveredSprite : w.sprite;
    return [{ text: '', sprite: sp || 'item/barrier', atlas: w.atlas ?? undefined }];
  }
  return hovered && w.hoveredText != null ? w.hoveredText : w.text;
}

/** Размер текстового виджета в блоках (для инспектора). */
export function textSizeBlocks(w: Widget): { w: number; h: number } {
  const L = layout(widgetText(w));
  return { w: L.bgW * calibration.px * w.scale[0], h: L.bgH * calibration.px * w.scale[1] };
}

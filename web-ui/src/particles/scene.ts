// 3D-предпросмотр эффекта частиц: пол (и по желанию стена) для коллизий, эмиттер в центре,
// частицы - как их рисует клиент: квадраты PIXEL, текст, предметы, блоки, с billboard.
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { ParticleEffect } from './model';
import { ParticleRuntime, type Particle, type Stats } from './runtime';
import { alpha, cssHex } from './colors';
import type { RichText } from '../model/screen';
import { buildTextTexture } from '../render/text';
import { itemTexture } from '../render/items';
import { spriteImage, spriteState } from '../render/sprites';
import { calibration } from '../render/metrics';

const BG_COLOR = 0x37383c;
/** Клиент не рисует фон и текст с прозрачностью ниже 26. */
const MIN_ALPHA = 26;

export interface PlayOptions {
  playing: boolean;
  loop: boolean;
  speed: number;
  /** true - как увидит игрок (кадры + интерполяция клиента); false - точная траектория */
  asGame: boolean;
  wall: boolean;
  /** Высота точки запуска над полом, блоков */
  height: number;
  seed: number;
}

interface View { mesh: THREE.Mesh; kind: string; textKey?: string; mask?: THREE.Mesh }

export class ParticleScene {
  readonly renderer: THREE.WebGLRenderer;
  readonly scene = new THREE.Scene();
  readonly camera = new THREE.PerspectiveCamera(70, 1, 0.05, 200);
  readonly controls: OrbitControls;
  private frame = 0;
  private last = performance.now();
  private acc = 0;
  private runtime: ParticleRuntime | null = null;
  private effect: ParticleEffect | null = null;
  private views = new Map<number, View>();
  private emitterGizmo = new THREE.Group();
  private wallGroup = new THREE.Group();
  private restartAt = -1;
  private textCache = new Map<string, { tex: THREE.Texture; w: number; h: number }>();
  private blockCache = new Map<string, THREE.Texture | null>();
  private planeGeo = new THREE.PlaneGeometry(1, 1);
  private boxGeo = new THREE.BoxGeometry(1, 1, 1);
  private spriteRevision = -1;
  opts: PlayOptions = { playing: true, loop: true, speed: 1, asGame: true, wall: false, height: 1.5, seed: 1 };
  onStats: ((s: Stats & { tick: number }) => void) | null = null;
  private statsAt = 0;

  constructor(readonly canvas: HTMLCanvasElement) {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    this.renderer.setPixelRatio(Math.min(2, window.devicePixelRatio));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.scene.background = new THREE.Color(BG_COLOR);
    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.15;
    this.controls.zoomToCursor = true;
    this.controls.screenSpacePanning = true;
    this.controls.mouseButtons = { LEFT: THREE.MOUSE.ROTATE, MIDDLE: THREE.MOUSE.PAN, RIGHT: THREE.MOUSE.PAN };
    this.scene.add(this.makeFloor(), this.emitterGizmo, this.wallGroup);
    this.buildWall();
    this.outlineScene.add(new THREE.Mesh(new THREE.PlaneGeometry(2, 2), this.outlineMat));
    this.resetCamera();
    canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    canvas.addEventListener('mousedown', (e) => { if (e.button === 1) e.preventDefault(); });
    this.loop();
  }

  resetCamera(): void {
    this.camera.position.set(1.9, this.opts.height + 0.7, 2.6);
    this.controls.target.set(0, this.opts.height, 0);
    this.controls.update();
  }

  dispose(): void {
    cancelAnimationFrame(this.frame);
    this.clearViews();
    this.controls.dispose();
    this.renderer.dispose();
  }

  // ---------------------------------------------------------------- состояние

  /** Новый или изменённый эффект: запуск заново (с тем же зерном - тот же разброс). */
  setEffect(e: ParticleEffect | null): void {
    this.effect = e;
    this.updateGizmo();
    this.restart();
  }

  setOptions(o: Partial<PlayOptions>): void {
    const restart = (o.wall !== undefined && o.wall !== this.opts.wall) || (o.height !== undefined && o.height !== this.opts.height) || (o.seed !== undefined && o.seed !== this.opts.seed);
    const heightChanged = o.height !== undefined && o.height !== this.opts.height;
    Object.assign(this.opts, o);
    this.wallGroup.visible = this.opts.wall;
    if (heightChanged) { this.updateGizmo(); this.controls.target.y = this.opts.height; }
    if (restart) this.restart();
  }

  restart(): void {
    this.clearViews();
    this.acc = 0;
    this.restartAt = -1;
    this.runtime = this.effect
      ? new ParticleRuntime(this.effect, { center: [0, this.opts.height, 0], solid: this.solid, seed: this.opts.seed })
      : null;
  }

  /** Остановить испускание (для бесконечных и долгих эффектов). */
  stopEmitting(): void { this.runtime?.stop(); }

  /** Твёрдые блоки сцены: пол (y < 0) и стена. */
  private solid = (x: number, y: number, z: number): boolean => y < 0 || (this.opts.wall && x === 2 && y < 3 && z >= -3 && z <= 3);

  // ---------------------------------------------------------------- сцена

  private makeFloor(): THREE.Group {
    const g = new THREE.Group();
    const m = new THREE.Mesh(new THREE.PlaneGeometry(60, 60), new THREE.MeshBasicMaterial({ color: 0x2c2d31 }));
    m.rotation.x = -Math.PI / 2;
    g.add(m);
    const grid = new THREE.GridHelper(60, 60, 0x4a4c52, 0x3d3f45);
    grid.position.y = 0.001;
    g.add(grid);
    const axes = new THREE.AxesHelper(0.6);
    axes.position.y = 0.002;
    g.add(axes);
    return g;
  }

  private buildWall(): void {
    // стена из блоков x = 2, y 0..2, z -3..3 - чтобы посмотреть отскок от стены
    const mat = new THREE.MeshBasicMaterial({ color: 0x6b6e76, transparent: true, opacity: 0.55 });
    const edges = new THREE.LineBasicMaterial({ color: 0x9a9da5, transparent: true, opacity: 0.6 });
    for (let y = 0; y < 3; y++) for (let z = -3; z <= 3; z++) {
      const b = new THREE.Mesh(this.boxGeo, mat);
      b.position.set(2.5, y + 0.5, z + 0.5);
      const e = new THREE.LineSegments(new THREE.EdgesGeometry(this.boxGeo), edges);
      e.position.copy(b.position);
      this.wallGroup.add(b, e);
    }
    this.wallGroup.visible = false;
  }

  /** Форма эмиттера и конус разлёта. */
  private updateGizmo(): void {
    const g = this.emitterGizmo;
    for (const c of [...g.children]) { g.remove(c); (c as any).geometry?.dispose?.(); }
    g.position.set(0, this.opts.height, 0);
    const e = this.effect; if (!e) return;
    const line = new THREE.LineBasicMaterial({ color: 0x47a2b6, transparent: true, opacity: 0.8 });
    const em = e.emitter;
    if (em.shape === 'SPHERE') g.add(new THREE.LineSegments(new THREE.WireframeGeometry(new THREE.SphereGeometry(Math.max(em.radius, 0.01), 16, 10)), new THREE.LineBasicMaterial({ color: 0x47a2b6, transparent: true, opacity: 0.25 })));
    else if (em.shape === 'CIRCLE') {
      const pts = Array.from({ length: 65 }, (_, i) => new THREE.Vector3(Math.cos((i / 64) * Math.PI * 2) * em.radius, 0, Math.sin((i / 64) * Math.PI * 2) * em.radius));
      g.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), line));
    } else if (em.shape === 'BOX') {
      const box = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(Math.max(em.size[0], 0.001), Math.max(em.size[1], 0.001), Math.max(em.size[2], 0.001))), line);
      g.add(box);
    }
    // точка запуска
    const dot = new THREE.Mesh(new THREE.SphereGeometry(0.03, 8, 6), new THREE.MeshBasicMaterial({ color: 0x47a2b6 }));
    g.add(dot);
    // ось разлёта и конус spread
    const d = e.motion.direction;
    if (d !== 'OUTWARD' && (e.motion.speed[1] > 0)) {
      const dir = new THREE.Vector3(d[0], d[1], d[2]);
      if (dir.lengthSq() < 1e-9) dir.set(0, 1, 0);
      dir.normalize();
      g.add(new THREE.ArrowHelper(dir, new THREE.Vector3(), 0.9, 0xd9a441, 0.15, 0.08));
      const spread = Math.min(e.motion.spread, 180);
      if (spread > 0 && spread < 90) {
        const h = 0.7, r = Math.tan((spread * Math.PI) / 180) * h;
        const cone = new THREE.LineSegments(new THREE.WireframeGeometry(new THREE.ConeGeometry(r, h, 16, 1, true)), new THREE.LineBasicMaterial({ color: 0xd9a441, transparent: true, opacity: 0.25 }));
        // конус вершиной в точке запуска, раскрывается вдоль оси
        cone.position.copy(dir.clone().multiplyScalar(h / 2));
        cone.quaternion.setFromUnitVectors(new THREE.Vector3(0, -1, 0), dir);
        g.add(cone);
      }
    }
  }

  // ---------------------------------------------------------------- частицы

  private clearViews(): void {
    for (const id of [...this.views.keys()]) this.removeView(id);
  }
  private removeView(id: number): void {
    const v = this.views.get(id); if (!v) return;
    this.scene.remove(v.mesh); (v.mesh.material as THREE.Material).dispose();
    if (v.mask) { this.maskScene.remove(v.mask); (v.mask.material as THREE.Material).dispose(); }
    this.views.delete(id);
  }

  // ---------------------------------------------------------------- свечение (glowing)
  // Как клиент: силуэт светящихся сущностей рисуется цветом свечения в отдельный буфер (сквозь блоки),
  // затем вокруг силуэта - контур в пару пикселей; сам силуэт не закрашивается.
  private maskScene = new THREE.Scene();
  private maskTarget: THREE.WebGLRenderTarget | null = null;
  private outlineScene = new THREE.Scene();
  private outlineCam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  private outlineMat = new THREE.ShaderMaterial({
    uniforms: { tMask: { value: null }, texel: { value: new THREE.Vector2(1, 1) }, radius: { value: 2 } },
    vertexShader: 'varying vec2 vUv; void main() { vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }',
    fragmentShader: `
      uniform sampler2D tMask; uniform vec2 texel; uniform float radius; varying vec2 vUv;
      void main() {
        if (texture2D(tMask, vUv).a > 0.5) discard;
        vec4 best = vec4(0.0);
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) {
          if (float(abs(x)) > radius || float(abs(y)) > radius) continue;
          vec4 c = texture2D(tMask, vUv + vec2(float(x), float(y)) * texel);
          if (c.a > best.a) best = c;
        }
        if (best.a < 0.5) discard;
        gl_FragColor = vec4(best.rgb, 1.0);
        #include <colorspace_fragment>
      }`,
    transparent: true, depthTest: false, depthWrite: false,
  });
  private hasGlow = false;

  private renderGlow(): void {
    if (!this.hasGlow || !this.maskScene.children.length) return;
    const size = this.renderer.getDrawingBufferSize(new THREE.Vector2());
    if (!this.maskTarget || this.maskTarget.width !== size.x || this.maskTarget.height !== size.y) {
      this.maskTarget?.dispose();
      this.maskTarget = new THREE.WebGLRenderTarget(size.x, size.y);
    }
    for (const v of this.views.values()) {
      if (!v.mask) continue;
      v.mesh.updateMatrixWorld();
      v.mask.matrix.copy(v.mesh.matrixWorld);
      v.mask.visible = v.mesh.visible;
    }
    const r = this.renderer;
    const prevColor = r.getClearColor(new THREE.Color()), prevAlpha = r.getClearAlpha();
    r.setRenderTarget(this.maskTarget);
    r.setClearColor(0x000000, 0); r.clear();
    r.render(this.maskScene, this.camera);
    r.setRenderTarget(null);
    r.setClearColor(prevColor, prevAlpha);
    this.outlineMat.uniforms.tMask.value = this.maskTarget.texture;
    this.outlineMat.uniforms.texel.value.set(1 / size.x, 1 / size.y);
    this.outlineMat.uniforms.radius.value = Math.max(1, Math.round(r.getPixelRatio() * 1.5));
    const auto = r.autoClear; r.autoClear = false;
    r.render(this.outlineScene, this.outlineCam);
    r.autoClear = auto;
  }

  private addMask(v: View, e: ParticleEffect): void {
    const src = v.mesh.material as THREE.MeshBasicMaterial;
    const color = e.render.glowColor ? parseInt(e.render.glowColor.replace('#', '').slice(0, 6), 16) : 0xffffff;
    // блок - сплошной куб; у предмета форму даёт прозрачность спрайта
    const map = e.render.type === 'ITEM' ? src.map ?? null : null;
    const mat = new THREE.MeshBasicMaterial({ color, map, alphaTest: map ? 0.1 : 0, depthTest: false, depthWrite: false, side: THREE.DoubleSide });
    if (mat.map) {
      // карта нужна только для формы: цвет - сплошной цвет свечения
      mat.onBeforeCompile = (sh) => { sh.fragmentShader = sh.fragmentShader.replace('#include <map_fragment>', '#ifdef USE_MAP\n vec4 texelColor = texture2D( map, vMapUv ); diffuseColor.a *= texelColor.a;\n#endif'); };
    }
    const mask = new THREE.Mesh(v.mesh.geometry, mat);
    mask.matrixAutoUpdate = false;
    this.maskScene.add(mask);
    v.mask = mask;
  }

  private richVariant(e: ParticleEffect, i: number): RichText {
    const all: RichText[] = [];
    if (e.render.text != null) all.push(e.render.text);
    all.push(...e.render.variants);
    return all[i] ?? all[0] ?? '?';
  }

  /** Текстура текста с цветом (корневой цвет красит сегменты без своего, как text.color() в плагине). */
  private textTexture(e: ParticleEffect, variant: number, rgb: number | null): { tex: THREE.Texture; w: number; h: number } {
    // ключ - содержимое варианта и фона, а не номер: правка текста не должна показывать старую картинку
    const rd = e.render;
    const key = JSON.stringify([this.richVariant(e, variant), rgb, rd.background, rd.backgroundAlpha, rd.shadow, this.spriteRevision]);
    let t = this.textCache.get(key);
    if (t) return t;
    let rich = this.richVariant(e, variant);
    if (rgb != null) {
      const col = cssHex(rgb);
      rich = typeof rich === 'string' ? [{ text: rich, color: col }] : rich.map((s) => (s.color || s.sprite !== undefined || s.player !== undefined ? s : { ...s, color: col }));
    }
    const bg = e.render.background ?? [0, 0, 0];
    const bgA = e.render.background ? (e.render.backgroundAlpha ?? 255) : 0;
    const r = buildTextTexture(rich, 'CENTERED', bg, bgA, 4);
    t = { tex: r.texture, w: r.layout.bgW * calibration.px, h: r.layout.bgH * calibration.px };
    if (this.textCache.size > 600) { for (const v of this.textCache.values()) v.tex.dispose(); this.textCache.clear(); }
    this.textCache.set(key, t);
    return t;
  }

  /**
   * Текстура грани блока: у многих блоков файл называется иначе (magma_block → magma, oak_log → oak_log
   * и oak_log_top), поэтому пробуем несколько имён. Пока картинки грузятся - null без кэша.
   */
  private blockTexture(name: string): THREE.Texture | null {
    const key = name.replace(/^minecraft:/, '').replace(/\[.*$/, '');
    if (this.blockCache.has(key)) return this.blockCache.get(key)!;
    const base = key.replace(/_block$/, '');
    const names = [...new Set([key, base, key + '_side', base + '_side', key + '_top', base + '_top', key + '_front'])];
    let found: HTMLCanvasElement | null = null, pending = false, first: HTMLCanvasElement | null = null;
    for (const n of names) {
      const img = spriteImage('block/' + n);
      if (!img.done) { pending = true; break; }
      if (img.exact && img.canvas) { found = img.canvas; break; }
      first ??= img.canvas;
    }
    if (pending) return null;
    const c = found ?? first;
    let tex: THREE.Texture | null = null;
    if (c) {
      tex = new THREE.CanvasTexture(c);
      tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter; tex.colorSpace = THREE.SRGBColorSpace;
    }
    this.blockCache.set(key, tex);
    return tex;
  }

  private viewFor(p: Particle, e: ParticleEffect): View {
    let v = this.views.get(p.id);
    if (v) return v;
    const type = e.render.type;
    let mesh: THREE.Mesh;
    if (type === 'BLOCK') {
      const tex = this.blockTexture(e.render.blocks[p.variant] ?? e.render.blocks[0] ?? 'stone');
      mesh = new THREE.Mesh(this.boxGeo, new THREE.MeshBasicMaterial(tex ? { map: tex } : { color: 0xb0a080 }));
    } else if (type === 'ITEM') {
      const tex = itemTexture(e.render.items[p.variant] ?? e.render.items[0] ?? 'barrier');
      mesh = new THREE.Mesh(this.planeGeo, new THREE.MeshBasicMaterial(tex ? { map: tex, transparent: true, alphaTest: 0.1, side: THREE.DoubleSide } : { color: 0xff00ff, side: THREE.DoubleSide }));
    } else {
      mesh = new THREE.Mesh(this.planeGeo, new THREE.MeshBasicMaterial({ transparent: true, side: THREE.DoubleSide, depthWrite: false }));
    }
    this.scene.add(mesh);
    v = { mesh, kind: type };
    if (e.render.glow && (type === 'ITEM' || type === 'BLOCK')) this.addMask(v, e);
    this.views.set(p.id, v);
    return v;
  }

  private readonly tmpQ = new THREE.Quaternion();
  private readonly zAxis = new THREE.Vector3(0, 0, 1);

  private draw(t: number): void {
    const rt = this.runtime, e = this.effect;
    if (!rt || !e) return;
    if (spriteState.revision !== this.spriteRevision) {
      this.spriteRevision = spriteState.revision;
      // догрузились текстуры: блоки без картинки создаём заново
      for (const [id, v] of this.views) if (v.kind === 'BLOCK' && !(v.mesh.material as THREE.MeshBasicMaterial).map) this.removeView(id);
    }
    const alive = new Set<number>();
    for (const p of rt.particles) {
      if (t <= p.spawnTick) continue; // клиент получит сущность со следующим тиком
      alive.add(p.id);
      const v = this.viewFor(p, e);
      const s = this.opts.asGame ? { pos: rt.shownPos(p, t), ...rt.shownLook(p, t) } : rt.exact(p, t);
      const m = v.mesh;
      m.position.set(s.pos[0], s.pos[1], s.pos[2]);
      const mat = m.material as THREE.MeshBasicMaterial;
      const a = alpha(s.color);
      let w = s.scale, h = s.scale;
      if (e.render.type === 'PIXEL') {
        mat.color.set(cssHex(s.color));
        mat.opacity = a / 255;
        m.visible = a >= MIN_ALPHA;
      } else if (e.render.type === 'TEXT') {
        const recolor = e.render.colors.length > 0 || e.animation.color != null;
        const tt = this.textTexture(e, p.variant, recolor ? s.color & 0xffffff : null);
        if (mat.map !== tt.tex) { mat.map = tt.tex; mat.needsUpdate = true; }
        mat.opacity = a / 255;
        m.visible = a >= MIN_ALPHA || !(e.animation.opacity || e.animation.color);
        w = tt.w * s.scale; h = tt.h * s.scale;
      } else if (e.render.type === 'ITEM') {
        w = h = s.scale * calibration.itemSize;
      }
      // ориентация: billboard как у клиента, затем поворот в плоскости частицы
      const bb = e.render.billboard;
      if (bb === 'CENTER') m.quaternion.copy(this.camera.quaternion);
      else if (bb === 'VERTICAL') { const d = this.camera.position.clone().sub(m.position); m.quaternion.setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.atan2(d.x, d.z)); }
      else if (bb === 'HORIZONTAL') { const d = this.camera.position.clone().sub(m.position); m.quaternion.setFromAxisAngle(new THREE.Vector3(1, 0, 0), -Math.atan2(d.y, Math.hypot(d.x, d.z))); }
      else m.quaternion.identity();
      m.quaternion.multiply(this.tmpQ.setFromAxisAngle(this.zAxis, (s.rotation * Math.PI) / 180));
      if (e.render.type === 'BLOCK') m.scale.set(s.scale, s.scale, s.scale);
      else m.scale.set(Math.max(w, 1e-4), Math.max(h, 1e-4), 1);
    }
    for (const [id, v] of this.views) {
      if (!alive.has(id)) this.removeView(id);
    }
  }

  private loop = (): void => {
    this.frame = requestAnimationFrame(this.loop);
    const now = performance.now();
    const dt = Math.min(0.1, (now - this.last) / 1000);
    this.last = now;
    this.resize();
    const rt = this.runtime;
    if (rt && this.opts.playing) {
      this.acc += dt * 20 * this.opts.speed;
      let n = 0;
      while (this.acc >= 1 && n++ < 40) {
        this.acc -= 1;
        rt.step();
        if (rt.done && this.opts.loop) {
          if (this.restartAt < 0) this.restartAt = rt.tick + 15;
          else if (rt.tick >= this.restartAt) { this.restart(); break; }
        }
      }
    }
    const cur = this.runtime;
    if (cur) {
      this.draw(cur.tick + (this.opts.playing ? this.acc : 0));
      if (this.onStats && now - this.statsAt > 250) { this.statsAt = now; this.onStats({ ...cur.stats(), tick: cur.tick }); }
    }
    this.controls.update();
    this.renderer.render(this.scene, this.camera);
    this.hasGlow = !!this.effect?.render.glow;
    this.renderGlow();
  };

  private resize(): void {
    const c = this.canvas; const w = c.clientWidth, h = c.clientHeight;
    if (!w || !h) return;
    if (c.width !== Math.floor(w * this.renderer.getPixelRatio()) || c.height !== Math.floor(h * this.renderer.getPixelRatio())) {
      this.renderer.setSize(w, h, false);
      this.camera.aspect = w / h;
      this.camera.updateProjectionMatrix();
    }
  }

  /** Один шаг на паузе (по тику). */
  stepOnce(): void { const rt = this.runtime; if (rt) { rt.step(); this.acc = 0; } }
}

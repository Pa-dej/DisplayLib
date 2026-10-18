// .jmc <-> ParticleEffect: те же ключи, синонимы, умолчания и проверки, что ParticleEffect.parse плагина.
// Ошибки не обрывают разбор: поле получает значение по умолчанию, текст ошибки - в errors (плагин
// такой файл не загрузит, редактор показывает, что исправить).
import type { JmcTable, JmcValue } from '../jmc/parser';
import { richFromJmc, richToJmc } from '../jmc/rich';
import type { RichText, Vec3, RGB, Easing } from '../model/screen';
import {
  type ParticleEffect, type Range, type Stop, type Render, type Emitter, type Motion, type Animation,
  type RenderType, type Shape, type Billboard, type Viewers,
  RENDER_TYPES, SHAPES, BILLBOARDS, EASINGS, defaultRender, defaultEmitter, defaultMotion, defaultAnimation,
} from './model';
import { parseColor } from './colors';

function get(t: JmcTable | null, ...keys: string[]): JmcValue {
  if (!t) return null;
  for (const k of keys) { const v = t[k]; if (v !== undefined && v !== null) return v; }
  return null;
}
const isTable = (v: JmcValue): v is JmcTable => !!v && typeof v === 'object' && !Array.isArray(v);
function str(t: JmcTable | null, ...keys: string[]): string | null { const v = get(t, ...keys); return v == null ? null : String(v); }
function num(t: JmcTable | null, ...keys: string[]): number | null { const v = get(t, ...keys); return typeof v === 'number' ? v : null; }
function bool(t: JmcTable | null, ...keys: string[]): boolean | null { const v = get(t, ...keys); return typeof v === 'boolean' ? v : v === 'true' ? true : v === 'false' ? false : null; }
function table(t: JmcTable | null, ...keys: string[]): JmcTable | null { const v = get(t, ...keys); return isTable(v) ? v : null; }
function list(t: JmcTable | null, ...keys: string[]): JmcValue[] | null { const v = get(t, ...keys); return Array.isArray(v) ? v : null; }
const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));

export interface ParseResult { effect: ParticleEffect; errors: string[] }

export function tableToEffect(top: JmcTable, fallbackId: string): ParseResult {
  const errors: string[] = [];
  const enumOf = <T extends string>(allowed: readonly T[], v: string | null, fallback: T, field: string): T => {
    if (v == null) return fallback;
    const u = v.trim().toUpperCase();
    if ((allowed as readonly string[]).includes(u)) return u as T;
    errors.push(`${field}: неизвестное значение '${v}', ожидается одно из ${allowed.join(', ')}`);
    return fallback;
  };
  const range = (v: JmcValue, fallback: Range, field: string): Range => {
    if (v == null) return fallback;
    if (typeof v === 'number') return [v, v];
    if (Array.isArray(v) && v.length === 2 && typeof v[0] === 'number' && typeof v[1] === 'number') return [Math.min(v[0], v[1]), Math.max(v[0], v[1])];
    errors.push(`${field}: ожидается число или [min, max], получено ${JSON.stringify(v)}`);
    return fallback;
  };
  const color = (v: JmcValue, field: string): string | null => {
    const s = colorString(v);
    if (s != null && parseColor(s) != null) return s;
    errors.push(`${field}: неизвестный цвет ${JSON.stringify(v)} ("#rrggbb", "#rrggbbaa", имя, [r, g, b] или [r, g, b, a])`);
    return null;
  };

  const id = str(top, 'id')?.trim() || fallbackId;
  const viewers = enumOf<Viewers>(['ALL', 'OWNER'], str(top, 'viewers'), 'ALL', 'viewers');
  const kf = num(top, 'keyframeTicks', 'keyframe_ticks');

  // ---- render
  const rt = table(top, 'render');
  if (!rt) errors.push('render: обязателен, например dyn render = { type: "PIXEL" };');
  const type = enumOf<RenderType>(RENDER_TYPES, str(rt, 'type'), 'TEXT', 'render.type');
  const render: Render = defaultRender(type);
  render.text = null; render.items = []; render.blocks = [];
  if (rt) {
    if (type === 'TEXT') {
      const t = get(rt, 'text');
      render.text = t != null ? richFromJmc(t) : null;
      render.variants = (list(rt, 'variants') ?? []).map((v) => richFromJmc(v)).filter((x): x is RichText => x != null);
      render.frames = (list(rt, 'frames') ?? []).map((v) => richFromJmc(v)).filter((x): x is RichText => x != null);
      // один кадр - просто текст (как в плагине)
      if (render.frames.length === 1 && render.text == null && !render.variants.length) { render.text = render.frames[0]; render.frames = []; }
      if (render.text == null && !render.variants.length && !render.frames.length) errors.push('render: для TEXT нужен text: "★", variants: ["★", "✦"] или frames: [...]');
      const fi = num(rt, 'frameInterval'); if (fi != null) render.frameInterval = Math.max(1, Math.round(fi));
      render.framesOverLife = bool(rt, 'framesOverLife') ?? false;
      const fs = str(rt, 'frameStart');
      if (fs != null && !/^(first|random)$/i.test(fs)) errors.push(`render.frameStart: ожидается "first" или "random", получено '${fs}'`);
      render.frameRandomStart = fs != null && fs.toLowerCase() === 'random';
    }
    if (type === 'ITEM') {
      render.items = strings(rt, 'item', 'items');
      if (!render.items.length) errors.push('render: для ITEM нужен item: "GOLD_NUGGET" или items: [...]');
    }
    if (type === 'BLOCK') {
      render.blocks = strings(rt, 'block', 'blocks');
      if (!render.blocks.length) errors.push('render: для BLOCK нужен block: "glowstone" или blocks: [...]');
    }
    const single = get(rt, 'color');
    if (single != null) { const c = color(single, 'render.color'); if (c) render.colors.push(c); }
    const cl = list(rt, 'colors');
    if (cl) {
      if ((cl.length === 3 || cl.length === 4) && cl.every((x) => typeof x === 'number')) { const c = color(cl, 'render.colors'); if (c) render.colors.push(c); }
      else for (const c of cl) { const s = color(c, 'render.colors'); if (s) render.colors.push(s); }
    }
    render.billboard = enumOf<Billboard>(BILLBOARDS, str(rt, 'billboard'), type === 'BLOCK' ? 'FIXED' : 'CENTER', 'render.billboard');
    const br = num(rt, 'brightness'); render.brightness = br == null ? null : clamp(Math.round(br), 0, 15);
    render.shadow = bool(rt, 'shadow') ?? false;
    const bg = list(rt, 'background', 'backgroundColor');
    render.background = bg && bg.length >= 3 && bg.slice(0, 3).every((x) => typeof x === 'number') ? (bg.slice(0, 3) as RGB) : null;
    const ba = num(rt, 'backgroundAlpha', 'bgAlpha'); render.backgroundAlpha = ba == null ? null : clamp(Math.round(ba), 0, 255);
    render.glow = bool(rt, 'glow') ?? false;
    const gc = get(rt, 'glowColor');
    if (gc != null) render.glowColor = color(gc, 'render.glowColor');
  }

  // ---- emitter
  const et = table(top, 'emitter');
  const emitter: Emitter = defaultEmitter();
  if (et) {
    emitter.shape = enumOf<Shape>(SHAPES, str(et, 'shape'), 'POINT', 'emitter.shape');
    const r = num(et, 'radius'); if (r != null) emitter.radius = Math.abs(r);
    const sz = list(et, 'size');
    if (sz) emitter.size = [0, 1, 2].map((i) => Math.abs(typeof sz[i] === 'number' ? (sz[i] as number) : 0)) as Vec3;
    emitter.surface = bool(et, 'surface') ?? false;
    const c = num(et, 'count'); emitter.count = c == null ? null : Math.max(0, Math.round(c));
    const d = num(et, 'duration'); emitter.duration = d == null ? 0 : d < 0 ? -1 : Math.round(d);
    emitter.rate = num(et, 'rate') ?? 0;
    if (emitter.duration !== 0 && !(emitter.rate > 0)) errors.push('emitter: при duration нужен rate > 0 (частиц за тик)');
  } else {
    emitter.count = null;
  }

  // ---- motion
  const mt = table(top, 'motion');
  const motion: Motion = defaultMotion();
  motion.lifetime = range(get(mt, 'lifetime', 'life'), [20, 30], 'motion.lifetime');
  if (motion.lifetime[0] < 1) errors.push('motion.lifetime: не меньше 1 тика');
  motion.speed = range(get(mt, 'speed'), [0, 0], 'motion.speed');
  const dir = get(mt, 'direction');
  if (typeof dir === 'string' && dir.toUpperCase() === 'OUTWARD') motion.direction = 'OUTWARD';
  else if (Array.isArray(dir)) motion.direction = [0, 1, 2].map((i) => (typeof dir[i] === 'number' ? (dir[i] as number) : 0)) as Vec3;
  else if (dir != null) errors.push('motion.direction: ожидается [x, y, z] или "OUTWARD"');
  if (mt) {
    motion.spread = clamp(num(mt, 'spread') ?? 0, 0, 180);
    motion.gravity = num(mt, 'gravity') ?? 0;
    motion.drag = clamp(num(mt, 'drag') ?? 0, 0, 1);
    motion.collide = bool(mt, 'collide') ?? false;
    motion.bounce = clamp(num(mt, 'bounce') ?? 0, 0, 1);
    motion.friction = clamp(num(mt, 'friction') ?? 0.6, 0, 1);
    motion.attract = num(mt, 'attract') ?? 0;
    const ax = get(mt, 'attractAxis');
    motion.attractAxis = (typeof ax === 'string' && ax.toUpperCase() === 'Y') || ax === true;
    motion.wander = Math.max(0, num(mt, 'wander') ?? 0);
  }

  // ---- animation
  const at = table(top, 'animation');
  const animation: Animation = defaultAnimation();
  animation.scale = range(get(at, 'scale'), [0.3, 0.3], 'animation.scale');
  if (at) {
    animation.endScale = Math.max(0, num(at, 'endScale') ?? 1);
    animation.easing = enumOf<Easing>(EASINGS, str(at, 'easing'), 'LINEAR', 'animation.easing');
    animation.fadeIn = Math.max(0, Math.round(num(at, 'fadeIn') ?? 0));
    animation.rotation = range(get(at, 'rotation'), [0, 0], 'animation.rotation');
    animation.spin = range(get(at, 'spin'), [0, 0], 'animation.spin');
    const g = get(at, 'color', 'colors');
    if (g != null) animation.color = gradientFrom(g, errors);
    const o = get(at, 'opacity');
    if (o != null) animation.opacity = curveFrom(o, errors);
  }

  return {
    effect: { id, viewers, keyframeTicks: clamp(kf == null ? 4 : Math.round(kf), 1, 59), render, emitter, motion, animation },
    errors,
  };
}

function strings(t: JmcTable, one: string, many: string): string[] {
  const out: string[] = [];
  const s = str(t, one); if (s != null) out.push(s);
  for (const v of list(t, many) ?? []) if (v != null) out.push(String(v));
  return out;
}

/** Цвет из значения файла как строка: "#..."/имя как есть, [r, g, b(, a)] - в "#rrggbb(aa)". */
export function colorString(v: JmcValue): string | null {
  if (typeof v === 'string') return v;
  if (Array.isArray(v) && (v.length === 3 || v.length === 4) && v.every((x) => typeof x === 'number')) {
    const h = (n: number) => clamp(Math.round(n as number), 0, 255).toString(16).padStart(2, '0');
    return '#' + h(v[0] as number) + h(v[1] as number) + h(v[2] as number) + (v.length === 4 ? h(v[3] as number) : '');
  }
  return null;
}

function stopsOf(list: JmcValue[], withBend = false): number[] | null {
  const out: number[] = [];
  for (const item of list) {
    if (!Array.isArray(item) || (item.length !== 2 && !(withBend && item.length === 3)) || typeof item[0] !== 'number') return null;
    out.push(item[0]);
  }
  return out;
}

function checkOrder(at: number[], field: string, errors: string[]): void {
  for (let i = 0; i < at.length; i++) {
    if (at[i] < 0 || at[i] > 1) { errors.push(`${field}: доли должны быть от 0 до 1, получено ${at[i]}`); return; }
    if (i > 0 && at[i] < at[i - 1]) { errors.push(`${field}: доли должны идти по возрастанию`); return; }
  }
}

function gradientFrom(v: JmcValue, errors: string[]): Stop<string>[] | null {
  const field = 'animation.color';
  const one = (x: JmcValue): string | null => { const s = colorString(x); if (s != null && parseColor(s) != null) return s; errors.push(`${field}: неизвестный цвет ${JSON.stringify(x)}`); return null; };
  if (!Array.isArray(v) || !v.length || v.every((x) => typeof x === 'number')) { const c = one(v); return c ? [{ at: null, value: c }] : null; }
  const at = stopsOf(v);
  if (at) {
    checkOrder(at, field, errors);
    return v.map((p, i) => ({ at: at[i], value: one((p as JmcValue[])[1]) ?? '#ffffff' }));
  }
  return v.map((c) => ({ at: null, value: one(c) ?? '#ffffff' }));
}

function curveFrom(v: JmcValue, errors: string[]): Stop<number>[] | null {
  const field = 'animation.opacity';
  if (typeof v === 'number') return [{ at: null, value: v }];
  if (Array.isArray(v) && v.length) {
    if (v.every((x) => typeof x === 'number')) return (v as number[]).map((x) => ({ at: null, value: x }));
    const at = stopsOf(v, true);
    if (at && v.every((p) => typeof (p as JmcValue[])[1] === 'number')) {
      checkOrder(at, field, errors);
      return v.map((p, i) => {
        const item = p as JmcValue[];
        const stop: Stop<number> = { at: at[i], value: item[1] as number };
        if (item.length === 3) {
          const b = item[2];
          if (Array.isArray(b) && b.length === 2 && typeof b[0] === 'number' && typeof b[1] === 'number') stop.bend = [Math.max(0.25, Math.min(0.75, b[0])), b[1]];
          else errors.push(`${field}: изгиб - [x, dy], например [0.5, 0.2, [0.5, -0.1]]`);
        }
        return stop;
      });
    }
  }
  errors.push(`${field}: ожидается число, [от, до] или [[доля, значение], ...]`);
  return null;
}

/** Проверки, которые плагин делает при загрузке: пустой список - эффект загрузится. */
export function validateEffect(e: ParticleEffect): string[] {
  const out: string[] = [];
  if (!e.id.trim()) out.push('id: пустой');
  if (/^(list|stop)$/i.test(e.id)) out.push(`id '${e.id}' занят командой /displaylib particle ${e.id.toLowerCase()}`);
  if (e.render.type === 'TEXT' && e.render.text == null && !e.render.variants.length && e.render.frames.length < 2) out.push('TEXT: нужен текст, варианты или кадры');
  if (e.render.type === 'ITEM' && !e.render.items.length) out.push('ITEM: нужен хотя бы один предмет');
  if (e.render.type === 'BLOCK' && !e.render.blocks.length) out.push('BLOCK: нужен хотя бы один блок');
  for (const c of e.render.colors) if (parseColor(c) == null) out.push(`цвет ${c}: не распознан`);
  for (const s of e.animation.color ?? []) if (parseColor(s.value) == null) out.push(`цвет ${s.value} в градиенте: не распознан`);
  const order = (stops: Stop<unknown>[] | null, name: string) => {
    if (!stops) return;
    const explicit = stops.filter((s) => s.at != null).length;
    if (explicit && explicit !== stops.length) out.push(`${name}: у всех точек должна быть доля или ни у одной`);
    const at = stops.map((s) => s.at ?? 0);
    for (let i = 0; i < at.length; i++) if (stops[i].at != null && (at[i] < 0 || at[i] > 1 || (i && at[i] < at[i - 1]))) { out.push(`${name}: доли от 0 до 1 по возрастанию`); break; }
  };
  order(e.animation.color, 'градиент цвета');
  order(e.animation.opacity, 'кривая прозрачности');
  if (e.emitter.duration !== 0 && !(e.emitter.rate > 0)) out.push('emitter: при duration нужен rate > 0');
  if (e.motion.lifetime[0] < 1) out.push('lifetime: не меньше 1 тика');
  return out;
}

// ---------------------------------------------------------------- запись

function jstr(s: string): string {
  return '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t') + '"';
}
export function jnum(n: number): string {
  if (!Number.isFinite(n)) return '0';
  if (Number.isInteger(n)) return String(n);
  return String(Math.round(n * 10000) / 10000);
}
const rangeJ = (r: Range) => (Math.abs(r[0] - r[1]) < 1e-12 ? jnum(r[0]) : '[' + jnum(r[0]) + ', ' + jnum(r[1]) + ']');
const vecJ = (v: readonly number[]) => '[' + v.map(jnum).join(', ') + ']';
const sameR = (a: Range, b: Range) => Math.abs(a[0] - b[0]) < 1e-12 && Math.abs(a[1] - b[1]) < 1e-12;

/** Таблица { k: v, ... }: в одну строку, если коротко, иначе по строке на поле. */
function tableJ(fields: [string, string][], indent = ''): string {
  if (!fields.length) return '{ }';
  const inline = '{ ' + fields.map(([k, v]) => k + ': ' + v).join(', ') + ' }';
  if (inline.length <= 96 && !inline.includes('\n')) return inline;
  return '{\n' + fields.map(([k, v]) => indent + '    ' + k + ': ' + v + ',').join('\n') + '\n' + indent + '}';
}

/** Изгиб, неотличимый от прямой, не пишем. */
const isStraight = (b: [number, number] | null | undefined) => !b || (Math.abs(b[1]) < 1e-9);
function stopsJ<T>(stops: Stop<T>[], val: (v: T) => string): string {
  const bends = stops.some((s, i) => i > 0 && !isStraight(s.bend));
  if (stops.length === 1 && stops[0].at == null) return val(stops[0].value);
  const explicit = bends || stops.every((s) => s.at != null);
  const at = (s: Stop<T>, i: number) => s.at ?? (stops.length === 1 ? 0 : i / (stops.length - 1));
  const items = stops.map((s, i) => (explicit
    ? '[' + jnum(Math.round(at(s, i) * 10000) / 10000) + ', ' + val(s.value) + (i > 0 && !isStraight(s.bend) ? ', [' + jnum(Math.round(s.bend![0] * 1000) / 1000) + ', ' + jnum(Math.round(s.bend![1] * 1000) / 1000) + ']' : '') + ']'
    : val(s.value)));
  const inline = '[' + items.join(', ') + ']';
  if (inline.length <= 72) return inline;
  return '[\n' + items.map((i) => '        ' + i + ',').join('\n') + '\n    ]';
}

/** Эффект → текст .jmc в стиле примеров плагина: поля со значениями по умолчанию опускаются. */
export function effectToJmc(e: ParticleEffect): string {
  const L: string[] = [];
  L.push(`// ${e.id}: эффект частиц DisplayLib (конфиг Jumper, .jmc) - /displaylib particle ${e.id}`);
  L.push(`String id = ${jstr(e.id)};`);
  L.push(`String viewers = ${jstr(e.viewers.toLowerCase())};`);
  L.push(`int keyframeTicks = ${e.keyframeTicks};`);
  L.push('');

  const r = e.render; const rf: [string, string][] = [['type', jstr(r.type)]];
  if (r.type === 'TEXT' && r.frames.length > 1) {
    const items = r.frames.map(richToJmc);
    const inline = '[' + items.join(', ') + ']';
    rf.push(['frames', inline.length <= 72 ? inline : '[\n' + items.map((x) => '        ' + x + ',').join('\n') + '\n    ]']);
    if (r.framesOverLife) rf.push(['framesOverLife', 'true']);
    if (!r.framesOverLife || r.frameInterval !== 4) rf.push(['frameInterval', jnum(r.frameInterval)]);
    if (r.frameRandomStart) rf.push(['frameStart', '"random"']);
  } else if (r.type === 'TEXT') {
    if (r.text != null) rf.push(['text', richToJmc(r.text)]);
    if (r.variants.length) rf.push(['variants', '[' + r.variants.map(richToJmc).join(', ') + ']']);
  }
  if (r.type === 'ITEM') rf.push(r.items.length === 1 ? ['item', jstr(r.items[0])] : ['items', '[' + r.items.map(jstr).join(', ') + ']']);
  if (r.type === 'BLOCK') rf.push(r.blocks.length === 1 ? ['block', jstr(r.blocks[0])] : ['blocks', '[' + r.blocks.map(jstr).join(', ') + ']']);
  if ((r.type === 'TEXT' || r.type === 'PIXEL') && r.colors.length) {
    rf.push(r.colors.length === 1 ? ['color', jstr(r.colors[0])] : ['colors', '[' + r.colors.map(jstr).join(', ') + ']']);
  }
  if (r.billboard !== (r.type === 'BLOCK' ? 'FIXED' : 'CENTER')) rf.push(['billboard', jstr(r.billboard)]);
  if (r.brightness != null) rf.push(['brightness', jnum(r.brightness)]);
  if (r.type === 'TEXT') {
    if (r.shadow) rf.push(['shadow', 'true']);
    if (r.background) rf.push(['background', vecJ(r.background)]);
    if (r.background && r.backgroundAlpha != null) rf.push(['backgroundAlpha', jnum(r.backgroundAlpha)]);
  }
  if ((r.type === 'ITEM' || r.type === 'BLOCK') && r.glow) {
    rf.push(['glow', 'true']);
    if (r.glowColor) rf.push(['glowColor', jstr(r.glowColor)]);
  }
  L.push('dyn render = ' + tableJ(rf) + ';');
  L.push('');

  const em = e.emitter; const d0 = defaultEmitter(); const ef: [string, string][] = [];
  if (em.shape !== 'POINT') ef.push(['shape', jstr(em.shape)]);
  if ((em.shape === 'SPHERE' || em.shape === 'CIRCLE') && em.radius !== d0.radius) ef.push(['radius', jnum(em.radius)]);
  if (em.shape === 'BOX') ef.push(['size', vecJ(em.size)]);
  if (em.surface && em.shape !== 'POINT') ef.push(['surface', 'true']);
  if (em.count != null) ef.push(['count', jnum(em.count)]);
  if (em.duration !== 0) { ef.push(['duration', jnum(em.duration)]); ef.push(['rate', jnum(em.rate)]); }
  if (ef.length) { L.push('dyn emitter = ' + tableJ(ef) + ';'); L.push(''); }

  const m = e.motion; const m0 = defaultMotion(); const mf: [string, string][] = [];
  if (!sameR(m.lifetime, m0.lifetime)) mf.push(['lifetime', rangeJ(m.lifetime)]);
  if (!sameR(m.speed, m0.speed)) mf.push(['speed', rangeJ(m.speed)]);
  if (m.direction === 'OUTWARD') mf.push(['direction', '"OUTWARD"']);
  else if (!(m.direction[0] === 0 && m.direction[1] === 1 && m.direction[2] === 0)) mf.push(['direction', vecJ(m.direction)]);
  if (m.spread) mf.push(['spread', jnum(m.spread)]);
  if (m.gravity) mf.push(['gravity', jnum(m.gravity)]);
  if (m.drag) mf.push(['drag', jnum(m.drag)]);
  if (m.collide) { mf.push(['collide', 'true']); if (m.bounce) mf.push(['bounce', jnum(m.bounce)]); if (m.friction !== m0.friction) mf.push(['friction', jnum(m.friction)]); }
  if (m.attract) { mf.push(['attract', jnum(m.attract)]); if (m.attractAxis) mf.push(['attractAxis', '"Y"']); }
  if (m.wander) mf.push(['wander', jnum(m.wander)]);
  if (mf.length) { L.push('dyn motion = ' + tableJ(mf) + ';'); L.push(''); }

  const a = e.animation; const a0 = defaultAnimation(); const af: [string, string][] = [];
  if (!sameR(a.scale, a0.scale)) af.push(['scale', rangeJ(a.scale)]);
  if (a.endScale !== 1) af.push(['endScale', jnum(a.endScale)]);
  if (a.easing !== 'LINEAR') af.push(['easing', jstr(a.easing)]);
  if (a.fadeIn) af.push(['fadeIn', jnum(a.fadeIn)]);
  if (!sameR(a.rotation, a0.rotation)) af.push(['rotation', rangeJ(a.rotation)]);
  if (!sameR(a.spin, a0.spin)) af.push(['spin', rangeJ(a.spin)]);
  if (a.color?.length) af.push(['color', stopsJ(a.color, jstr)]);
  if (a.opacity?.length) af.push(['opacity', stopsJ(a.opacity, jnum)]);
  if (af.length) { L.push('dyn animation = ' + tableJ(af) + ';'); L.push(''); }

  return L.join('\n').replace(/\n+$/, '\n');
}

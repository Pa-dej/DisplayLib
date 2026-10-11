// Превью morph-перехода между экранами - повторяет MorphTransition плагина:
//   matched  - виджеты с одинаковым id и тем же классом сущности (TextDisplay ↔ TextDisplay, т.е. TEXT/SPRITE
//              между собой; ItemDisplay ↔ ItemDisplay) и фон: позиция/масштаб/translation/поворот/цвет фона
//              интерполируются клиентом за D тиков; текст и цвет текста - по steps шагам (morphText: общий
//              префикс и суффикс остаются, середина старой съедается, новой - растёт); спрайт/предмет - на половине;
//   fade-out - сущности только старого экрана: масштаб → 0;
//   fade-in  - только нового: масштаб 0 → цель с тика 1.
import type { Screen, Widget, Background, RichText, RGB } from '../model/screen';
import { cloneDeep, richToPlain as plainText } from '../model/screen';

export interface MorphPlan {
  ms: number;
  steps: number;
  matched: { from: Widget; to: Widget }[];
  fadeOut: Widget[];
  fadeIn: Widget[];
  bg: { from: Background | null; to: Background | null };
}

const entityClass = (w: Widget) => (w.type === 'ITEM_BUTTON' ? 'item' : 'text');

export function planMorph(from: Screen, to: Screen): MorphPlan {
  const D = Math.max(1, Math.min(59, to.morph.duration));
  const steps = Math.max(1, Math.min(to.morph.steps > 0 ? to.morph.steps : Math.max(1, Math.floor(D / 2)), D));
  const used = new Set<string>();
  const matched: { from: Widget; to: Widget }[] = [];
  const fadeOut: Widget[] = [];
  for (const a of from.widgets) {
    if (a.hidden) continue;
    const b = a.id ? to.widgets.find((x) => !x.hidden && !used.has(x.uid) && x.id === a.id && entityClass(x) === entityClass(a)) : undefined;
    if (b) { used.add(b.uid); matched.push({ from: a, to: b }); } else fadeOut.push(a);
  }
  const fadeIn = to.widgets.filter((x) => !x.hidden && !used.has(x.uid));
  return { ms: D * 50, steps, matched, fadeOut, fadeIn, bg: { from: from.background, to: to.background } };
}

/** Как MorphTransition.morphText: середина старой строки съедается, новой - растёт. */
export function morphText(a: string, b: string, f: number): string {
  let p = 0; const max = Math.min(a.length, b.length);
  while (p < max && a[p] === b[p]) p++;
  let q = 0;
  while (q < max - p && a[a.length - 1 - q] === b[b.length - 1 - q]) q++;
  const prefix = a.slice(0, p), suffix = a.slice(a.length - q);
  const aMid = a.slice(p, a.length - q), bMid = b.slice(p, b.length - q);
  const nb = Math.round(bMid.length * f);
  const na = aMid.length - Math.round(aMid.length * f);
  return prefix + bMid.slice(0, nb) + aMid.slice(aMid.length - na) + suffix;
}

const hasSprite = (t: RichText) => typeof t !== 'string' && t.some((s) => s.sprite !== undefined);
const firstColor = (t: RichText): string | undefined => (typeof t === 'string' ? undefined : t.find((s) => s.color)?.color);

export const lerp = (a: number, b: number, f: number) => a + (b - a) * f;
export const lerp3 = (a: readonly number[], b: readonly number[], f: number): [number, number, number] => [lerp(a[0], b[0], f), lerp(a[1], b[1], f), lerp(a[2], b[2], f)];
export const lerpRgb = (a: RGB, b: RGB, f: number): RGB => [Math.round(lerp(a[0], b[0], f)), Math.round(lerp(a[1], b[1], f)), Math.round(lerp(a[2], b[2], f))];

/**
 * Состояние matched-виджета на доле пути f (0…1): геометрия - непрерывно (клиент интерполирует),
 * текст/цвет текста - по шагам, спрайт и предмет - на половине. Возвращает синтетический виджет для отрисовки.
 */
export function matchedAt(from: Widget, to: Widget, f: number, steps: number): Widget {
  const w = cloneDeep(to);
  w.uid = from.uid;
  w.position = lerp3(from.position, to.position, f);
  w.scale = lerp3(from.scale, to.scale, f);
  w.translation = lerp3(from.translation, to.translation, f);
  w.rotation = lerp(from.rotation, to.rotation, f);
  w.backgroundColor = lerpRgb(from.backgroundColor, to.backgroundColor, f);
  w.backgroundAlpha = Math.round(lerp(from.backgroundAlpha, to.backgroundAlpha, f));
  w.hoverAnimation = null;
  if (f >= 1) return w;
  const step = Math.floor(f * steps) / steps; // доля пути на последнем пройденном шаге
  if (w.type === 'ITEM_BUTTON') { w.material = step < 0.5 ? from.material : to.material; return w; }
  const a = frameText(from), b = frameText(to);
  if (plainText(a) === plainText(b) && !hasSprite(a) && !hasSprite(b)) { w.text = a; w.frames = []; return w; }
  if (hasSprite(a) || hasSprite(b) || from.type !== to.type) {
    // спрайт с любой стороны - смена на половине; SPRITE_BUTTON рисуется своим полем sprite
    const src = step < 0.5 ? from : to;
    w.type = src.type; w.text = frameText(src); w.frames = []; w.sprite = src.sprite; w.atlas = src.atlas; w.spriteColor = src.spriteColor;
    return w;
  }
  const ca = firstColor(a), cb = firstColor(b);
  const color = ca && cb ? lerpHex(ca, cb, step) : step < 0.5 ? ca : cb;
  w.frames = []; w.hoveredText = null;
  w.text = [{ text: morphText(plainText(a), plainText(b), step), color }];
  return w;
}

function frameText(w: Widget): RichText { return w.frames.length ? w.frames[0] : w.text; }

function lerpHex(a: string, b: string, f: number): string {
  const pa = hexRgb(a), pb = hexRgb(b);
  if (!pa || !pb) return f < 0.5 ? a : b;
  const c = lerpRgb(pa, pb, f);
  return '#' + c.map((v) => v.toString(16).padStart(2, '0')).join('');
}
const NAMED: Record<string, string> = { black: '#000000', dark_blue: '#0000aa', dark_green: '#00aa00', dark_aqua: '#00aaaa', dark_red: '#aa0000', dark_purple: '#aa00aa', gold: '#ffaa00', gray: '#aaaaaa', dark_gray: '#555555', blue: '#5555ff', green: '#55ff55', aqua: '#55ffff', red: '#ff5555', light_purple: '#ff55ff', yellow: '#ffff55', white: '#ffffff' };
function hexRgb(c: string): RGB | null {
  const h = c.startsWith('#') ? c : NAMED[c.toLowerCase()];
  const m = h && /^#?([0-9a-f]{6})$/i.exec(h);
  if (!m) return null;
  const v = parseInt(m[1], 16);
  return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
}

export function backgroundAt(from: Background, to: Background, f: number): Background {
  const b = cloneDeep(to);
  b.position = lerp3(from.position, to.position, f);
  b.scale = lerp3(from.scale, to.scale, f);
  b.translation = lerp3(from.translation, to.translation, f);
  b.anchor = [lerp(from.anchor[0], to.anchor[0], f), lerp(from.anchor[1], to.anchor[1], f)];
  b.color = lerpRgb(from.color, to.color, f);
  b.alpha = Math.round(lerp(from.alpha, to.alpha, f));
  b.text = f < 0.5 ? from.text : to.text;
  return b;
}

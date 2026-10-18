// Порт ParticleColors плагина: цвет ARGB (беззнаковое число), смешивание в OkLab, градиенты и кривые по доле жизни.
import { NAMED_COLORS } from '../render/text';
import type { Stop } from './model';

/** "#rrggbb", "#rrggbbaa" или имя (как NamedTextColor) → ARGB; null - не цвет (плагин такой файл не примет). */
export function parseColor(s: string): number | null {
  const v = s.trim();
  if (/^#[0-9a-f]{8}$/i.test(v)) {
    const x = parseInt(v.slice(1), 16);
    return (((x & 0xff) << 24) | (x >>> 8)) >>> 0;
  }
  if (/^#[0-9a-f]{6}$/i.test(v)) return (0xff000000 | parseInt(v.slice(1), 16)) >>> 0;
  const named = NAMED_COLORS[v.toLowerCase()];
  return named ? (0xff000000 | parseInt(named.slice(1), 16)) >>> 0 : null;
}

export const alpha = (argb: number) => argb >>> 24;
export const rgbOf = (argb: number) => argb & 0xffffff;
export function argb(a: number, r: number, g: number, b: number): number {
  return (((a & 0xff) << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff)) >>> 0;
}
export function withOpacity(c: number, opacity: number): number {
  const a = Math.round(alpha(c) * Math.max(0, Math.min(1, opacity)));
  return ((c & 0x00ffffff) | (a << 24)) >>> 0;
}
/** "#rrggbb" для CSS / three.js. */
export function cssHex(c: number): string { return '#' + rgbOf(c).toString(16).padStart(6, '0'); }
/** ARGB → строка как в файле: "#rrggbb" или "#rrggbbaa". */
export function colorToString(c: number): string {
  const a = alpha(c);
  return cssHex(c) + (a === 255 ? '' : a.toString(16).padStart(2, '0'));
}

// ---- OkLab (Björn Ottosson), те же коэффициенты, что в плагине

const linear = (c: number) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
function srgb(c: number): number {
  c = Math.max(0, Math.min(1, c));
  const v = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
  return Math.round(Math.max(0, Math.min(1, v)) * 255);
}
export function toOklab(c: number): [number, number, number] {
  const r = linear(((c >>> 16) & 0xff) / 255), g = linear(((c >>> 8) & 0xff) / 255), b = linear((c & 0xff) / 255);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
}
export function fromOklab(L: number, A: number, B: number): number {
  let l = L + 0.3963377774 * A + 0.2158037573 * B;
  let m = L - 0.1055613458 * A - 0.0638541728 * B;
  let s = L - 0.0894841775 * A - 1.291485548 * B;
  l = l * l * l; m = m * m * m; s = s * s * s;
  return argb(255,
    srgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    srgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s));
}
/** Смесь: RGB в OkLab, прозрачность линейно. */
export function lerpColor(from: number, to: number, t: number): number {
  if (t <= 0) return from;
  if (t >= 1) return to;
  const a = toOklab(from), b = toOklab(to);
  const rgb = fromOklab(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
  const al = Math.round(alpha(from) + (alpha(to) - alpha(from)) * t);
  return ((rgb & 0x00ffffff) | (al << 24)) >>> 0;
}

// ---- кривые по доле жизни

/** Доли точек: явные или равномерные (как Gradient.parse / Curve.parse плагина). */
function times<T>(stops: Stop<T>[]): number[] {
  const explicit = stops.every((s) => s.at != null);
  return stops.map((s, i) => (explicit ? (s.at as number) : stops.length === 1 ? 0 : i / (stops.length - 1)));
}

export interface Gradient { at: number[]; colors: number[] }
export interface Curve { at: number[]; values: number[] }

export function gradientOf(stops: Stop<string>[] | null): Gradient | null {
  if (!stops?.length) return null;
  return { at: times(stops), colors: stops.map((s) => parseColor(s.value) ?? 0xffffffff) };
}
export function curveOf(stops: Stop<number>[] | null): Curve {
  if (!stops?.length) return { at: [0], values: [1] };
  return { at: times(stops), values: stops.map((s) => s.value) };
}

export function sampleGradient(g: Gradient, t: number): number {
  if (t <= g.at[0]) return g.colors[0];
  for (let i = 1; i < g.at.length; i++) {
    if (t <= g.at[i]) {
      const span = g.at[i] - g.at[i - 1];
      return lerpColor(g.colors[i - 1], g.colors[i], span <= 0 ? 1 : (t - g.at[i - 1]) / span);
    }
  }
  return g.colors[g.colors.length - 1];
}
export function sampleCurve(c: Curve, t: number): number {
  if (t <= c.at[0]) return c.values[0];
  for (let i = 1; i < c.at.length; i++) {
    if (t <= c.at[i]) {
      const span = c.at[i] - c.at[i - 1];
      return c.values[i - 1] + (c.values[i] - c.values[i - 1]) * (span <= 0 ? 1 : (t - c.at[i - 1]) / span);
    }
  }
  return c.values[c.values.length - 1];
}

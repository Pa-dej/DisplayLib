// Цвет из любой записи, которую люди вставляют из буфера: #rgb, #rgba, #rrggbb, #rrggbbaa, rrggbb без #,
// 0xRRGGBB, rgb()/rgba() (запятые или пробелы, проценты, "/ альфа"), hsl()/hsla(), hsv()/hsb(),
// "255, 128, 0" и "255 128 0 / 50%", [r, g, b(, a)], {r, g, b}, §c / &c, имена Minecraft и CSS.
// Плюс перевод между RGB, HSV и HSL для пикера.
import { NAMED_COLORS } from '../render/text';

/** r, g, b - 0..255 (целые), a - 0..1 */
export interface RGBA { r: number; g: number; b: number; a: number }
/** h - 0..360, s, v, l - 0..1 */
export interface HSV { h: number; s: number; v: number }
export interface HSL { h: number; s: number; l: number }

const clamp = (x: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, x));
const byte = (x: number) => clamp(Math.round(x), 0, 255);

const LEGACY = '0123456789abcdef';

/** Разобрать цвет; null - не похоже на цвет. */
export function parseAnyColor(input: string): RGBA | null {
  let s = input.trim().replace(/^['"`]+|['"`;,]+$/g, '').trim();
  if (!s) return null;
  const low = s.toLowerCase();

  // §c / &c - коды цвета Minecraft
  const legacy = /^[§&]([0-9a-f])$/i.exec(s);
  if (legacy) return hexToRgba(Object.values(NAMED_COLORS)[LEGACY.indexOf(legacy[1].toLowerCase())]);

  // имена Minecraft (dark_red) и с пробелом/дефисом (dark red, dark-red)
  const mc = NAMED_COLORS[low.replace(/[\s-]+/g, '_')];
  if (mc) return hexToRgba(mc);

  // hex: #..., 0x..., голые 3/4/6/8 hex-цифр (только если есть буква a-f или длина 6/8 - чтобы "255" не стал #225555)
  const hx = /^(?:#|0x)?([0-9a-f]{3,8})$/i.exec(s);
  if (hx && [3, 4, 6, 8].includes(hx[1].length) && (s.startsWith('#') || /^0x/i.test(s) || hx[1].length >= 6 || /[a-f]/i.test(hx[1]))) {
    return hexToRgba('#' + hx[1]);
  }

  // функции: rgb(a), hsl(a), hsv/hsb
  const fn = /^(rgba?|hsla?|hsva?|hsba?)\s*\(\s*(.*?)\s*\)$/i.exec(s);
  if (fn) {
    const kind = fn[1].toLowerCase();
    const parts = splitArgs(fn[2]);
    if (!parts || parts.length < 3) return null;
    const a = parts.length > 3 ? alphaOf(parts[3]) : 1;
    if (a == null) return null;
    if (kind.startsWith('rgb')) {
      const ch = parts.slice(0, 3).map(channel);
      if (ch.some((x) => x == null)) return null;
      return { r: byte(ch[0]!), g: byte(ch[1]!), b: byte(ch[2]!), a };
    }
    const h = hueOf(parts[0]); const p1 = percentOf(parts[1]); const p2 = percentOf(parts[2]);
    if (h == null || p1 == null || p2 == null) return null;
    const rgb = kind.startsWith('hsl') ? hslToRgb({ h, s: p1, l: p2 }) : hsvToRgb({ h, s: p1, v: p2 });
    return { ...rgb, a };
  }

  // [r, g, b(, a)] / {r: .., g: .., b: ..} / "r, g, b" / "r g b / a"
  const obj = /^\{(.*)\}$/.exec(s);
  if (obj) {
    const get = (k: string) => { const m = new RegExp('\\b' + k + '\\w*\\s*[:=]\\s*(-?[\\d.]+)', 'i').exec(obj[1]); return m ? Number(m[1]) : null; };
    const r = get('r'), g = get('g'), b = get('b'), a = get('a');
    if (r != null && g != null && b != null) return { r: byte(r), g: byte(g), b: byte(b), a: a == null ? 1 : a > 1 ? clamp(a / 255, 0, 1) : clamp(a, 0, 1) };
    return null;
  }
  const bare = s.replace(/^\[|\]$/g, '');
  if (/^[-\d.%\s,/]+$/.test(bare)) {
    const parts = splitArgs(bare);
    if (parts && (parts.length === 3 || parts.length === 4)) {
      const ch = parts.slice(0, 3).map(channel);
      // 0..1 у всех трёх - доли (как в шейдерах), иначе 0..255
      const unit = ch.every((x) => x != null && x <= 1) && parts.slice(0, 3).some((p) => p.includes('.'));
      if (ch.every((x) => x != null)) {
        let a: number | null = 1;
        if (parts.length === 4) {
          const raw = parts[3];
          a = raw.endsWith('%') || Number(raw) <= 1 ? alphaOf(raw) : clamp(Number(raw) / 255, 0, 1);
        }
        if (a == null) return null;
        return unit ? { r: byte(ch[0]! * 255), g: byte(ch[1]! * 255), b: byte(ch[2]! * 255), a } : { r: byte(ch[0]!), g: byte(ch[1]!), b: byte(ch[2]!), a };
      }
    }
  }

  // остальные CSS-имена (orange, rebeccapurple) - через браузер
  if (/^[a-z]+$/i.test(s) && typeof document !== 'undefined') return cssNamed(low);
  return null;
}

function splitArgs(s: string): string[] | null {
  // "255, 0, 0, .5" | "255 0 0 / 50%" | "120deg 50% 50%"
  const slash = s.split('/');
  if (slash.length > 2) return null;
  const main = slash[0].split(/[\s,]+/).filter(Boolean);
  if (slash.length === 2) { const a = slash[1].trim(); if (!a) return null; main.push(a); }
  return main;
}
function channel(p: string): number | null {
  if (p.endsWith('%')) { const n = Number(p.slice(0, -1)); return Number.isFinite(n) ? (n / 100) * 255 : null; }
  const n = Number(p); return Number.isFinite(n) ? n : null;
}
function alphaOf(p: string): number | null {
  if (p.endsWith('%')) { const n = Number(p.slice(0, -1)); return Number.isFinite(n) ? clamp(n / 100, 0, 1) : null; }
  const n = Number(p); return Number.isFinite(n) ? clamp(n, 0, 1) : null;
}
function percentOf(p: string): number | null {
  const n = Number(p.replace('%', ''));
  if (!Number.isFinite(n)) return null;
  return clamp(p.endsWith('%') || n > 1 ? n / 100 : n, 0, 1);
}
function hueOf(p: string): number | null {
  const m = /^(-?[\d.]+)(deg|rad|grad|turn)?$/i.exec(p); if (!m) return null;
  let n = Number(m[1]);
  const u = (m[2] ?? 'deg').toLowerCase();
  if (u === 'rad') n = (n * 180) / Math.PI; else if (u === 'grad') n *= 0.9; else if (u === 'turn') n *= 360;
  return ((n % 360) + 360) % 360;
}

let ctx2d: CanvasRenderingContext2D | null = null;
function cssNamed(name: string): RGBA | null {
  ctx2d ??= document.createElement('canvas').getContext('2d');
  if (!ctx2d) return null;
  ctx2d.fillStyle = '#010203';
  ctx2d.fillStyle = name;
  const v = String(ctx2d.fillStyle);
  if (v === '#010203' && name !== '#010203') return null;
  return hexToRgba(v) ?? parseAnyColor(v);
}

export function hexToRgba(hex: string): RGBA | null {
  let h = hex.replace(/^#/, '');
  if (!/^[0-9a-f]+$/i.test(h)) return null;
  if (h.length === 3 || h.length === 4) h = h.split('').map((c) => c + c).join('');
  if (h.length !== 6 && h.length !== 8) return null;
  const n = (i: number) => parseInt(h.slice(i, i + 2), 16);
  return { r: n(0), g: n(2), b: n(4), a: h.length === 8 ? n(6) / 255 : 1 };
}

const h2 = (x: number) => byte(x).toString(16).padStart(2, '0');
/** "#rrggbb" или "#rrggbbaa" (если альфа < 1 и withAlpha). */
export function rgbaToHex(c: RGBA, withAlpha = true): string {
  const a = Math.round(c.a * 255);
  return '#' + h2(c.r) + h2(c.g) + h2(c.b) + (withAlpha && a < 255 ? h2(a) : '');
}
export function rgbaToCss(c: RGBA): string { return `rgba(${c.r}, ${c.g}, ${c.b}, ${+c.a.toFixed(3)})`; }

export function rgbToHsv(c: { r: number; g: number; b: number }): HSV {
  const r = c.r / 255, g = c.g / 255, b = c.b / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return { h: ((h * 60) + 360) % 360, s: max ? d / max : 0, v: max };
}
export function hsvToRgb({ h, s, v }: HSV): { r: number; g: number; b: number } {
  const f = (n: number) => { const k = (n + h / 60) % 6; return v - v * s * Math.max(0, Math.min(k, 4 - k, 1)); };
  return { r: byte(f(5) * 255), g: byte(f(3) * 255), b: byte(f(1) * 255) };
}
export function rgbToHsl(c: { r: number; g: number; b: number }): HSL {
  const { h, s: sv, v } = rgbToHsv(c);
  const l = v * (1 - sv / 2);
  return { h, s: l === 0 || l === 1 ? 0 : (v - l) / Math.min(l, 1 - l), l };
}
export function hslToRgb({ h, s, l }: HSL): { r: number; g: number; b: number } {
  const v = l + s * Math.min(l, 1 - l);
  return hsvToRgb({ h, s: v === 0 ? 0 : 2 * (1 - l / v), v });
}

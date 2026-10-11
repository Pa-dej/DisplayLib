// Текстура TextDisplay: фон + строки текста, пиксель в пиксель с клиентом (см. metrics.ts).
import * as THREE from 'three';
import type { RichText, RGB, Segment } from '../model/screen';
import { calibration } from './metrics';
import { font } from './font';
import { drawSprite } from './sprites';

export const NAMED_COLORS: Record<string, string> = {
  black: '#000000', dark_blue: '#0000AA', dark_green: '#00AA00', dark_aqua: '#00AAAA', dark_red: '#AA0000',
  dark_purple: '#AA00AA', gold: '#FFAA00', gray: '#AAAAAA', dark_gray: '#555555', blue: '#5555FF', green: '#55FF55',
  aqua: '#55FFFF', red: '#FF5555', light_purple: '#FF55FF', yellow: '#FFFF55', white: '#FFFFFF',
};
const LEGACY = '0123456789abcdef'.split('').map((c, i) => [c, Object.values(NAMED_COLORS)[i]] as const);
const LEGACY_MAP = new Map<string, string>(LEGACY);

export function cssColor(c: string | undefined, fallback = '#FFFFFF'): string {
  if (!c) return fallback;
  if (c.startsWith('#')) return /^#[0-9a-f]{6}$/i.test(c) ? c.toUpperCase() : fallback;
  return NAMED_COLORS[c.toLowerCase()] ?? fallback;
}

export interface Run { text: string; color: string; bold: boolean; sprite?: string; atlas?: string }

/** Строки из RichText: сегменты + §-коды внутри строк (клиент их разбирает в литеральном тексте). */
export function toRuns(t: RichText): Run[][] {
  const segs: Segment[] = typeof t === 'string' ? [{ text: t }] : t;
  const lines: Run[][] = [[]];
  for (const s of segs) {
    if (s.sprite !== undefined) { if (s.sprite) lines[lines.length - 1].push({ text: '', color: cssColor(s.color), bold: false, sprite: s.sprite, atlas: s.atlas }); continue; }
    let color = cssColor(s.color);
    let bold = false;
    const base = color;
    let buf = '';
    const flush = () => { if (buf) { lines[lines.length - 1].push({ text: buf, color, bold }); buf = ''; } };
    const chars = [...(s.text ?? '')];
    for (let i = 0; i < chars.length; i++) {
      const ch = chars[i];
      if (ch === '\n') { flush(); lines.push([]); continue; }
      if (ch === '§' && i + 1 < chars.length) {
        const code = chars[i + 1].toLowerCase(); i++;
        const c = LEGACY_MAP.get(code);
        flush();
        if (c) { color = c; bold = false; }
        else if (code === 'l') bold = true;
        else if (code === 'r') { color = base; bold = false; }
        continue;
      }
      buf += ch;
    }
    flush();
  }
  return lines;
}

function runWidth(r: Run): number { return r.sprite ? calibration.spriteAdvance : font.width(r.text) + (r.bold ? [...r.text].length : 0); }
function lineWidth(l: Run[]): number { return l.reduce((w, r) => w + runWidth(r), 0); }

/** Перенос по ширине lineWidth (TextDisplay по умолчанию 200 px), по словам, как Font.split. */
export function wrap(lines: Run[][], maxWidth = 200): Run[][] {
  const out: Run[][] = [];
  for (const line of lines) {
    if (lineWidth(line) <= maxWidth) { out.push(line); continue; }
    let cur: Run[] = [];
    let curW = 0;
    for (const r of line) {
      if (r.sprite) {
        const ww = runWidth(r);
        if (curW + ww > maxWidth && cur.length) { out.push(trimEnd(cur)); cur = []; curW = 0; }
        cur.push({ ...r }); curW += ww;
        continue;
      }
      const words = r.text.split(/(?<= )/); // "слово " с пробелом в конце
      for (const w of words) {
        const ww = runWidth({ ...r, text: w });
        if (curW + ww > maxWidth && cur.length) { out.push(trimEnd(cur)); cur = []; curW = 0; }
        cur.push({ ...r, text: w }); curW += ww;
      }
    }
    if (cur.length) out.push(trimEnd(cur));
  }
  return out.length ? out : [[]];
}
function trimEnd(l: Run[]): Run[] {
  const c = l.map((r) => ({ ...r }));
  const last = c[c.length - 1]; if (last) last.text = last.text.replace(/ +$/, '');
  return c;
}

export interface TextLayout {
  lines: Run[][];
  /** k: ширина самой длинной строки, px */
  width: number;
  /** высота текста l = lines * lineHeight, px */
  height: number;
  /** размер фона в px */
  bgW: number;
  bgH: number;
}

export function layout(t: RichText): TextLayout {
  const lines = wrap(toRuns(t));
  const width = Math.max(0, ...lines.map(lineWidth));
  const height = lines.length * calibration.lineHeight;
  return { lines, width, height, bgW: width + calibration.bgLeft + calibration.bgRight, bgH: height + calibration.bgTop + calibration.bgBottom };
}

export interface TextTextureResult { texture: THREE.CanvasTexture; layout: TextLayout }

/**
 * Canvas-текстура фона и текста в координатах шрифта (y вниз): фон [-bgLeft, k + bgRight] x
 * [-bgTop, l + bgBottom]; canvas (0,0) = (-bgLeft, -bgTop). Сдвиг на +1 px по x клиент делает
 * через translate(1 - k/2) - его учитывает scene.ts при позиционировании меша.
 */
export function buildTextTexture(t: RichText, align: 'LEFT' | 'CENTERED' | 'RIGHT', bg: RGB, bgAlpha: number, scale = 4): TextTextureResult {
  const L = layout(t);
  const S = scale;
  const c = document.createElement('canvas');
  c.width = Math.max(1, L.bgW * S);
  c.height = Math.max(1, L.bgH * S);
  const ctx = c.getContext('2d')!;
  ctx.imageSmoothingEnabled = false;
  // фон: альфа < 26 клиент не рисует
  if (bgAlpha >= 26) {
    ctx.fillStyle = `rgba(${bg[0]},${bg[1]},${bg[2]},${Math.min(255, bgAlpha) / 255})`;
    ctx.fillRect(0, 0, c.width, c.height);
  }
  const lh = calibration.lineHeight;
  L.lines.forEach((line, i) => {
    const w = lineWidth(line);
    const x0 = calibration.bgLeft + (align === 'LEFT' ? 0 : align === 'RIGHT' ? L.width - w : Math.floor((L.width - w) / 2));
    const y0 = calibration.bgTop + i * lh;
    let x = x0;
    for (const r of line) {
      if (r.sprite) { drawSprite(ctx, r.sprite, r.atlas, x, y0, S, r.color); x += runWidth(r); continue; }
      font.draw(ctx, r.text, x, y0, S, r.color, calibration.shadow);
      if (r.bold) font.draw(ctx, r.text, x + 1, y0, S, r.color, false);
      x += runWidth(r);
    }
  });
  const texture = new THREE.CanvasTexture(c);
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.LinearMipmapLinearFilter;
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 4;
  return { texture, layout: L };
}

// Глифы: прямоугольные картинки произвольного размера в тексте через шрифт displaylib:icons
// из ресурспака (bitmap-провайдер на каждую картинку: height, ascent). В отличие от спрайта-объекта
// (всегда 8×8) ширина сохраняется пропорционально. Имя глифа = путь в textures/ без .png.
// Реестр (height/ascent) - в state.glyphs, пишется в plugins/DisplayLib/glyphs.jmc для плагина.
import { reactive } from 'vue';
import { spriteImage, spriteState } from './sprites';

export interface GlyphSpec { height: number; ascent: number }
/** Реестр глифов экрана/папки: порядок ключей = порядок символов U+E000… в шрифте. */
export const glyphRegistry = reactive<{ map: Record<string, GlyphSpec>; dirty: boolean }>({ map: {}, dirty: false });

export const GLYPH_FIRST_CHAR = 0xe000;
export const GLYPH_FONT = 'displaylib:icons';

export function glyphChar(name: string): string {
  const i = Object.keys(glyphRegistry.map).indexOf(name);
  return i < 0 ? '?' : String.fromCodePoint(GLYPH_FIRST_CHAR + i);
}

/** Как BitmapProvider: ascent по умолчанию так, чтобы картинка была по центру строки (10 px). */
export function defaultAscent(height: number): number { return Math.round(height / 2 + 2); }

/** Спецификация глифа; создаётся (height = высота картинки) только когда картинка загрузилась. */
export function ensureGlyph(name: string): GlyphSpec {
  const g = glyphRegistry.map[name];
  if (g) return g;
  const img = spriteImage(name, 'minecraft:blocks').canvas; // путь как есть от textures/
  if (!img) return { height: 16, ascent: defaultAscent(16) }; // временно, в реестр не пишем
  const h = Math.min(img.height, 64);
  const spec = { height: h, ascent: defaultAscent(h) };
  glyphRegistry.map[name] = spec; glyphRegistry.dirty = true;
  return spec;
}

/** Размеры глифа в текстовых px: advance = round(w·scale) + 1 (как у bitmap-глифа), высота = height. */
export function glyphMetrics(name: string): { w: number; h: number; advance: number; ascent: number; ready: boolean } {
  void spriteState.revision;
  const spec = glyphRegistry.map[name] ?? { height: 16, ascent: defaultAscent(16) };
  const img = spriteImage(name, 'minecraft:blocks').canvas;
  if (!img) return { w: spec.height, h: spec.height, advance: spec.height + 1, ascent: spec.ascent, ready: false };
  const scale = spec.height / img.height;
  const w = Math.round(img.width * scale);
  return { w, h: spec.height, advance: w + 1, ascent: spec.ascent, ready: true };
}

export function drawGlyph(ctx: CanvasRenderingContext2D, name: string, x: number, y: number, S: number): void {
  const m = glyphMetrics(name);
  const img = spriteImage(name, 'minecraft:blocks').canvas;
  const top = y + 7 - m.ascent; // как у bitmap-глифа: верх на baseline(7) − ascent
  ctx.imageSmoothingEnabled = false;
  if (img) { ctx.drawImage(img, x * S, top * S, m.w * S, m.h * S); return; }
  ctx.fillStyle = 'rgba(255,0,255,.5)'; ctx.fillRect(x * S, top * S, m.w * S, m.h * S);
}

/** Текст glyphs.jmc для папки плагина. */
export function glyphsToJmc(): string {
  const L = ['// glyphs: прямоугольные картинки в тексте (шрифт displaylib:icons из ресурспака DisplayLib-icons.zip).', '// Порядок записей = порядок символов U+E000… - не меняйте вручную, редактор пишет его вместе с паком.', '', 'dyn glyphs = {'];
  for (const [name, g] of Object.entries(glyphRegistry.map)) {
    const m = glyphMetrics(name);
    L.push(`    "${name}": { height: ${g.height}, ascent: ${g.ascent}, width: ${m.advance} },`);
  }
  L.push('};', '');
  return L.join('\n');
}

/** Разбор glyphs.jmc (таблица glyphs) в реестр. */
export function glyphsFromTable(t: Record<string, unknown>): void {
  const map: Record<string, GlyphSpec> = {};
  for (const [name, v] of Object.entries(t)) {
    const spec = (v && typeof v === 'object' ? v : {}) as Record<string, unknown>;
    const height = typeof spec.height === 'number' ? spec.height : 16;
    map[name] = { height, ascent: typeof spec.ascent === 'number' ? spec.ascent : defaultAscent(height) };
  }
  glyphRegistry.map = map; glyphRegistry.dirty = false;
}

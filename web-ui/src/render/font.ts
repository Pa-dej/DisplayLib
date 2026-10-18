// Растровый шрифт Minecraft из ресурсов клиента, структура как в jar:
//   public/assets/minecraft/font/default.json (+ include/*.json по ссылкам "reference")
//   public/assets/minecraft/textures/font/*.png (ascii.png, accented.png, nonlatin_european.png)
// Ширина глифа считается как в клиенте (BitmapProvider): по последнему непрозрачному столбцу,
// advance = ширина + 1; пробел - из провайдера "space"; провайдер "unihex" (unifont) пропускается.
// Если файлов нет - запасной шрифт через canvas (размеры примерные, вид другой).

export interface Glyph {
  canvas: HTMLCanvasElement | null; // null у пробела/пустого
  w: number;      // ширина в текстовых пикселях (без промежутка)
  h: number;
  advance: number;
  ascent: number;
  /** Высота строки глифа в его собственной сетке (height провайдера, обычно 8) */
  height: number;
}

interface SpaceProvider { type: 'space'; advances: Record<string, number> }
interface BitmapProvider { type: 'bitmap'; file: string; height?: number; ascent: number; chars: string[] }
interface ReferenceProvider { type: 'reference'; id: string }
type Provider = SpaceProvider | BitmapProvider | ReferenceProvider | { type: string };

export class BitmapFont {
  readonly glyphs = new Map<string, Glyph>();
  readonly missing: Glyph;
  loaded = false;
  source = 'fallback';
  problems: string[] = [];

  constructor() {
    this.missing = { canvas: null, w: 5, h: 8, advance: 6, ascent: 7, height: 8 };
  }

  /** Загрузить из public/assets/minecraft (font/default.json + textures/font/*.png). */
  async load(base = 'assets/minecraft/'): Promise<boolean> {
    this.glyphs.clear(); this.problems.length = 0;
    try {
      await this.loadFontFile(base, 'font/default.json', 0);
      this.loaded = [...this.glyphs.values()].some((g) => g.canvas);
      this.source = this.loaded ? 'minecraft' : 'fallback';
      if (!this.loaded && !this.problems.length) this.problems.push('нет ни одного png в textures/font');
      return this.loaded;
    } catch (e) {
      this.problems.push(String(e));
      this.loaded = false;
      return false;
    }
  }

  private async loadFontFile(base: string, path: string, depth: number): Promise<void> {
    const res = await fetch(base + path);
    if (!res.ok) throw new Error(path + ': HTTP ' + res.status);
    const json = (await res.json()) as { providers: Provider[] };
    for (const p of json.providers) {
      if (p.type === 'space') {
        for (const [ch, adv] of Object.entries((p as SpaceProvider).advances)) {
          this.glyphs.set(ch, { canvas: null, w: 0, h: 0, advance: adv, ascent: 0, height: 8 });
        }
      } else if (p.type === 'bitmap') {
        await this.loadBitmap(base, p as BitmapProvider);
      } else if (p.type === 'reference' && depth < 4) {
        const id = (p as ReferenceProvider).id.replace(/^minecraft:/, '');
        if (id.includes('unifont')) continue; // unihex: большой zip, не нужен для предпросмотра
        try { await this.loadFontFile(base, 'font/' + id + '.json', depth + 1); }
        catch (e) { this.problems.push(String(e)); }
      }
    }
  }

  private async loadBitmap(base: string, p: BitmapProvider): Promise<void> {
    const file = p.file.replace(/^minecraft:/, '');
    const img = await loadImage(base + 'textures/' + file);
    if (!img) { this.problems.push('нет файла ' + file); return; }
    const rows = p.chars.length;
    const cols = Math.max(...p.chars.map((r) => [...r].length));
    const cellW = Math.floor(img.width / cols);
    const cellH = Math.floor(img.height / rows);
    const height = p.height ?? 8;
    const scale = height / cellH;
    const off = document.createElement('canvas');
    off.width = img.width; off.height = img.height;
    const ctx = off.getContext('2d', { willReadFrequently: true })!;
    ctx.drawImage(img, 0, 0);
    const data = ctx.getImageData(0, 0, img.width, img.height).data;
    for (let r = 0; r < rows; r++) {
      const chars = [...p.chars[r]];
      for (let c = 0; c < chars.length; c++) {
        const ch = chars[c];
        if (ch === '\u0000' || this.glyphs.has(ch)) continue;
        const x0 = c * cellW, y0 = r * cellH;
        // ширина: последний столбец с непрозрачным пикселем
        let w = 0;
        for (let x = cellW - 1; x >= 0 && w === 0; x--) {
          for (let y = 0; y < cellH; y++) {
            if (data[((y0 + y) * img.width + x0 + x) * 4 + 3] !== 0) { w = x + 1; break; }
          }
        }
        if (w === 0) { this.glyphs.set(ch, { canvas: null, w: 0, h: 0, advance: 1, ascent: p.ascent, height }); continue; }
        const g = document.createElement('canvas');
        g.width = w; g.height = cellH;
        g.getContext('2d')!.drawImage(off, x0, y0, w, cellH, 0, 0, w, cellH);
        this.glyphs.set(ch, { canvas: g, w: Math.round(w * scale), h: height, advance: Math.floor(0.5 + w * scale) + 1, ascent: p.ascent, height });
      }
    }
  }

  glyph(ch: string): Glyph {
    return this.glyphs.get(ch) ?? this.missing;
  }

  /** Ширина строки в текстовых пикселях (как Font.width: сумма advance). */
  width(s: string): number {
    if (!this.loaded) return fallbackWidth(s);
    let w = 0;
    for (const ch of s) w += this.glyph(ch).advance;
    return w;
  }

  /**
   * Нарисовать строку на canvas. (x, y) - левый верхний угол строки в текстовых пикселях,
   * S - масштаб (пикселей canvas на текстовый пиксель). Цвет - CSS.
   */
  draw(ctx: CanvasRenderingContext2D, s: string, x: number, y: number, S: number, color: string, shadow: boolean): void {
    if (!this.loaded) { fallbackDraw(ctx, s, x, y, S, color, shadow); return; }
    let cx = x;
    for (const ch of s) {
      const g = this.glyph(ch);
      if (g.canvas) {
        const gy = y + (7 - g.ascent); // строка высотой 10: базовая линия на 7 px (ascent 7 у ascii)
        if (shadow) drawTinted(ctx, g.canvas, (cx + 1) * S, (gy + 1) * S, g.w * S, g.h * S, shadowOf(color));
        drawTinted(ctx, g.canvas, cx * S, gy * S, g.w * S, g.h * S, color);
      } else if (g === this.missing && ch.trim()) {
        // символа нет в растровых шрифтах (в игре он из unifont: ✦, ✧, ♪, …) - рисуем системным шрифтом,
        // чтобы он хотя бы был виден; ширина остаётся как у недостающего глифа
        ctx.save();
        ctx.font = `${9 * S}px "Segoe UI Symbol", "Noto Sans Symbols 2", "DejaVu Sans", sans-serif`;
        ctx.textAlign = 'center'; ctx.textBaseline = 'alphabetic';
        if (shadow) { ctx.fillStyle = shadowOf(color); ctx.fillText(ch, (cx + 3.5) * S, (y + 8) * S, g.w * S); }
        ctx.fillStyle = color;
        ctx.fillText(ch, (cx + 2.5) * S, (y + 7) * S, g.w * S);
        ctx.restore();
      }
      cx += g.advance;
    }
  }
}

function loadImage(url: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = url;
  });
}

const tintCache = new Map<string, HTMLCanvasElement>();
function drawTinted(ctx: CanvasRenderingContext2D, glyph: HTMLCanvasElement, x: number, y: number, w: number, h: number, color: string): void {
  // глифы в ресурсах белые; красим через source-in на временном canvas (кэш по глифу+цвету)
  const key = (glyph as any).__id ?? ((glyph as any).__id = Math.random().toString(36).slice(2));
  const ck = key + '|' + color;
  let t = tintCache.get(ck);
  if (!t) {
    t = document.createElement('canvas');
    t.width = glyph.width; t.height = glyph.height;
    const c = t.getContext('2d')!;
    c.drawImage(glyph, 0, 0);
    c.globalCompositeOperation = 'source-in';
    c.fillStyle = color;
    c.fillRect(0, 0, t.width, t.height);
    if (tintCache.size > 4000) tintCache.clear();
    tintCache.set(ck, t);
  }
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(t, x, y, w, h);
}

function shadowOf(color: string): string {
  const m = /^#?([0-9a-f]{6})$/i.exec(color);
  if (!m) return 'rgba(0,0,0,0.6)';
  const v = parseInt(m[1], 16);
  const r = ((v >> 16) & 255) >> 2, g = ((v >> 8) & 255) >> 2, b = (v & 255) >> 2;
  return `rgb(${r},${g},${b})`;
}

// --- запасной шрифт: примерные ширины Minecraft (5 px + 1 у большинства символов)
const NARROW: Record<string, number> = { ' ': 4, i: 2, l: 3, t: 4, f: 5, k: 5, I: 4, '!': 2, '.': 2, ',': 2, ':': 2, ';': 2, "'": 3, '|': 2, '(': 4, ')': 4, '[': 4, ']': 4, '{': 4, '}': 4, '<': 5, '>': 5, '"': 4, '*': 4, '`': 3 };
function fallbackWidth(s: string): number {
  let w = 0;
  for (const ch of s) {
    const code = ch.codePointAt(0) ?? 0;
    if (code > 0x2E80) w += 9; // CJK/эмодзи - широкие
    else w += NARROW[ch] ?? 6;
  }
  return w;
}
function fallbackDraw(ctx: CanvasRenderingContext2D, s: string, x: number, y: number, S: number, color: string, shadow: boolean): void {
  ctx.font = `${8 * S}px "Minecraft", "Press Start 2P", monospace`;
  ctx.textBaseline = 'alphabetic';
  let cx = x;
  for (const ch of s) {
    const w = fallbackWidth(ch);
    if (shadow) { ctx.fillStyle = shadowOf(color); ctx.fillText(ch, (cx + 1) * S, (y + 8) * S, (w - 1) * S); }
    ctx.fillStyle = color;
    ctx.fillText(ch, cx * S, (y + 7) * S, (w - 1) * S);
    cx += w;
  }
}

export const font = new BitmapFont();

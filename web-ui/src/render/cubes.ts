// Объёмные предметы в превью: ItemDisplay с transform FIXED/NONE/HEAD/… показывает блок (и голову игрока)
// как куб, а не плоскую иконку. Текстуры блоков - из ассетов (block/<name>[_top|_bottom|_side|_front].png),
// скины голов - с mc-heads.net (CORS), по имени/uuid; без сети - заглушка «Стив».
import * as THREE from 'three';
import { spriteImage, spriteState, spriteIndex } from './sprites';

type Faces = { px: HTMLCanvasElement | null; nx: HTMLCanvasElement | null; py: HTMLCanvasElement | null; ny: HTMLCanvasElement | null; pz: HTMLCanvasElement | null; nz: HTMLCanvasElement | null };

function tex(c: HTMLCanvasElement | null, fallback: number): THREE.Material {
  if (!c) return new THREE.MeshBasicMaterial({ color: fallback });
  const t = new THREE.CanvasTexture(c);
  t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.colorSpace = THREE.SRGBColorSpace;
  return new THREE.MeshBasicMaterial({ map: t, transparent: true, alphaTest: 0.05 });
}

/** Чуть темнее боковые и нижняя грани - как шейдинг блоков в игре. */
function shade(c: HTMLCanvasElement | null, k: number): HTMLCanvasElement | null {
  if (!c) return null;
  const o = document.createElement('canvas'); o.width = c.width; o.height = c.height;
  const g = o.getContext('2d')!; g.drawImage(c, 0, 0);
  g.globalCompositeOperation = 'multiply'; g.fillStyle = `rgb(${k * 255},${k * 255},${k * 255})`; g.fillRect(0, 0, o.width, o.height);
  g.globalCompositeOperation = 'destination-in'; g.drawImage(c, 0, 0);
  return o;
}

/** Материалы куба блока [px, nx, py, ny, pz, nz]; текстуры догружаются асинхронно (spriteState.revision). */
export function blockMaterials(material: string): THREE.Material[] {
  const name = material.toLowerCase();
  const has = (s: string) => { const e = spriteImage('block/' + s, 'minecraft:blocks'); return e.canvas; };
  // биомный тинт: трава и листва в текстурах серые, клиент красит их цветом биома (равнины)
  const tintColor = name === 'grass_block' ? '#79c05a' : /_leaves$|^(grass|fern|vine|lily_pad|short_grass|tall_grass)$/.test(name) ? '#59ae30' : null;
  const tint = (c: HTMLCanvasElement | null) => {
    if (!c || !tintColor) return c;
    const o = document.createElement('canvas'); o.width = c.width; o.height = c.height;
    const g = o.getContext('2d')!; g.drawImage(c, 0, 0);
    g.globalCompositeOperation = 'multiply'; g.fillStyle = tintColor; g.fillRect(0, 0, o.width, o.height);
    g.globalCompositeOperation = 'destination-in'; g.drawImage(c, 0, 0);
    return o;
  };
  const all = tint(has(name));
  const top = tint(has(name + '_top')) ?? has(name + '_end') ?? all, bottom = has(name + '_bottom') ?? top, side = has(name + '_side') ?? all, front = has(name + '_front') ?? side;
  const f: Faces = { px: side, nx: side, py: top, ny: bottom, pz: front, nz: side };
  return [tex(shade(f.px, 0.8), 0x9e9e9e), tex(shade(f.nx, 0.8), 0x9e9e9e), tex(f.py, 0xbdbdbd), tex(shade(f.ny, 0.5), 0x616161), tex(shade(f.pz, 0.8), 0x9e9e9e), tex(shade(f.nz, 0.8), 0x9e9e9e)];
}

// ---- головы

const skins = new Map<string, { img: HTMLImageElement | null; done: boolean }>();

/** Скин 64×64 по спецификации головы: имя/uuid → mc-heads.net; base64/URL → hash текстуры → mc-heads.net; иначе null. */
export function skinFor(spec: string | null | undefined): HTMLImageElement | null {
  if (!spec) return null;
  let id = spec.trim();
  if (id.startsWith('@')) return null; // @viewer - кто смотрит; в редакторе - заглушка
  if (id.startsWith('http')) { const m = /texture\/([0-9a-f]+)/i.exec(id); id = m ? m[1] : ''; }
  else if (id.length > 60) { try { const j = JSON.parse(atob(id)); const m = /texture\/([0-9a-f]+)/i.exec(j?.textures?.SKIN?.url ?? ''); id = m ? m[1] : ''; } catch { id = ''; } }
  if (!id) return null;
  let e = skins.get(id);
  if (!e) {
    e = { img: null, done: false }; skins.set(id, e);
    const img = new Image(); img.crossOrigin = 'anonymous';
    const entry = e;
    img.onload = () => { entry.img = img; entry.done = true; spriteState.revision++; };
    img.onerror = () => { entry.done = true; spriteState.revision++; };
    img.src = 'https://mc-heads.net/skin/' + encodeURIComponent(id);
  }
  return e.img;
}

/** Заглушка-скин «Стив» 64×64: кожа, волосы, глаза, рубашка - чтобы голова читалась без сети. */
let steve: HTMLCanvasElement | null = null;
function steveSkin(): HTMLCanvasElement {
  if (steve) return steve;
  const c = document.createElement('canvas'); c.width = 64; c.height = 64;
  const g = c.getContext('2d')!;
  const rect = (x: number, y: number, w: number, h: number, col: string) => { g.fillStyle = col; g.fillRect(x, y, w, h); };
  // голова: верх/низ/4 стороны в раскладке скина (8×8 каждая)
  const skin = '#b48a65', hair = '#3b2a1a', eye = '#ffffff', iris = '#4a3cb5', mouth = '#8a5a3c';
  rect(8, 0, 8, 8, hair);           // верх
  rect(16, 0, 8, 8, skin);          // низ
  rect(0, 8, 32, 8, skin);          // стороны
  rect(0, 8, 32, 2, hair);          // волосы сверху всех сторон
  rect(8, 8, 8, 3, hair);           // чёлка спереди
  rect(24, 8, 8, 6, hair);          // затылок
  rect(9, 12, 2, 1, eye); rect(13, 12, 2, 1, eye); rect(10, 12, 1, 1, iris); rect(13, 12, 1, 1, iris);
  rect(11, 14, 2, 1, mouth);
  steve = c; return c;
}

function face(src: CanvasImageSource, sx: number, sy: number, overlaySx: number, overlaySy: number, flip = false): HTMLCanvasElement {
  const c = document.createElement('canvas'); c.width = 8; c.height = 8;
  const g = c.getContext('2d')!; g.imageSmoothingEnabled = false;
  if (flip) { g.translate(8, 0); g.scale(-1, 1); }
  g.drawImage(src, sx, sy, 8, 8, 0, 0, 8, 8);
  g.drawImage(src, overlaySx, overlaySy, 8, 8, 0, 0, 8, 8); // второй слой (шапка)
  return c;
}

/** Материалы куба головы игрока [px, nx, py, ny, pz, nz]; у нас +z смотрит на зрителя - туда лицо. */
export function headMaterials(spec: string | null | undefined): THREE.Material[] {
  const src: CanvasImageSource = skinFor(spec) ?? steveSkin();
  // раскладка скина: верх (8,0), низ (16,0), правая (0,8), лицо (8,8), левая (16,8), затылок (24,8); шапка +32 по x
  const right = face(src, 0, 8, 32, 8), front = face(src, 8, 8, 40, 8), left = face(src, 16, 8, 48, 8), back = face(src, 24, 8, 56, 8);
  const top = face(src, 8, 0, 40, 0), bottom = face(src, 16, 0, 48, 0);
  return [tex(shade(left, 0.8), 0x9e9e9e), tex(shade(right, 0.8), 0x9e9e9e), tex(top, 0xbdbdbd), tex(shade(bottom, 0.5), 0x616161), tex(front, 0x9e9e9e), tex(shade(back, 0.8), 0x9e9e9e)];
}

/** Масштаб куба для transform ItemDisplay (как у клиента для блоков): FIXED/GROUND - 0.5, HEAD/NONE - 1, руки - 0.4. */
export function cubeScale(transform: string): number {
  switch (transform) {
    case 'NONE': case 'HEAD': return 1;
    case 'FIXED': case 'GROUND': return 0.5;
    default: return 0.4;
  }
}

/** Рисовать ли предмет кубом: не-GUI transform и материал - блок (есть текстура block/<name>) или голова. */
export function isCube(material: string, transform: string): boolean {
  if (transform === 'GUI') return false;
  const n = material.toLowerCase();
  if (n === 'player_head') return true;
  const names = spriteIndex.byAtlas['minecraft:blocks'];
  if (names) return ['', '_top', '_side', '_front', '_end'].some((suf) => names.includes('block/' + n + suf));
  const e = spriteImage('block/' + n, 'minecraft:blocks');
  return !!e.canvas || !e.done;
}

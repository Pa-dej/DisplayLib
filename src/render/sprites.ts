// Спрайты атласов клиента для компонентов-объектов ({atlas, sprite}) - в тексте рисуются квадратом 8×8 px.
//
// Как клиент находит картинку: каждый атлас (assets/minecraft/atlases/<имя>.json) собран из папок
// textures/: items <- item/ (имя спрайта "item/apple" = путь в textures), blocks <- block/ (+ item/),
// gui <- gui/sprites/ (спрайт "hud/heart/full" = textures/gui/sprites/hud/heart/full.png),
// иконки эффектов - в gui под префиксом mob_effect/ ("mob_effect/speed" = textures/mob_effect/speed.png), particles <- particle/,
// paintings <- painting/, banner_patterns <- entity/banner/, shield_patterns <- entity/shield/,
// armor_trims <- trims/. То есть имя спрайта = путь внутри папки-источника атласа, без .png,
// а "minecraft:" спереди можно опускать.
//
// Источник для предпросмотра: public/assets/minecraft/textures/ (скопировать из клиента). Пробуем
// несколько путей (папка атласа, затем путь как есть от textures/); если файла нет - иконка из листа
// предметов (items.ts) по последней части имени, но только пока текстуры клиента не скопированы;
// при скопированных текстурах отсутствие файла = «missing texture», как в игре.
import { reactive } from 'vue';
import itemsJson from '../items.json';
import { loadSheet } from './items';

interface ItemsFile { cell: number; items: Record<string, { x: number; y: number }> }
const DATA = itemsJson as ItemsFile;

/** Растёт при каждой догрузке спрайта - по нему сцена перестраивается. */
export const spriteState = reactive({ revision: 0 });

interface Entry { canvas: HTMLCanvasElement | null; exact: boolean; done: boolean }
const cache = new Map<string, Entry>();

/** Нормализовать имя спрайта: без префикса minecraft:, без .png. */
export function spriteKey(sprite: string): string {
  return sprite.trim().replace(/^minecraft:/, '').replace(/\.png$/, '');
}

/** Атлас по умолчанию, как у плагина: items для item/..., иначе blocks. */
export function defaultAtlas(sprite: string): string {
  return spriteKey(sprite).startsWith('item/') ? 'minecraft:items' : 'minecraft:blocks';
}

/** Встроенные атласы клиента 1.21.9+ (поле atlas) и где лежат их текстуры в jar. */
export const ATLASES: { id: string; dir: string; hint: string; example: string }[] = [
  { id: 'minecraft:blocks', dir: '', hint: 'блоки (и всё из textures/)', example: 'block/bricks' },
  { id: 'minecraft:items', dir: '', hint: 'предметы', example: 'item/diamond_sword' },
  { id: 'minecraft:gui', dir: 'gui/sprites/', hint: 'интерфейс и иконки эффектов (mob_effect/…)', example: 'hud/heart/full' },
  { id: 'minecraft:particles', dir: 'particle/', hint: 'частицы', example: 'heart' },
  { id: 'minecraft:paintings', dir: 'painting/', hint: 'картины', example: 'kebab' },
  { id: 'minecraft:banner_patterns', dir: 'entity/banner/', hint: 'узоры флагов', example: 'creeper' },
  { id: 'minecraft:shield_patterns', dir: 'entity/shield/', hint: 'узоры щитов', example: 'creeper' },
  { id: 'minecraft:armor_trims', dir: 'trims/', hint: 'узоры брони', example: 'items/helmet_trim' },
];

function atlasDef(atlas?: string | null) {
  const a = (atlas && atlas.trim()) || '';
  return a ? ATLASES.find((x) => x.id === a.replace(/^(?!minecraft:)/, 'minecraft:')) ?? null : null;
}

/** Все пути-кандидаты в public/assets/minecraft/textures/ (по порядку проверки). */
/** Атлас + спрайт как их поймёт клиент: mob_effects → gui/mob_effect/…, голое имя эффекта → gui. */
export function normalizeSprite(sprite: string, atlas?: string | null): { atlas: string | null; sprite: string } {
  let key = spriteKey(sprite);
  let a = (atlas && atlas.trim()) ? atlas.trim().replace(/^(?!minecraft:)/, 'minecraft:') : null;
  if (a === 'minecraft:mob_effects') { a = 'minecraft:gui'; if (!key.startsWith('mob_effect/')) key = 'mob_effect/' + key; }
  return { atlas: a, sprite: key };
}

export function texturePaths(sprite: string, atlas?: string | null): string[] {
  const norm = normalizeSprite(sprite, atlas);
  atlas = norm.atlas; const key = norm.sprite;
  const def = atlasDef(atlas ?? defaultAtlas(key));
  const base = 'assets/minecraft/textures/';
  const list: string[] = [];
  const add = (p: string) => { if (!list.includes(p)) list.push(p); };
  const bare = !key.includes('/');
  // голое имя без атласа (или items/blocks): плагин сам подставляет item/ → block/ → эффект
  if (bare && (!atlas || def?.id === 'minecraft:items' || def?.id === 'minecraft:blocks')) { add(base + 'item/' + key + '.png'); add(base + 'block/' + key + '.png'); }
  if (bare && !atlas) add(base + 'mob_effect/' + key + '.png');
  if (def && def.id === 'minecraft:gui' && key.startsWith('mob_effect/')) add(base + key + '.png'); // textures/mob_effect/x.png
  if (def) add(base + def.dir + key + '.png');
  add(base + key + '.png');                       // путь как есть (например "gui/sprites/hud/heart/full")
  if (!def || def.id !== 'minecraft:gui') add(base + 'gui/sprites/' + key + '.png');
  if (bare) { add(base + 'particle/' + key + '.png'); }
  return list;
}

/** Основной путь к png (как его искал бы клиент) для пары атлас + спрайт. */
export function texturePath(sprite: string, atlas?: string | null): string {
  return texturePaths(sprite, atlas)[0];
}

/**
 * Картинка спрайта (8×8 … любой размер; масштабируется при отрисовке). Пока грузится - null;
 * после загрузки увеличивается spriteState.revision. exact=false - запасная иконка из листа предметов.
 */
export function spriteImage(sprite: string, atlas?: string | null): { canvas: HTMLCanvasElement | null; exact: boolean; done: boolean } {
  const key = spriteKey(sprite);
  const ck = ((atlas && atlas.trim()) || '') + '|' + key;
  let e = cache.get(ck);
  if (e) return e;
  e = { canvas: null, exact: false, done: false };
  cache.set(ck, e);
  const paths = texturePaths(key, atlas);
  const entry = e;
  const tryLoad = (i: number) => {
    if (i >= paths.length) { fallback(key, entry); return; }
    const img = new Image();
    img.onload = () => {
      const c = document.createElement('canvas');
      // анимированные текстуры (вертикальная полоса кадров) - первый кадр
      const size = Math.min(img.width, img.height);
      c.width = img.width; c.height = size;
      c.getContext('2d')!.drawImage(img, 0, 0, img.width, size, 0, 0, img.width, size);
      Object.assign(entry, { canvas: c, exact: true, done: true });
      spriteState.revision++;
    };
    img.onerror = () => tryLoad(i + 1);
    img.src = paths[i];
  };
  tryLoad(0);
  return e;
}

async function fallback(key: string, e: Entry): Promise<void> {
  // Файла нет среди текстур клиента: иконка из листа предметов (пререндер 3D-модели). В игре такой спрайт
  // есть только с ресурспаком DisplayLib-icons.zip (он добавляет item/<name>.png из того же листа).
  const name = key.replace(/^.*\//, '').replace(/^minecraft:/, '');
  const it = DATA.items[name];
  const sheet = it ? await loadSheet() : null;
  if (it && sheet) {
    const c = document.createElement('canvas');
    c.width = DATA.cell; c.height = DATA.cell;
    const ctx = c.getContext('2d')!;
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(sheet, it.x, it.y, DATA.cell, DATA.cell, 0, 0, DATA.cell, DATA.cell);
    e.canvas = c;
  }
  e.done = true;
  spriteState.revision++;
}

/** Нарисовать спрайт в текстовых координатах (x, y - левый верхний угол, S - px canvas на текстовый px). */
export function drawSprite(ctx: CanvasRenderingContext2D, sprite: string, atlas: string | undefined, x: number, y: number, S: number): void {
  const e = spriteImage(sprite, atlas);
  const size = 8 * S;
  ctx.imageSmoothingEnabled = false;
  if (e.canvas) { ctx.drawImage(e.canvas, x * S, y * S, size, size); return; }
  // нет текстуры: «missing texture» - фиолетово-чёрная шахматка
  const h = size / 2;
  ctx.fillStyle = '#f800f8'; ctx.fillRect(x * S, y * S, h, h); ctx.fillRect(x * S + h, y * S + h, h, h);
  ctx.fillStyle = '#000'; ctx.fillRect(x * S + h, y * S, h, h); ctx.fillRect(x * S, y * S + h, h, h);
}

/** Подсказки имён спрайтов по списку предметов (когда текстур клиента нет): item/<name> и block/<name>. */
export const SPRITE_NAMES: string[] = Object.keys(DATA.items).sort().flatMap((n) => ['item/' + n, 'block/' + n]);

/** Индекс спрайтов из скопированных текстур (public/assets/sprite-index.json, делается `npm run sprites`). */
export const spriteIndex = reactive<{ loaded: boolean; byAtlas: Record<string, string[]> }>({ loaded: false, byAtlas: {} });

export async function loadSpriteIndex(): Promise<void> {
  try {
    const res = await fetch('assets/sprite-index.json');
    if (!res.ok) return;
    spriteIndex.byAtlas = await res.json();
    spriteIndex.loaded = true;
    const r2 = await fetch('assets/icon-pack.json');
    if (r2.ok) iconPack.names = await r2.json();
  } catch { /* нет индекса - подсказки по листу предметов */ }
}

/** Имена спрайтов для автодополнения в данном атласе (пусто - все). */
export function spriteNames(atlas?: string | null): string[] {
  const a = (atlas && atlas.trim()) ? atlas.trim().replace(/^(?!minecraft:)/, 'minecraft:') : '';
  if (spriteIndex.loaded) {
    if (a) return spriteIndex.byAtlas[a] ?? [];
    return [...(spriteIndex.byAtlas['minecraft:items'] ?? []), ...(spriteIndex.byAtlas['minecraft:blocks'] ?? []).filter((n) => n.startsWith('block/'))];
  }
  if (a === 'minecraft:items') return SPRITE_NAMES.filter((n) => n.startsWith('item/'));
  if (a === 'minecraft:blocks') return SPRITE_NAMES.filter((n) => n.startsWith('block/'));
  if (a) return [];
  return SPRITE_NAMES;
}

export interface SpriteHit { atlas: string; sprite: string; /** есть только в ресурспаке DisplayLib-icons */ pack?: boolean }

/** Имена иконок из ресурспака DisplayLib-icons (item/<name> для блоков без плоской текстуры). */
export const iconPack = reactive<{ names: string[] }>({ names: [] });

/** Короткая подпись атласа для списка подсказок. */
export function atlasLabel(atlas: string): string {
  return atlas.replace(/^minecraft:/, '');
}

/**
 * Поиск спрайта по имени по всем атласам (или внутри заданного): «heart» найдёт particles/heart,
 * gui/hud/heart/full, item/… Сначала совпадения по началу последней части пути, потом по вхождению.
 */
export function searchSprites(q: string, atlas?: string | null, limit = 40): SpriteHit[] {
  const s = q.trim().toLowerCase().replace(/ /g, '_').replace(/^minecraft:/, '');
  const a = (atlas && atlas.trim()) ? atlas.trim().replace(/^(?!minecraft:)/, 'minecraft:') : '';
  const pools: [string, string[]][] = [];
  if (spriteIndex.loaded) {
    for (const [id, names] of Object.entries(spriteIndex.byAtlas)) {
      if (a && id !== a) continue;
      // item/… из blocks - дубликаты items, пропускаем, когда ищем по всем атласам
      pools.push([id, !a && id === 'minecraft:blocks' ? names.filter((n) => !n.startsWith('item/')) : names]);
    }
  } else {
    if (!a || a === 'minecraft:items') pools.push(['minecraft:items', SPRITE_NAMES.filter((n) => n.startsWith('item/'))]);
    if (!a || a === 'minecraft:blocks') pools.push(['minecraft:blocks', SPRITE_NAMES.filter((n) => n.startsWith('block/'))]);
  }
  // иконки предметов-блоков из ресурспака: item/barrel и т.п. - сразу после настоящих items
  const packHits: [string, string[], boolean][] = (!a || a === 'minecraft:items') && iconPack.names.length
    ? [['minecraft:items', iconPack.names.map((n) => 'item/' + n), true]] : [];
  const all: [string, string[], boolean][] = [...pools.map(([id, names]) => [id, names, false] as [string, string[], boolean])];
  const itemsAt = all.findIndex(([id]) => id === 'minecraft:items');
  all.splice(itemsAt >= 0 ? itemsAt + 1 : all.length, 0, ...packHits);
  const starts: SpriteHit[] = [], inside: SpriteHit[] = [];
  for (const [id, names, pack] of all) {
    for (const n of names) {
      const hit: SpriteHit = pack ? { atlas: id, sprite: n, pack: true } : { atlas: id, sprite: n };
      if (!s) { if (starts.length < limit) starts.push(hit); continue; }
      const base = n.replace(/^.*\//, '');
      if (base.startsWith(s) || n.startsWith(s)) starts.push(hit);
      else if (n.includes(s)) inside.push(hit);
    }
  }
  // точное совпадение последней части имени - в самый верх
  starts.sort((x, y) => Number(y.sprite.replace(/^.*\//, '') === s) - Number(x.sprite.replace(/^.*\//, '') === s));
  return [...starts, ...inside].slice(0, limit);
}

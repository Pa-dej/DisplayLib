// Ресурсы клиента прямо из его jar: пользователь выбирает client.jar (например
// .minecraft/versions/1.21.11/1.21.11.jar), редактор распаковывает в браузере шрифт и текстуры, нужные
// предпросмотру, и держит их в IndexedDB. Ничего не публикуется и не уходит с машины - на GitHub Pages
// редактор остаётся без файлов Mojang, но у каждого пользователя они берутся из его собственного клиента.
import { reactive } from 'vue';
import { unzip } from 'fflate';

export const clientAssets = reactive({ loaded: false, version: '', files: 0, busy: false, error: '' });

/** путь внутри jar → байты (только то, что нужно предпросмотру) */
let files = new Map<string, Uint8Array>();
const urls = new Map<string, string>();

/** Папки textures/, которые читает редактор (как в scripts/sprite-index.mjs). */
const TEXTURE_DIRS = ['font/', 'item/', 'block/', 'gui/sprites/', 'mob_effect/', 'particle/', 'painting/', 'entity/banner/', 'entity/shield/', 'trims/'];
const ATLAS_SOURCES: Record<string, [string, string][]> = {
  'minecraft:items': [['item', 'item/']],
  'minecraft:blocks': [['block', 'block/'], ['item', 'item/']],
  'minecraft:gui': [['gui/sprites', ''], ['mob_effect', 'mob_effect/']],
  'minecraft:particles': [['particle', '']],
  'minecraft:paintings': [['painting', '']],
  'minecraft:banner_patterns': [['entity/banner', '']],
  'minecraft:shield_patterns': [['entity/shield', '']],
  'minecraft:armor_trims': [['trims', 'trims/']],
};

function wanted(name: string): boolean {
  if (name === 'version.json') return true;
  if (name.startsWith('assets/minecraft/font/')) return name.endsWith('.json');
  if (name.startsWith('assets/minecraft/textures/')) {
    const rest = name.slice('assets/minecraft/textures/'.length);
    return name.endsWith('.png') && TEXTURE_DIRS.some((d) => rest.startsWith(d));
  }
  return false;
}

/** URL ресурса: из jar, если он загружен, иначе как есть (public/assets/…). */
export function assetUrl(path: string): string {
  const f = files.get(path);
  if (!f) return path;
  let u = urls.get(path);
  if (!u) {
    const type = path.endsWith('.png') ? 'image/png' : 'application/json';
    u = URL.createObjectURL(new Blob([f as BlobPart], { type }));
    urls.set(path, u);
  }
  return u;
}

export function hasAsset(path: string): boolean { return files.has(path); }

/** Индекс спрайтов по атласам из содержимого jar (как npm run sprites, но в браузере). */
export function clientSpriteIndex(): Record<string, string[]> | null {
  if (!files.size) return null;
  const base = 'assets/minecraft/textures/';
  const out: Record<string, string[]> = {};
  for (const [atlas, sources] of Object.entries(ATLAS_SOURCES)) {
    const acc = new Set<string>();
    for (const [sub, prefix] of sources) {
      const dir = base + sub + '/';
      for (const name of files.keys()) {
        if (name.startsWith(dir) && name.endsWith('.png')) acc.add(prefix + name.slice(dir.length, -4));
      }
    }
    out[atlas] = [...acc].sort();
  }
  return out;
}

// ---------------------------------------------------------------- IndexedDB

const DB = 'displaylib', STORE = 'client';
function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => { req.result.createObjectStore(STORE); };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
async function dbPut(key: string, value: unknown): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite'); tx.objectStore(STORE).put(value, key);
    tx.oncomplete = () => resolve(); tx.onerror = () => reject(tx.error);
  });
  db.close();
}
async function dbGet<T>(key: string): Promise<T | undefined> {
  const db = await openDb();
  const v = await new Promise<T | undefined>((resolve, reject) => {
    const req = db.transaction(STORE, 'readonly').objectStore(STORE).get(key);
    req.onsuccess = () => resolve(req.result as T | undefined); req.onerror = () => reject(req.error);
  });
  db.close();
  return v;
}
async function dbDelete(key: string): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite'); tx.objectStore(STORE).delete(key);
    tx.oncomplete = () => resolve(); tx.onerror = () => reject(tx.error);
  });
  db.close();
}

// ---------------------------------------------------------------- загрузка

function apply(map: Map<string, Uint8Array>): void {
  for (const u of urls.values()) URL.revokeObjectURL(u);
  urls.clear();
  files = map;
  let version = '';
  const v = map.get('version.json');
  if (v) { try { version = String(JSON.parse(new TextDecoder().decode(v)).name ?? ''); } catch { /* без версии */ } }
  clientAssets.loaded = map.size > 0; clientAssets.version = version; clientAssets.files = map.size;
}

/** Распаковать выбранный client.jar и запомнить ресурсы в IndexedDB. */
export async function importClientJar(file: File): Promise<void> {
  clientAssets.busy = true; clientAssets.error = '';
  try {
    const data = new Uint8Array(await file.arrayBuffer());
    const unzipped = await new Promise<Record<string, Uint8Array>>((resolve, reject) => {
      unzip(data, { filter: (f) => wanted(f.name) }, (err, out) => (err ? reject(err) : resolve(out)));
    });
    const map = new Map<string, Uint8Array>(Object.entries(unzipped).filter(([, b]) => b.length > 0));
    if (!map.has('assets/minecraft/font/default.json')) throw new Error('в архиве нет assets/minecraft/font/default.json - это не jar клиента Minecraft');
    apply(map);
    await dbPut('jar', { name: file.name, entries: [...map.entries()] });
  } catch (e) {
    clientAssets.error = (e as Error).message;
    throw e;
  } finally { clientAssets.busy = false; }
}

/** Поднять ресурсы прошлой сессии из IndexedDB. */
export async function restoreClientJar(): Promise<boolean> {
  try {
    const saved = await dbGet<{ name: string; entries: [string, Uint8Array][] }>('jar');
    if (!saved?.entries?.length) return false;
    apply(new Map(saved.entries));
    return true;
  } catch { return false; }
}

/** Забыть ресурсы клиента. */
export async function forgetClientJar(): Promise<void> {
  apply(new Map());
  try { await dbDelete('jar'); } catch { /* ignore */ }
}

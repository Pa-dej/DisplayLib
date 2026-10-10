// Папка плагина через File System Access API (Chrome/Edge). Хэндл запоминается в IndexedDB,
// чтобы при следующем открытии редактора папку не выбирать заново (браузер спросит разрешение).

export interface PluginFolder {
  root: FileSystemDirectoryHandle;
  screens: FileSystemDirectoryHandle;
  scripts: FileSystemDirectoryHandle;
}

export const fsSupported = typeof window !== 'undefined' && 'showDirectoryPicker' in window;

const DB = 'displaylib-editor', STORE = 'handles';

function idb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const r = indexedDB.open(DB, 1);
    r.onupgradeneeded = () => r.result.createObjectStore(STORE);
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}
async function idbGet<T>(key: string): Promise<T | undefined> {
  const db = await idb();
  return new Promise((resolve, reject) => {
    const t = db.transaction(STORE, 'readonly').objectStore(STORE).get(key);
    t.onsuccess = () => resolve(t.result as T); t.onerror = () => reject(t.error);
  });
}
async function idbSet(key: string, v: unknown): Promise<void> {
  const db = await idb();
  return new Promise((resolve, reject) => {
    const t = db.transaction(STORE, 'readwrite').objectStore(STORE).put(v, key);
    t.onsuccess = () => resolve(); t.onerror = () => reject(t.error);
  });
}

async function open(root: FileSystemDirectoryHandle): Promise<PluginFolder> {
  const screens = await root.getDirectoryHandle('screens', { create: true });
  const scripts = await root.getDirectoryHandle('scripts', { create: true });
  return { root, screens, scripts };
}

export async function pickFolder(): Promise<PluginFolder> {
  const root = await (window as any).showDirectoryPicker({ mode: 'readwrite', id: 'displaylib' }) as FileSystemDirectoryHandle;
  await idbSet('root', root);
  return open(root);
}

/** Прежняя папка, если браузер её помнит и даёт доступ; null - нужно выбирать заново. */
export async function restoreFolder(): Promise<PluginFolder | null> {
  try {
    const root = await idbGet<FileSystemDirectoryHandle>('root');
    if (!root) return null;
    const q = await (root as any).queryPermission({ mode: 'readwrite' });
    if (q !== 'granted') {
      const r = await (root as any).requestPermission({ mode: 'readwrite' });
      if (r !== 'granted') return null;
    }
    return await open(root);
  } catch {
    return null;
  }
}

export async function listFiles(dir: FileSystemDirectoryHandle, ext: string, prefix = ''): Promise<string[]> {
  const out: string[] = [];
  for await (const [name, h] of (dir as any).entries() as AsyncIterable<[string, FileSystemHandle]>) {
    if (h.kind === 'file' && name.toLowerCase().endsWith(ext)) out.push(prefix + name);
    else if (h.kind === 'directory' && !name.startsWith('.')) out.push(...await listFiles(h as FileSystemDirectoryHandle, ext, prefix + name + '/'));
  }
  return out.sort();
}

async function resolveFile(dir: FileSystemDirectoryHandle, path: string, create: boolean): Promise<FileSystemFileHandle> {
  const parts = path.split('/').filter(Boolean);
  let d = dir;
  for (let i = 0; i < parts.length - 1; i++) d = await d.getDirectoryHandle(parts[i], { create });
  return d.getFileHandle(parts[parts.length - 1], { create });
}

export async function readText(dir: FileSystemDirectoryHandle, path: string): Promise<string> {
  const h = await resolveFile(dir, path, false);
  return (await h.getFile()).text();
}

export async function writeText(dir: FileSystemDirectoryHandle, path: string, text: string): Promise<void> {
  const h = await resolveFile(dir, path, true);
  const w = await (h as any).createWritable();
  await w.write(text);
  await w.close();
}

export async function removeFile(dir: FileSystemDirectoryHandle, path: string): Promise<void> {
  const parts = path.split('/').filter(Boolean);
  let d = dir;
  for (let i = 0; i < parts.length - 1; i++) d = await d.getDirectoryHandle(parts[i]);
  await d.removeEntry(parts[parts.length - 1]);
}

export function download(name: string, text: string): void {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

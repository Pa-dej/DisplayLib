// Спрайты предметов: web-ui/css-blocks-and-entities (icons-minecraft-268) -> src/items.json.
import itemsJson from '../items.json';
import * as THREE from 'three';

interface ItemsFile { cell: number; sheet: string; items: Record<string, { x: number; y: number; label: string }> }
const DATA = itemsJson as ItemsFile;

export const ITEM_NAMES: string[] = Object.keys(DATA.items).sort();
export function itemLabel(name: string): string { return DATA.items[name.toLowerCase()]?.label ?? name; }
export function hasItem(material: string): boolean { return !!DATA.items[material.toLowerCase()]; }

let sheet: HTMLImageElement | null = null;
let loading: Promise<HTMLImageElement | null> | null = null;

export function loadSheet(): Promise<HTMLImageElement | null> {
  if (sheet) return Promise.resolve(sheet);
  if (!loading) {
    loading = new Promise((resolve) => {
      const img = new Image();
      img.onload = () => { sheet = img; resolve(img); };
      img.onerror = () => resolve(null);
      img.src = DATA.sheet;
    });
  }
  return loading;
}

const texCache = new Map<string, THREE.CanvasTexture>();

/** Текстура 32x32 предмета по имени Material (регистр не важен); null - нет такого спрайта. */
export function itemTexture(material: string): THREE.CanvasTexture | null {
  const key = material.toLowerCase();
  const e = DATA.items[key];
  if (!e || !sheet) return null;
  let t = texCache.get(key);
  if (t) return t;
  const c = document.createElement('canvas');
  c.width = DATA.cell; c.height = DATA.cell;
  const ctx = c.getContext('2d')!;
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(sheet, e.x, e.y, DATA.cell, DATA.cell, 0, 0, DATA.cell, DATA.cell);
  t = new THREE.CanvasTexture(c);
  t.magFilter = THREE.NearestFilter;
  t.minFilter = THREE.NearestFilter;
  t.colorSpace = THREE.SRGBColorSpace;
  texCache.set(key, t);
  return t;
}

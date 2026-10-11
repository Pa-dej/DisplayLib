// Выравнивание и распределение выделенных виджетов по их видимым прямоугольникам (как в PowerPoint/Figma).
// Возвращает сдвиги dx/dy (в мировых единицах плоскости экрана) по uid; применять к position с учётом знака оси x.
import type { Rect } from './snap';

export type AlignKind = 'left' | 'hcenter' | 'right' | 'top' | 'vcenter' | 'bottom';
export type DistributeKind = 'h' | 'v';

export function alignRects(rects: Map<string, Rect>, kind: AlignKind): Map<string, { dx: number; dy: number }> {
  const out = new Map<string, { dx: number; dy: number }>();
  const all = [...rects.values()]; if (all.length < 2) return out;
  const L = Math.min(...all.map((r) => r.l)), R = Math.max(...all.map((r) => r.r));
  const B = Math.min(...all.map((r) => r.b)), T = Math.max(...all.map((r) => r.t));
  const cx = (L + R) / 2, cy = (B + T) / 2;
  for (const [uid, r] of rects) {
    const rcx = (r.l + r.r) / 2, rcy = (r.b + r.t) / 2;
    const dx = kind === 'left' ? L - r.l : kind === 'right' ? R - r.r : kind === 'hcenter' ? cx - rcx : 0;
    const dy = kind === 'top' ? T - r.t : kind === 'bottom' ? B - r.b : kind === 'vcenter' ? cy - rcy : 0;
    out.set(uid, { dx, dy });
  }
  return out;
}

/** Равные зазоры между соседями вдоль оси; крайние остаются на месте. */
export function distributeRects(rects: Map<string, Rect>, kind: DistributeKind): Map<string, { dx: number; dy: number }> {
  const out = new Map<string, { dx: number; dy: number }>();
  const items = [...rects.entries()].map(([uid, r]) => ({ uid, r }));
  if (items.length < 3) return out;
  const lo = (r: Rect) => (kind === 'h' ? r.l : r.b), hi = (r: Rect) => (kind === 'h' ? r.r : r.t);
  items.sort((a, b) => lo(a.r) - lo(b.r));
  const span = hi(items[items.length - 1].r) - lo(items[0].r);
  const total = items.reduce((s, it) => s + (hi(it.r) - lo(it.r)), 0);
  const gap = (span - total) / (items.length - 1);
  let cur = lo(items[0].r);
  for (const it of items) {
    const d = cur - lo(it.r);
    out.set(it.uid, kind === 'h' ? { dx: d, dy: 0 } : { dx: 0, dy: d });
    cur += hi(it.r) - lo(it.r) + gap;
  }
  return out;
}

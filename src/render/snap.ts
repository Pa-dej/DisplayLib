// «Умные направляющие» как в PowerPoint: при перетаскивании виджет прилипает к краям и центрам
// других виджетов (и фона/осей), а также к равным зазорам между соседями. Всё в плоскости экрана (x, y).

export interface Rect { l: number; r: number; b: number; t: number }

export interface Guide {
  /** 'v' - вертикальная линия (x = at), 'h' - горизонтальная (y = at) */
  dir: 'v' | 'h';
  at: number;
  from: number;
  to: number;
  kind: 'align' | 'gap';
  /** для kind = 'gap': отрезки зазоров [a, b] вдоль направляющей (две штуки: эталонный и новый) */
  gaps?: [number, number][];
}

/** sx/sy - по этой оси сработала привязка (dx/dy может быть и 0, если уже выровнено). */
export interface SnapResult { dx: number; dy: number; sx: boolean; sy: boolean; guides: Guide[] }

interface Cand { delta: number; guide: Guide; score: number }

const cx = (r: Rect) => (r.l + r.r) / 2;
const cy = (r: Rect) => (r.b + r.t) / 2;

/** Расстояние между центрами - при равной точности ближний объект выигрывает у дальнего. */
const dist = (a: Rect, b: Rect) => Math.hypot(cx(a) - cx(b), cy(a) - cy(b));
/** Штраф за дальность: до 5 % порога, чтобы не перебивать точность, но решать ничьи в пользу соседей. */
const far = (a: Rect, b: Rect, thr: number) => thr * 0.05 * Math.min(1, dist(a, b) / 10);

/**
 * @param m      прямоугольник перетаскиваемого виджета в кандидатной позиции
 * @param others остальные прямоугольники (виджеты, фон)
 * @param thr    порог прилипания в мировых единицах
 * @param axes   линии «экрана» для прилипания (x = 0, y = 0 и т.п.)
 */
export function computeSnap(m: Rect, others: Rect[], thr: number, axes: { x: number[]; y: number[] } = { x: [0], y: [0] }): SnapResult {
  const vx: Cand[] = [], vy: Cand[] = [];
  const mx = [m.l, cx(m), m.r], my = [m.b, cy(m), m.t];

  // --- выравнивание по краям/центрам
  for (const o of others) {
    const ox = [o.l, cx(o), o.r], oy = [o.b, cy(o), o.t];
    const f = far(m, o, thr);
    for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) {
      if (i !== j && i + j !== 2) continue; // край-край, центр-центр, край-противоположный край
      const d = ox[j] - mx[i];
      if (Math.abs(d) <= thr) vx.push({ delta: d, score: Math.abs(d) + (i === j ? 0 : thr * 0.01) + f, guide: { dir: 'v', at: ox[j], from: Math.min(m.b, o.b), to: Math.max(m.t, o.t), kind: 'align' } });
      const e = oy[j] - my[i];
      if (Math.abs(e) <= thr) vy.push({ delta: e, score: Math.abs(e) + (i === j ? 0 : thr * 0.01) + f, guide: { dir: 'h', at: oy[j], from: Math.min(m.l, o.l), to: Math.max(m.r, o.r), kind: 'align' } });
    }
  }
  // --- зеркало: центр m на том же расстоянии от оси экрана, что центр другого виджета, но с другой стороны.
  // Так выравниваются кнопки разной ширины симметрично относительно центра - точка опоры у них центр.
  for (const ax of axes.x) for (const o of others) {
    const oc = cx(o); if (Math.abs(oc - ax) < 1e-6) continue;
    const want = 2 * ax - oc, d = want - cx(m);
    if (Math.abs(d) > thr) continue;
    const at = (cy(m) + cy(o)) / 2;
    vx.push({ delta: d, score: Math.abs(d) + thr * 0.012 + far(m, o, thr), guide: { dir: 'h', at, from: Math.min(oc, want), to: Math.max(oc, want), kind: 'gap', gaps: [[Math.min(oc, ax), Math.max(oc, ax)], [Math.min(ax, want), Math.max(ax, want)]] } });
  }
  for (const ay of axes.y) for (const o of others) {
    const oc = cy(o); if (Math.abs(oc - ay) < 1e-6) continue;
    const want = 2 * ay - oc, d = want - cy(m);
    if (Math.abs(d) > thr) continue;
    const at = (cx(m) + cx(o)) / 2;
    vy.push({ delta: d, score: Math.abs(d) + thr * 0.012 + far(m, o, thr), guide: { dir: 'v', at, from: Math.min(oc, want), to: Math.max(oc, want), kind: 'gap', gaps: [[Math.min(oc, ay), Math.max(oc, ay)], [Math.min(ay, want), Math.max(ay, want)]] } });
  }
  // --- оси экрана (центр и т.п.): только центр и края виджета
  for (const ax of axes.x) for (let i = 0; i < 3; i++) {
    const d = ax - mx[i];
    if (Math.abs(d) <= thr) vx.push({ delta: d, score: Math.abs(d) + thr * 0.02, guide: { dir: 'v', at: ax, from: m.b - 0.3, to: m.t + 0.3, kind: 'align' } });
  }
  for (const ay of axes.y) for (let i = 0; i < 3; i++) {
    const d = ay - my[i];
    if (Math.abs(d) <= thr) vy.push({ delta: d, score: Math.abs(d) + thr * 0.02, guide: { dir: 'h', at: ay, from: m.l - 0.3, to: m.r + 0.3, kind: 'align' } });
  }

  // --- равные зазоры: по горизонтали среди виджетов, пересекающихся с m по вертикали (и наоборот)
  gapCandidates(m, others, thr, 'x', vx);
  gapCandidates(m, others, thr, 'y', vy);

  const bx = best(vx), by = best(vy);
  const guides: Guide[] = [];
  const dx = bx ? bx.delta : 0, dy = by ? by.delta : 0;
  // после сдвига собрать все направляющие, которые совпали с выбранной (несколько объектов на одной линии)
  if (bx) for (const c of vx) if (Math.abs(c.delta - bx.delta) < 1e-6 && c.guide.kind === bx.guide.kind) guides.push(shift(c.guide, dx, dy));
  if (by) for (const c of vy) if (Math.abs(c.delta - by.delta) < 1e-6 && c.guide.kind === by.guide.kind) guides.push(shift(c.guide, dx, dy));
  return { dx, dy, sx: !!bx, sy: !!by, guides: dedupe(guides) };
}

function gapCandidates(m: Rect, others: Rect[], thr: number, axis: 'x' | 'y', out: Cand[]): void {
  // проекции: вдоль оси - lo/hi, поперёк - для проверки «соседства» (пересечение по другой оси)
  const lo = (r: Rect) => (axis === 'x' ? r.l : r.b), hi = (r: Rect) => (axis === 'x' ? r.r : r.t);
  const plo = (r: Rect) => (axis === 'x' ? r.b : r.l), phi = (r: Rect) => (axis === 'x' ? r.t : r.r);
  const overlaps = (a: Rect, b: Rect) => plo(a) < phi(b) && plo(b) < phi(a);
  const row = others.filter((o) => overlaps(o, m)).sort((a, b) => lo(a) - lo(b));
  const size = hi(m) - lo(m);
  const mid = (r: Rect) => (lo(r) + hi(r)) / 2;
  const dir: 'v' | 'h' = axis === 'x' ? 'v' : 'h';
  const edgeDeltas: number[] = [];
  const pushEdge = (delta: number, at: number, gaps: [number, number][], from: number, to: number) => {
    if (Math.abs(delta) <= thr) edgeDeltas.push(delta);
    pushGap(out, delta, thr, dir, at, gaps, from, to);
  };
  const line = (a: Rect, b: Rect) => { // положение поперечной линии для отрисовки зазора: середина пересечения
    const p1 = Math.max(plo(a), plo(b), plo(m)), p2 = Math.min(phi(a), phi(b), phi(m));
    return (p1 + p2) / 2;
  };
  // пары соседей A, B (B справа от A, без m между ними)
  for (let i = 0; i < row.length; i++) for (let j = i + 1; j < row.length; j++) {
    const A = row[i], B = row[j];
    const g = lo(B) - hi(A);
    if (g <= 1e-6) continue;
    const at = line(A, B);
    // 1) m справа от B с тем же зазором
    pushEdge(hi(B) + g - lo(m), at, [[hi(A), lo(B)], [hi(B), hi(B) + g]], hi(A), hi(B) + g + size);
    // 2) m слева от A с тем же зазором
    pushEdge(lo(A) - g - hi(m), at, [[lo(A) - g, lo(A)], [hi(A), lo(B)]], lo(A) - g - size, lo(B));
    // 3) m между A и B с равными зазорами по обе стороны
    const free = g - size;
    if (free > 1e-6) {
      const want = hi(A) + free / 2;
      pushEdge(want - lo(m), at, [[hi(A), want], [want + size, lo(B)]], hi(A), lo(B));
    }
  }
  // равные расстояния между ЦЕНТРАМИ (виджеты разной ширины): шаг центров A→B повторяется для m
  // справа от B, слева от A, либо центр m - посередине между центрами A и B
  for (let i = 0; i < row.length; i++) for (let j = i + 1; j < row.length; j++) {
    const A = row[i], B = row[j];
    const step = mid(B) - mid(A);
    if (step <= 1e-6) continue;
    const at = line(A, B);
    const cm = mid(m);
    const wants: [number, [number, number][]][] = [
      [mid(B) + step, [[mid(A), mid(B)], [mid(B), mid(B) + step]]],
      [mid(A) - step, [[mid(A) - step, mid(A)], [mid(A), mid(B)]]],
      [(mid(A) + mid(B)) / 2, [[mid(A), (mid(A) + mid(B)) / 2], [(mid(A) + mid(B)) / 2, mid(B)]]],
    ];
    for (const [want, gaps] of wants) {
      const delta = want - cm;
      if (Math.abs(delta) > thr) continue;
      if (edgeDeltas.some((e) => Math.abs(e - delta) < 1e-6)) continue; // совпало с зазором по краям - он уже есть
      const lo0 = Math.min(gaps[0][0], gaps[1][0]), hi0 = Math.max(gaps[0][1], gaps[1][1]);
      out.push({ delta, score: Math.abs(delta) + thr * 0.008, guide: { dir: dir === 'v' ? 'h' : 'v', at, from: lo0, to: hi0, kind: 'gap', gaps } });
    }
  }
  // равные зазоры с одним соседом с каждой стороны: A | m | B, где m уже между ними
  for (let i = 0; i < row.length; i++) for (let j = 0; j < row.length; j++) {
    if (i === j) continue;
    const A = row[i], B = row[j];
    if (!(hi(A) <= lo(m) + thr && lo(B) >= hi(m) - thr && lo(B) - hi(A) > size)) continue;
    const want = (hi(A) + lo(B)) / 2 - size / 2;
    const at = line(A, B);
    pushGap(out, want - lo(m), thr, dir, at, [[hi(A), want], [want + size, lo(B)]], hi(A), lo(B));
  }
}

function pushGap(out: Cand[], delta: number, thr: number, dir: 'v' | 'h', at: number, gaps: [number, number][], from: number, to: number): void {
  if (Math.abs(delta) > thr) return;
  out.push({ delta, score: Math.abs(delta) + thr * 0.005, guide: { dir: dir === 'v' ? 'h' : 'v', at, from, to, kind: 'gap', gaps } });
}

function best(c: Cand[]): Cand | null {
  let b: Cand | null = null;
  for (const x of c) if (!b || x.score < b.score) b = x;
  return b;
}

function shift(g: Guide, dx: number, dy: number): Guide {
  // направляющие заданы в координатах «других» объектов, они не двигаются; но границы from/to, взятые
  // из m, нужно сдвинуть вместе с m - для простоты расширяем диапазон на сдвиг
  const s = g.dir === 'v' ? dy : dx;
  return { ...g, from: Math.min(g.from, g.from + s), to: Math.max(g.to, g.to + s) };
}

function dedupe(gs: Guide[]): Guide[] {
  const out: Guide[] = [];
  for (const g of gs) {
    const same = out.find((o) => o.dir === g.dir && o.kind === g.kind && Math.abs(o.at - g.at) < 1e-6);
    if (same) { same.from = Math.min(same.from, g.from); same.to = Math.max(same.to, g.to); if (g.gaps) same.gaps = [...(same.gaps ?? []), ...g.gaps]; }
    else out.push({ ...g });
  }
  return out;
}

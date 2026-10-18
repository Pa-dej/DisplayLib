// Счёт нагрузки эффекта частиц в отдельном потоке: прогоны симуляции (десятки мс каждый) не должны
// подтормаживать интерфейс и его анимации. Сначала - общая оценка, потом маркеры по одному; новое
// задание отменяет недосчитанное.
import type { ParticleEffect } from '../particles/model';
import { particleLoad } from './particles';
import { CANDIDATES, impactOf, type Impact } from './impacts';
import type { LoadAssumptions } from './score';

export type WorkerIn = { id: number; effect: ParticleEffect; assumptions: LoadAssumptions };
export type WorkerOut =
  | { id: number; kind: 'report'; report: ReturnType<typeof particleLoad> | null }
  | { id: number; kind: 'impact'; impact: Impact | null; done: boolean };

let latest = 0;
const post = (m: WorkerOut) => (self as unknown as Worker).postMessage(m);

self.onmessage = (ev: MessageEvent<WorkerIn>) => {
  const { id, effect, assumptions } = ev.data;
  latest = id;
  let base;
  try { base = particleLoad(effect, assumptions); } catch { post({ id, kind: 'report', report: null }); return; }
  post({ id, kind: 'report', report: base });
  const todo = CANDIDATES.filter((c) => c.when(effect));
  if (!todo.length) { post({ id, kind: 'impact', impact: null, done: true }); return; }
  const step = (i: number) => {
    if (id !== latest) return; // пришло новое задание
    let impact = null;
    try { impact = impactOf(todo[i], effect, base!, assumptions); } catch { /* без маркера */ }
    post({ id, kind: 'impact', impact, done: i === todo.length - 1 });
    if (i + 1 < todo.length) setTimeout(() => step(i + 1), 0);
  };
  setTimeout(() => step(0), 0);
};

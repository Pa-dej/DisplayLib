// Нагрузка текущего эффекта частиц и «что именно нагружает» - для шкалы и маркеров инспектора.
// Счёт идёт в Web Worker (load/worker.ts); если воркер недоступен - в основном потоке по кусочкам.
import { reactive, watch, toRaw } from 'vue';
import type { ParticleEffect } from '../particles/model';
import { cloneEffect } from '../particles/model';
import { pstate, currentEffect } from '../particles/store';
import { particleLoad } from './particles';
import { CANDIDATES, impactOf, applyCandidate, type Impact, type ImpactKey } from './impacts';
import type { LoadReport, LoadAssumptions } from './score';
import { loadAssumptions } from './settings';
import { snapshot, touch } from '../store';
import type { WorkerOut } from './worker';

export type { Impact, ImpactKey } from './impacts';
export { IMPACT_MIN } from './impacts';

export const particleLoadState = reactive({
  report: null as LoadReport | null,
  busy: false,
  impacts: {} as Partial<Record<ImpactKey, Impact>>,
  /** Ещё считаются маркеры */
  scanning: false,
});


let jobId = 0;
let worker: Worker | null = null;
try {
  worker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' });
  worker.onmessage = (ev: MessageEvent<WorkerOut>) => {
    const m = ev.data;
    if (m.id !== jobId) return;
    if (m.kind === 'report') { particleLoadState.report = m.report; particleLoadState.busy = false; collecting = {}; return; }
    if (m.impact) collecting[m.impact.key] = m.impact;
    if (m.done) { particleLoadState.impacts = collecting; particleLoadState.scanning = false; }
  };
  worker.onerror = () => { worker = null; };
} catch { worker = null; }
let collecting: Partial<Record<ImpactKey, Impact>> = {};

/** Запасной путь без воркера: в основном потоке, по одному варианту за макрозадачу. */
function runInline(id: number, e: ParticleEffect, a: LoadAssumptions): void {
  let base: LoadReport;
  try { base = particleLoad(e, a); } catch { particleLoadState.report = null; particleLoadState.busy = false; return; }
  particleLoadState.report = base; particleLoadState.busy = false;
  const todo = CANDIDATES.filter((c) => c.when(e));
  const found: Partial<Record<ImpactKey, Impact>> = {};
  const next = (i: number) => {
    if (id !== jobId) return;
    if (i >= todo.length) { particleLoadState.impacts = found; particleLoadState.scanning = false; return; }
    try { const imp = impactOf(todo[i], e, base, a); if (imp) found[imp.key] = imp; } catch { /* */ }
    setTimeout(() => next(i + 1), 0);
  };
  setTimeout(() => next(0), 0);
}

let timer = 0;
watch(() => [pstate.revision, pstate.current, loadAssumptions.players, loadAssumptions.launches], () => {
  particleLoadState.busy = true;
  clearTimeout(timer);
  timer = window.setTimeout(() => {
    const id = ++jobId;
    const e = currentEffect.value;
    if (!e) { particleLoadState.report = null; particleLoadState.impacts = {}; particleLoadState.busy = false; return; }
    particleLoadState.scanning = true;
    const effect = cloneEffect(toRaw(e)), assumptions = { ...loadAssumptions };
    if (worker) worker.postMessage({ id, effect, assumptions });
    else runInline(id, effect, assumptions);
  }, 300);
}, { immediate: true });

/** Применить совет маркера к текущему эффекту (одним шагом undo). */
export function applyImpact(key: ImpactKey): void {
  const e = currentEffect.value;
  if (!e || !particleLoadState.impacts[key]) return;
  snapshot(); applyCandidate(key, e); touch();
}

/** Маркеры по убыванию эффекта - для списка в окне шкалы. */
export function impactList(): Impact[] {
  return Object.values(particleLoadState.impacts).filter((x): x is Impact => !!x).sort((a, b) => b.drop - a.drop);
}

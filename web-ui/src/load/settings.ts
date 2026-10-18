// Допущения оценки нагрузки (игроки, запуски): общие для режимов, помнятся в браузере.
import { reactive, watch } from 'vue';
import type { LoadAssumptions } from './load';

const KEY = 'displaylib.load';
function load(): LoadAssumptions {
  try { const v = JSON.parse(localStorage.getItem(KEY) || '{}'); return { players: Number(v.players) > 0 ? Number(v.players) : 10, launches: Number(v.launches) > 0 ? Number(v.launches) : 1 }; }
  catch { return { players: 10, launches: 1 }; }
}
export const loadAssumptions = reactive<LoadAssumptions>(load());
watch(loadAssumptions, (v) => { try { localStorage.setItem(KEY, JSON.stringify(v)); } catch { /* */ } });

/** Цвет шкалы: зелёный → жёлтый → красный. */
export function loadColor(score: number): string {
  const h = 130 * (1 - Math.max(0, Math.min(1, score)));
  return `hsl(${h.toFixed(0)}, 72%, ${score > 0.55 ? 52 : 45}%)`;
}
export function loadLabel(score: number): string {
  return score < 0.2 ? 'Лёгкий' : score < 0.45 ? 'Умеренный' : score < 0.7 ? 'Заметный' : score < 0.9 ? 'Тяжёлый' : 'Очень тяжёлый';
}

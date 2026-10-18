// Опции эффекта частиц, у которых есть дешёвый вариант, и сравнение нагрузки «было / стало».
// Без Vue и DOM: используется и в Web Worker (счёт), и в интерфейсе (применить совет).
import type { ParticleEffect } from '../particles/model';
import { cloneEffect } from '../particles/model';
import { particleLoad } from './particles';
import type { LoadReport, LoadAssumptions } from './score';

/** Поле инспектора, у которого стоит маркер. */
export type ImpactKey = 'viewers' | 'keyframeTicks' | 'color' | 'count' | 'rate' | 'lifetime' | 'collide'
  | 'wander' | 'attract' | 'easing' | 'fadeIn' | 'spin' | 'opacity';

export interface Impact {
  key: ImpactKey;
  /** Что предлагается: «collide: false» */
  suggestion: string;
  /** Меняет ли совет внешний вид (меньше частиц, короче жизнь) - тогда это компромисс, а не бесплатная экономия */
  changesLook: boolean;
  /** Насколько уменьшится самое большое из: время сервера, пакеты на игрока, сущности (0..1) */
  drop: number;
  /** Шкала до и после, 0..100 */
  scoreFrom: number;
  scoreTo: number;
  detail: string;
}

export interface Candidate { key: ImpactKey; when: (e: ParticleEffect) => boolean; suggestion: (e: ParticleEffect) => string; changesLook: boolean; apply: (e: ParticleEffect) => void }

const half = (x: number) => Math.round(x * 50) / 100;
export const CANDIDATES: Candidate[] = [
  { key: 'collide', when: (e) => e.motion.collide, suggestion: () => 'collide: false', changesLook: true, apply: (e) => { e.motion.collide = false; } },
  { key: 'keyframeTicks', when: (e) => e.keyframeTicks < 5, suggestion: () => 'keyframeTicks: 5', changesLook: false, apply: (e) => { e.keyframeTicks = 5; } },
  { key: 'easing', when: (e) => e.animation.easing !== 'LINEAR' && e.animation.endScale !== 1, suggestion: () => 'кривая размера «Линейная»', changesLook: false, apply: (e) => { e.animation.easing = 'LINEAR'; } },
  { key: 'rate', when: (e) => e.emitter.duration !== 0 && e.emitter.rate > 0.1, suggestion: (e) => `rate: ${half(e.emitter.rate)} (вдвое меньше)`, changesLook: true, apply: (e) => { e.emitter.rate = half(e.emitter.rate); } },
  { key: 'count', when: (e) => (e.emitter.count ?? 0) >= 8, suggestion: (e) => `count: ${Math.ceil((e.emitter.count ?? 0) / 2)} (вдвое меньше)`, changesLook: true, apply: (e) => { e.emitter.count = Math.ceil((e.emitter.count ?? 0) / 2); } },
  { key: 'lifetime', when: (e) => e.motion.lifetime[1] >= 6, suggestion: (e) => { const a = Math.max(1, Math.round(e.motion.lifetime[0] / 2)), b = Math.max(1, Math.round(e.motion.lifetime[1] / 2)); return `lifetime: ${a === b ? a : a + '…' + b} (вдвое короче)`; }, changesLook: true,
    apply: (e) => { e.motion.lifetime = [Math.max(1, Math.round(e.motion.lifetime[0] / 2)), Math.max(1, Math.round(e.motion.lifetime[1] / 2))]; } },
  { key: 'spin', when: (e) => e.animation.spin[0] !== 0 || e.animation.spin[1] !== 0, suggestion: () => 'spin: 0 (без вращения)', changesLook: true, apply: (e) => { e.animation.spin = [0, 0]; } },
  { key: 'fadeIn', when: (e) => e.animation.fadeIn > 0, suggestion: () => 'fadeIn: 0', changesLook: true, apply: (e) => { e.animation.fadeIn = 0; } },
  { key: 'color', when: (e) => !!e.animation.color && e.animation.color.length > 1, suggestion: () => 'постоянный цвет вместо градиента', changesLook: true,
    apply: (e) => { e.render.colors = [e.animation.color![0].value]; e.animation.color = null; } },
  { key: 'opacity', when: (e) => !!e.animation.opacity && e.animation.opacity.length > 1, suggestion: () => 'без кривой opacity', changesLook: true, apply: (e) => { e.animation.opacity = null; } },
  { key: 'wander', when: (e) => e.motion.wander > 0, suggestion: () => 'wander: 0 (путь прямее - кадры длиннее)', changesLook: true, apply: (e) => { e.motion.wander = 0; } },
  { key: 'attract', when: (e) => e.motion.attract !== 0, suggestion: () => 'attract: 0 (путь прямее - кадры длиннее)', changesLook: true, apply: (e) => { e.motion.attract = 0; } },
];

/** Порог, с которого опция считается заметной (доля). */
export const IMPACT_MIN = 0.1;

export function dropOf(base: LoadReport, alt: LoadReport): number {
  const rel = (a: number, b: number) => (a > 1e-9 ? Math.max(0, 1 - b / a) : 0);
  return Math.max(rel(base.cpu.usPerTick, alt.cpu.usPerTick), rel(base.net.packetsPerSec, alt.net.packetsPerSec), rel(base.client.entities, alt.client.entities));
}
export function detailOf(base: LoadReport, alt: LoadReport): string {
  const parts: string[] = [];
  const rel = (a: number, b: number) => (a > 1e-9 ? Math.round((1 - b / a) * 100) : 0);
  const c = rel(base.cpu.usPerTick, alt.cpu.usPerTick), n = rel(base.net.packetsPerSec, alt.net.packetsPerSec), e = rel(base.client.entities, alt.client.entities);
  if (c >= 5) parts.push(`сервер −${c}%`);
  if (n >= 5) parts.push(`пакеты −${n}%`);
  if (e >= 5) parts.push(`сущности −${e}%`);
  return parts.join(', ');
}


/** Маркер без функции (её не передать из воркера) - применение ищется по ключу в CANDIDATES. */
export function applyCandidate(key: ImpactKey, e: ParticleEffect): void {
  CANDIDATES.find((c) => c.key === key)?.apply(e);
}

/** Оценка одного варианта: null - опция почти не влияет. */
export function impactOf(c: Candidate, e: ParticleEffect, base: LoadReport, a: LoadAssumptions): Impact | null {
  const alt = cloneEffect(e); c.apply(alt);
  const r = particleLoad(alt, a);
  const drop = dropOf(base, r);
  if (drop < IMPACT_MIN) return null;
  return { key: c.key, suggestion: c.suggestion(e), changesLook: c.changesLook, drop, scoreFrom: Math.round(base.score * 100), scoreTo: Math.round(r.score * 100), detail: detailOf(base, r) };
}

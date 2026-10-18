// Превью hover-анимаций: что плагин делает с трансформацией сущности при наведении
// (HoverAnimation.applyHoverAnimation) - в виде набора дельт, которые сцена накладывает на виджет.
//
// Плагин: масштаб и смещение при duration <= 10 отдаёт клиенту (линейная интерполяция),
// при большей длительности ведёт сам с easing; поворот всегда через клиента (линейно).
// Всё применяется вокруг якоря сущности (у текста - низ по центру), как в игре.
import type { HoverAnimation, Easing, Vec3 } from '../model/screen';

/** Дельты трансформации относительно обычного состояния. */
export interface HoverDelta {
  /** Множители масштаба по x/y/z. */
  mul: Vec3;
  /** Смещение translation (в локальных блоках сущности). */
  offset: Vec3;
  /** Поворот, градусы, вокруг оси axis (единичной). */
  angle: number;
  axis: Vec3;
}

export const IDENTITY: HoverDelta = { mul: [1, 1, 1], offset: [0, 0, 0], angle: 0, axis: [0, 1, 0] };

export function isIdentity(d: HoverDelta): boolean {
  return d.mul.every((v) => v === 1) && d.offset.every((v) => v === 0) && d.angle === 0;
}

function norm(a: Vec3): Vec3 {
  const l = Math.hypot(a[0], a[1], a[2]) || 1;
  return [a[0] / l, a[1] / l, a[2] / l];
}

/** Целевые дельты для анимации (COMBINED - объединение эффектов). */
export function targetOf(a: HoverAnimation | null | undefined): HoverDelta {
  if (!a) return IDENTITY;
  const d: HoverDelta = { mul: [1, 1, 1], offset: [0, 0, 0], angle: 0, axis: [0, 1, 0] };
  const merge = (e: HoverAnimation) => {
    switch (e.type) {
      case 'PRESET':
        if (e.preset === 'BOUNCE' || e.preset === 'SCALE' || e.preset === 'PULSE') { d.mul = [d.mul[0] * e.intensity, d.mul[1] * e.intensity, d.mul[2]]; }
        else if (e.preset === 'LIFT') d.offset = [d.offset[0], d.offset[1] + 0.02 * e.intensity, d.offset[2]];
        else if (e.preset === 'WOBBLE') { d.angle += 15 * e.intensity; d.axis = [0, 1, 0]; }
        break; // GLOW - только у предметов, трансформацию не меняет
      case 'SCALE': d.mul = [d.mul[0] * e.scale[0], d.mul[1] * e.scale[1], d.mul[2] * e.scale[2]]; break;
      case 'TRANSLATE': d.offset = [d.offset[0] + e.offset[0], d.offset[1] + e.offset[1], d.offset[2] + e.offset[2]]; break;
      case 'ROTATE': d.angle += e.rotation[1]; d.axis = norm(e.axis); break;
      case 'TRANSFORM':
        // плагин ставит абсолютную трансформацию; в превью приближаем как множитель к базовому масштабу и смещение
        d.mul = [d.mul[0] * e.scale[0], d.mul[1] * e.scale[1], d.mul[2] * e.scale[2]];
        d.offset = [d.offset[0] + e.translation[0], d.offset[1] + e.translation[1], d.offset[2] + e.translation[2]];
        break;
      case 'COMBINED': for (const s of e.effects) if (s) merge(s); break;
    }
  };
  merge(a);
  return d;
}

/** Длительность в мс: тики плагина × 50. */
export function durationMs(a: HoverAnimation | null | undefined): number {
  return Math.max(1, a?.duration ?? 10) * 50;
}

/** Easing, как в EasedAnimation.Easing плагина. Короткие (≤10 тиков) анимации клиент ведёт линейно. */
export function easingOf(a: HoverAnimation | null | undefined): (t: number) => number {
  if (!a || a.duration <= 10 || a.type === 'ROTATE') return (t) => t;
  return EASINGS[a.easing] ?? ((t) => t);
}

export const EASINGS: Record<Easing, (t: number) => number> = {
  LINEAR: (t) => t,
  EASE_IN: (t) => t * t,
  EASE_OUT: (t) => 1 - (1 - t) * (1 - t),
  EASE_IN_OUT: (t) => (t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t),
  BOUNCE: (t) => {
    const n1 = 7.5625, d1 = 2.75;
    if (t < 1 / d1) return n1 * t * t;
    if (t < 2 / d1) return n1 * (t -= 1.5 / d1) * t + 0.75;
    if (t < 2.5 / d1) return n1 * (t -= 2.25 / d1) * t + 0.9375;
    return n1 * (t -= 2.625 / d1) * t + 0.984375;
  },
  ELASTIC: (t) => (t === 0 ? 0 : t === 1 ? 1 : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * ((2 * Math.PI) / 3)) + 1),
};

export function lerpDelta(a: HoverDelta, b: HoverDelta, f: number): HoverDelta {
  const l = (x: number, y: number) => x + (y - x) * f;
  return {
    mul: [l(a.mul[0], b.mul[0]), l(a.mul[1], b.mul[1]), l(a.mul[2], b.mul[2])],
    offset: [l(a.offset[0], b.offset[0]), l(a.offset[1], b.offset[1]), l(a.offset[2], b.offset[2])],
    angle: l(a.angle, b.angle),
    axis: b.angle !== 0 ? b.axis : a.axis,
  };
}

/** Состояние одной идущей анимации виджета. */
export interface HoverTween { t0: number; ms: number; from: HoverDelta; to: HoverDelta; ease: (t: number) => number }

export function tweenAt(tw: HoverTween, now: number): { d: HoverDelta; done: boolean } {
  const f = Math.min(1, (now - tw.t0) / tw.ms);
  return { d: lerpDelta(tw.from, tw.to, tw.ease(f)), done: f >= 1 };
}

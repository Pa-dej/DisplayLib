// Порт ParticleSim плагина: рождение, шаг движения с силами и коллизиями, планирование отрезков,
// размер/поворот/цвет во времени. Числа и порядок операций те же, что в Java, - предпросмотр
// показывает ту же траекторию, что увидит игрок.
import type { ParticleEffect, Motion, Emitter, Animation } from './model';
import { gradientOf, curveOf, sampleGradient, sampleCurve, withOpacity, parseColor, type Gradient, type Curve } from './colors';
import { EASINGS as EASE_FN } from '../render/hoverAnim';

// ---------------------------------------------------------------- случайность

/** Детерминированный генератор (mulberry32): одно и то же зерно - один и тот же эффект. */
export class Rng {
  private s: number;
  constructor(seed: number) { this.s = seed >>> 0 || 1; }
  nextDouble(): number {
    let t = (this.s = (this.s + 0x6d2b79f5) >>> 0);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }
  nextInt(n: number): number { return Math.floor(this.nextDouble() * n); }
  nextBoolean(): boolean { return this.nextDouble() < 0.5; }
  range(r: [number, number]): number { return r[0] === r[1] ? r[0] : r[0] + (r[1] - r[0]) * this.nextDouble(); }
}

// ---------------------------------------------------------------- эффект в «рабочем» виде

/** Эффект с заранее посчитанными производными величинами (нормированная ось, градиенты, easing). */
export interface Compiled {
  effect: ParticleEffect;
  motion: Motion & { dir: [number, number, number] | null };
  emitter: Emitter & { half: [number, number, number] };
  animation: Animation;
  gradient: Gradient | null;
  opacity: Curve;
  ease: (t: number) => number;
  colors: number[];
}

export function compile(e: ParticleEffect): Compiled {
  const d = e.motion.direction;
  let dir: [number, number, number] | null = null;
  if (d !== 'OUTWARD') {
    const len = Math.hypot(d[0], d[1], d[2]);
    dir = len < 1e-9 ? [0, 1, 0] : [d[0] / len, d[1] / len, d[2] / len];
  }
  const colors = e.render.colors.map((c) => parseColor(c)).filter((c): c is number => c != null);
  if (e.render.type === 'PIXEL' && !colors.length) colors.push(0xffffffff);
  return {
    effect: e,
    motion: { ...e.motion, dir },
    emitter: { ...e.emitter, half: [Math.abs(e.emitter.size[0]) / 2, Math.abs(e.emitter.size[1]) / 2, Math.abs(e.emitter.size[2]) / 2] },
    animation: e.animation,
    gradient: gradientOf(e.animation.color),
    opacity: curveOf(e.animation.opacity),
    ease: EASE_FN[e.animation.easing] ?? ((t: number) => t),
    colors,
  };
}

// ---------------------------------------------------------------- тело

export type Solid = (x: number, y: number, z: number) => boolean;

export interface Body {
  x: number; y: number; z: number;
  vx: number; vy: number; vz: number;
  resting: boolean; ghost: boolean;
  cx: number; cy: number; cz: number;
  wx: number; wy: number; wz: number;
  random: Rng | null;
}
export function body(x: number, y: number, z: number, vx: number, vy: number, vz: number): Body {
  return { x, y, z, vx, vy, vz, resting: false, ghost: false, cx: 0, cy: 0, cz: 0, wx: 0, wy: 1, wz: 0, random: null };
}
export const copyBody = (b: Body): Body => ({ ...b });

// ---------------------------------------------------------------- рождение

export function randomUnit(r: Rng): [number, number, number] {
  const z = r.nextDouble() * 2 - 1;
  const a = r.nextDouble() * Math.PI * 2;
  const s = Math.sqrt(1 - z * z);
  return [s * Math.cos(a), z, s * Math.sin(a)];
}

export function spawnOffset(c: Compiled, r: Rng): [number, number, number] {
  const e = c.emitter;
  switch (e.shape) {
    case 'POINT': return [0, 0, 0];
    case 'SPHERE': {
      const d = randomUnit(r);
      const rad = e.surface ? e.radius : e.radius * Math.cbrt(r.nextDouble());
      return [d[0] * rad, d[1] * rad, d[2] * rad];
    }
    case 'CIRCLE': {
      const a = r.nextDouble() * Math.PI * 2;
      const rad = e.surface ? e.radius : e.radius * Math.sqrt(r.nextDouble());
      return [Math.cos(a) * rad, 0, Math.sin(a) * rad];
    }
    case 'BOX': {
      const h = e.half;
      const p: [number, number, number] = [(r.nextDouble() * 2 - 1) * h[0], (r.nextDouble() * 2 - 1) * h[1], (r.nextDouble() * 2 - 1) * h[2]];
      if (e.surface) { const axis = r.nextInt(3); p[axis] = (r.nextBoolean() ? 1 : -1) * h[axis]; }
      return p;
    }
  }
}

function cross(a: number[], b: number[]): [number, number, number] { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
function normalize(a: number[]): [number, number, number] { const l = Math.hypot(a[0], a[1], a[2]); return [a[0] / l, a[1] / l, a[2] / l]; }

export function randomInCone(axis: number[], angle: number, r: Rng): [number, number, number] {
  if (angle <= 0) return [axis[0], axis[1], axis[2]];
  const cosT = 1 - r.nextDouble() * (1 - Math.cos(Math.min(angle, Math.PI)));
  const sinT = Math.sqrt(Math.max(0, 1 - cosT * cosT));
  const phi = r.nextDouble() * Math.PI * 2;
  const helper = Math.abs(axis[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
  const u = normalize(cross(axis, helper));
  const v = cross(axis, u);
  const c = Math.cos(phi) * sinT, s = Math.sin(phi) * sinT;
  return [axis[0] * cosT + u[0] * c + v[0] * s, axis[1] * cosT + u[1] * c + v[1] * s, axis[2] * cosT + u[2] * c + v[2] * s];
}

export function spawnVelocity(c: Compiled, offset: number[], r: Rng): [number, number, number] {
  const speed = r.range(c.motion.speed);
  if (speed === 0) return [0, 0, 0];
  let axis: number[] | null = c.motion.dir;
  if (!axis) {
    const len = Math.hypot(offset[0], offset[1], offset[2]);
    axis = len < 1e-9 ? randomUnit(r) : [offset[0] / len, offset[1] / len, offset[2] / len];
  }
  const d = randomInCone(axis, (c.motion.spread * Math.PI) / 180, r);
  return [d[0] * speed, d[1] * speed, d[2] * speed];
}

// ---------------------------------------------------------------- движение

export const REST_SPEED = 0.008;
export const SKIN = 1e-3;

export function applyForces(b: Body, m: Motion): void {
  if (m.attract !== 0) {
    const dx = b.cx - b.x, dy = m.attractAxis ? 0 : b.cy - b.y, dz = b.cz - b.z;
    const d = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (d > 1e-6) { const k = (m.attract * Math.min(1, d / 0.25)) / d; b.vx += dx * k; b.vy += dy * k; b.vz += dz * k; }
  }
  if (m.wander > 0 && b.random) {
    const j = randomUnit(b.random);
    const wx = b.wx + j[0] * 0.6, wy = b.wy + j[1] * 0.6, wz = b.wz + j[2] * 0.6;
    const len = Math.sqrt(wx * wx + wy * wy + wz * wz);
    if (len > 1e-9) { b.wx = wx / len; b.wy = wy / len; b.wz = wz / len; }
    const push = m.wander * b.random.nextDouble();
    b.vx += b.wx * push; b.vy += b.wy * push; b.vz += b.wz * push;
  }
}

const fl = Math.floor;

/** Один тик (как ParticleSim.step): true - удар о блок. */
export function step(b: Body, m: Motion, solid: Solid | null): boolean {
  if (b.resting) return false;
  b.vy -= m.gravity;
  applyForces(b, m);
  const keep = 1 - m.drag;
  b.vx *= keep; b.vy *= keep; b.vz *= keep;
  if (!solid || b.ghost || !m.collide) { b.x += b.vx; b.y += b.vy; b.z += b.vz; return false; }

  let hit = false, floorContact = false;
  const ny = b.y + b.vy;
  if (b.vy !== 0 && solid(fl(b.x), fl(ny), fl(b.z))) {
    hit = true;
    const falling = b.vy < 0;
    b.y = falling ? fl(ny) + 1 + SKIN : fl(ny) - SKIN;
    const impact = Math.abs(b.vy);
    if (falling && impact < 2 * Math.max(m.gravity, 0) + REST_SPEED) b.vy = 0;
    else b.vy = -b.vy * m.bounce;
    floorContact = falling;
    b.vx *= m.friction; b.vz *= m.friction;
  } else b.y = ny;
  const nx = b.x + b.vx;
  if (b.vx !== 0 && solid(fl(nx), fl(b.y), fl(b.z))) {
    hit = true;
    b.x = b.vx < 0 ? fl(nx) + 1 + SKIN : fl(nx) - SKIN;
    b.vx = -b.vx * m.bounce;
    b.vy *= m.friction; b.vz *= m.friction;
  } else b.x = nx;
  const nz = b.z + b.vz;
  if (b.vz !== 0 && solid(fl(b.x), fl(b.y), fl(nz))) {
    hit = true;
    b.z = b.vz < 0 ? fl(nz) + 1 + SKIN : fl(nz) - SKIN;
    b.vz = -b.vz * m.bounce;
    b.vx *= m.friction; b.vy *= m.friction;
  } else b.z = nz;

  if (hit && b.vx * b.vx + b.vy * b.vy + b.vz * b.vz < REST_SPEED * REST_SPEED && (m.gravity <= 0 || floorContact)) {
    b.vx = b.vy = b.vz = 0;
    b.resting = true;
  }
  return hit;
}

// ---------------------------------------------------------------- отрезки ключевых кадров

export const SEGMENT_SPEED_TOLERANCE = 0.12;
export const SEGMENT_SPEED_FLOOR = 0.003;
export const SEGMENT_PATH_TOLERANCE = 0.02;

export interface Segment {
  length: number;
  end: Body;
  /** Положения по тикам отрезка: states[i] - через i тиков (1..length) */
  path: [number, number, number][];
}

/** Как ParticleSim.planSegment: самый длинный отрезок, который клиент проведёт по прямой незаметно для глаза. */
export function planSegment(start: Body, m: Motion, solid: Solid | null, maxLength: number): Segment {
  maxLength = Math.max(1, maxLength);
  const cur = copyBody(start);
  const states: Body[] = [];
  const xs = [start.x], ys = [start.y], zs = [start.z];
  for (let i = 1; i <= maxLength; i++) {
    step(cur, m, solid);
    states[i] = copyBody(cur);
    xs.push(cur.x); ys.push(cur.y); zs.push(cur.z);
  }
  let best = 1;
  for (let length = 2; length <= maxLength; length++) {
    if (!smooth(xs, ys, zs, length)) break;
    best = length;
  }
  const path: [number, number, number][] = [];
  for (let i = 1; i <= best; i++) path.push([xs[i], ys[i], zs[i]]);
  return { length: best, end: states[best], path };
}

function smooth(xs: number[], ys: number[], zs: number[], length: number): boolean {
  const ax = (xs[length] - xs[0]) / length, ay = (ys[length] - ys[0]) / length, az = (zs[length] - zs[0]) / length;
  const allowed = Math.max(SEGMENT_SPEED_TOLERANCE * Math.sqrt(ax * ax + ay * ay + az * az), SEGMENT_SPEED_FLOOR);
  for (let j = 0; j < length; j++) {
    const dx = xs[j + 1] - xs[j] - ax, dy = ys[j + 1] - ys[j] - ay, dz = zs[j + 1] - zs[j] - az;
    if (dx * dx + dy * dy + dz * dz > allowed * allowed) return false;
    const ex = xs[0] + ax * (j + 1) - xs[j + 1], ey = ys[0] + ay * (j + 1) - ys[j + 1], ez = zs[0] + az * (j + 1) - zs[j + 1];
    if (ex * ex + ey * ey + ez * ez > SEGMENT_PATH_TOLERANCE * SEGMENT_PATH_TOLERANCE) return false;
  }
  return true;
}

/** Длина отрезка вида: на старте 1, 1, 2, 2, дальше keyframeTicks (LiveParticle.segmentLength). */
export function segmentLength(index: number, keyframeTicks: number): number {
  const ramp = index < 2 ? 1 : index < 4 ? 2 : keyframeTicks;
  return Math.max(1, Math.min(keyframeTicks, ramp));
}

// ---------------------------------------------------------------- вид во времени (дробный возраст допускается)

export function scaleAt(c: Compiled, start: number, age: number, life: number): number {
  const a = c.animation;
  const t = life <= 0 ? 1 : Math.max(0, Math.min(1, age / life));
  const k = c.ease(t);
  let s = start * (1 + (a.endScale - 1) * k);
  if (a.fadeIn > 0 && age < a.fadeIn) s *= age / a.fadeIn;
  return Math.max(0, s);
}
export const rotationAt = (start: number, spin: number, age: number) => start + spin * age;
export function colorAt(c: Compiled, startColor: number, age: number, life: number): number {
  const t = life <= 0 ? 1 : Math.max(0, Math.min(1, age / life));
  const col = c.gradient ? sampleGradient(c.gradient, t) : startColor;
  return withOpacity(col, sampleCurve(c.opacity, t));
}

// ---------------------------------------------------------------- кадры вида (как ParticleSim.planTransform)

export const TRANSFORM_SCALE_TOLERANCE = 0.03;
export const TRANSFORM_SCALE_FLOOR = 0.003;
export const TRANSFORM_COLOR_TOLERANCE = 5;

/** Клиент интерполирует цвет фона поканально в ARGB. */
export function lerpArgb(a: number, b: number, f: number): number {
  if (f <= 0 || a === b) return a;
  if (f >= 1) return b;
  let out = 0;
  for (let sh = 24; sh >= 0; sh -= 8) {
    const x = (a >>> sh) & 0xff, y = (b >>> sh) & 0xff;
    out |= (javaRound(x + (y - x) * f) & 0xff) << sh;
  }
  return out >>> 0;
}
/** Math.round Java (полуцелые - вверх), совпадает с Math.round JS для положительных. */
const javaRound = (v: number) => Math.floor(v + 0.5);
function closeArgb(a: number, b: number): boolean {
  for (let sh = 24; sh >= 0; sh -= 8) if (Math.abs(((a >>> sh) & 0xff) - ((b >>> sh) & 0xff)) > TRANSFORM_COLOR_TOLERANCE) return false;
  return true;
}

/**
 * Длина кадра вида от возраста from (не больше maxSteps): линейная интерполяция клиента размера и цвета
 * не отходит от настоящих значений дальше допусков. LINEAR без кривых цвета - один кадр на всю жизнь.
 */
export function planTransform(c: Compiled, startScale: number, startColor: number, colored: boolean,
  fromScale: number, fromColor: number, from: number, life: number, maxSteps: number): number {
  if (maxSteps <= 1) return Math.max(1, maxSteps);
  const scales = new Float64Array(maxSteps + 1);
  const colors = colored ? new Array<number>(maxSteps + 1) : null;
  for (let j = 1; j <= maxSteps; j++) {
    scales[j] = scaleAt(c, startScale, from + j, life);
    if (colors) colors[j] = colorAt(c, startColor, from + j, life);
  }
  let best = 1;
  for (let n = 2; n <= maxSteps; n++) {
    let ok = true;
    for (let j = 1; j < n && ok; j++) {
      const f = j / n;
      const lerpScale = fromScale + (scales[n] - fromScale) * f;
      const tol = Math.max(TRANSFORM_SCALE_TOLERANCE * Math.abs(scales[j]), TRANSFORM_SCALE_FLOOR);
      if (Math.abs(lerpScale - scales[j]) > tol) ok = false;
      else if (colors && !closeArgb(lerpArgb(fromColor, colors[n], f), colors[j])) ok = false;
    }
    if (!ok) break;
    best = n;
  }
  return best;
}

/** Длины всех кадров вида за жизнь (как ParticleSim.transformPlan): оценка нагрузки и тесты. */
export function transformPlan(c: Compiled, startScale: number, startColor: number, colored: boolean, spin: number, life: number): number[] {
  const out: number[] = [];
  let sentScale = scaleAt(c, startScale, 0, life);
  let sentColor = colorAt(c, startColor, 0, life);
  let visual = 0;
  while (visual < life) {
    let steps = life - visual;
    if (spin !== 0) steps = Math.min(steps, Math.max(1, Math.trunc(170 / Math.abs(spin))));
    steps = planTransform(c, startScale, startColor, colored, sentScale, sentColor, visual, life, steps);
    out.push(steps);
    visual += steps;
    sentScale = scaleAt(c, startScale, visual, life);
    if (colored) sentColor = colorAt(c, startColor, visual, life);
  }
  return out;
}

// Воспроизведение эффекта в редакторе: «сервер» - тот же порядок действий, что ParticleManager /
// LiveParticle плагина (испускание, возраст, планирование отрезков, отправка кадров), и «клиент» -
// что рисует Minecraft между пакетами: позицию ведёт к цели за teleportDuration тиков, размер,
// поворот и цвет - за interpolationDuration, линейно от того места, где частица была в момент пакета.
import type { ParticleEffect } from './model';
import { burstCount } from './model';
import {
  type Compiled, type Body, type Solid, type Segment, Rng, compile, body, spawnOffset, spawnVelocity, randomUnit,
  planSegment, planTransform, lerpArgb, scaleAt, rotationAt, colorAt,
} from './sim';
import { alpha } from './colors';

/** Линейная интерполяция клиента от значения в момент пакета к цели. */
interface Lerp<T> { from: T; to: T; start: number; dur: number }

export interface Particle {
  id: number;
  /** Номер варианта текста/предмета/блока */
  variant: number;
  startColor: number;
  life: number;
  startScale: number;
  startRotation: number;
  spin: number;
  spawnTick: number;
  age: number;
  body: Body;
  planned: Segment | null;
  nextPosition: number;
  nextTransform: number;
  transformSegment: number;
  sentAge: number;
  sentScale: number;
  sentRotation: number;
  sentColor: number;
  teleportDuration: number;
  /** Что показывает клиент */
  pos: Lerp<[number, number, number]>;
  look: Lerp<{ scale: number; rotation: number; color: number }>;
  /** Точный путь по тикам «видимого возраста» (для режима «точная траектория») */
  path: [number, number, number][];
  dead: boolean;
}

export interface Stats {
  alive: number;
  /** Пакетов за последнюю секунду (спавн, телепорт, метаданные, удаление) */
  packetsPerSecond: number;
  /** Среднее число тиков между кадрами позиции */
  avgSegment: number;
  spawned: number;
}

export interface RuntimeOptions {
  center: [number, number, number];
  solid: Solid | null;
  seed: number;
  maxAlive?: number;
}

export class ParticleRuntime {
  readonly c: Compiled;
  tick = 0;
  particles: Particle[] = [];
  private rng: Rng;
  private ticksLeft: number;
  private carry = 0;
  stopped = false;
  private nextId = 1;
  private packetLog: number[] = [];
  private segSum = 0;
  private segCount = 0;
  spawned = 0;

  constructor(effect: ParticleEffect, private opts: RuntimeOptions) {
    this.c = compile(effect);
    this.rng = new Rng(opts.seed);
    const d = effect.emitter.duration;
    this.ticksLeft = d === -1 ? Number.MAX_SAFE_INTEGER : d;
    this.emit(burstCount(effect.emitter));
  }

  /** Испускание закончено и все частицы исчезли. */
  get done(): boolean { return (this.ticksLeft <= 0 || this.stopped) && !this.particles.length; }

  /** Остановить испускание (выпущенные доживают). */
  stop(): void { this.stopped = true; this.ticksLeft = 0; }

  private packet(n = 1): void { for (let i = 0; i < n; i++) this.packetLog.push(this.tick); }

  /** Один серверный тик, как ParticleManager.tickEmission. */
  step(): void {
    this.tick++;
    const K = this.c.effect.keyframeTicks;
    const solid = this.c.motion.collide ? this.opts.solid : null;
    for (const p of this.particles) {
      if (p.dead || this.tick <= p.spawnTick) continue;
      p.age++;
      if (p.age > p.life) { p.dead = true; this.packet(); continue; }
      if (p.age >= p.nextPosition) this.sendPosition(p, K, solid);
      if (p.age >= p.nextTransform) this.sendTransform(p, K);
    }
    this.particles = this.particles.filter((p) => !p.dead);
    if (this.ticksLeft > 0 && !this.stopped) {
      if (this.ticksLeft !== Number.MAX_SAFE_INTEGER) this.ticksLeft--;
      this.carry += this.c.emitter.rate;
      const n = Math.floor(this.carry);
      this.carry -= n;
      this.emit(n);
    }
    const from = this.tick - 20;
    while (this.packetLog.length && this.packetLog[0] <= from) this.packetLog.shift();
  }

  stats(): Stats {
    return {
      alive: this.particles.length,
      packetsPerSecond: this.packetLog.length,
      avgSegment: this.segCount ? this.segSum / this.segCount : 0,
      spawned: this.spawned,
    };
  }

  private emit(count: number): void {
    const max = this.opts.maxAlive ?? 1500;
    for (let i = 0; i < count; i++) {
      if (this.particles.length >= max) return;
      this.particles.push(this.spawn());
    }
  }

  private spawn(): Particle {
    const c = this.c, r = this.rng, e = c.effect;
    const [cx, cy, cz] = this.opts.center;
    const off = spawnOffset(c, r);
    const vel = spawnVelocity(c, off, r);
    const b = body(cx + off[0], cy + off[1], cz + off[2], vel[0], vel[1], vel[2]);
    const solid = c.motion.collide ? this.opts.solid : null;
    if (solid && solid(Math.floor(b.x), Math.floor(b.y), Math.floor(b.z))) b.ghost = true;
    b.cx = cx; b.cy = cy; b.cz = cz;
    if (c.motion.wander > 0) { const w = randomUnit(r); b.wx = w[0]; b.wy = w[1]; b.wz = w[2]; b.random = r; }

    const life = Math.max(1, Math.round(r.range(c.motion.lifetime)));
    const first = planSegment(b, c.motion, solid, e.keyframeTicks);
    const scale = r.range(c.animation.scale);
    const rotation = r.range(c.animation.rotation);
    const spin = r.range(c.animation.spin);
    const nVariants = e.render.type === 'TEXT' ? (e.render.text != null ? 1 : 0) + e.render.variants.length
      : e.render.type === 'ITEM' ? e.render.items.length : e.render.type === 'BLOCK' ? e.render.blocks.length : 1;
    const variant = nVariants > 1 ? r.nextInt(nVariants) : 0;
    const startColor = c.colors.length ? (c.colors.length === 1 ? c.colors[0] : c.colors[r.nextInt(c.colors.length)]) : 0xffffffff;
    const color0 = colorAt(c, startColor, 0, life);
    const s0 = scaleAt(c, scale, 0, life);
    this.spawned++;
    this.packet(2); // спавн + метаданные
    return {
      id: this.nextId++, variant, startColor, life, startScale: scale, startRotation: rotation, spin,
      spawnTick: this.tick, age: 0, body: b, planned: first, nextPosition: 1, nextTransform: 1, transformSegment: 0,
      sentAge: 0, sentScale: s0, sentRotation: rotation, sentColor: color0, teleportDuration: first.length,
      pos: { from: [b.x, b.y, b.z], to: [b.x, b.y, b.z], start: this.tick, dur: 1 },
      look: { from: { scale: s0, rotation, color: color0 }, to: { scale: s0, rotation, color: color0 }, start: this.tick, dur: 1 },
      path: [[b.x, b.y, b.z]],
      dead: false,
    };
  }

  /** LiveParticle.sendPosition */
  private sendPosition(p: Particle, K: number, solid: Solid | null): void {
    const seg = p.planned ?? planSegment(p.body, this.c.motion, solid, K);
    const wasResting = p.body.resting;
    const prev = p.body;
    p.body = seg.end;
    p.nextPosition = p.age + seg.length;
    for (let i = 0; i < seg.path.length; i++) p.path[p.age - 1 + i + 1] = seg.path[i];
    this.segSum += seg.length; this.segCount++;
    if (!wasResting && (p.body.x !== prev.x || p.body.y !== prev.y || p.body.z !== prev.z)) {
      // клиент: от текущего показанного места к цели за teleportDuration, заданный до этого пакета
      p.pos = { from: this.shownPos(p, this.tick), to: [p.body.x, p.body.y, p.body.z], start: this.tick, dur: p.teleportDuration };
      this.packet();
    }
    p.planned = planSegment(p.body, this.c.motion, solid, K);
    if (p.planned.length !== seg.length) { p.teleportDuration = p.planned.length; this.packet(); }
  }

  /** LiveParticle.sendTransform */
  private sendTransform(p: Particle, K: number): void {
    void K;
    const visual = p.age - 1;
    let steps = p.life - visual;
    if (p.spin !== 0) {
      const maxSpan = Math.max(1, Math.floor(170 / Math.abs(p.spin)));
      steps = Math.min(steps, Math.max(1, maxSpan - (visual - p.sentAge)));
    }
    if (steps <= 0) { p.nextTransform = Number.MAX_SAFE_INTEGER; return; }
    const colored = this.c.effect.render.type === 'TEXT' || this.c.effect.render.type === 'PIXEL';
    steps = planTransform(this.c, p.startScale, p.startColor, colored, p.sentScale, p.sentColor, visual, p.life, steps);
    p.transformSegment++;
    const target = visual + steps;
    p.nextTransform = p.age + steps;
    const s = scaleAt(this.c, p.startScale, target, p.life);
    const rot = rotationAt(p.startRotation, p.spin, target);
    const textual = this.c.effect.render.type === 'TEXT' || this.c.effect.render.type === 'PIXEL';
    const col = textual ? colorAt(this.c, p.startColor, target, p.life) : p.sentColor;
    if (s !== p.sentScale || rot !== p.sentRotation || col !== p.sentColor) {
      p.look = { from: this.shownLook(p, this.tick), to: { scale: s, rotation: rot, color: col }, start: this.tick, dur: steps };
      p.sentScale = s; p.sentRotation = rot; p.sentColor = col;
      this.packet();
    }
    p.sentAge = target;
  }

  // ---------------------------------------------------------------- что видно в момент t (дробные тики)

  shownPos(p: Particle, t: number): [number, number, number] {
    const l = p.pos; const f = Math.max(0, Math.min(1, (t - l.start) / l.dur));
    return [l.from[0] + (l.to[0] - l.from[0]) * f, l.from[1] + (l.to[1] - l.from[1]) * f, l.from[2] + (l.to[2] - l.from[2]) * f];
  }
  shownLook(p: Particle, t: number): { scale: number; rotation: number; color: number } {
    const l = p.look; const f = Math.max(0, Math.min(1, (t - l.start) / l.dur));
    return { scale: l.from.scale + (l.to.scale - l.from.scale) * f, rotation: l.from.rotation + (l.to.rotation - l.from.rotation) * f, color: lerpArgb(l.from.color, l.to.color, f) };
  }

  /** Точное состояние без кадров (для сравнения): тот же сдвиг на тик, что у клиента. */
  exact(p: Particle, t: number): { pos: [number, number, number]; scale: number; rotation: number; color: number } {
    const v = Math.max(0, t - p.spawnTick - 1);
    const i = Math.floor(v), f = v - i;
    const a = p.path[Math.min(i, p.path.length - 1)] ?? p.path[0];
    const b = p.path[Math.min(i + 1, p.path.length - 1)] ?? a;
    const pos: [number, number, number] = [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f];
    const textual = this.c.effect.render.type === 'TEXT' || this.c.effect.render.type === 'PIXEL';
    return {
      pos,
      scale: scaleAt(this.c, p.startScale, v, p.life),
      rotation: rotationAt(p.startRotation, p.spin, v),
      color: textual ? colorAt(this.c, p.startColor, v, p.life) : p.sentColor,
    };
  }
}

export { lerpArgb } from './sim';

export { alpha };

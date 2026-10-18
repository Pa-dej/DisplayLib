// Эффект частиц (plugins/DisplayLib/particles/<id>.jmc) - та же структура и те же умолчания,
// что ParticleEffect в плагине. Цвета хранятся строками, как их пишут в файле ("#rrggbb",
// "#rrggbbaa", имя), чтобы файл после редактора выглядел так же, как до него.
import type { RichText, Vec3, RGB, Easing } from '../model/screen';

export type Viewers = 'ALL' | 'OWNER';
export type RenderType = 'TEXT' | 'PIXEL' | 'ITEM' | 'BLOCK';
export type Shape = 'POINT' | 'SPHERE' | 'BOX' | 'CIRCLE';
export type Billboard = 'FIXED' | 'VERTICAL' | 'HORIZONTAL' | 'CENTER';

/** Число или случайное значение из диапазона: [min, max]; min == max - точное значение. */
export type Range = [number, number];

/**
 * Точка градиента/кривой: доля жизни 0..1 (null - точки распределены равномерно) и значение.
 * bend (только у кривых) - изгиб отрезка, который приходит в эту точку: узел [x, dy] - кривая проходит
 * на доле x отрезка (0.25..0.75) на dy выше прямой; null/нет - прямая.
 */
export interface Stop<T> { at: number | null; value: T; bend?: [number, number] | null }

export interface Render {
  type: RenderType;
  /** TEXT: один текст (text) и/или варианты (variants) - на частицу случайный */
  text: RichText | null;
  variants: RichText[];
  /**
   * TEXT: кадры текста, как frames у кнопок экранов (2+; тогда text/variants не используются). Сменяются каждые
   * frameInterval тиков по кругу или (framesOverLife) один раз за жизнь; frameRandomStart - со случайного кадра.
   */
  frames: RichText[];
  frameInterval: number;
  framesOverLife: boolean;
  frameRandomStart: boolean;
  /** Случайный цвет на частицу: цвет текста (TEXT) или квадрата (PIXEL) */
  colors: string[];
  items: string[];
  blocks: string[];
  billboard: Billboard;
  brightness: number | null;
  shadow: boolean;
  background: RGB | null;
  backgroundAlpha: number | null;
  /** Контур свечения (клиент рисует у ITEM и BLOCK) и его цвет; null - белый */
  glow: boolean;
  glowColor: string | null;
}

export interface Emitter {
  shape: Shape;
  radius: number;
  /** Полные размеры коробки (в файле так же; плагин делит пополам) */
  size: Vec3;
  surface: boolean;
  /** null - по умолчанию: 1 для всплеска, 0 при duration */
  count: number | null;
  /** 0 - всплеск; > 0 - тиков испускания; -1 - пока не остановят */
  duration: number;
  rate: number;
}

export interface Motion {
  lifetime: Range;
  speed: Range;
  /** Ось разлёта или 'OUTWARD' - от центра формы */
  direction: Vec3 | 'OUTWARD';
  spread: number;
  gravity: number;
  drag: number;
  collide: boolean;
  bounce: number;
  friction: number;
  attract: number;
  attractAxis: boolean;
  wander: number;
}

export interface Animation {
  scale: Range;
  endScale: number;
  easing: Easing;
  fadeIn: number;
  rotation: Range;
  spin: Range;
  /** Цвет по доле жизни или null */
  color: Stop<string>[] | null;
  /** Множитель непрозрачности по доле жизни или null (= 1) */
  opacity: Stop<number>[] | null;
}

export interface ParticleEffect {
  id: string;
  viewers: Viewers;
  keyframeTicks: number;
  render: Render;
  emitter: Emitter;
  motion: Motion;
  animation: Animation;
}

export const RENDER_TYPES: RenderType[] = ['TEXT', 'PIXEL', 'ITEM', 'BLOCK'];
export const SHAPES: Shape[] = ['POINT', 'SPHERE', 'CIRCLE', 'BOX'];
export const BILLBOARDS: Billboard[] = ['CENTER', 'FIXED', 'VERTICAL', 'HORIZONTAL'];
export const EASINGS: Easing[] = ['LINEAR', 'EASE_IN', 'EASE_OUT', 'EASE_IN_OUT', 'BOUNCE', 'ELASTIC'];

export function defaultRender(type: RenderType = 'PIXEL'): Render {
  return {
    type, text: type === 'TEXT' ? '★' : null, variants: [], frames: [], frameInterval: 4, framesOverLife: false, frameRandomStart: false, colors: [], items: type === 'ITEM' ? ['GOLD_NUGGET'] : [],
    blocks: type === 'BLOCK' ? ['minecraft:glowstone'] : [], billboard: type === 'BLOCK' ? 'FIXED' : 'CENTER',
    brightness: null, shadow: false, background: null, backgroundAlpha: null, glow: false, glowColor: null,
  };
}
export function defaultEmitter(): Emitter {
  return { shape: 'POINT', radius: 0.25, size: [0.5, 0.5, 0.5], surface: false, count: null, duration: 0, rate: 0 };
}
export function defaultMotion(): Motion {
  return { lifetime: [20, 30], speed: [0, 0], direction: [0, 1, 0], spread: 0, gravity: 0, drag: 0, collide: false, bounce: 0, friction: 0.6, attract: 0, attractAxis: false, wander: 0 };
}
export function defaultAnimation(): Animation {
  return { scale: [0.3, 0.3], endScale: 1, easing: 'LINEAR', fadeIn: 0, rotation: [0, 0], spin: [0, 0], color: null, opacity: null };
}

/** Новый эффект: белые квадраты разлетаются во все стороны и тают - сразу видно, что происходит. */
export function newEffect(id: string): ParticleEffect {
  const e: ParticleEffect = { id, viewers: 'ALL', keyframeTicks: 4, render: defaultRender('PIXEL'), emitter: defaultEmitter(), motion: defaultMotion(), animation: defaultAnimation() };
  e.render.colors = ['#ffffff'];
  e.emitter.shape = 'SPHERE'; e.emitter.radius = 0.2; e.emitter.count = 20;
  e.motion.speed = [0.05, 0.12]; e.motion.direction = 'OUTWARD'; e.motion.drag = 0.1;
  e.animation.scale = [0.1, 0.15]; e.animation.endScale = 0; e.animation.easing = 'EASE_IN';
  return e;
}

/** Фактическое число частиц во всплеске (как в плагине: без count - 1 для всплеска, 0 при duration). */
export function burstCount(e: Emitter): number {
  return e.count ?? (e.duration !== 0 ? 0 : 1);
}

export function cloneEffect(e: ParticleEffect): ParticleEffect {
  return JSON.parse(JSON.stringify(e));
}

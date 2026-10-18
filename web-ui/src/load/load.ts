// Примерная оценка нагрузки эффекта частиц или экрана: время сервера за тик, трафик на игрока и число
// сущностей у клиента. Числа - прикидки по устройству плагина (что он делает каждый тик и сколько пакетов
// шлёт), а не замеры: шкала нужна для ориентира «дёшево / заметно / тяжело» и сравнения вариантов.
import type { ParticleEffect } from '../particles/model';
import { burstCount } from '../particles/model';
import { ParticleRuntime } from '../particles/runtime';
import type { Screen, Widget } from '../model/screen';
import type { ScriptInfo } from '../jmp/scan';
import { clickWorks, hasHoverVisual } from '../lint';

/** Допущения: сколько игроков рядом / с открытым экраном и сколько запусков эффекта одновременно. */
export interface LoadAssumptions { players: number; launches: number }

export interface LoadPart {
  /** 0..1 - доля шкалы */
  score: number;
  value: string;
  detail: string;
}
export interface LoadFactor { label: string; share: number; tip?: string }
export interface LoadReport {
  /** 0..1 - заполнение шкалы (самое узкое место из трёх) */
  score: number;
  cpu: LoadPart & { usPerTick: number };
  net: LoadPart & { kbPerSec: number; packetsPerSec: number };
  client: LoadPart & { entities: number };
  /** Из чего складывается время сервера, по убыванию */
  factors: LoadFactor[];
  tips: string[];
  notes: string[];
}

// ---------------------------------------------------------------- шкалы

/** Бюджет тика сервера, мкс (20 TPS). */
export const TICK_BUDGET_US = 50_000;
/** Средний размер пакета сущности с заголовками, байт. */
const PACKET_BYTES = 48;

/** 0 при v <= lo, 1 при v >= hi, между - по логарифму (нагрузка растёт на порядки). */
export function logScore(v: number, lo: number, hi: number): number {
  if (!(v > lo)) return 0;
  if (v >= hi) return 1;
  return Math.log(v / lo) / Math.log(hi / lo);
}
const CPU_LO = 40, CPU_HI = 2500;          // мкс/тик: ~0.1% и 5% тика
const NET_LO = 4, NET_HI = 160;            // кБ/с на игрока: фон обычного сервера - десятки кБ/с
const fmtUs = (us: number) => (us >= 1000 ? (us / 1000).toFixed(1) + ' мс' : Math.round(us) + ' мкс');
const pct = (us: number) => {
  const p = (us / TICK_BUDGET_US) * 100;
  return p < 0.1 ? '<0.1%' : p < 10 ? p.toFixed(1) + '%' : Math.round(p) + '%';
};

function report(cpuUs: number, packetsPerSec: number, entities: number, clientLo: number, clientHi: number,
  terms: { label: string; us: number; tip?: string }[], tips: string[], notes: string[]): LoadReport {
  const kb = (packetsPerSec * PACKET_BYTES) / 1024;
  const cpu = logScore(cpuUs, CPU_LO, CPU_HI);
  const net = logScore(kb, NET_LO, NET_HI);
  const client = logScore(entities, clientLo, clientHi);
  const total = terms.reduce((a, t) => a + t.us, 0) || 1;
  return {
    score: Math.max(cpu, net, client),
    cpu: { score: cpu, usPerTick: cpuUs, value: fmtUs(cpuUs) + ' / тик', detail: pct(cpuUs) + ' тика сервера (50 мс)' },
    net: { score: net, kbPerSec: kb, packetsPerSec, value: (kb < 10 ? kb.toFixed(1) : Math.round(kb)) + ' кБ/с', detail: Math.round(packetsPerSec) + ' пакетов/с на игрока' },
    client: { score: client, entities, value: Math.round(entities) + ' сущн.', detail: 'одновременно у игрока' },
    factors: terms.filter((t) => t.us > 0).map((t) => ({ label: t.label, share: t.us / total, tip: t.tip })).sort((a, b) => b.share - a.share),
    tips, notes,
  };
}

// ---------------------------------------------------------------- частицы

/** Мкс на одну операцию - порядок величин для Paper на средней машине. */
const P = {
  entityTick: 1.0,      // тик Display-сущности в мире
  particleUpdate: 0.4,  // LiveParticle.update без отправки
  collide: 1.6,         // проверка блоков на пути (за тик жизни)
  tracker: 0.15,        // трекер сущности на одного зрителя
  spawn: 30,            // создание сущности, метаданные, добавление в мир
  packet: 1.2,          // кодирование и отправка пакета одному игроку
};

/**
 * Прогоняет эффект без отрисовки (тот же ParticleRuntime, что предпросмотр) и берёт самую нагруженную
 * секунду: сколько частиц живо, сколько рождается, сколько пакетов уходит каждому зрителю.
 */
export function particleLoad(e: ParticleEffect, a: LoadAssumptions): LoadReport {
  const rt = new ParticleRuntime(e, { center: [0, 1.5, 0], solid: (_x, y) => y < 0, seed: 7, maxAlive: 4000 });
  const forever = e.emitter.duration === -1;
  const maxTicks = forever ? 400 : 1200;
  const alive: number[] = [], packets: number[] = [], spawns: number[] = [];
  let prevPackets = rt.packetsTotal, prevSpawned = rt.spawned;
  // тик 0 - всплеск при запуске
  alive.push(rt.particles.length); packets.push(prevPackets); spawns.push(prevSpawned);
  for (let t = 0; t < maxTicks && !rt.done; t++) {
    rt.step();
    alive.push(rt.particles.length);
    packets.push(rt.packetsTotal - prevPackets); prevPackets = rt.packetsTotal;
    spawns.push(rt.spawned - prevSpawned); prevSpawned = rt.spawned;
  }
  // самая нагруженная секунда (20 тиков)
  const W = Math.min(20, alive.length);
  let best = { alive: 0, packets: 0, spawns: 0, cost: -1 };
  for (let i = 0; i + W <= alive.length; i++) {
    let al = 0, pk = 0, sp = 0;
    for (let j = i; j < i + W; j++) { al += alive[j]; pk += packets[j]; sp += spawns[j]; }
    const w = { alive: al / W, packets: pk / W, spawns: sp / W };
    const cost = w.alive * 2 + w.spawns * P.spawn + w.packets * P.packet * 3;
    if (cost > best.cost) best = { ...w, cost };
  }
  const peakAlive = Math.max(...alive);

  const L = Math.max(1, a.launches);
  const viewers = e.viewers === 'OWNER' ? 1 : Math.max(1, a.players);
  const collide = e.motion.collide;
  const terms = [
    { label: `Живые частицы (≈${Math.round(best.alive * L)})`, us: best.alive * (P.entityTick + P.particleUpdate) * L,
      tip: 'Меньше частиц одновременно: короче lifetime или меньше count/rate.' },
    { label: `Трекинг сущностей × ${viewers} игр.`, us: best.alive * P.tracker * viewers * L,
      tip: e.viewers === 'ALL' ? 'viewers: "owner" - эффект видит только тот, для кого запущен.' : undefined },
    { label: `Рождение (${(best.spawns * 20 * L).toFixed(best.spawns * 20 * L < 10 ? 1 : 0)}/с)`, us: best.spawns * P.spawn * L,
      tip: 'Каждая частица - новая сущность: создание дороже её жизни. Меньше rate, но крупнее частицы.' },
    { label: `Пакеты (${Math.round(best.packets * 20 * L)}/с × ${viewers} игр.)`, us: best.packets * P.packet * viewers * L },
    { label: 'Коллизии с блоками', us: collide ? best.alive * P.collide * L : 0, tip: 'collide: false, если частицы не долетают до блоков.' },
  ];
  const cpuUs = terms.reduce((s, t) => s + t.us, 0);
  const packetsPerSec = best.packets * 20 * L;

  const tips: string[] = [];
  // какие именно опции нагружают - считает particleState (маркеры у полей), здесь - только общее
  if (collide) tips.push('Коллизии считаются на сервере для каждой частицы каждый тик.');
  if (peakAlive * L > 600) tips.push('Сотни сущностей одновременно тяжелы и для клиента (FPS), не только для сервера.');
  if (burstCount(e.emitter) > 80) tips.push(`Всплеск из ${burstCount(e.emitter)} частиц создаёт их в один тик - возможен скачок.`);

  const notes = [
    'Прикидка по устройству плагина, не замер: для ориентира и сравнения вариантов.',
    `Самая нагруженная секунда эффекта${forever ? ' (бесконечный - установившийся режим)' : ''}; зрителей: ${viewers}${e.viewers === 'OWNER' ? ' (owner)' : ''}, запусков: ${L}.`,
  ];
  // для клиента частицы тяжелее экранных виджетов: много мелких прозрачных сущностей
  return report(cpuUs, packetsPerSec, peakAlive * L, 40, 1500, terms, tips, notes);
}

// ---------------------------------------------------------------- экраны

const S = {
  updateBase: 3,        // ScreenInstance.update: дистанция, служебное
  hover: 0.8,           // проверка наведения: виджет × игрок (луч + прямоугольник)
  entity: 0.3,          // Display-сущность виджета в мире
  tracker: 0.15,        // трекинг сущности на одного игрока
  frame: 0.3,           // тик кадровой анимации
  frameChange: 4,       // смена кадра: новый текст + метаданные
  packet: 1.2,
  scriptCall: 25,       // вызов функции скрипта Jumper из таймера
};

/** Таймеры скрипта: timer.every(N, ...) и timer.times(N, ...) - сколько вызовов за тик. */
export function scriptCallsPerTick(scriptText: string): { perTick: number; timers: number } {
  let perTick = 0, timers = 0;
  const re = /timer\s*\.\s*(every|times)\s*\(\s*([0-9.]+)/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(scriptText))) { const n = Number(m[2]); if (n > 0) { perTick += 1 / Math.max(1, n); timers++; } }
  return { perTick, timers };
}

/** Виджет обсчитывается на тиках (не статичный): как lintScreen / ScreenAnalyzer. */
export function isTicked(w: Widget, s: Screen, script: ScriptInfo | null, screenIds: string[]): boolean {
  if (w.tolerance[0] <= 0 || w.tolerance[1] <= 0) return false;
  return clickWorks(w, s, script, screenIds).ok || hasHoverVisual(w, s);
}

export function screenLoad(s: Screen, script: ScriptInfo | null, scriptText: string, screenIds: string[], a: LoadAssumptions): LoadReport {
  const pub = s.screenType === 'PUBLIC';
  const players = Math.max(1, a.players);
  const instances = pub ? 1 : players;          // приватный - у каждого игрока свой экземпляр
  const viewers = pub ? players : 1;            // кто видит один экземпляр
  const entities = s.widgets.length + (s.background ? 1 : 0);
  const ticked = s.widgets.filter((w) => isTicked(w, s, script, screenIds)).length;
  const animated = s.widgets.filter((w) => w.type === 'TEXT_BUTTON' && w.frames.length > 1);
  const frameChangesPerTick = animated.reduce((acc, w) => acc + 1 / Math.max(1, w.frameInterval), 0);
  const timers = scriptCallsPerTick(scriptText);
  const T = Math.max(1, s.tickRate);

  const terms = [
    { label: `Проверка наведения (${ticked} видж. × ${viewers} игр., раз в ${T} т)`, us: ((S.updateBase + ticked * viewers * S.hover) / T) * instances,
      tip: ticked ? 'Статичные виджеты (без клика и реакции на наведение) не обсчитываются - уберите лишние hover-эффекты.' : undefined },
    { label: `Сущности (${entities}${instances > 1 ? ' × ' + instances + ' экз.' : ''})`, us: entities * (S.entity + S.tracker * viewers) * instances },
    { label: `Кадровая анимация (${animated.length} видж.)`, us: (animated.length * S.frame + frameChangesPerTick * (S.frameChange + S.packet * viewers)) * instances,
      tip: animated.length ? 'Больше frameInterval - реже смена кадров и пакеты.' : undefined },
    { label: `Таймеры скрипта (${timers.timers})`, us: timers.perTick * S.scriptCall * instances,
      tip: timers.timers ? 'timer.every с маленьким интервалом вызывает скрипт очень часто.' : undefined },
  ];
  if (s.tickRate < 4 && ticked) terms[0].tip = `tickRate ${s.tickRate}: наведение проверяется ${s.tickRate === 1 ? 'каждый тик' : 'очень часто'}; 4 обычно незаметно.`;
  const cpuUs = terms.reduce((acc, t) => acc + t.us, 0);
  // пакеты одному игроку в установившемся режиме: смены кадров + (грубо) по пакету на вызов таймера
  const packetsPerSec = (frameChangesPerTick + timers.perTick) * 20;

  const tips: string[] = [];
  if (s.tickRate <= 1 && ticked) tips.push('tickRate 1 - наведение каждый тик; 3-5 обычно хватает.');
  if (entities > 40) tips.push(`${entities} сущностей на экран: при открытии игрок получает ≈${entities * 3} пакетов разом.`);
  const fastFrames = animated.filter((w) => w.frameInterval <= 2).length;
  if (fastFrames) tips.push(`${fastFrames} виджет(а) меняют кадр чаще 10 раз в секунду.`);
  if (pub && players > 1) tips.push('Публичный экран: наведение считается для каждого игрока рядом.');
  const notes = [
    'Прикидка по устройству плагина, не замер: для ориентира и сравнения вариантов.',
    pub ? `Публичный экран, игроков рядом: ${players}.` : `Приватный экран открыт у ${players} игр. (у каждого свой экземпляр).`,
    `При открытии: ≈${entities * 3} пакетов (появление сущностей).`,
  ];
  return report(cpuUs, packetsPerSec, entities, 15, 300, terms, tips, notes);
}

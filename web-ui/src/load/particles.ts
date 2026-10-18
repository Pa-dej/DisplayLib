// Нагрузка эффекта частиц: прогон без отрисовки. Без зависимостей от DOM - работает и в Web Worker.
import type { ParticleEffect } from '../particles/model';
import { burstCount } from '../particles/model';
import { ParticleRuntime } from '../particles/runtime';
import { report, type LoadAssumptions, type LoadReport } from './score';

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


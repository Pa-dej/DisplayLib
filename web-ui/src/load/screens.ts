// Нагрузка экрана: что обсчитывается каждый тик (как ScreenAnalyzer / UIManager).
import type { Screen, Widget } from '../model/screen';
import type { ScriptInfo } from '../jmp/scan';
import { clickWorks, hasHoverVisual } from '../lint';
import { report, type LoadAssumptions, type LoadReport } from './score';

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

// Примерная оценка нагрузки эффекта частиц или экрана: время сервера за тик, трафик на игрока и число
// сущностей у клиента. Числа - прикидки по устройству плагина (что он делает каждый тик и сколько пакетов
// шлёт), а не замеры: шкала нужна для ориентира «дёшево / заметно / тяжело» и сравнения вариантов.

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

export function report(cpuUs: number, packetsPerSec: number, entities: number, clientLo: number, clientHi: number,
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


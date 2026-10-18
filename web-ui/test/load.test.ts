import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { parseJmc, type JmcTable } from '../src/jmc/parser';
import { tableToEffect } from '../src/particles/io';
import { tableToScreen } from '../src/jmc/toModel';
import { scanScript } from '../src/jmp/scan';
import { particleLoad, screenLoad, logScore, scriptCallsPerTick } from '../src/load/load';

const EX = join(__dirname, '../../plugin/src/main/resources/examples');
const effect = (f: string) => tableToEffect(parseJmc(readFileSync(join(EX, 'particles', f), 'utf8')) as JmcTable, f.replace('.jmc', '')).effect;

describe('оценка нагрузки', () => {
  it('шкала', () => {
    expect(logScore(10, 40, 2500)).toBe(0);
    expect(logScore(5000, 40, 2500)).toBe(1);
    expect(logScore(316, 40, 2500)).toBeGreaterThan(0.4);
  });
  it('частицы: примеры, больше игроков и запусков - тяжелее', () => {
    const rows: string[] = [];
    for (const f of readdirSync(join(EX, 'particles'))) {
      const e = effect(f);
      const a = particleLoad(e, { players: 1, launches: 1 });
      const b = particleLoad(e, { players: 10, launches: 1 });
      const c = particleLoad(e, { players: 10, launches: 5 });
      expect(b.score).toBeGreaterThanOrEqual(a.score);
      expect(c.score).toBeGreaterThanOrEqual(b.score);
      expect(c.cpu.usPerTick).toBeGreaterThan(b.cpu.usPerTick);
      rows.push(`${f.padEnd(18)} 1p ${a.score.toFixed(2)} | 10p ${b.score.toFixed(2)} cpu ${b.cpu.value} net ${b.net.value} ent ${b.client.entities} | 10p×5 ${c.score.toFixed(2)}`);
    }
    console.log(rows.join('\n'));
  });
  it('коллизии и своя кривая дороже', () => {
    const e = effect('confetti.jmc');
    const off = structuredClone(e); off.motion.collide = false;
    expect(particleLoad(e, { players: 5, launches: 1 }).cpu.usPerTick).toBeGreaterThan(particleLoad(off, { players: 5, launches: 1 }).cpu.usPerTick);
    const lin = structuredClone(effect('sparkle.jmc')); lin.animation.easing = 'LINEAR';
    const eased = structuredClone(lin); eased.animation.easing = 'EASE_IN';
    expect(particleLoad(eased, { players: 5, launches: 1 }).net.packetsPerSec).toBeGreaterThan(particleLoad(lin, { players: 5, launches: 1 }).net.packetsPerSec);
  });
  it('экраны: примеры; приватный у многих игроков тяжелее', () => {
    const rows: string[] = [];
    for (const f of readdirSync(join(EX, 'screens'))) {
      const s = tableToScreen(parseJmc(readFileSync(join(EX, 'screens', f), 'utf8')) as JmcTable, f).screen;
      const sp = join(EX, 'scripts', (s.script || s.id + '.jmp'));
      const text = existsSync(sp) ? readFileSync(sp, 'utf8') : '';
      const one = screenLoad(s, scanScript(text), text, [s.id], { players: 1, launches: 1 });
      const many = screenLoad(s, scanScript(text), text, [s.id], { players: 50, launches: 1 });
      expect(many.cpu.usPerTick).toBeGreaterThan(one.cpu.usPerTick);
      rows.push(`${f.padEnd(18)} 1p ${one.score.toFixed(2)} cpu ${one.cpu.value} | 50p ${many.score.toFixed(2)} cpu ${many.cpu.value} net ${many.net.value} ent ${many.client.entities}`);
    }
    console.log(rows.join('\n'));
  });
  it('таймеры скрипта', () => {
    expect(scriptCallsPerTick('timer.every(20, () -> x()); timer.times(5, 3, (i) -> y());')).toEqual({ perTick: 0.25, timers: 2 });
  });
});

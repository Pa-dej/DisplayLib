import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { parseJmc, type JmcTable } from '../src/jmc/parser';
import { tableToEffect, effectToJmc, validateEffect } from '../src/particles/io';
import { newEffect } from '../src/particles/model';

const DIR = join(__dirname, '../../plugin/src/main/resources/examples/particles');
const files = readdirSync(DIR).filter((f) => f.endsWith('.jmc'));

describe('эффекты частиц: .jmc туда и обратно', () => {
  it('примеры плагина на месте', () => expect(files.length).toBeGreaterThanOrEqual(8));
  for (const f of files) {
    it(f, () => {
      const src = readFileSync(join(DIR, f), 'utf8');
      const a = tableToEffect(parseJmc(src) as JmcTable, f.replace('.jmc', ''));
      expect(a.errors).toEqual([]);
      expect(validateEffect(a.effect)).toEqual([]);
      const text = effectToJmc(a.effect);
      const b = tableToEffect(parseJmc(text) as JmcTable, 'x');
      expect(b.errors).toEqual([]);
      expect(b.effect).toEqual(a.effect);
    });
  }
  it('новый эффект пишется и читается', () => {
    const e = newEffect('test');
    const b = tableToEffect(parseJmc(effectToJmc(e)) as JmcTable, 'x');
    expect(b.errors).toEqual([]);
    expect(b.effect).toEqual(e);
  });
  it('ошибки как в плагине', () => {
    const r = tableToEffect(parseJmc('dyn render = { type: "SPARK" }; dyn emitter = { duration: 20 };') as JmcTable, 'x');
    expect(r.errors.join('\n')).toMatch(/render.type/);
    expect(r.errors.join('\n')).toMatch(/rate/);
    expect(tableToEffect(parseJmc('int x = 1;') as JmcTable, 'x').errors.join()).toMatch(/render/);
  });
  it('цвет [r, g, b, a] и синонимы', () => {
    const r = tableToEffect(parseJmc('dyn render = { type: "PIXEL", colors: [10, 20, 30, 64] }; dyn motion = { life: 5 };') as JmcTable, 'x');
    expect(r.effect.render.colors).toEqual(['#0a141e40']);
    expect(r.effect.motion.lifetime).toEqual([5, 5]);
  });
});

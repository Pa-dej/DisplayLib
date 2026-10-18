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

describe('кадры текста', () => {
  it('читаются, пишутся и считаются как в плагине', async () => {
    const { frameAt } = await import('../src/particles/sim');
    const src = 'dyn render = { type: "TEXT", frames: ["a", [{ text: "b", color: "red" }], "c"], frameInterval: 2, frameStart: "random" };';
    const a = tableToEffect(parseJmc(src) as JmcTable, 't');
    expect(a.errors).toEqual([]);
    expect(a.effect.render.frames.length).toBe(3);
    expect(a.effect.render.frameRandomStart).toBe(true);
    const b = tableToEffect(parseJmc(effectToJmc(a.effect)) as JmcTable, 't');
    expect(b.effect.render).toEqual(a.effect.render);
    const life = tableToEffect(parseJmc('dyn render = { type: "TEXT", frames: ["a", "b"], framesOverLife: true, frameInterval: 3 };') as JmcTable, 't');
    expect(tableToEffect(parseJmc(effectToJmc(life.effect)) as JmcTable, 't').effect.render).toEqual(life.effect.render);
    // те же значения, что ParticleEffectTest.textFrames
    expect([frameAt(0, 3, 2, false, 0, 30), frameAt(0, 3, 2, false, 1, 30), frameAt(0, 3, 2, false, 2, 30), frameAt(0, 3, 2, false, 6, 30), frameAt(1, 3, 2, false, 2, 30)]).toEqual([0, 0, 1, 0, 2]);
    expect([frameAt(0, 4, 1, true, 0, 40), frameAt(0, 4, 1, true, 10, 40), frameAt(0, 4, 1, true, 40, 40)]).toEqual([0, 1, 3]);
    const one = tableToEffect(parseJmc('dyn render = { type: "TEXT", frames: ["a"] };') as JmcTable, 't').effect;
    expect(one.render.frames).toEqual([]);
    expect(one.render.text).toBe('a');
  });
  it('смена кадра - пакет, предпросмотр видит кадр', async () => {
    const { ParticleRuntime } = await import('../src/particles/runtime');
    const mk = (fi: number) => tableToEffect(parseJmc(`dyn render = { type: "TEXT", frames: ["a", "b", "c", "d"], frameInterval: ${fi} }; dyn emitter = { count: 5 }; dyn motion = { lifetime: 40 };`) as JmcTable, 't').effect;
    const run = (fi: number) => { const rt = new ParticleRuntime(mk(fi), { center: [0, 0, 0], solid: null, seed: 1 }); while (!rt.done) rt.step(); return rt.packetsTotal; };
    expect(run(2)).toBeGreaterThan(run(8));
    const rt = new ParticleRuntime(mk(2), { center: [0, 0, 0], solid: null, seed: 1 });
    for (let i = 0; i < 6; i++) rt.step();
    const p = rt.particles[0];
    expect(rt.frameShown(p, rt.tick)).toBe(rt.frameExact(p, rt.tick - 1 + 1) >= 0 ? p.frame : -1);
    expect(p.frame).toBe(2); // видимый возраст 5 -> 5/2 = 2
  });
});

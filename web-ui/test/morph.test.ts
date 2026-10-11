import { describe, it, expect } from 'vitest';
import { morphText, planMorph } from '../src/render/morph';
import { defaultScreen, defaultWidget } from '../src/model/screen';

describe('morphText (как MorphTransition плагина)', () => {
  it('общий префикс/суффикс остаются, середина меняется', () => {
    expect(morphText('text', 'example text', 0)).toBe('text');
    expect(morphText('text', 'example text', 1)).toBe('example text');
    expect(morphText('abcXYZdef', 'abc12def', 0.5)).toBe('abc1Zdef'); // половина новой середины + остаток старой
    expect(morphText('same', 'same', 0.3)).toBe('same');
  });
  it('монотонно: длина растёт/убывает без скачков', () => {
    let prev = morphText('Магазин', 'Настройки', 0);
    for (let i = 1; i <= 10; i++) { const cur = morphText('Магазин', 'Настройки', i / 10); expect(Math.abs(cur.length - prev.length)).toBeLessThanOrEqual(3); prev = cur; }
  });
});

describe('planMorph', () => {
  it('matched по id и классу сущности, остальное fade', () => {
    const a = defaultScreen('a'), b = defaultScreen('b');
    const t1 = defaultWidget('TEXT_BUTTON'); t1.id = 'x';
    const s1 = defaultWidget('SPRITE_BUTTON'); s1.id = 'x';     // текст ↔ спрайт - одна сущность
    const i1 = defaultWidget('ITEM_BUTTON'); i1.id = 'y';
    const t2 = defaultWidget('TEXT_BUTTON'); t2.id = 'y';        // предмет ↔ текст - нет
    const only = defaultWidget('TEXT_BUTTON'); only.id = 'z';
    a.widgets = [t1, i1]; b.widgets = [s1, t2, only];
    b.morph.duration = 8; b.morph.steps = -1;
    const p = planMorph(a, b);
    expect(p.matched.map((m) => m.from.id)).toEqual(['x']);
    expect(p.fadeOut.map((w) => w.id)).toEqual(['y']);
    expect(p.fadeIn.map((w) => w.id)).toEqual(['y', 'z']);
    expect(p.ms).toBe(400); expect(p.steps).toBe(4);
  });
});

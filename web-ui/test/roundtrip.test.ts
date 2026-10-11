// Разбор → модель → запись → разбор: все примеры плагина должны проходить без потерь.
import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { parseJmc } from '../src/jmc/parser';
import { tableToScreen } from '../src/jmc/toModel';
import { screenToJmc } from '../src/jmc/writer';
import type { JmcTable } from '../src/jmc/parser';

const DIR = new URL('../../plugin/src/main/resources/examples/screens/', import.meta.url).pathname;
const files = readdirSync(DIR).filter((f) => f.endsWith('.jmc'));

function strip(s: ReturnType<typeof tableToScreen>['screen']) {
  // uid - внутренний ключ редактора, меняется при каждом разборе
  return JSON.parse(JSON.stringify(s, (k, v) => (k === 'uid' ? undefined : v)));
}

describe('round-trip примеров', () => {
  it.each(files)('%s', (f) => {
    const text = readFileSync(DIR + f, 'utf8');
    const a = tableToScreen(parseJmc(text) as JmcTable, f);
    expect(a.warnings).toEqual([]);
    const written = screenToJmc(a.screen);
    const b = tableToScreen(parseJmc(written) as JmcTable, f);
    expect(b.warnings).toEqual([]);
    expect(strip(b.screen)).toEqual(strip(a.screen));
    // повторная запись стабильна
    expect(screenToJmc(b.screen)).toBe(written);
  });
});

describe('поля виджета', () => {
  it('rotation, lineWidth, spriteColor, звуки читаются и пишутся', () => {
    const src = `String id = "t";
dyn widgets = [
  { id: "a", text: "x", rotation: 15, lineWidth: 120, clickSound: false, hoverSound: { sound: "ui.button.click", volume: 0.3, pitch: 1.5 } },
  { id: "b", atlas: "minecraft:gui", sprite: "hud/heart/full", spriteColor: "red", hoveredSpriteColor: "#ff9999" },
  { id: "c", text: [ { text: "hp " }, { atlas: "minecraft:gui", sprite: "hud/heart/full", color: "red" } ] },
];`;
    const { screen, warnings } = tableToScreen(parseJmc(src) as JmcTable, 't.jmc');
    expect(warnings).toEqual([]);
    const [a, b, c] = screen.widgets;
    expect(a.rotation).toBe(15); expect(a.lineWidth).toBe(120); expect(a.clickSound).toBe('none');
    expect(a.hoverSound).toEqual({ sound: 'ui.button.click', volume: 0.3, pitch: 1.5 });
    expect(b.type).toBe('SPRITE_BUTTON'); expect(b.spriteColor).toBe('red'); expect(b.hoveredSpriteColor).toBe('#ff9999');
    expect(typeof c.text !== 'string' && c.text[1].color).toBe('red');
    const out = screenToJmc(screen);
    for (const k of ['rotation: 15', 'lineWidth: 120', 'clickSound: false', 'hoverSound: { sound: "ui.button.click", volume: 0.3, pitch: 1.5 }', 'spriteColor: "red"', 'hoveredSpriteColor: "#ff9999"', 'color: "red"']) expect(out).toContain(k);
  });
});

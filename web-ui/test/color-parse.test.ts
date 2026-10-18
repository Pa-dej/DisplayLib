import { describe, it, expect } from 'vitest';
import { parseAnyColor, rgbaToHex, rgbToHsv, hsvToRgb, rgbToHsl, hslToRgb } from '../src/color/parse';

const hex = (s: string) => { const c = parseAnyColor(s); return c ? rgbaToHex(c) : null; };

describe('разбор цвета из буфера', () => {
  it.each([
    ['#ff8800', '#ff8800'], ['#F80', '#ff8800'], ['#f808', '#ff880088'], ['#ff880080', '#ff880080'],
    ['ff8800', '#ff8800'], ['0xFF8800', '#ff8800'], ['abc', '#aabbcc'],
    ['rgb(255, 136, 0)', '#ff8800'], ['rgba(255,136,0,0.5)', '#ff880080'], ['rgb(255 136 0 / 50%)', '#ff880080'],
    ['rgb(100%, 0%, 0%)', '#ff0000'],
    ['hsl(32, 100%, 50%)', '#ff8800'], ['hsl(32deg 100% 50%)', '#ff8800'], ['hsla(0, 100%, 50%, .5)', '#ff000080'],
    ['hsl(0.5turn, 100%, 50%)', '#00ffff'], ['hsv(120, 100%, 100%)', '#00ff00'], ['hsb(240, 1, 1)', '#0000ff'],
    ['255, 136, 0', '#ff8800'], ['255 136 0', '#ff8800'], ['[255, 136, 0, 128]', '#ff880080'], ['255, 136, 0, 0.5', '#ff880080'],
    ['1.0, 0.5, 0.0', '#ff8000'], ['{r: 255, g: 136, b: 0}', '#ff8800'],
    ['gold', '#ffaa00'], ['dark_red', '#aa0000'], ['Dark Red', '#aa0000'], ['§c', '#ff5555'], ['&6', '#ffaa00'],
    ['  "#ff8800";', '#ff8800'],
  ])('%s', (src, want) => expect(hex(src)).toBe(want));

  it.each(['', 'hello world', '255', '#12345', 'rgb(1,2)', '1/2/3/4'])('не цвет: %s', (s) => expect(parseAnyColor(s)).toBeNull());

  it('HSV/HSL туда и обратно', () => {
    for (const c of [{ r: 255, g: 136, b: 0 }, { r: 12, g: 200, b: 99 }, { r: 0, g: 0, b: 0 }, { r: 255, g: 255, b: 255 }, { r: 70, g: 70, b: 200 }]) {
      expect(hsvToRgb(rgbToHsv(c))).toEqual(c);
      expect(hslToRgb(rgbToHsl(c))).toEqual(c);
    }
  });
});

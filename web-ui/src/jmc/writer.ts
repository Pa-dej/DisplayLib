// Screen -> текст .jmc в том виде, в каком пишут примеры плагина: переменные верхнего уровня,
// camelCase, сокращённые формы onClick, поля со значениями по умолчанию опускаются.
import {
  type HoverAnimation, type RichText, type Screen, type Widget, type Vec3, WIDGET_DEFAULTS, anchorPresetName, defaultHoverAnimation,
} from '../model/screen';

function jstr(s: string): string {
  return '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t') + '"';
}
function jnum(n: number): string {
  if (!Number.isFinite(n)) return '0';
  if (Number.isInteger(n)) return String(n);
  // до 4 знаков, без хвостовых нулей
  return String(Math.round(n * 10000) / 10000);
}
function arr(a: readonly number[]): string { return '[' + a.map(jnum).join(', ') + ']'; }
function same(a: readonly number[], b: readonly number[]): boolean { return a.length === b.length && a.every((v, i) => Math.abs(v - b[i]) < 1e-9); }
function richText(t: RichText): string {
  if (typeof t === 'string') return jstr(t);
  return '[ ' + t.map((s) => s.sprite !== undefined
    ? '{ ' + (atlasFor(s.atlas, s.sprite) ? 'atlas: ' + jstr(atlasFor(s.atlas, s.sprite)!) + ', ' : '') + 'sprite: ' + jstr(spriteFor(s.atlas, s.sprite)) + (s.color ? ', color: ' + jstr(s.color) : '') + ' }'
    : '{ text: ' + jstr(s.text) + (s.color ? ', color: ' + jstr(s.color) : '') + ' }').join(', ') + ' ]';
}

/** Атлас для записи: заданный, иначе выведенный из имени (item/… → items, block/… → blocks); null - не писать. */
function atlasFor(atlas: string | null | undefined, sprite: string): string | null {
  if (atlas && atlas.trim()) return atlas.trim() === 'minecraft:mob_effects' || atlas.trim() === 'mob_effects' ? 'minecraft:gui' : atlas.trim();
  const k = sprite.trim().replace(/^minecraft:/, '');
  if (k.startsWith('item/')) return 'minecraft:items';
  if (k.startsWith('block/')) return 'minecraft:blocks';
  return null;
}

/** Имя спрайта для записи: для бывшего атласа mob_effects добавляем префикс mob_effect/. */
function spriteFor(atlas: string | null | undefined, sprite: string): string {
  const a = (atlas ?? '').trim();
  if ((a === 'minecraft:mob_effects' || a === 'mob_effects') && !sprite.startsWith('mob_effect/')) return 'mob_effect/' + sprite.replace(/^minecraft:/, '');
  return sprite;
}

const IDENT = /^[A-Za-z_][A-Za-z0-9_]*$/;
function key(k: string): string { return IDENT.test(k) ? k : jstr(k); }

export interface WriteOptions {
  /** Писать все поля виджета, даже совпадающие со значениями по умолчанию. */
  verbose?: boolean;
}

export function screenToJmc(s: Screen, opts: WriteOptions = {}): string {
  const L: string[] = [];
  L.push(`// ${s.id}: экран DisplayLib (конфиг Jumper, .jmc)`);
  L.push('');
  L.push(`String id = ${jstr(s.id)};`);
  L.push(`String screenType = ${jstr(s.screenType)};`);
  L.push(`int tickRate = ${s.tickRate};`);
  if (s.interactionRadius !== -1 || opts.verbose) L.push(`double interactionRadius = ${jnum(s.interactionRadius)};`);
  if (s.closeDistance !== -1 || opts.verbose) L.push(`double closeDistance = ${jnum(s.closeDistance)};`);
  if (s.rangeCheckInterval !== 10 || opts.verbose) L.push(`int rangeCheckInterval = ${s.rangeCheckInterval};`);
  if (s.morph.enabled || opts.verbose) {
    const f = [`enabled: ${s.morph.enabled}`, `duration: ${s.morph.duration}`];
    if (s.morph.steps > 0 || opts.verbose) f.push(`steps: ${s.morph.steps > 0 ? s.morph.steps : Math.max(1, Math.floor(s.morph.duration / 2))}`);
    L.push(`dyn morph = { ${f.join(', ')} };`);
  }
  if (s.background) {
    const b = s.background;
    const f: string[] = [`color: ${arr(b.color)}`, `alpha: ${b.alpha}`, `scale: ${arr(b.scale)}`];
    if (!same(b.position, [0, 0, 0]) || opts.verbose) f.push(`position: ${arr(b.position)}`);
    if (!same(b.translation, [0, 0, 0]) || opts.verbose) f.push(`translation: ${arr(b.translation)}`);
    if (!same(b.anchor, [0.5, 0]) || opts.verbose) { const p = anchorPresetName(b.anchor); f.push(`anchor: ${p ? jstr(p) : arr(b.anchor)}`); }
    if (b.text !== ' ' || opts.verbose) f.push(`text: ${jstr(b.text)}`);
    L.push('');
    L.push(`dyn background = { ${f.join(', ')} };`);
  }
  // скрипт: заданный, иначе <id>.jmp - если на экране есть обработчики (у каждого экрана скрипт лежит рядом с разметкой)
  const script = s.script || (s.widgets.some((w) => w.onClick?.action === 'RUN_SCRIPT' && w.onClick.function) ? s.id + '.jmp' : null);
  if (script) { L.push(''); L.push(`String script = ${jstr(script)};`); }
  L.push('');
  L.push('dyn widgets = [');
  for (const w of s.widgets) L.push(widgetToJmc(w, opts));
  L.push('];');
  L.push('');
  return L.join('\n');
}

export function widgetToJmc(w: Widget, opts: WriteOptions): string {
  const f: string[] = [];
  const v = !!opts.verbose;
  if (w.id) f.push(`id: ${jstr(w.id)}`);
  f.push(`type: ${jstr(w.type)}`);
  if (w.type === 'TEXT_BUTTON') {
    f.push(`text: ${richText(w.text)}`);
    if (w.hoveredText != null) f.push(`hoveredText: ${richText(w.hoveredText)}`);
    if (w.frames.length) { f.push(`frames: [ ${w.frames.map(richText).join(', ')} ]`); f.push(`frameInterval: ${w.frameInterval}`); }
    if (w.hoveredFrames.length) { f.push(`hoveredFrames: [ ${w.hoveredFrames.map(richText).join(', ')} ]`); if (w.hoveredFrameInterval > 0 || v) f.push(`hoveredFrameInterval: ${w.hoveredFrameInterval > 0 ? w.hoveredFrameInterval : w.frameInterval}`); }
  } else if (w.type === 'SPRITE_BUTTON') {
    const at = atlasFor(w.atlas, w.sprite); if (at) f.push(`atlas: ${jstr(at)}`);
    f.push(`sprite: ${jstr(spriteFor(w.atlas, w.sprite))}`);
    if (w.hoveredSprite) f.push(`hoveredSprite: ${jstr(w.hoveredSprite)}`);
    if (w.spriteColor) f.push(`spriteColor: ${jstr(w.spriteColor)}`);
    if (w.hoveredSpriteColor) f.push(`hoveredSpriteColor: ${jstr(w.hoveredSpriteColor)}`);
  } else {
    f.push(`material: ${jstr(w.material)}`);
  }
  for (const [k, sv] of [['clickSound', w.clickSound], ['hoverSound', w.hoverSound]] as const) {
    if (sv === 'none') f.push(`${k}: false`);
    else if (sv) f.push(`${k}: { sound: ${jstr(sv.sound)}, volume: ${jnum(sv.volume)}, pitch: ${jnum(sv.pitch)} }`);
  }
  f.push(`position: ${arr(w.position)}`);
  f.push(`scale: ${arr(w.scale)}`);
  if (v || !same(w.tolerance, WIDGET_DEFAULTS.tolerance)) f.push(`tolerance: ${arr(w.tolerance)}`);
  if (v || !same(w.translation, WIDGET_DEFAULTS.translation)) f.push(`translation: ${arr(w.translation)}`);
  if (v || w.rotation) f.push(`rotation: ${jnum(w.rotation)}`);
  if (w.type !== 'ITEM_BUTTON') {
    if (w.type === 'TEXT_BUTTON' && (v || w.alignment !== 'CENTERED')) f.push(`alignment: ${jstr(w.alignment)}`);
    if (v || !same(w.backgroundColor, WIDGET_DEFAULTS.backgroundColor) || w.backgroundAlpha !== 0) f.push(`backgroundColor: ${arr(w.backgroundColor)}`);
    if (v || w.backgroundAlpha !== 0) f.push(`backgroundAlpha: ${w.backgroundAlpha}`);
    if (v || !same(w.hoveredBackgroundColor, WIDGET_DEFAULTS.hoveredBackgroundColor) || w.hoveredBackgroundAlpha !== 0) f.push(`hoveredBackgroundColor: ${arr(w.hoveredBackgroundColor)}`);
    if (v || w.hoveredBackgroundAlpha !== 0) f.push(`hoveredBackgroundAlpha: ${w.hoveredBackgroundAlpha}`);
  } else {
    if (v || !w.glowOnHover) f.push(`glowOnHover: ${w.glowOnHover}`);
    if (w.glowColor) f.push(`glowColor: ${arr(w.glowColor)}`);
  }
  if (w.tooltip != null) {
    f.push(`tooltip: ${richText(w.tooltip)}`);
    if (w.tooltipColor) f.push(`tooltipColor: ${arr(w.tooltipColor)}`);
    if (v || w.tooltipDelay !== 0) f.push(`tooltipDelay: ${w.tooltipDelay}`);
  }
  if (w.hoverAnimation) f.push(`hoverAnimation: ${animToJmc(w.hoverAnimation, v)}`);
  if (w.onClick && w.onClick.action !== 'NONE') f.push(`onClick: ${clickToJmc(w)}`);
  return '    {\n' + f.map((x) => '        ' + x + ',').join('\n') + '\n    },';
}

// onClick: true - функция названа как id виджета (без строки); иначе табличная форма { function: "buy" }
function clickToJmc(w: Widget): string {
  const c = w.onClick!;
  switch (c.action) {
    case 'CLOSE_SCREEN': return '{ close: true }';
    case 'SWITCH_SCREEN': return `{ switchTo: ${jstr(c.target ?? '')} }`;
    case 'RUN_SCRIPT': return c.function && c.function === w.id ? 'true' : `{ function: ${jstr(c.function ?? '')} }`;
    default: return '{ action: "NONE" }';
  }
}

function animToJmc(a: HoverAnimation, verbose: boolean): string {
  const d = defaultHoverAnimation();
  const f: string[] = [`type: ${jstr(a.type)}`];
  if (a.type === 'PRESET') { f.push(`preset: ${jstr(a.preset)}`); if (verbose || a.intensity !== d.intensity) f.push(`intensity: ${jnum(a.intensity)}`); }
  if (a.type === 'SCALE') f.push(`scale: ${arr(a.scale)}`);
  if (a.type === 'TRANSLATE') f.push(`offset: ${arr(a.offset)}`);
  if (a.type === 'ROTATE') { f.push(`rotation: ${arr(a.rotation)}`); f.push(`axis: ${arr(a.axis)}`); }
  if (a.type === 'TRANSFORM') { f.push(`translation: ${arr(a.translation)}`); f.push(`scale: ${arr(a.scale)}`); }
  if (verbose || a.duration !== d.duration) f.push(`duration: ${a.duration}`);
  if (verbose || a.easing !== d.easing) f.push(`easing: ${jstr(a.easing)}`);
  if (verbose || a.reverseOnExit !== d.reverseOnExit) f.push(`reverseOnExit: ${a.reverseOnExit}`);
  if (verbose || a.delay !== 0) f.push(`delay: ${a.delay}`);
  if (verbose || a.loop) f.push(`loop: ${a.loop}`);
  if (a.loop && (verbose || a.loopCount !== -1)) f.push(`loopCount: ${a.loopCount}`);
  if (a.type === 'COMBINED') f.push(`effects: [ ${a.effects.map((e) => animToJmc(e, verbose)).join(', ')} ]`);
  return '{ ' + f.join(', ') + ' }';
}

export { key as jmcKey };

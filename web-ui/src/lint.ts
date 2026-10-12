// Проверки экрана «как это увидит плагин»: дубли id, битые ссылки клика, пересечение зон наведения,
// виджеты за фоном, статичные (не тикаемые) виджеты по правилам ScreenAnalyzer. Чисто, без сцены:
// размеры текста - по той же раскладке шрифта, что и предпросмотр.
import type { Screen, Widget } from './model/screen';
import type { ScriptInfo } from './jmp/scan';
import { layout } from './render/text';
import { widgetText } from './render/scene';
import { calibration } from './render/metrics';

export type Level = 'error' | 'warn' | 'info';
export interface Issue { level: Level; text: string; uid?: string }

interface Rect { l: number; r: number; b: number; t: number }
const overlaps = (a: Rect, b: Rect) => a.l < b.r && b.l < a.r && a.b < b.t && b.b < a.t;
const inside = (a: Rect, b: Rect) => a.l >= b.l && a.r <= b.r && a.b >= b.b && a.t <= b.t;

/** Видимый прямоугольник виджета в координатах экрана (x вправо, y вверх), как в сцене. */
export function visualRect(w: Widget): Rect {
  const tx = w.position[0] + w.translation[0], ty = w.position[1] + w.translation[1];
  if (w.type === 'ITEM_BUTTON') {
    const hw = (calibration.itemSize * w.scale[0]) / 2, hh = (calibration.itemSize * w.scale[1]) / 2;
    return { l: tx - hw, r: tx + hw, b: ty - hh, t: ty + hh };
  }
  const L = layout(widgetText(w), w.lineWidth);
  const px = calibration.px;
  const wb = L.bgW * px * w.scale[0], hb = L.bgH * px * w.scale[1];
  if (w.backgroundAlpha < 26) {
    // фон не рисуется - считаем по глифам, как сцена
    const l = tx - wb / 2 + calibration.bgLeft * px * w.scale[0];
    return { l, r: l + L.width * px * w.scale[0], b: ty + hb - (calibration.bgTop + L.height) * px * w.scale[1], t: ty + hb - calibration.bgTop * px * w.scale[1] };
  }
  return { l: tx - wb / 2, r: tx + wb / 2, b: ty, t: ty + hb }; // якорь - низ по центру
}

/** Зона наведения (tolerance вокруг центра: у текста - центр нарисованного текста). */
export function hitRect(w: Widget): Rect {
  let tx = w.position[0] + w.translation[0];
  let ty = w.position[1] + w.translation[1];
  if (w.type !== 'ITEM_BUTTON') {
    const L = layout(widgetText(w), w.lineWidth);
    const objectOnly = L.lines.length === 1 && L.lines[0].length > 0 && L.lines[0].every((r) => !!r.sprite);
    tx += calibration.px * w.scale[0];
    ty += (L.bgH / 2 - (objectOnly ? 0 : 1)) * calibration.px * w.scale[1];
  }
  return { l: tx - w.tolerance[0], r: tx + w.tolerance[0], b: ty - w.tolerance[1], t: ty + w.tolerance[1] };
}

export function backgroundRect(s: Screen): Rect | null {
  const b = s.background; if (!b) return null;
  const L = layout(b.text);
  const W = L.bgW * calibration.px * b.scale[0], H = L.bgH * calibration.px * b.scale[1];
  const x = b.position[0] + b.translation[0] - b.anchor[0] * W, y = b.position[1] + b.translation[1] - b.anchor[1] * H;
  return { l: x, r: x + W, b: y, t: y + H };
}

/** Будет ли клик что-то делать (ScreenSupport.clickDoesSomething). */
export function clickWorks(w: Widget, s: Screen, script: ScriptInfo | null, screenIds: string[]): { ok: boolean; why?: string } {
  const oc = w.onClick; const pub = s.screenType === 'PUBLIC';
  if (!oc || oc.action === 'NONE') return { ok: false };
  if (oc.action === 'SWITCH_SCREEN') {
    if (pub) return { ok: false, why: 'switchTo на публичном экране не работает' };
    if (!oc.target) return { ok: false, why: 'switchTo без экрана' };
    return { ok: true };
  }
  if (oc.action === 'CLOSE_SCREEN') return pub ? { ok: false, why: 'close на публичном экране не работает' } : { ok: true };
  const fn = oc.function || w.id;
  if (!fn) return { ok: false, why: 'onClick без функции и без id' };
  if (script && !script.imports.length && !script.functions.includes(fn)) return { ok: false, why: `функции ${fn}() нет в скрипте` };
  if (script && script.emptyFunctions.includes(fn)) return { ok: false, why: `функция ${fn}() пустая` };
  return { ok: true };
}

/** Есть ли видимая реакция на наведение (ScreenSupport.hasHoverVisual). */
export function hasHoverVisual(w: Widget, s: Screen): boolean {
  const pub = s.screenType === 'PUBLIC';
  if (w.tooltip != null) return true;
  if (w.hoverSound && w.hoverSound !== 'none') return true;
  if (w.hoverAnimation && !pub) return true;
  if (w.type === 'ITEM_BUTTON') return w.glowOnHover;
  if (w.type === 'SPRITE_BUTTON' && ((w.hoveredSprite && w.hoveredSprite !== w.sprite) || (w.hoveredPlayer && w.hoveredPlayer !== w.player) || (w.hoveredSpriteColor && w.hoveredSpriteColor !== w.spriteColor))) return true;
  if (w.hoveredText != null && w.hoveredText !== '') return true;
  if (w.hoveredFrames.length && !pub) return true;
  if (w.backgroundAlpha !== w.hoveredBackgroundAlpha) return true;
  return w.backgroundAlpha > 0 && w.backgroundColor.join() !== w.hoveredBackgroundColor.join();
}

export function lintScreen(s: Screen, script: ScriptInfo | null, screenIds: string[]): Issue[] {
  const out: Issue[] = [];
  if (!/^[A-Za-z0-9_\-]+$/.test(s.id)) out.push({ level: 'error', text: `id экрана «${s.id}»: только латиница, цифры, _ и -` });
  const ids = new Map<string, number>();
  for (const w of s.widgets) if (w.id) ids.set(w.id, (ids.get(w.id) ?? 0) + 1);
  const bg = backgroundRect(s);
  const pub = s.screenType === 'PUBLIC';
  const hits: { w: Widget; r: Rect }[] = [];
  for (const w of s.widgets) {
    const name = w.id || '(без id)';
    if (w.id && (ids.get(w.id) ?? 0) > 1) out.push({ level: 'error', uid: w.uid, text: `${name}: id повторяется - screen.widget() и morph возьмут первый` });
    const click = clickWorks(w, s, script, screenIds);
    if (!click.ok && click.why) out.push({ level: 'warn', uid: w.uid, text: `${name}: клик ничего не сделает - ${click.why}` });
    if (w.onClick?.action === 'SWITCH_SCREEN' && w.onClick.target && !screenIds.includes(w.onClick.target)) out.push({ level: 'info', uid: w.uid, text: `${name}: switchTo «${w.onClick.target}» - экрана нет в редакторе (должен быть на сервере)` });
    if (w.hoverAnimation && pub) out.push({ level: 'info', uid: w.uid, text: `${name}: hover-анимация на публичном экране не показывается` });
    if (w.hoveredFrames.length && pub) out.push({ level: 'info', uid: w.uid, text: `${name}: hovered-кадры на публичном экране не показываются` });
    if (w.frames.length && w.type !== 'TEXT_BUTTON') out.push({ level: 'warn', uid: w.uid, text: `${name}: кадры работают только у TEXT_BUTTON` });
    const inert = w.tolerance[0] <= 0 || w.tolerance[1] <= 0 || (!click.ok && !hasHoverVisual(w, s));
    if (inert) out.push({ level: 'info', uid: w.uid, text: `${name}: статичный - без действия и реакции на наведение, плагин его не обсчитывает` });
    else hits.push({ w, r: hitRect(w) });
    if (w.type === 'ITEM_BUTTON' && !w.material) out.push({ level: 'error', uid: w.uid, text: `${name}: не задан material` });
    if (w.type === 'SPRITE_BUTTON' && !w.sprite && !w.player) out.push({ level: 'error', uid: w.uid, text: `${name}: не задан sprite / player` });
    if (bg && !w.hidden && !inside(visualRect(w), bg)) out.push({ level: 'warn', uid: w.uid, text: `${name}: выходит за фон` });
  }
  for (let i = 0; i < hits.length; i++) for (let j = i + 1; j < hits.length; j++) {
    if (overlaps(hits[i].r, hits[j].r)) {
      const a = hits[i].w, b = hits[j].w;
      out.push({ level: 'warn', uid: a.uid, text: `${a.id || '(без id)'} и ${b.id || '(без id)'}: зоны наведения пересекаются - сработает ближний к взгляду` });
    }
  }
  return out;
}

export function worstLevel(issues: Issue[]): Level | null {
  if (issues.some((i) => i.level === 'error')) return 'error';
  if (issues.some((i) => i.level === 'warn')) return 'warn';
  return issues.length ? 'info' : null;
}

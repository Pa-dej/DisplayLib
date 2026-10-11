// JmcTable -> Screen: та же логика и те же синонимы ключей, что в ScreenLoader плагина.
import type { JmcTable, JmcValue } from './parser';
import {
  type Alignment, type ClickAction, type HoverAnimation, type RGB, type RichText, type Screen, type Segment,
  type Vec2, type Vec3, type Widget, ANCHOR_PRESETS, defaultBackground, defaultHoverAnimation, defaultWidget, defaultScreen,
} from '../model/screen';

function get(t: JmcTable | null, ...keys: string[]): JmcValue {
  if (!t) return null;
  for (const k of keys) { const v = t[k]; if (v !== undefined && v !== null) return v; }
  return null;
}
function str(t: JmcTable | null, ...keys: string[]): string | null { const v = get(t, ...keys); return v == null ? null : String(v); }
function num(t: JmcTable | null, ...keys: string[]): number | null { const v = get(t, ...keys); if (typeof v === 'number') return v; if (typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) return Number(v); return null; }
function bool(t: JmcTable | null, ...keys: string[]): boolean | null { const v = get(t, ...keys); if (typeof v === 'boolean') return v; if (typeof v === 'string') return v === 'true'; return null; }
function table(t: JmcTable | null, ...keys: string[]): JmcTable | null { const v = get(t, ...keys); return v && typeof v === 'object' && !Array.isArray(v) ? (v as JmcTable) : null; }
function list(t: JmcTable | null, ...keys: string[]): JmcValue[] | null { const v = get(t, ...keys); return Array.isArray(v) ? v : null; }
function nums(t: JmcTable | null, n: number, ...keys: string[]): number[] | null {
  const l = list(t, ...keys); if (!l) return null;
  const r = new Array(n).fill(0);
  for (let i = 0; i < Math.min(n, l.length); i++) if (typeof l[i] === 'number') r[i] = l[i] as number;
  return r;
}
function enumValue<T extends string>(allowed: readonly T[], v: string | null): T | null {
  if (v == null) return null; const u = v.trim().toUpperCase(); return (allowed as readonly string[]).includes(u) ? (u as T) : null;
}
function rich(v: JmcValue): RichText | null {
  if (v == null) return null;
  if (typeof v === 'string') return v;
  if (Array.isArray(v)) {
    const segs: Segment[] = [];
    for (const p of v) {
      if (typeof p === 'string') segs.push({ text: p });
      else if (p && typeof p === 'object') {
        const pt = p as JmcTable;
        if (pt.sprite != null) segs.push({ text: '', sprite: String(pt.sprite), atlas: pt.atlas == null ? undefined : String(pt.atlas), color: pt.color == null ? undefined : String(pt.color) });
        else segs.push({ text: pt.text == null ? '' : String(pt.text), color: pt.color == null ? undefined : String(pt.color) });
      }
    }
    return segs;
  }
  return String(v);
}

function soundOf(v: JmcValue): import('../model/screen').SoundSpec | 'none' | null {
  if (v == null) return null;
  if (v === false) return 'none';
  if (v === true) return null;
  if (typeof v === 'string') return v.trim() === '' || v.trim().toLowerCase() === 'none' ? 'none' : { sound: v.trim(), volume: 0.5, pitch: 1 };
  if (typeof v === 'object' && !Array.isArray(v)) {
    const t = v as JmcTable; const name = str(t, 'sound', 'name');
    return { sound: name ?? 'ui.button.click', volume: num(t, 'volume') ?? 0.5, pitch: num(t, 'pitch') ?? 1 };
  }
  return null;
}

export interface ToModelResult { screen: Screen; warnings: string[] }

export function tableToScreen(data: JmcTable, fileName: string): ToModelResult {
  const warnings: string[] = [];
  const idFromFile = fileName.replace(/\.jmc$/i, '');
  const s = defaultScreen(str(data, 'id') || idFromFile);
  const tr = num(data, 'tickRate', 'tick_rate'); if (tr != null) s.tickRate = Math.max(1, Math.min(20, Math.trunc(tr)));
  const st = str(data, 'screenType', 'screen_type', 'type');
  if (st != null) { const e = enumValue(['PRIVATE', 'PUBLIC'] as const, st); if (e) s.screenType = e; else warnings.push(`screenType '${st}' неизвестен, PRIVATE`); }
  const ir = num(data, 'interactionRadius', 'interaction_radius'); if (ir != null) s.interactionRadius = ir;
  const rc = num(data, 'rangeCheckInterval', 'range_check_interval'); if (rc != null) s.rangeCheckInterval = Math.trunc(rc);
  const cd = num(data, 'closeDistance', 'close_distance'); if (cd != null) s.closeDistance = cd;

  const bg = table(data, 'background');
  if (bg) {
    const b = defaultBackground();
    const c = nums(bg, 3, 'color'); if (c) b.color = c as RGB;
    const a = num(bg, 'alpha'); if (a != null) b.alpha = Math.trunc(a);
    const sc = nums(bg, 3, 'scale'); if (sc) b.scale = sc as Vec3;
    const p = nums(bg, 3, 'position'); if (p) b.position = p as Vec3;
    const t = str(bg, 'text'); if (t != null) b.text = t;
    const trn = nums(bg, 3, 'translation'); if (trn) b.translation = trn as Vec3;
    const an = get(bg, 'anchor', 'pivot', 'origin');
    if (typeof an === 'string') { const p = ANCHOR_PRESETS.find((x) => x.name === an.trim().toUpperCase().replace(/-/g, '_')); if (p) b.anchor = [...p.value] as Vec2; else warnings.push(`anchor '${an}' неизвестен, BOTTOM`); }
    else { const av = nums(bg, 2, 'anchor', 'pivot', 'origin'); if (av) b.anchor = av as Vec2; }
    s.background = b;
  } else s.background = null;

  // morph: true | 12 | { enabled, duration, steps }
  const mr = get(data, 'morph');
  if (typeof mr === 'boolean') s.morph.enabled = mr;
  else if (typeof mr === 'number') { s.morph.enabled = mr > 0; if (mr > 0) s.morph.duration = Math.trunc(mr); }
  else if (mr && typeof mr === 'object' && !Array.isArray(mr)) {
    const mt = mr as JmcTable;
    const en = bool(mt, 'enabled'); s.morph.enabled = en == null ? true : en;
    const du = num(mt, 'duration', 'ticks'); if (du != null) s.morph.duration = Math.max(1, Math.min(59, Math.trunc(du)));
    const stp = num(mt, 'steps'); if (stp != null) s.morph.steps = Math.trunc(stp);
  }

  let script = str(data, 'script');
  if (script == null) { const sc = table(data, 'scripts'); if (sc) script = str(sc, 'file'); }
  s.script = script && script.trim() ? script.trim() : null;

  const ws = list(data, 'widgets');
  if (ws) {
    let i = 0;
    for (const item of ws) {
      i++;
      if (item && typeof item === 'object' && !Array.isArray(item)) {
        const w = tableToWidget(item as JmcTable, i, warnings);
        if (w) s.widgets.push(w);
      } else warnings.push(`виджет #${i} не таблица`);
    }
  }
  return { screen: s, warnings };
}

export function tableToWidget(d: JmcTable, index: number, warnings: string[]): Widget | null {
  const typeStr = str(d, 'type');
  let type = enumValue(['TEXT_BUTTON', 'ITEM_BUTTON', 'SPRITE_BUTTON'] as const, typeStr);
  if (!type) {
    if (typeStr != null) { warnings.push(`виджет #${index}: неизвестный type '${typeStr}'`); return null; }
    type = get(d, 'material') != null ? 'ITEM_BUTTON' : get(d, 'sprite') != null ? 'SPRITE_BUTTON' : 'TEXT_BUTTON';
  }
  const w = defaultWidget(type);
  // дефолты плагина, а не редактора
  w.scale = [0.15, 0.15, 0.15]; w.tolerance = [0.06, 0.06];
  w.backgroundColor = [0, 0, 0]; w.backgroundAlpha = 0; w.hoveredBackgroundColor = [60, 60, 60]; w.hoveredBackgroundAlpha = 0;
  w.text = ''; w.material = type === 'ITEM_BUTTON' ? 'STONE' : ''; w.sprite = '';
  w.id = str(d, 'id') ?? '';

  const text = rich(get(d, 'text')); if (text != null) w.text = text;
  const ft = rich(get(d, 'formattedText', 'formatted_text')); if (ft != null) w.text = ft;
  const ht = rich(get(d, 'hoveredText', 'hovered_text')); if (ht != null) w.hoveredText = ht;
  const fht = rich(get(d, 'formattedHoveredText', 'formatted_hovered_text')); if (fht != null) w.hoveredText = fht;
  const fr = get(d, 'frames'); if (Array.isArray(fr)) { w.frames = fr.map((x) => rich(x)).filter((x): x is RichText => x != null); if (w.frames.length) w.text = w.frames[0]; }
  const fi = num(d, 'frameInterval', 'frame_interval'); if (fi != null) w.frameInterval = Math.max(1, Math.trunc(fi));
  const hfr = get(d, 'hoveredFrames', 'hovered_frames'); if (Array.isArray(hfr)) { w.hoveredFrames = hfr.map((x) => rich(x)).filter((x): x is RichText => x != null); }
  const hfi = num(d, 'hoveredFrameInterval', 'hovered_frame_interval'); if (hfi != null) w.hoveredFrameInterval = Math.trunc(hfi);
  const mat = str(d, 'material'); if (mat != null) w.material = mat.trim().toUpperCase();
  const sp = str(d, 'sprite'); if (sp != null) w.sprite = sp.trim();
  const at = str(d, 'atlas'); if (at != null) w.atlas = at.trim();
  const hs = str(d, 'hoveredSprite', 'hovered_sprite'); if (hs != null) w.hoveredSprite = hs.trim();
  const spc = str(d, 'spriteColor', 'sprite_color', 'color'); if (spc != null && spc.trim()) w.spriteColor = spc.trim();
  const hsc = str(d, 'hoveredSpriteColor', 'hovered_sprite_color', 'hoveredColor', 'hovered_color'); if (hsc != null && hsc.trim()) w.hoveredSpriteColor = hsc.trim();
  if (type === 'SPRITE_BUTTON' && !w.sprite) warnings.push(`виджет ${w.id || '#' + index}: SPRITE_BUTTON без sprite`);
  const g = bool(d, 'glowOnHover', 'glow_on_hover'); if (g != null) w.glowOnHover = g;
  const gc = nums(d, 3, 'glowColor', 'glow_color'); if (gc) w.glowColor = gc as RGB;
  const p = nums(d, 3, 'position'); if (p) w.position = p as Vec3;
  const sc = nums(d, 3, 'scale'); if (sc) w.scale = sc as Vec3;
  const tol = nums(d, 2, 'tolerance'); if (tol) w.tolerance = tol as Vec2;
  const tr = nums(d, 3, 'translation'); if (tr) w.translation = tr as Vec3;
  const rot = num(d, 'rotation', 'rotate', 'angle'); if (rot != null) w.rotation = rot;
  const bc = nums(d, 3, 'backgroundColor', 'background_color'); if (bc) w.backgroundColor = bc as RGB;
  const hbc = nums(d, 3, 'hoveredBackgroundColor', 'hovered_background_color'); if (hbc) w.hoveredBackgroundColor = hbc as RGB;
  const ba = num(d, 'backgroundAlpha', 'background_alpha'); if (ba != null) w.backgroundAlpha = Math.trunc(ba);
  const hba = num(d, 'hoveredBackgroundAlpha', 'hovered_background_alpha'); if (hba != null) w.hoveredBackgroundAlpha = Math.trunc(hba);
  const al = str(d, 'alignment');
  if (al != null) { const e = enumValue(['LEFT', 'CENTERED', 'RIGHT'] as const, al); if (e) w.alignment = e as Alignment; else warnings.push(`виджет ${w.id || '#' + index}: alignment '${al}'`); }
  const tt = rich(get(d, 'tooltip')); if (tt != null) w.tooltip = tt;
  const tc = nums(d, 3, 'tooltipColor', 'tooltip_color'); if (tc) w.tooltipColor = tc as RGB;
  const td = num(d, 'tooltipDelay', 'tooltip_delay'); if (td != null) w.tooltipDelay = Math.trunc(td);
  w.clickSound = soundOf(get(d, 'clickSound', 'click_sound', 'sound'));
  w.hoverSound = soundOf(get(d, 'hoverSound', 'hover_sound'));
  const oc = get(d, 'onClick', 'on_click', 'click');
  if (oc != null) w.onClick = parseClick(oc, `виджет ${w.id || '#' + index}`, warnings, w.id);
  const ha = table(d, 'hoverAnimation', 'hover_animation');
  if (ha) w.hoverAnimation = parseAnim(ha, warnings);
  return w;
}

export function parseClick(oc: JmcValue, where: string, warnings: string[], widgetId = ''): ClickAction | null {
  // onClick: true - функция скрипта с именем id виджета
  if (typeof oc === 'boolean') return oc ? { action: 'RUN_SCRIPT', function: widgetId } : { action: 'NONE' };
  if (typeof oc === 'string') {
    const v = oc.trim(); const u = v.toUpperCase();
    if (u === '' || u === 'NONE') return { action: 'NONE' };
    if (u === 'CLOSE' || u === 'CLOSE_SCREEN') return { action: 'CLOSE_SCREEN' };
    return { action: 'RUN_SCRIPT', function: v };
  }
  if (!oc || typeof oc !== 'object' || Array.isArray(oc)) { warnings.push(`${where}: onClick должен быть true, строкой или таблицей`); return null; }
  const t = oc as JmcTable;
  const fn = str(t, 'function', 'call');
  const target = str(t, 'switchTo', 'switch_to', 'target', 'screen');
  const actionStr = str(t, 'action');
  let action: ClickAction['action'];
  if (actionStr != null) {
    const e = enumValue(['NONE', 'SWITCH_SCREEN', 'CLOSE_SCREEN', 'RUN_SCRIPT'] as const, actionStr);
    if (!e) { warnings.push(`${where}: onClick action '${actionStr}'`); return null; }
    action = e;
  } else if (fn != null) action = 'RUN_SCRIPT';
  else if (target != null) action = 'SWITCH_SCREEN';
  else if (bool(t, 'close')) action = 'CLOSE_SCREEN';
  else action = 'NONE';
  const r: ClickAction = { action };
  if (fn != null) r.function = fn;
  if (target != null) r.target = target;
  return r;
}

function parseAnim(d: JmcTable, warnings: string[]): HoverAnimation | null {
  const a = defaultHoverAnimation();
  const ts = str(d, 'type');
  if (ts != null) { const e = enumValue(['PRESET', 'SCALE', 'TRANSLATE', 'ROTATE', 'TRANSFORM', 'COMBINED'] as const, ts); if (!e) { warnings.push(`hoverAnimation type '${ts}'`); return null; } a.type = e; }
  const du = num(d, 'duration'); if (du != null) a.duration = Math.trunc(du);
  const es = str(d, 'easing'); if (es != null) { const e = enumValue(['LINEAR', 'EASE_IN', 'EASE_OUT', 'EASE_IN_OUT', 'BOUNCE', 'ELASTIC'] as const, es); if (e) a.easing = e; }
  const ro = bool(d, 'reverseOnExit', 'reverse_on_exit'); if (ro != null) a.reverseOnExit = ro;
  const de = num(d, 'delay'); if (de != null) a.delay = Math.trunc(de);
  const lo = bool(d, 'loop'); if (lo != null) a.loop = lo;
  const lc = num(d, 'loopCount', 'loop_count'); if (lc != null) a.loopCount = Math.trunc(lc);
  const pr = str(d, 'preset'); if (pr != null) { const e = enumValue(['BOUNCE', 'SCALE', 'PULSE', 'GLOW', 'WOBBLE', 'LIFT'] as const, pr); if (e) a.preset = e; }
  const inten = num(d, 'intensity'); if (inten != null) a.intensity = inten;
  for (const k of ['scale', 'offset', 'rotation', 'axis', 'translation'] as const) { const v = nums(d, 3, k); if (v) a[k] = v as Vec3; }
  const ang = num(d, 'angle'); if (ang != null) a.rotation = [0, ang, 0];
  const ef = list(d, 'effects');
  if (ef) for (const e of ef) if (e && typeof e === 'object' && !Array.isArray(e)) { const x = parseAnim(e as JmcTable, warnings); if (x) a.effects.push(x); }
  return a;
}

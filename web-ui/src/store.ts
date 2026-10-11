// Состояние редактора: экраны (в памяти + автосохранение в localStorage), выделение, история правок.
// Файлы на диск не пишутся: только экспорт (скачать / скопировать).
import { reactive, computed, watch } from 'vue';
import { type Screen, type Widget, type WidgetType, type Vec3, type HoverAnimation, cloneDeep, defaultScreen, defaultWidget, newUid } from './model/screen';
import { parseJmc } from './jmc/parser';
import { tableToScreen } from './jmc/toModel';
import { screenToJmc } from './jmc/writer';
import { scanScript, scaffoldScript } from './jmp/scan';
import * as fs from './fs';

export interface ScreenDoc {
  /** путь внутри screens/, напр. "main_menu.jmc" или "shop/items.jmc" */
  path: string;
  screen: Screen;
  /** исходный текст из файла (для сравнения) */
  savedText: string | null;
  warnings: string[];
  /** файл был изменён не редактором (hot reload в игре перечитает) */
  dirty: boolean;
  /** текст скрипта: null - показываем живой шаблон по виджетам; иначе загруженный/отредактированный */
  scriptText: string | null;
  /** текст скрипта на диске (null - файла нет) */
  scriptSaved: string | null;
  scriptDirty: boolean;
}

interface State {
  docs: ScreenDoc[];
  current: string | null;      // path текущего экрана
  selected: string | null;     // uid виджета
  hovered: string | null;
  /** счётчик запросов «открыть вкладку скрипта» (Preview следит) */
  openScript: number;
  message: string;
  error: string;
  undo: { path: string; before: Screen }[];
  redo: { path: string; before: Screen }[];
  revision: number;            // растёт при любой правке модели: сцена перестраивается
  posRevision: number;         // только позиция виджета (перетаскивание): сцена двигает узел
  busy: boolean;
}

export const state = reactive<State>({
  docs: [], current: null, selected: null, hovered: null,
  openScript: 0, message: '', error: '', undo: [], redo: [], revision: 0, posRevision: 0, busy: false,
});

export const currentDoc = computed(() => state.docs.find((d) => d.path === state.current) ?? null);
export const currentScreen = computed(() => currentDoc.value?.screen ?? null);
export const selectedWidget = computed(() => currentScreen.value?.widgets.find((w) => w.uid === state.selected) ?? null);
/** Функции/хуки скрипта текущего экрана - по его тексту (файл или живой шаблон). */
export const currentScriptInfo = computed(() => { void state.revision; const d = currentDoc.value; return d ? scanScript(scriptTextOf(d)) : null; });
export const screenIds = computed(() => state.docs.map((d) => d.screen.id));

export function notify(msg: string): void { state.message = msg; state.error = ''; setTimeout(() => { if (state.message === msg) state.message = ''; }, 4000); }
export function fail(msg: string): void { state.error = msg; console.error(msg); }

// ---------------------------------------------------------------- правки с историей

/** Запомнить состояние перед правкой (один шаг undo). */
export function snapshot(): void {
  const d = currentDoc.value; if (!d) return;
  state.undo.push({ path: d.path, before: cloneDeep(d.screen) });
  if (state.undo.length > 100) state.undo.shift();
  state.redo = [];
}
export function touch(): void { state.revision++; const d = currentDoc.value; if (d) d.dirty = true; }
export function touchPosition(): void { state.posRevision++; const d = currentDoc.value; if (d) d.dirty = true; }

export function undo(): void {
  const e = state.undo.pop(); if (!e) return;
  const d = state.docs.find((x) => x.path === e.path); if (!d) return;
  state.redo.push({ path: d.path, before: cloneDeep(d.screen) });
  d.screen = e.before; state.current = d.path; touch();
}
export function redo(): void {
  const e = state.redo.pop(); if (!e) return;
  const d = state.docs.find((x) => x.path === e.path); if (!d) return;
  state.undo.push({ path: d.path, before: cloneDeep(d.screen) });
  d.screen = e.before; state.current = d.path; touch();
}

// ---------------------------------------------------------------- экраны

export function newScreen(id = 'new_screen'): ScreenDoc {
  let n = id, i = 2;
  while (state.docs.some((d) => d.path === n + '.jmc')) n = id + '_' + i++;
  const s = defaultScreen(n);
  const doc: ScreenDoc = { path: n + '.jmc', screen: s, savedText: null, warnings: [], dirty: true, scriptText: null, scriptSaved: null, scriptDirty: false };
  state.docs.push(doc);
  state.current = doc.path; state.selected = null;
  touch();
  return doc;
}

export function loadScreenText(path: string, text: string): ScreenDoc {
  const table = parseJmc(text);
  if (!table || typeof table !== 'object' || Array.isArray(table)) throw new Error('ожидались переменные верхнего уровня или return { ... }');
  const { screen, warnings } = tableToScreen(table, path.split('/').pop()!);
  applyEditorFlags(screen);
  const existing = state.docs.find((d) => d.path === path);
  const doc: ScreenDoc = { path, screen, savedText: text, warnings, dirty: false, scriptText: existing?.scriptText ?? null, scriptSaved: existing?.scriptSaved ?? null, scriptDirty: existing?.scriptDirty ?? false };
  if (existing) Object.assign(existing, doc); else state.docs.push(doc);
  return existing ?? doc;
}

// ---- флаги редактора (скрыт / заблокирован): не часть .jmc, живут в localStorage по id экрана
function flagsKey(screenId: string): string { return 'displaylib.flags.' + screenId; }
export function applyEditorFlags(s: Screen): void {
  try {
    const raw = localStorage.getItem(flagsKey(s.id)); if (!raw) return;
    const m = JSON.parse(raw) as Record<string, string>;
    for (const w of s.widgets) { const f = w.id ? m[w.id] : undefined; if (!f) continue; w.hidden = f.includes('h') || undefined; w.locked = f.includes('l') || undefined; }
  } catch { /* нет доступа к localStorage */ }
}
export function saveEditorFlags(s: Screen): void {
  try {
    const m: Record<string, string> = {};
    for (const w of s.widgets) { const f = (w.hidden ? 'h' : '') + (w.locked ? 'l' : ''); if (f && w.id) m[w.id] = f; }
    if (Object.keys(m).length) localStorage.setItem(flagsKey(s.id), JSON.stringify(m)); else localStorage.removeItem(flagsKey(s.id));
  } catch { /* ignore */ }
}
/** Скрыть/показать виджет в редакторе (скрытый не рисуется и не участвует в привязках). */
export function toggleHidden(uid: string): void {
  const s = currentScreen.value!; const w = s.widgets.find((x) => x.uid === uid); if (!w) return;
  w.hidden = !w.hidden || undefined; saveEditorFlags(s); state.revision++;
}
/** Заблокировать/разблокировать: заблокированный не выбирается кликом в сцене и не перетаскивается. */
export function toggleLocked(uid: string): void {
  const s = currentScreen.value!; const w = s.widgets.find((x) => x.uid === uid); if (!w) return;
  w.locked = !w.locked || undefined; saveEditorFlags(s); state.revision++;
}

export function currentText(): string { const s = currentScreen.value; return s ? screenToJmc(s) : ''; }

export function closeDoc(path: string): void {
  const i = state.docs.findIndex((d) => d.path === path);
  if (i < 0) return;
  state.docs.splice(i, 1);
  if (state.current === path) { state.current = state.docs[0]?.path ?? null; state.selected = null; }
  touch();
}

// ---------------------------------------------------------------- виджеты

export function addWidget(type: WidgetType): Widget {
  const s = currentScreen.value!; snapshot();
  const w = defaultWidget(type);
  const base = type === 'TEXT_BUTTON' ? 'text' : type === 'SPRITE_BUTTON' ? 'sprite' : 'item';
  let i = 1; while (s.widgets.some((x) => x.id === base + i)) i++;
  w.id = base + i;
  // новые кнопки чуть впереди фона (z = −0.01: ось z файла направлена от игрока); следующая - на 0.5 ниже предыдущей, чтобы не накладывались
  const last = s.widgets[s.widgets.length - 1];
  w.position = last ? [last.position[0], Math.round((last.position[1] - 0.5) * 100) / 100, last.position[2]] : [0, 0, -0.01];
  s.widgets.push(w);
  state.selected = w.uid; touch();
  return w;
}

/**
 * Изменить общий размер экрана в k раз, сохранив пропорции: позиции, масштабы, tolerance и translation
 * виджетов и фона, а также линейные величины hover-анимаций (offset, translation, в т.ч. во вложенных
 * effects). Относительные величины (scale анимации - множитель, intensity, повороты) не трогаются.
 */
export function scaleScreen(k: number): void {
  const s = currentScreen.value; if (!s || !(k > 0) || k === 1) return;
  snapshot();
  const r = (v: number) => Math.round(v * k * 10000) / 10000;
  const v3 = (v: Vec3): Vec3 => [r(v[0]), r(v[1]), r(v[2])];
  const anim = (a: HoverAnimation | null): void => {
    if (!a) return;
    a.offset = v3(a.offset); a.translation = v3(a.translation);
    for (const e of a.effects) anim(e);
  };
  if (s.background) {
    const b = s.background;
    b.scale = v3(b.scale); b.position = v3(b.position); b.translation = v3(b.translation);
  }
  for (const w of s.widgets) {
    w.position = v3(w.position); w.scale = v3(w.scale); w.translation = v3(w.translation);
    w.tolerance = [r(w.tolerance[0]), r(w.tolerance[1])];
    anim(w.hoverAnimation);
  }
  touch();
}

export function duplicateWidget(uid: string): void {
  const s = currentScreen.value!; const i = s.widgets.findIndex((w) => w.uid === uid); if (i < 0) return;
  snapshot();
  const c = cloneDeep(s.widgets[i]); c.uid = newUid();
  if (c.id) { let n = 2; while (s.widgets.some((x) => x.id === c.id + '_' + n)) n++; c.id = c.id + '_' + n; }
  c.position = [c.position[0] + 0.5, c.position[1], c.position[2]];
  s.widgets.splice(i + 1, 0, c);
  state.selected = c.uid; touch();
}

export function removeWidget(uid: string): void {
  const s = currentScreen.value!; const i = s.widgets.findIndex((w) => w.uid === uid); if (i < 0) return;
  snapshot(); s.widgets.splice(i, 1);
  if (state.selected === uid) state.selected = s.widgets[Math.min(i, s.widgets.length - 1)]?.uid ?? null;
  touch();
}

export function moveWidgetOrder(uid: string, delta: number): void {
  const s = currentScreen.value!; const i = s.widgets.findIndex((w) => w.uid === uid); const j = i + delta;
  if (i < 0 || j < 0 || j >= s.widgets.length) return;
  snapshot(); const [w] = s.widgets.splice(i, 1); s.widgets.splice(j, 0, w); touch();
}

// ---------------------------------------------------------------- экспорт

/**
 * «Скачать» / Ctrl+S: экран как screens/<id>.jmc и, если он нужен (есть обработчики или скрипт правили),
 * scripts/<id>.jmp вторым файлом. Редактор на диск не пишет - файлы кладутся в папку плагина вручную.
 */
export async function saveCurrent(): Promise<void> {
  const d = currentDoc.value; if (!d) return;
  const text = screenToJmc(d.screen);
  const wanted = d.screen.id + '.jmc';
  if (!d.path.includes('/')) d.path = wanted;
  if (state.current !== d.path) state.current = d.path;
  fs.download(wanted, text);
  d.savedText = text; d.dirty = false;
  let msg = 'Скачан ' + wanted;
  if (d.scriptDirty || hasHandlers(d.screen)) {
    const sname = scriptNameOf(d);
    const st = scriptTextOf(d);
    // второй файл - через мгновение, иначе браузер склеивает два клика в один
    setTimeout(() => fs.download(sname, st), 300);
    d.scriptText = st; d.scriptSaved = st; d.scriptDirty = false;
    msg += ' и ' + sname;
  }
  notify(msg + ' - положи в plugins/DisplayLib/screens и scripts');
}

export async function saveAll(): Promise<void> {
  const cur = state.current;
  for (const d of state.docs) { if (d.dirty || d.scriptDirty) { state.current = d.path; await saveCurrent(); } }
  state.current = cur;
}

export async function deleteCurrent(): Promise<void> {
  const d = currentDoc.value; if (!d) return;
  closeDoc(d.path);
  notify('Экран убран из редактора (файлы на диске не трогались)');
}

// ---------------------------------------------------------------- скрипт экрана
// У каждого экрана есть скрипт - файл scripts/<id>.jmp рядом с разметкой. Пока файла нет и его не правили,
// показывается живой шаблон по виджетам (onOpen/onClose + обработчики всех onClick). На диск он попадает
// при сохранении, только если на экране есть обработчики или текст правили руками.

/** Имя файла скрипта экрана: поле script или <id>.jmp. */
export function scriptNameOf(d: ScreenDoc): string {
  return (d.screen.script && d.screen.script.trim()) || d.screen.id + '.jmp';
}

/** Есть ли на экране кнопки, вызывающие функции скрипта. */
export function hasHandlers(s: Screen): boolean {
  return s.widgets.some((w) => w.onClick?.action === 'RUN_SCRIPT' && !!w.onClick.function);
}

function handlersOf(s: Screen): string[] {
  return [...new Set(s.widgets.map((w) => (w.onClick?.action === 'RUN_SCRIPT' ? w.onClick.function : null)).filter((x): x is string => !!x))];
}

/** Текст скрипта экрана: файл/правка или живой шаблон. */
export function scriptTextOf(d: ScreenDoc): string {
  if (d.scriptText != null) return d.scriptText;
  return scaffoldScript(d.screen.id, handlersOf(d.screen), d.screen.screenType === 'PUBLIC');
}

/** Состояние скрипта для подписи в интерфейсе. */
export function scriptStatus(d: ScreenDoc): 'saved' | 'dirty' | 'template' | 'template-needed' {
  if (d.scriptDirty) return 'dirty';
  if (d.scriptSaved !== null) return 'saved';   // уже экспортировали
  return hasHandlers(d.screen) ? 'template-needed' : 'template';
}

// ---------------------------------------------------------------- автосохранение в браузере
// Экраны живут в памяти; чтобы перезагрузка вкладки их не теряла, текст .jmc и скрипт складываются в localStorage.
const AUTOSAVE_KEY = 'displaylib.session';
let autosaveTimer: ReturnType<typeof setTimeout> | null = null;
export function scheduleAutosave(): void {
  if (autosaveTimer) clearTimeout(autosaveTimer);
  autosaveTimer = setTimeout(() => {
    autosaveTimer = null;
    try {
      const docs = state.docs.map((d) => ({ path: d.path, jmc: screenToJmc(d.screen), script: d.scriptText, scriptDirty: d.scriptDirty, scriptSaved: d.scriptSaved, dirty: d.dirty }));
      localStorage.setItem(AUTOSAVE_KEY, JSON.stringify({ docs, current: state.current }));
    } catch { /* нет места или доступа - просто без автосохранения */ }
  }, 400);
}
/** Восстановить экраны прошлой сессии; true - что-то восстановлено. */
export function restoreSession(): boolean {
  try {
    const raw = localStorage.getItem(AUTOSAVE_KEY); if (!raw) return false;
    const data = JSON.parse(raw) as { docs: { path: string; jmc: string; script: string | null; scriptDirty: boolean; scriptSaved: string | null; dirty: boolean }[]; current: string | null };
    for (const d of data.docs) {
      try {
        const doc = loadScreenText(d.path, d.jmc);
        doc.scriptText = d.script; doc.scriptDirty = !!d.scriptDirty; doc.scriptSaved = d.scriptSaved ?? null; doc.dirty = !!d.dirty;
        if (!d.dirty) doc.savedText = d.jmc; else doc.savedText = null;
      } catch { /* битый экран пропускаем */ }
    }
    if (data.current && state.docs.some((x) => x.path === data.current)) state.current = data.current;
    return state.docs.length > 0;
  } catch { return false; }
}
watch(() => [state.revision, state.posRevision, state.current], () => scheduleAutosave());

/** Правка текста скрипта в редакторе. */
export function setScriptText(text: string): void {
  const d = currentDoc.value; if (!d) return;
  d.scriptText = text; d.scriptDirty = text !== (d.scriptSaved ?? null); d.dirty = true;
  state.revision++;
}

/** Вернуться к живому шаблону (сбросить правки, файл на диске не трогаем до сохранения). */
export function resetScriptToTemplate(): void {
  const d = currentDoc.value; if (!d) return;
  d.scriptText = null; d.scriptDirty = d.scriptSaved !== null; d.dirty = true; state.revision++;
}

/** Открыть вкладку скрипта под предпросмотром. */
export function showScript(): void { state.openScript++; }

/** Шаблон обработчика клика. */
export function handlerTemplate(name: string): string {
  return `void ${name}(dyn widget, dyn player) {\n    player.message("§e${name}: " + widget.id());\n}\n`;
}

/**
 * Убедиться, что в скрипте есть функция-обработчик: у живого шаблона она появляется сама,
 * в отредактированный текст дописывается в конец.
 */
export function ensureHandler(name: string): void {
  const d = currentDoc.value; if (!d || !name) return;
  if (d.scriptText != null) {
    const info = scanScript(d.scriptText);
    if (!info.functions.includes(name) && !info.hooks.includes(name)) {
      const sep = d.scriptText === '' || d.scriptText.endsWith('\n') ? '' : '\n';
      setScriptText(d.scriptText + sep + '\n' + handlerTemplate(name));
      notify('В ' + scriptNameOf(d) + ' добавлена функция ' + name);
    }
  }
  showScript();
}

// Сброс выделения при смене экрана
watch(() => state.current, () => { state.selected = null; state.hovered = null; });

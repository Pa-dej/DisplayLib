// Копирование/вставка виджетов через системный буфер обмена в виде фрагмента .jmc:
//   dyn widgets = [ { ... }, { ... } ];
// Такой текст можно вставить и в другой экран, и в другую вкладку редактора, и прямо в файл экрана.
import { state, currentScreen, selectedWidgets, selectMany, snapshot, touch, notify, fail } from './store';
import { widgetToJmc } from './jmc/writer';
import { parseJmc } from './jmc/parser';
import { tableToWidget } from './jmc/toModel';
import type { JmcTable, JmcValue } from './jmc/parser';
import { newUid, type Widget } from './model/screen';

let internal = ''; // запасной буфер, если системный недоступен

export function widgetsToClipboardText(ws: Widget[]): string {
  return 'dyn widgets = [\n' + ws.map((w) => widgetToJmc(w, {})).join('\n') + '\n];\n';
}

export function copySelected(ev?: ClipboardEvent): boolean {
  const ws = selectedWidgets.value; if (!ws.length) return false;
  const text = widgetsToClipboardText(ws);
  internal = text;
  if (ev?.clipboardData) { ev.clipboardData.setData('text/plain', text); ev.preventDefault(); }
  else navigator.clipboard?.writeText(text).catch(() => { /* останется внутренний буфер */ });
  notify(ws.length === 1 ? 'Виджет скопирован' : `Скопировано виджетов: ${ws.length}`);
  return true;
}

/** Разобрать текст буфера: фрагмент `dyn widgets = [...]`, массив таблиц, одна таблица или целый экран. */
export function widgetsFromText(text: string): Widget[] {
  const warnings: string[] = [];
  let v: JmcValue;
  try { v = parseJmc(text); } catch { return []; }
  let list: JmcValue[] = [];
  if (Array.isArray(v)) list = v;
  else if (v && typeof v === 'object') {
    const t = v as JmcTable;
    if (Array.isArray(t.widgets)) list = t.widgets as JmcValue[];
    else if (t.type != null || t.text != null || t.material != null || t.sprite != null) list = [t];
  }
  const out: Widget[] = [];
  list.forEach((item, i) => { if (item && typeof item === 'object' && !Array.isArray(item)) { const w = tableToWidget(item as JmcTable, i, warnings); if (w) out.push(w); } });
  return out;
}

export function pasteWidgets(text: string): boolean {
  const s = currentScreen.value; if (!s) return false;
  const ws = widgetsFromText(text || internal);
  if (!ws.length) { if (text.trim()) fail('В буфере нет виджетов DisplayLib'); return false; }
  snapshot();
  const added: string[] = [];
  for (const w of ws) {
    w.uid = newUid();
    if (w.id && s.widgets.some((x) => x.id === w.id)) { let n = 2; while (s.widgets.some((x) => x.id === w.id + '_' + n)) n++; w.id = w.id + '_' + n; }
    // на то же место, где уже стоит такой же виджет, не кладём - сдвигаем, чтобы было видно
    if (s.widgets.some((x) => x.position[0] === w.position[0] && x.position[1] === w.position[1] && x.position[2] === w.position[2])) w.position = [w.position[0] + 0.2, w.position[1] - 0.2, w.position[2]];
    s.widgets.push(w); added.push(w.uid);
  }
  selectMany(added); touch();
  notify(added.length === 1 ? 'Виджет вставлен' : `Вставлено виджетов: ${added.length}`);
  return true;
}

export function cutSelected(ev?: ClipboardEvent): boolean {
  if (!copySelected(ev)) return false;
  const s = currentScreen.value!; const ids = new Set(selectedWidgets.value.map((w) => w.uid));
  snapshot();
  for (let i = s.widgets.length - 1; i >= 0; i--) if (ids.has(s.widgets[i].uid)) s.widgets.splice(i, 1);
  state.selected = null; state.extra = []; touch();
  return true;
}

function inEditable(e: Event): boolean {
  const t = e.target as HTMLElement | null;
  if (!t) return false;
  const tag = t.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || t.isContentEditable;
}

/** Повесить copy/cut/paste на окно: вне полей ввода они работают с виджетами. */
export function installClipboard(): void {
  window.addEventListener('copy', (e) => { if (!inEditable(e)) copySelected(e); });
  window.addEventListener('cut', (e) => { if (!inEditable(e)) cutSelected(e); });
  let lastPaste = 0;
  window.addEventListener('paste', (e) => {
    if (inEditable(e)) return;
    lastPaste = Date.now();
    const text = e.clipboardData?.getData('text/plain') ?? '';
    if (pasteWidgets(text)) e.preventDefault();
  });
  // если событие paste не пришло (бывает в некоторых окружениях) - читаем буфер через API
  window.addEventListener('keydown', (e) => {
    if (!(e.ctrlKey || e.metaKey) || e.code !== 'KeyV' || inEditable(e)) return;
    const at = Date.now();
    setTimeout(() => {
      if (lastPaste >= at) return;
      (navigator.clipboard?.readText?.() ?? Promise.resolve('')).then((t) => pasteWidgets(t), () => pasteWidgets(''));
    }, 80);
  });
}

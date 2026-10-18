// Состояние режима «Частицы»: эффекты в памяти (+ автосохранение в localStorage), история правок,
// примеры плагина, экспорт particles/<id>.jmc. На диск, как и экраны, только скачиванием.
import { reactive, computed, watch } from 'vue';
import { parseJmc, type JmcTable } from '../jmc/parser';
import { type ParticleEffect, newEffect, cloneEffect } from './model';
import { tableToEffect, effectToJmc, validateEffect } from './io';
import { particleHooks } from '../mode';
import { notify } from '../store';
import * as fs from '../fs';

export interface EffectDoc {
  /** уникальный ключ в редакторе (id может меняться при правке) */
  key: string;
  effect: ParticleEffect;
  /** текст при последнем скачивании/загрузке (для пометки «не сохранено») */
  savedText: string | null;
  warnings: string[];
}

interface HistoryEntry { key: string; before: ParticleEffect; json: string }

export const pstate = reactive({
  docs: [] as EffectDoc[],
  current: null as string | null,
  undo: [] as HistoryEntry[],
  redo: [] as HistoryEntry[],
  revision: 0,
});

export const currentEffectDoc = computed(() => pstate.docs.find((d) => d.key === pstate.current) ?? null);
export const currentEffect = computed(() => currentEffectDoc.value?.effect ?? null);
export const currentEffectText = computed(() => { void pstate.revision; const e = currentEffect.value; return e ? effectToJmc(e) : ''; });
export const currentEffectErrors = computed(() => { void pstate.revision; const e = currentEffect.value; return e ? validateEffect(e) : []; });
export function isDirty(d: EffectDoc): boolean { void pstate.revision; return d.savedText !== effectToJmc(d.effect); }

let keySeq = 1;
const newKey = () => 'e' + Date.now().toString(36) + (keySeq++);

// ---------------------------------------------------------------- история

let lastSnapJson = '';
export function psnapshot(): void {
  const d = currentEffectDoc.value; if (!d) return;
  const json = JSON.stringify(d.effect);
  lastSnapJson = json;
  const top = pstate.undo[pstate.undo.length - 1];
  if (top && top.key === d.key && top.json === json) return;
  pstate.undo.push({ key: d.key, before: cloneEffect(d.effect), json });
  if (pstate.undo.length > 200) pstate.undo.shift();
}
export function ptouch(): void {
  pstate.revision++;
  const d = currentEffectDoc.value; if (!d) return;
  if (pstate.redo.length && JSON.stringify(d.effect) !== lastSnapJson) pstate.redo = [];
}
function restore(from: HistoryEntry[], to: HistoryEntry[]): boolean {
  for (;;) {
    const e = from.pop(); if (!e) return false;
    const d = pstate.docs.find((x) => x.key === e.key); if (!d) continue;
    const cur = JSON.stringify(d.effect);
    if (cur === e.json) continue;
    to.push({ key: d.key, before: cloneEffect(d.effect), json: cur });
    d.effect = e.before;
    pstate.current = d.key;
    lastSnapJson = e.json;
    (document.activeElement as HTMLElement | null)?.blur?.();
    pstate.revision++;
    return true;
  }
}
export const pundo = () => restore(pstate.undo, pstate.redo);
export const predo = () => restore(pstate.redo, pstate.undo);
export const pcanUndo = computed(() => pstate.undo.length > 0);
export const pcanRedo = computed(() => pstate.redo.length > 0);

particleHooks.h = { snapshot: psnapshot, touch: ptouch, undo: pundo, redo: predo };

// ---------------------------------------------------------------- эффекты

function uniqueId(id: string, except?: EffectDoc): string {
  let n = id, i = 2;
  while (pstate.docs.some((d) => d !== except && d.effect.id === n)) n = id + '_' + i++;
  return n;
}

export function addEffect(id = 'new_effect'): EffectDoc {
  const doc: EffectDoc = { key: newKey(), effect: newEffect(uniqueId(id)), savedText: null, warnings: [] };
  pstate.docs.push(doc);
  pstate.current = doc.key;
  pstate.revision++;
  return doc;
}

/** Разобрать .jmc эффекта и открыть (эффект с тем же id заменяется - как перезагрузка файла). */
export function loadEffectText(text: string, fileName: string, opts: { replace?: boolean; saved?: boolean } = {}): EffectDoc {
  const table = parseJmc(text);
  if (!table || typeof table !== 'object' || Array.isArray(table)) throw new Error('ожидались переменные верхнего уровня или return { ... }');
  const { effect, errors } = tableToEffect(table as JmcTable, fileName.replace(/\.jmc$/, '').split('/').pop()!);
  const existing = opts.replace !== false ? pstate.docs.find((d) => d.effect.id === effect.id) : undefined;
  if (existing) {
    psnapshotFor(existing);
    existing.effect = effect; existing.warnings = errors; existing.savedText = opts.saved ? effectToJmc(effect) : null;
    pstate.current = existing.key; pstate.revision++;
    return existing;
  }
  if (opts.replace === false) effect.id = uniqueId(effect.id);
  const doc: EffectDoc = { key: newKey(), effect, savedText: opts.saved ? effectToJmc(effect) : null, warnings: errors };
  pstate.docs.push(doc);
  pstate.current = doc.key;
  pstate.revision++;
  return doc;
}
function psnapshotFor(d: EffectDoc): void {
  const prev = pstate.current; pstate.current = d.key; psnapshot(); pstate.current = prev;
}

export function duplicateEffect(key: string): void {
  const d = pstate.docs.find((x) => x.key === key); if (!d) return;
  const e = cloneEffect(d.effect); e.id = uniqueId(d.effect.id + '_copy');
  const doc: EffectDoc = { key: newKey(), effect: e, savedText: null, warnings: [] };
  pstate.docs.splice(pstate.docs.indexOf(d) + 1, 0, doc);
  pstate.current = doc.key; pstate.revision++;
}

export function closeEffect(key: string): void {
  const i = pstate.docs.findIndex((d) => d.key === key); if (i < 0) return;
  pstate.docs.splice(i, 1);
  pstate.undo = pstate.undo.filter((h) => h.key !== key);
  pstate.redo = pstate.redo.filter((h) => h.key !== key);
  if (pstate.current === key) pstate.current = pstate.docs[Math.min(i, pstate.docs.length - 1)]?.key ?? null;
  pstate.revision++;
}

/** id эффекта = имя файла; повтор id в редакторе запрещаем (в плагине файлы с одним id перекрывают друг друга). */
export function setEffectId(raw: string): void {
  const d = currentEffectDoc.value; if (!d) return;
  const id = raw.trim().replace(/[^A-Za-z0-9_\-./]/g, '_');
  d.effect.id = id ? uniqueId(id, d) : d.effect.id;
}

// ---------------------------------------------------------------- примеры плагина

const exampleFiles = import.meta.glob('../../../plugin/src/main/resources/examples/particles/*.jmc', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
export const EXAMPLES: { id: string; text: string; title: string }[] = Object.entries(exampleFiles)
  .map(([path, text]) => ({ id: path.split('/').pop()!.replace('.jmc', ''), text, title: (text.match(/^\/\/\s*(.+)$/m)?.[1] ?? '').replace(/^\w+:\s*/, '').trim() }))
  .sort((a, b) => a.id.localeCompare(b.id));

export function openExample(id: string): void {
  const ex = EXAMPLES.find((x) => x.id === id); if (!ex) return;
  loadEffectText(ex.text, id + '.jmc', { saved: true });
  notify('Открыт пример ' + id + ' (тот же файл, что кладёт плагин в examples/particles)');
}

// ---------------------------------------------------------------- экспорт

export function saveCurrentEffect(): void {
  const d = currentEffectDoc.value; if (!d) return;
  const text = effectToJmc(d.effect);
  fs.download(d.effect.id.split('/').pop() + '.jmc', text);
  d.savedText = text; pstate.revision++;
  notify(`Скачан ${d.effect.id}.jmc - положи в plugins/DisplayLib/particles и выполни /displaylib reload`);
}
export async function saveAllEffects(): Promise<void> {
  if (!pstate.docs.length) return;
  const files: Record<string, string> = {};
  for (const d of pstate.docs) { const t = effectToJmc(d.effect); files['particles/' + d.effect.id + '.jmc'] = t; d.savedText = t; }
  await fs.downloadZip('DisplayLib-particles.zip', files);
  pstate.revision++;
  notify(`Скачан DisplayLib-particles.zip (${Object.keys(files).length} эффектов) - распакуй в plugins/DisplayLib`);
}

// ---------------------------------------------------------------- автосохранение

const AUTOSAVE_KEY = 'displaylib.particles';
let timer: ReturnType<typeof setTimeout> | null = null;
watch(() => [pstate.revision, pstate.current], () => {
  if (timer) clearTimeout(timer);
  timer = setTimeout(() => {
    timer = null;
    try {
      const cur = pstate.docs.findIndex((d) => d.key === pstate.current);
      localStorage.setItem(AUTOSAVE_KEY, JSON.stringify({ current: cur, docs: pstate.docs.map((d) => ({ jmc: effectToJmc(d.effect), saved: d.savedText })) }));
    } catch { /* без автосохранения */ }
  }, 400);
});

export function restoreEffects(): void {
  try {
    const raw = localStorage.getItem(AUTOSAVE_KEY);
    if (raw) {
      const data = JSON.parse(raw) as { current: number; docs: { jmc: string; saved: string | null }[] };
      for (const x of data.docs) {
        try { const d = loadEffectText(x.jmc, 'restored.jmc', { replace: false }); d.savedText = x.saved; } catch { /* битый пропускаем */ }
      }
      pstate.current = pstate.docs[data.current]?.key ?? pstate.docs[0]?.key ?? null;
    }
  } catch { /* нет доступа */ }
  if (!pstate.docs.length) openExample('sparkle');
  pstate.undo = []; pstate.redo = [];
}

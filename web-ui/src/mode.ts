// Режим редактора: экраны или частицы. Общие поля (VecInput, ColorInput, …) зовут snapshot()/touch()
// из store.ts - в режиме частиц они уходят в историю эффектов через эти крючки.
import { ref, watch } from 'vue';

export type EditorMode = 'screens' | 'particles';

const KEY = 'displaylib.mode';
function initial(): EditorMode {
  try { return localStorage.getItem(KEY) === 'particles' ? 'particles' : 'screens'; } catch { return 'screens'; }
}
export const editorMode = ref<EditorMode>(initial());
watch(editorMode, (m) => { try { localStorage.setItem(KEY, m); } catch { /* без памяти режима */ } });

export interface ModeHooks { snapshot(): void; touch(): void; undo(): boolean; redo(): boolean }
/** Регистрирует particles/store.ts. */
export const particleHooks: { h: ModeHooks | null } = { h: null };
export function particleMode(): ModeHooks | null { return editorMode.value === 'particles' ? particleHooks.h : null; }

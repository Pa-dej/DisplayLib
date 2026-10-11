<script setup lang="ts">
import Icon from './Icon.vue';
import { onMounted, onBeforeUnmount, ref, watch, computed } from 'vue';
import { ScreenScene } from '../render/scene';
import Splitter from './Splitter.vue';
import { state, currentDoc, currentScreen, currentText, snapshot, snapshotCoalesced, touch, touchPosition, selectedWidget, selectedWidgets, selectedUids, select, selectMany, removeWidgets, scriptTextOf, scriptNameOf, scriptStatus, setScriptText, resetScriptToTemplate } from '../store';
import { calibration } from '../render/metrics';
import { alignRects, distributeRects, alignToRect, type AlignKind, type DistributeKind } from '../render/align';
import { spriteState } from '../render/sprites';

const canvas = ref<HTMLCanvasElement | null>(null);
let scene: ScreenScene | null = null;
const tab = ref<'jmc' | 'jmp' | 'none'>('jmc');
const showHit = ref(true);
const showGrid = ref(true);
const hoverPreview = ref(true);
const smartGuides = ref(localStorage.getItem('displaylib.smartGuides') !== '0');
const jmc = computed(() => { void state.revision; void state.posRevision; return currentText(); });
const jmp = computed(() => { void state.revision; const d = currentDoc.value; return d ? scriptTextOf(d) : ''; });
const jmpName = computed(() => { void state.revision; const d = currentDoc.value; return d ? scriptNameOf(d) : ''; });
const jmpStatus = computed(() => { void state.revision; const d = currentDoc.value; return d ? scriptStatus(d) : 'template'; });
const jmpStatusText: Record<string, string> = {
  saved: 'Файл на диске; Ctrl+S сохраняет правки',
  dirty: 'Есть несохранённые правки (Ctrl+S)',
  'template-needed': 'Живой шаблон по кнопкам экрана - запишется при сохранении',
  template: 'Живой шаблон; на экране нет вызовов функций - при сохранении не создаётся',
};

let dragSnapshotTaken = false;

onMounted(() => {
  scene = new ScreenScene(canvas.value!, {
    onSelect: (uid, additive) => { select(uid, additive); dragSnapshotTaken = false; },
    onSelectMany: (uids) => { selectMany(uids); dragSnapshotTaken = false; },
    onMove: (uid, pos) => {
      const w = currentScreen.value?.widgets.find((x) => x.uid === uid); if (!w) return;
      if (!dragSnapshotTaken) { snapshot(); dragSnapshotTaken = true; }
      if (w.position[0] === pos[0] && w.position[1] === pos[1] && w.position[2] === pos[2]) return;
      // остальные выделенные едут на ту же дельту
      const d = [pos[0] - w.position[0], pos[1] - w.position[1], pos[2] - w.position[2]];
      w.position = pos; scene?.updatePosition(uid);
      for (const o of selectedWidgets.value) {
        if (o.uid === uid || o.locked) continue;
        o.position = [r(o.position[0] + d[0]), r(o.position[1] + d[1]), r(o.position[2] + d[2])]; scene?.updatePosition(o.uid);
      }
      touchPosition();
    },
    onHover: (uid) => { state.hovered = uid; },
  });
  scene.smartGuides = smartGuides.value;
  (window as any).__dl = { state, scene }; // для отладки/автотестов
  scene.setScreen(currentScreen.value);
  camScreen = state.current; const c0 = camScreen ? cameras.get(camScreen) : undefined; if (c0) scene.setCamera(c0);
  window.addEventListener('blur', onWindowBlur);
  document.addEventListener('visibilitychange', onWindowBlur);
});
onBeforeUnmount(() => { scene?.dispose(); window.removeEventListener('blur', onWindowBlur); document.removeEventListener('visibilitychange', onWindowBlur); });

// смена экрана или любая правка модели -> перестроить (дёшево: экраны маленькие)
watch(() => [state.current, state.revision], () => scene?.setScreen(currentScreen.value));
watch(() => state.current, (cur, prev) => {
  if (!scene) return;
  if (prev) { cameras.set(prev, scene.getCamera()); persistCameras(); }
  camScreen = cur;
  const saved = cur ? cameras.get(cur) : undefined;
  if (saved) scene.setCamera(saved);
}, { flush: 'post' });
window.addEventListener('beforeunload', rememberCamera);
watch(() => state.morphTo, (path) => {
  if (!path || !scene) return;
  const target = state.docs.find((d) => d.path === path); state.morphTo = null;
  if (!target || target.path === state.current) return;
  scene.playMorph(target.screen, () => { state.current = target.path; });
});
watch(() => [state.selected, state.extra] as const, () => scene?.setSelection(state.selected, state.extra), { deep: true });
watch(() => state.hovered, (uid) => scene?.setHovered(uid));
watch(calibration, () => scene?.rebuildAll(), { deep: true });
watch(() => spriteState.revision, () => scene?.rebuildAll()); // догрузился спрайт атласа
watch(() => state.openScript, () => { tab.value = 'jmp'; });
watch(showHit, (v) => scene?.setShowHitAreas(v));
watch(showGrid, (v) => scene?.setShowGrid(v));
watch(smartGuides, (v) => { if (scene) scene.smartGuides = v; localStorage.setItem('displaylib.smartGuides', v ? '1' : '0'); });
watch(hoverPreview, (v) => { if (scene) { scene.hoverPreview = v; scene.rebuildAll(); } });

// устойчивость к уходу со вкладки/alt-tab: сбросить перетаскивание и состояние мыши в сцене
function onWindowBlur() { scene?.cancelInteraction(); }

function nudge(e: KeyboardEvent) {
  if (e.code === 'KeyF' && !e.ctrlKey && !e.metaKey) { scene?.focusSelected(); return; }
  if ((e.code === 'Digit1' || e.code === 'Digit3' || e.code === 'Digit7') && !e.altKey) { scene?.alignTo(e.code === 'Digit1' ? 'z' : e.code === 'Digit3' ? 'x' : 'y', e.ctrlKey); e.preventDefault(); return; }
  if (e.key === 'Delete' || (e.key === 'Backspace' && !e.ctrlKey)) { if (selectedUids.value.length) { e.preventDefault(); removeWidgets(selectedUids.value); } return; }
  if (e.code === 'KeyA' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); selectMany((currentScreen.value?.widgets ?? []).filter((w) => !w.hidden && !w.locked).map((w) => w.uid)); return; }
  const ws = selectedWidgets.value.filter((w) => !w.locked); if (!ws.length) return;
  const step = e.shiftKey ? 0.5 : e.altKey ? 0.01 : 0.1;
  const d: Record<string, [number, number, number]> = { ArrowLeft: [-step, 0, 0], ArrowRight: [step, 0, 0], ArrowUp: [0, step, 0], ArrowDown: [0, -step, 0], PageUp: [0, 0, -step], PageDown: [0, 0, step] };
  const v = d[e.key]; if (!v) return;
  e.preventDefault(); snapshotCoalesced('nudge:' + ws.map((w) => w.uid).join(',') + ':' + e.key);
  for (const w of ws) w.position = [r(w.position[0] + v[0]), r(w.position[1] + v[1]), r(w.position[2] + v[2])];
  touch();
}
function r(v: number) { return Math.round(v * 1000) / 1000; }

const multi = computed(() => selectedUids.value.length >= 2);
const hasBg = computed(() => { void state.revision; return !!currentScreen.value?.background; });
function alignBg(kind: AlignKind) { const ref = scene?.backgroundRect(); if (scene && ref) applyShifts(alignToRect(scene.widgetRects(selectedUids.value), ref, kind)); }

// камера запоминается на экран (в рамках сессии), чтобы переключение экранов не сбивало вид
// камера на экран: в памяти сессии и в localStorage (переживает перезагрузку вкладки)
type Cam = ReturnType<ScreenScene['getCamera']>;
const CAM_KEY = 'displaylib.cameras';
const cameras = new Map<string, Cam>((() => { try { return Object.entries(JSON.parse(localStorage.getItem(CAM_KEY) || '{}')) as [string, Cam][]; } catch { return []; } })());
let camScreen: string | null = null;
function persistCameras() { try { localStorage.setItem(CAM_KEY, JSON.stringify(Object.fromEntries([...cameras].slice(-50)))); } catch { /* ignore */ } }
function rememberCamera() { if (scene && camScreen) { cameras.set(camScreen, scene.getCamera()); persistCameras(); } }
function applyShifts(sh: Map<string, { dx: number; dy: number }>) {
  if (!sh.size) return;
  snapshot();
  for (const w of selectedWidgets.value) {
    const d = sh.get(w.uid); if (!d || w.locked) continue;
    w.position = [r(w.position[0] + d.dx * calibration.positionXSign), r(w.position[1] + d.dy), w.position[2]];
  }
  touch();
}
function alignSel(kind: AlignKind) { if (scene) applyShifts(alignRects(scene.widgetRects(selectedUids.value), kind)); }
function distributeSel(kind: DistributeKind) { if (scene) applyShifts(distributeRects(scene.widgetRects(selectedUids.value), kind)); }
</script>

<template>
  <div class="preview" tabindex="0" @keydown="nudge">
    <canvas ref="canvas"></canvas>
    <div class="preview-toolbar">
      <button class="btn sm" @click="scene?.resetCamera('player')" title="Глаза игрока: экран в 2 блоках, взгляд горизонтально"><Icon name="street-view" /> Как игрок</button>
      <button class="btn sm" @click="scene?.resetCamera('center')" title="Глаза игрока, взгляд в центр экрана"><Icon name="bullseye" /> В центр</button>
      <button class="btn sm" @click="scene?.resetCamera('front')" title="Прямо перед экраном"><Icon name="square" /> Спереди</button>
      <button class="btn sm" @click="scene?.resetCamera('fit')" title="Показать все виджеты"><Icon name="expand" /> Всё</button>
      <button class="btn sm" @click="scene?.focusSelected()" title="Центр камеры на выбранном виджете (F)"><Icon name="crosshairs" /> Фокус</button>
      <label class="btn sm"><input type="checkbox" v-model="showHit" /> <Icon name="vector-square" /> Зоны наведения</label>
      <label class="btn sm"><input type="checkbox" v-model="showGrid" /> <Icon name="th" /> Сетка</label>
      <label class="btn sm" title="Прилипание к краям, центрам и равным зазорам других виджетов (Ctrl при перетаскивании - отключить)"><input type="checkbox" v-model="smartGuides" /> <Icon name="magic" /> Направляющие</label>
      <label class="btn sm" title="Виджет под курсором показывается как при наведении в игре: hover-текст/фон и hover-анимация (масштаб, смещение, поворот) с длительностью и easing плагина"><input type="checkbox" v-model="hoverPreview" /> <Icon name="hand-pointer" /> Hover</label>
    </div>
    <div v-if="selectedUids.length" class="preview-toolbar align-bar">
      <span class="btn sm static"><Icon name="object-group" /> {{ selectedUids.length }}</span>
      <span class="btn-group" v-if="hasBg" title="К фону экрана">
        <button class="btn sm icon" title="К левому краю фона" @click="alignBg('left')"><Icon name="align-left" /></button>
        <button class="btn sm icon" title="По центру фона (горизонтально)" @click="alignBg('hcenter')"><Icon name="align-center" /></button>
        <button class="btn sm icon" title="К правому краю фона" @click="alignBg('right')"><Icon name="align-right" /></button>
        <button class="btn sm icon rot" title="К верхнему краю фона" @click="alignBg('top')"><Icon name="align-left" /></button>
        <button class="btn sm icon rot" title="По середине фона (вертикально)" @click="alignBg('vcenter')"><Icon name="align-center" /></button>
        <button class="btn sm icon rot" title="К нижнему краю фона" @click="alignBg('bottom')"><Icon name="align-right" /></button>
        <span class="btn sm static" style="padding-left:2px"><Icon name="square" :size="10" /></span>
      </span>
      <span class="btn-group" v-if="multi">
        <button class="btn sm icon" title="По левому краю" @click="alignSel('left')"><Icon name="align-left" /></button>
        <button class="btn sm icon" title="По центру (горизонтально)" @click="alignSel('hcenter')"><Icon name="align-center" /></button>
        <button class="btn sm icon" title="По правому краю" @click="alignSel('right')"><Icon name="align-right" /></button>
      </span>
      <span class="btn-group" v-if="multi">
        <button class="btn sm icon rot" title="По верхнему краю" @click="alignSel('top')"><Icon name="align-left" /></button>
        <button class="btn sm icon rot" title="По середине (вертикально)" @click="alignSel('vcenter')"><Icon name="align-center" /></button>
        <button class="btn sm icon rot" title="По нижнему краю" @click="alignSel('bottom')"><Icon name="align-right" /></button>
      </span>
      <span class="btn-group" v-if="multi">
        <button class="btn sm" title="Равные промежутки по горизонтали (крайние на месте)" :disabled="selectedUids.length < 3" @click="distributeSel('h')"><Icon name="arrows-alt-h" /> ⋯</button>
        <button class="btn sm" title="Равные промежутки по вертикали" :disabled="selectedUids.length < 3" @click="distributeSel('v')"><Icon name="arrows-alt-v" /> ⋮</button>
      </span>
      <button class="btn sm danger" title="Удалить выделенные (Delete)" @click="removeWidgets(selectedUids)"><Icon name="trash" /></button>
    </div>
    <div class="preview-legend">ЛКМ: виджет - выбрать/тащить (Shift: шаг 0.5, Alt: 0.01, Ctrl: без направляющих), Shift+клик - к выделению, Shift+тащить по пустому - рамка, Ctrl+A - все, Ctrl+C/V - копировать/вставить (и между экранами); пусто - вращать · СКМ/ПКМ - сдвиг · колесо - зум · стрелки: x/y, PgUp/PgDn: ближе/дальше · гизмо в углу - оси</div>
  </div>
  <Splitter v-if="tab !== 'none'" css-var="--codebar-h" dir="h" :sign="-1" :min="80" :max="900" />
  <div class="codebar" :class="{ collapsed: tab === 'none' }">
    <div class="tabs">
      <span class="tab" :class="{ active: tab === 'jmc' }" @click="tab = 'jmc'"><Icon name="file-alt" /> {{ currentScreen ? 'screens/' + currentScreen.id + '.jmc' : '.jmc' }}</span>
      <span class="tab" :class="{ active: tab === 'jmp' }" @click="tab = 'jmp'"><Icon name="file-code" /> scripts/{{ jmpName }}<span v-if="jmpStatus === 'dirty'" title="Не сохранено"> ●</span></span>
      <span class="tab" :class="{ active: tab === 'none' }" @click="tab = 'none'"><Icon name="chevron-down" /> Скрыть</span>
      <span class="grow"></span>
      <template v-if="tab === 'jmp'">
        <span class="hint" style="align-self:center">{{ jmpStatusText[jmpStatus] }}</span>
        <button v-if="currentDoc?.scriptText != null" class="btn sm" style="margin:0 0 4px 8px" title="Отбросить правки и вернуть живой шаблон по кнопкам экрана" @click="resetScriptToTemplate">К шаблону</button>
      </template>
    </div>
    <pre v-if="tab === 'jmc'" class="code">{{ jmc }}</pre>
    <div v-else-if="tab === 'jmp'" class="body">
      <textarea class="code" style="width:100%;border:0;border-radius:0;resize:none" :value="jmp" @input="setScriptText(($event.target as HTMLTextAreaElement).value)" spellcheck="false"></textarea>
    </div>
  </div>
</template>

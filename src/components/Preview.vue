<script setup lang="ts">
import Icon from './Icon.vue';
import { onMounted, onBeforeUnmount, ref, watch, computed } from 'vue';
import { ScreenScene } from '../render/scene';
import Splitter from './Splitter.vue';
import { state, currentDoc, currentScreen, currentText, snapshot, touch, touchPosition, selectedWidget, scriptTextOf, scriptNameOf, scriptStatus, setScriptText, resetScriptToTemplate } from '../store';
import { calibration } from '../render/metrics';
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
    onSelect: (uid) => { state.selected = uid; dragSnapshotTaken = false; },
    onMove: (uid, pos) => {
      const w = currentScreen.value?.widgets.find((x) => x.uid === uid); if (!w) return;
      if (!dragSnapshotTaken) { snapshot(); dragSnapshotTaken = true; }
      if (w.position[0] === pos[0] && w.position[1] === pos[1]) return;
      w.position = pos; touchPosition(); scene?.updatePosition(uid);
    },
    onHover: (uid) => { state.hovered = uid; },
  });
  scene.smartGuides = smartGuides.value;
  scene.setScreen(currentScreen.value);
  window.addEventListener('blur', onWindowBlur);
  document.addEventListener('visibilitychange', onWindowBlur);
});
onBeforeUnmount(() => { scene?.dispose(); window.removeEventListener('blur', onWindowBlur); document.removeEventListener('visibilitychange', onWindowBlur); });

// смена экрана или любая правка модели -> перестроить (дёшево: экраны маленькие)
watch(() => [state.current, state.revision], () => scene?.setScreen(currentScreen.value));
watch(() => state.selected, (uid) => scene?.setSelected(uid));
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
  if (e.key === 'f' || e.key === 'F') { scene?.focusSelected(); return; }
  if (e.key === '1' || e.key === '3' || e.key === '7') { scene?.alignTo(e.key === '1' ? 'z' : e.key === '3' ? 'x' : 'y', e.ctrlKey); e.preventDefault(); return; }
  const w = selectedWidget.value; if (!w) return;
  const step = e.shiftKey ? 0.5 : e.altKey ? 0.01 : 0.1;
  const d: Record<string, [number, number, number]> = { ArrowLeft: [-step, 0, 0], ArrowRight: [step, 0, 0], ArrowUp: [0, step, 0], ArrowDown: [0, -step, 0], PageUp: [0, 0, -step], PageDown: [0, 0, step] };
  const v = d[e.key]; if (!v) return;
  e.preventDefault(); snapshot();
  w.position = [r(w.position[0] + v[0]), r(w.position[1] + v[1]), r(w.position[2] + v[2])]; touch();
}
function r(v: number) { return Math.round(v * 1000) / 1000; }
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
      <label class="btn sm" title="Виджет под курсором показывается как при наведении в игре"><input type="checkbox" v-model="hoverPreview" /> <Icon name="hand-pointer" /> Hover</label>
    </div>
    <div class="preview-legend">ЛКМ: виджет - выбрать/тащить (Shift: шаг 0.5, Alt: 0.01, Ctrl: без направляющих), пусто - вращать · СКМ/ПКМ - сдвиг · колесо - зум · стрелки: x/y, PgUp/PgDn: ближе/дальше · гизмо в углу - оси</div>
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

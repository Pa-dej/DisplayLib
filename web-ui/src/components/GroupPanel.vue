<script setup lang="ts">
// Панель группы: выбрано несколько виджетов. Положение правится «как у одного»: центр группы (сдвиг всех),
// масштаб (вокруг центра, вместе с позициями, tolerance и смещениями анимаций), поворот (вокруг центра или у каждого),
// а translation / tolerance / scale можно задать всем одинаковыми. Плюс копирование свойств с главного.
import { computed, ref } from 'vue';
import Icon from './Icon.vue';
import VecInput from './fields/VecInput.vue';
import { state, selectedWidgets, selectedWidget, snapshot, touch, removeWidgets, selectedUids } from '../store';
import { cloneDeep, type Vec3, type Widget, type HoverAnimation } from '../model/screen';

const ws = computed(() => selectedWidgets.value.filter((w) => !w.locked));
const r = (v: number) => Math.round(v * 10000) / 10000;

/** Центр группы - середина охватывающего прямоугольника позиций. */
const center = computed<Vec3>(() => {
  const xs = ws.value.map((w) => w.position[0]), ys = ws.value.map((w) => w.position[1]), zs = ws.value.map((w) => w.position[2]);
  if (!xs.length) return [0, 0, 0];
  return [r((Math.min(...xs) + Math.max(...xs)) / 2), r((Math.min(...ys) + Math.max(...ys)) / 2), r((Math.min(...zs) + Math.max(...zs)) / 2)];
});
function setCenter(v: number[]) {
  const c = center.value; const d = [v[0] - c[0], v[1] - c[1], v[2] - c[2]];
  for (const w of ws.value) w.position = [r(w.position[0] + d[0]), r(w.position[1] + d[1]), r(w.position[2] + d[2])];
}

// ---- масштаб группы вокруг центра
const scaleK = ref(1);
function scaleAnim(a: HoverAnimation | null, k: number) {
  if (!a) return;
  const v3 = (v: Vec3): Vec3 => [r(v[0] * k), r(v[1] * k), r(v[2] * k)];
  a.offset = v3(a.offset); a.translation = v3(a.translation);
  for (const e of a.effects) scaleAnim(e, k);
}
function applyScale(k: number) {
  if (!(k > 0) || k === 1) return;
  snapshot();
  const c = center.value;
  for (const w of ws.value) {
    w.position = [r(c[0] + (w.position[0] - c[0]) * k), r(c[1] + (w.position[1] - c[1]) * k), w.position[2]];
    w.scale = [r(w.scale[0] * k), r(w.scale[1] * k), r(w.scale[2] * k)];
    w.translation = [r(w.translation[0] * k), r(w.translation[1] * k), r(w.translation[2] * k)];
    w.tolerance = [r(w.tolerance[0] * k), r(w.tolerance[1] * k)];
    scaleAnim(w.hoverAnimation, k);
  }
  touch(); scaleK.value = 1;
}

// ---- одинаковые значения всем
const same = (pick: (w: Widget) => readonly number[]) => computed(() => {
  const first = ws.value[0]; if (!first) return null;
  const a = pick(first); return ws.value.every((w) => pick(w).every((v, i) => v === a[i])) ? [...a] : null;
});
const sameScale = same((w) => w.scale), sameTr = same((w) => w.translation), sameTol = same((w) => w.tolerance);
const sameRot = computed(() => { const f = ws.value[0]; return f && ws.value.every((w) => w.rotation === f.rotation) ? f.rotation : null; });
function setAll<K extends 'scale' | 'translation' | 'tolerance'>(k: K, v: number[]) { for (const w of ws.value) (w as any)[k] = [...v]; touch(); }
function setRotation(deg: number) { snapshot(); for (const w of ws.value) w.rotation = deg; touch(); }

// ---- поворот группы вокруг центра: позиции едут по дуге, у каждого rotation += угол
const groupRot = ref(0);
function rotateGroup(deg: number) {
  if (!deg) return;
  snapshot();
  const c = center.value, a = (deg * Math.PI) / 180, cos = Math.cos(a), sin = Math.sin(a);
  for (const w of ws.value) {
    const dx = w.position[0] - c[0], dy = w.position[1] - c[1];
    w.position = [r(c[0] + dx * cos - dy * sin), r(c[1] + dx * sin + dy * cos), w.position[2]];
    w.rotation = r(w.rotation + deg);
  }
  touch(); groupRot.value = 0;
}

// ---- копировать с главного
function copyFromPrimary(what: 'bg' | 'anim' | 'tolerance' | 'scale' | 'sound') {
  const p = selectedWidget.value; if (!p) return;
  snapshot();
  for (const w of ws.value) {
    if (w === p) continue;
    if (what === 'bg') { w.backgroundColor = [...p.backgroundColor]; w.backgroundAlpha = p.backgroundAlpha; w.hoveredBackgroundColor = [...p.hoveredBackgroundColor]; w.hoveredBackgroundAlpha = p.hoveredBackgroundAlpha; }
    else if (what === 'anim') w.hoverAnimation = p.hoverAnimation ? cloneDeep(p.hoverAnimation) : null;
    else if (what === 'tolerance') w.tolerance = [...p.tolerance];
    else if (what === 'scale') w.scale = [...p.scale];
    else if (what === 'sound') { w.clickSound = p.clickSound == null || p.clickSound === 'none' ? p.clickSound : { ...p.clickSound }; w.hoverSound = p.hoverSound == null || p.hoverSound === 'none' ? p.hoverSound : { ...p.hoverSound }; }
  }
  touch();
}
const num = (e: Event) => Number((e.target as HTMLInputElement).value);
</script>
<template>
  <h2 class="row" style="justify-content:space-between">Группа <span class="hint">{{ selectedUids.length }} виджетов{{ ws.length !== selectedUids.length ? ', заблокированные не трогаем' : '' }}</span>
    <button class="btn sm icon danger" title="Удалить выделенные (Delete)" @click="removeWidgets(selectedUids)"><Icon name="trash" /></button></h2>
  <div class="hint" style="margin-bottom:8px">Главный - <code>{{ selectedWidget?.id || '(без id)' }}</code> (его панель - по клику без Shift). Поля ниже правят всех выделенных сразу.</div>

  <div class="section">
    <h3>Положение группы</h3>
    <div class="field"><label>центр</label><VecInput :model-value="center" :labels="['X', 'Y', 'Z']" :step="0.05" @update:model-value="setCenter($event)" /></div>
    <div class="hint">Середина охватывающего прямоугольника позиций; правка сдвигает все виджеты на одну и ту же дельту (как перетаскивание).</div>
    <div class="field"><label>масштаб ×</label><div class="row wrap">
      <input type="number" min="0.05" step="0.05" style="width:72px" v-model.number="scaleK" @keydown.enter="applyScale(scaleK)" />
      <button class="btn sm" @click="applyScale(scaleK)" :disabled="!(scaleK > 0) || scaleK === 1">Применить</button>
      <button class="btn sm" @click="applyScale(0.5)">½</button><button class="btn sm" @click="applyScale(2)">×2</button>
    </div></div>
    <div class="hint">Вокруг центра группы: позиции, scale, translation, tolerance и смещения hover-анимаций - пропорции сохраняются.</div>
    <div class="field"><label>повернуть на</label><div class="row wrap">
      <input type="number" step="5" style="width:72px" v-model.number="groupRot" @keydown.enter="rotateGroup(groupRot)" /><span class="hint">°</span>
      <button class="btn sm" @click="rotateGroup(groupRot)" :disabled="!groupRot">Применить</button>
      <button class="btn sm" @click="rotateGroup(90)">+90</button><button class="btn sm" @click="rotateGroup(-90)">−90</button>
    </div></div>
    <div class="hint">Вокруг центра группы: позиции идут по дуге, к rotation каждого прибавляется угол.</div>
  </div>

  <div class="section">
    <h3>Одинаково всем</h3>
    <div class="field"><label>scale</label><div class="row"><VecInput v-if="sameScale" :model-value="sameScale" :labels="['x', 'y', 'z']" :step="0.05" @update:model-value="setAll('scale', $event)" /><template v-else><span class="hint grow">разные</span><button class="btn sm" @click="snapshot(); setAll('scale', ws[0].scale)">Как у первого</button></template></div></div>
    <div class="field"><label>translation</label><div class="row"><VecInput v-if="sameTr" :model-value="sameTr" :labels="['x', 'y', 'z']" :step="0.01" @update:model-value="setAll('translation', $event)" /><template v-else><span class="hint grow">разные</span><button class="btn sm" @click="snapshot(); setAll('translation', ws[0].translation)">Как у первого</button></template></div></div>
    <div class="field"><label>rotation</label><div class="row"><input v-if="sameRot != null" type="number" step="1" style="width:72px" :value="sameRot" @focus="snapshot()" @input="setRotation(num($event))" /><template v-else><span class="hint grow">разные</span><button class="btn sm" @click="setRotation(ws[0].rotation)">Как у первого</button></template><span class="hint">°</span></div></div>
    <div class="field"><label>tolerance</label><div class="row"><VecInput v-if="sameTol" :model-value="sameTol" :labels="['Полуширина', 'Полувысота']" :step="0.01" :min="0" @update:model-value="setAll('tolerance', $event)" /><template v-else><span class="hint grow">разные</span><button class="btn sm" @click="snapshot(); setAll('tolerance', ws[0].tolerance)">Как у первого</button></template></div></div>
  </div>

  <div class="section">
    <h3>Скопировать с главного <span class="hint">{{ selectedWidget?.id || '' }}</span></h3>
    <div class="row wrap" style="gap:4px">
      <button class="btn sm" @click="copyFromPrimary('bg')" title="backgroundColor/Alpha и hover-фон">Фон</button>
      <button class="btn sm" @click="copyFromPrimary('anim')" title="hoverAnimation целиком">Hover-анимацию</button>
      <button class="btn sm" @click="copyFromPrimary('sound')">Звуки</button>
      <button class="btn sm" @click="copyFromPrimary('scale')">Scale</button>
      <button class="btn sm" @click="copyFromPrimary('tolerance')">Tolerance</button>
    </div>
  </div>
  <div style="margin-top:8px"><button class="btn sm" @click="state.extra = []">Оставить только главный</button></div>
</template>

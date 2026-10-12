<script setup lang="ts">
// Панель группы: выбрано несколько виджетов. Положение правится «как у одного»: центр группы (сдвиг всех),
// масштаб (вокруг центра, вместе с позициями, tolerance и смещениями анимаций), поворот (вокруг центра или у каждого),
// а translation / tolerance / scale можно задать всем одинаковыми. Плюс копирование свойств с главного.
import { computed } from 'vue';
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

// ---- масштаб группы вокруг центра (быстрые кнопки; применяется сразу)
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
  touch();
}

// ---- поля «как у одного виджета»: показываем значение первого (если у всех разные - помечаем ≠), правка ставит всем
const mixed = (pick: (w: Widget) => readonly number[]) => computed(() => {
  const first = ws.value[0]; if (!first) return false;
  const a = pick(first); return ws.value.some((w) => pick(w).some((v, i) => v !== a[i]));
});
const scaleV = computed(() => [...(ws.value[0]?.scale ?? [1, 1, 1])]), trV = computed(() => [...(ws.value[0]?.translation ?? [0, 0, 0])]), tolV = computed(() => [...(ws.value[0]?.tolerance ?? [0.1, 0.1])]);
const scaleMixed = mixed((w) => w.scale), trMixed = mixed((w) => w.translation), tolMixed = mixed((w) => w.tolerance);
function setAll<K extends 'scale' | 'translation' | 'tolerance'>(k: K, v: number[]) { for (const w of ws.value) (w as any)[k] = [...v]; touch(); }

// ---- поворот: слайдер как у одного виджета. Если у всех одинаковый rotation - показываем его; сдвиг слайдера
// поворачивает группу вокруг центра (позиции по дуге) и прибавляет дельту к rotation каждого.
const rotMixed = computed(() => { const f = ws.value[0]; return !!f && ws.value.some((w) => w.rotation !== f.rotation); });
const groupRot = computed(() => (rotMixed.value ? 0 : ws.value[0]?.rotation ?? 0));
function rotateTo(deg: number) {
  const d = deg - groupRot.value; if (!d) return;
  const c = center.value, a = (d * Math.PI) / 180, cos = Math.cos(a), sin = Math.sin(a);
  for (const w of ws.value) {
    const dx = w.position[0] - c[0], dy = w.position[1] - c[1];
    w.position = [r(c[0] + dx * cos - dy * sin), r(c[1] + dx * sin + dy * cos), w.position[2]];
    w.rotation = r(w.rotation + d);
  }
  touch();
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
    <h3>Положение</h3>
    <div class="field"><label>position</label><VecInput :model-value="center" :labels="['X', 'Y', 'Z']" :step="0.05" @update:model-value="setCenter($event)" /></div>
    <div class="field"><label>scale<span v-if="scaleMixed" class="hint" title="У виджетов разные значения; показано первого, правка задаст всем"> ≠</span></label><VecInput :model-value="scaleV" :labels="['x', 'y', 'z']" :step="0.05" @update:model-value="setAll('scale', $event)" /></div>
    <div class="field"><label>translation<span v-if="trMixed" class="hint" title="Разные; правка задаст всем"> ≠</span></label><VecInput :model-value="trV" :labels="['x', 'y', 'z']" :step="0.01" @update:model-value="setAll('translation', $event)" /></div>
    <div class="field"><label>rotation<span v-if="rotMixed" class="hint" title="Разные; слайдер поворачивает группу на дельту"> ≠</span></label><div class="row">
      <input type="range" min="-180" max="180" step="1" class="grow" :value="groupRot" title="Поворот группы вокруг её центра" @pointerdown="snapshot()" @input="rotateTo(num($event))" @dblclick="snapshot(); rotateTo(0)" />
      <input type="number" step="1" style="width:64px" :value="groupRot" @focus="snapshot()" @input="rotateTo(num($event) || 0)" /><span class="hint">°</span>
    </div></div>
    <div class="field"><label>tolerance<span v-if="tolMixed" class="hint" title="Разные; правка задаст всем"> ≠</span></label><VecInput :model-value="tolV" :labels="['Полуширина', 'Полувысота']" :step="0.01" :min="0" @update:model-value="setAll('tolerance', $event)" /></div>
    <div class="field"><label>масштаб группы</label><div class="row"><button class="btn sm" @click="applyScale(0.5)">½</button><button class="btn sm" @click="applyScale(0.8)">×0.8</button><button class="btn sm" @click="applyScale(1.25)">×1.25</button><button class="btn sm" @click="applyScale(2)">×2</button></div></div>
    <div class="hint">position - центр охватывающего прямоугольника (правка сдвигает всех). Поворот - вокруг центра группы: позиции идут по дуге, rotation каждого меняется на ту же дельту. «Масштаб группы» - вокруг центра: позиции, scale, translation, tolerance и смещения hover-анимаций.</div>
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

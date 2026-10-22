<script setup lang="ts">
// Панель группы: выбрано несколько виджетов. Положение правится «как у одного»: центр группы (сдвиг всех),
// масштаб (вокруг центра, вместе с позициями, tolerance и смещениями анимаций), поворот (вокруг центра или у каждого),
// а translation / tolerance / scale можно задать всем одинаковыми. Плюс копирование свойств с главного.
import { computed, reactive, watch } from 'vue';
import WidgetPanel from './WidgetPanel.vue';
import Icon from './Icon.vue';
import VecInput from './fields/VecInput.vue';
import { state, selectedWidgets, selectedWidget, snapshot, touch, removeWidgets, selectedUids } from '../store';
import { cloneDeep, type Vec3, type Widget, type HoverAnimation } from '../model/screen';
import { rotationCenterOffset } from '../render/scene';
import { calibration } from '../render/metrics';

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
// поворачивает группу как одно целое: визуальный центр вращения каждого виджета (с учётом translation и высоты
// текста - ровно та точка, вокруг которой крутится он сам) идёт по дуге вокруг общего центра, rotation += дельта.
// Считаем всегда от состояния на начало жеста, чтобы центр не «плыл» от шага к шагу.
const rotMixed = computed(() => { const f = ws.value[0]; return !!f && ws.value.some((w) => w.rotation !== f.rotation); });
const groupRot = computed(() => (rotMixed.value ? 0 : ws.value[0]?.rotation ?? 0));
interface RotBase { startDeg: number; c: [number, number]; items: { w: Widget; pos: Vec3; rot: number; cw: [number, number] }[] }
let rotBase: RotBase | null = null;
function beginRotate() {
  const sx = calibration.positionXSign;
  const items = ws.value.map((w) => {
    const o = rotationCenterOffset(w, w.rotation);
    return { w, pos: [...w.position] as Vec3, rot: w.rotation, cw: [w.position[0] * sx + o[0], w.position[1] + o[1]] as [number, number] };
  });
  const xs = items.map((i) => i.cw[0]), ys = items.map((i) => i.cw[1]);
  const c: [number, number] = xs.length ? [(Math.min(...xs) + Math.max(...xs)) / 2, (Math.min(...ys) + Math.max(...ys)) / 2] : [0, 0];
  rotBase = { startDeg: groupRot.value, c, items };
}
function endRotate() { rotBase = null; }
function rotateTo(deg: number) {
  if (!rotBase || rotBase.items.length !== ws.value.length || rotBase.items.some((i, k) => i.w !== ws.value[k])) beginRotate();
  const b = rotBase!, d = deg - b.startDeg;
  const a = (d * Math.PI) / 180, cos = Math.cos(a), sin = Math.sin(a), sx = calibration.positionXSign;
  for (const it of b.items) {
    const dx = it.cw[0] - b.c[0], dy = it.cw[1] - b.c[1];
    const nx = b.c[0] + dx * cos - dy * sin, ny = b.c[1] + dx * sin + dy * cos;
    const rot = r(it.rot + d), o = rotationCenterOffset(it.w, rot);
    it.w.position = [r((nx - o[0]) * sx), r(ny - o[1]), it.pos[2]];
    it.w.rotation = rot;
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

// ---- общие настройки: панель виджета правит черновик (копия главного), правка поля уходит всем выделенным.
// Только настройки оформления/поведения - содержимое (id, текст, спрайт, предмет, клик) у каждого своё.
const SHARED = ['backgroundColor', 'backgroundAlpha', 'hoveredBackgroundColor', 'hoveredBackgroundAlpha', 'shadow', 'alignment', 'lineWidth',
  'spriteColor', 'hoveredSpriteColor', 'glowOnHover', 'glowColor', 'tooltip', 'tooltipDelay', 'tooltipColor',
  'clickSound', 'hoverSound', 'hoverAnimation'] as const;
type SharedKey = typeof SHARED[number];
const LABELS: Record<SharedKey, string> = {
  backgroundColor: 'фон', backgroundAlpha: 'фон', hoveredBackgroundColor: 'фон hover', hoveredBackgroundAlpha: 'фон hover', shadow: 'тень текста',
  alignment: 'alignment', lineWidth: 'lineWidth', spriteColor: 'spriteColor', hoveredSpriteColor: 'hoveredSpriteColor', glowOnHover: 'свечение',
  glowColor: 'glowColor', tooltip: 'подсказка', tooltipDelay: 'tooltipDelay', tooltipColor: 'tooltipColor', clickSound: 'clickSound',
  hoverSound: 'hoverSound', hoverAnimation: 'hover-анимация',
};
/** У каких типов есть настройка (нет записи - у всех). */
const T = 'TEXT_BUTTON', S = 'SPRITE_BUTTON', I = 'ITEM_BUTTON';
const APPLIES: Partial<Record<SharedKey, string[]>> = {
  backgroundColor: [T, S], backgroundAlpha: [T, S], hoveredBackgroundColor: [T, S], hoveredBackgroundAlpha: [T, S], shadow: [T, S], lineWidth: [T, S],
  alignment: [T], spriteColor: [S], hoveredSpriteColor: [S], glowOnHover: [I], glowColor: [I],
};
const primary = computed(() => (selectedWidget.value && ws.value.includes(selectedWidget.value) ? selectedWidget.value : ws.value[0]) ?? null);
const draft = reactive(cloneDeep(primary.value ?? ({} as Widget))) as Widget;
const seen: Record<string, string> = {};
/** Черновик = главный (при смене выделения, отмене, правке главного в сцене) - без переноса на остальных. */
function resync() {
  const p = primary.value; if (!p) return;
  const copy = cloneDeep(p);
  for (const k of Object.keys(copy) as (keyof Widget)[]) (draft as any)[k] = (copy as any)[k];
  for (const k of SHARED) seen[k] = JSON.stringify((draft as any)[k]);
}
resync();
watch(() => [selectedUids.value.join(), primary.value?.uid], resync);
watch(() => primary.value && JSON.stringify(SHARED.map((k) => (primary.value as any)[k])), () => {
  // главный изменился не через черновик (отмена, правка в сцене) - подтянуть
  const p = primary.value; if (!p) return;
  if (SHARED.some((k) => JSON.stringify((p as any)[k]) !== seen[k])) resync();
});
watch(draft, () => {
  let changed = false;
  for (const k of SHARED) {
    const j = JSON.stringify((draft as any)[k]);
    if (j === seen[k]) continue;
    seen[k] = j;
    for (const w of ws.value) if (!APPLIES[k] || APPLIES[k]!.includes(w.type)) (w as any)[k] = cloneDeep((draft as any)[k]);
    changed = true;
  }
  if (changed) touch();
}, { deep: true });
/** Какие общие настройки у выделенных разные (показано значение главного, правка задаст всем). */
const differing = computed(() => {
  void state.revision;
  const out = new Set<string>();
  const first = ws.value[0]; if (!first) return [];
  const types = new Set(ws.value.map((w) => w.type));
  for (const k of SHARED) {
    const only = APPLIES[k];
    if (only && [...types].some((t) => !only.includes(t))) continue; // настройки нет у части выбранных типов - в панели её нет
    if (ws.value.some((w) => JSON.stringify((w as any)[k]) !== JSON.stringify((first as any)[k]))) out.add(LABELS[k]);
  }
  return [...out];
});
const typeNames = computed(() => [...new Set(ws.value.map((w) => w.type === 'TEXT_BUTTON' ? 'текст' : w.type === 'SPRITE_BUTTON' ? 'спрайт' : 'предмет'))].join(' + '));
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
      <input type="range" min="-180" max="180" step="1" class="grow" :value="groupRot" title="Поворот группы вокруг её центра" @pointerdown="snapshot(); beginRotate()" @pointerup="endRotate()" @change="endRotate()" @input="rotateTo(num($event))" @dblclick="snapshot(); beginRotate(); rotateTo(0); endRotate()" />
      <input type="number" step="1" style="width:64px" :value="groupRot" @focus="snapshot(); beginRotate()" @blur="endRotate()" @input="rotateTo(num($event) || 0)" /><span class="hint">°</span>
    </div></div>
    <div class="field"><label>tolerance<span v-if="tolMixed" class="hint" title="Разные; правка задаст всем"> ≠</span></label><VecInput :model-value="tolV" :labels="['Полуширина', 'Полувысота']" :step="0.01" :min="0" @update:model-value="setAll('tolerance', $event)" /></div>
    <div class="field"><label>масштаб группы</label><div class="row"><button class="btn sm" @click="applyScale(0.5)">½</button><button class="btn sm" @click="applyScale(0.8)">×0.8</button><button class="btn sm" @click="applyScale(1.25)">×1.25</button><button class="btn sm" @click="applyScale(2)">×2</button></div></div>
    <div class="hint">position - центр охватывающего прямоугольника (правка сдвигает всех). Поворот - группа крутится как одно целое вокруг своего центра: центры виджетов идут по дуге, rotation каждого меняется на ту же дельту. «Масштаб группы» - вокруг центра: позиции, scale, translation, tolerance и смещения hover-анимаций.</div>
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
  <div class="section" v-if="primary">
    <h3>Общие настройки <span class="hint">{{ typeNames }}</span></h3>
    <div class="hint">Значения главного виджета; правка поля задаёт его всем выделенным. Показаны только настройки, которые есть у всех выбранных типов{{ differing.length ? '.' : ', у всех сейчас одинаковые.' }}</div>
    <div v-if="differing.length" class="hint" style="color:var(--vp-c-warning-1)">Сейчас разные: {{ differing.join(', ') }}</div>
  </div>
  <WidgetPanel v-if="primary" :widget="draft" :group="ws" />
  <div style="margin-top:8px"><button class="btn sm" @click="state.extra = []">Оставить только главный</button></div>
</template>

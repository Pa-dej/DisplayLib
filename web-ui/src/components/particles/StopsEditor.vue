<script setup lang="ts">
// Градиент цвета или кривая числа по доле жизни частицы (0 - рождение, 1 - смерть).
// Без своих долей точки распределены равномерно - так короче в файле.
import { computed } from 'vue';
import { snapshot, touch } from '../../store';
import type { Stop } from '../../particles/model';
import { gradientOf, curveOf, sampleGradient, sampleCurve, alpha, cssHex } from '../../particles/colors';
import PColorInput from './PColorInput.vue';
import NumInput from './NumInput.vue';
import Icon from '../Icon.vue';

type V = string | number;
const props = defineProps<{ modelValue: Stop<V>[] | null; kind: 'color' | 'number'; defaults: Stop<V>[]; max?: number }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: Stop<V>[] | null): void }>();

const stops = computed(() => props.modelValue ?? []);
const explicit = computed(() => stops.value.length > 0 && stops.value.every((s) => s.at != null));
const times = computed(() => stops.value.map((s, i, a) => (explicit.value ? (s.at as number) : a.length === 1 ? 0 : i / (a.length - 1))));

function set(v: Stop<V>[] | null) { emit('update:modelValue', v); touch(); }
function enable() { snapshot(); set(props.defaults.map((s) => ({ ...s }))); }
function disable() { snapshot(); set(null); }
function toggleExplicit() {
  snapshot();
  const t = times.value;
  set(stops.value.map((s, i) => ({ at: explicit.value ? null : Math.round(t[i] * 1000) / 1000, value: s.value })));
}
function setAt(i: number, v: number | null) {
  const a = stops.value.map((s) => ({ ...s })); a[i].at = Math.max(0, Math.min(1, v ?? 0)); set(a);
}
function setValue(i: number, v: V) { const a = stops.value.map((s) => ({ ...s })); a[i].value = v; set(a); }
function add() {
  snapshot();
  const a = stops.value.map((s) => ({ ...s }));
  const last = a[a.length - 1];
  if (explicit.value) {
    // новая точка - посередине самого длинного промежутка
    let best = 0, gap = -1; const t = times.value;
    for (let i = 1; i < t.length; i++) if (t[i] - t[i - 1] > gap) { gap = t[i] - t[i - 1]; best = i; }
    if (gap <= 0) a.push({ at: 1, value: last.value });
    else { const at = Math.round(((t[best] + t[best - 1]) / 2) * 1000) / 1000; a.splice(best, 0, { at, value: a[best - 1].value }); }
  } else a.push({ at: null, value: last?.value ?? props.defaults[0].value });
  set(a);
}
function remove(i: number) { snapshot(); const a = stops.value.filter((_, j) => j !== i); set(a.length ? a : null); }

// предпросмотр
const gradientCss = computed(() => {
  if (props.kind !== 'color' || !stops.value.length) return '';
  const g = gradientOf(stops.value as Stop<string>[]); if (!g) return '';
  const parts: string[] = [];
  for (let i = 0; i <= 32; i++) {
    const c = sampleGradient(g, i / 32);
    const rgb = cssHex(c);
    parts.push(`rgba(${parseInt(rgb.slice(1, 3), 16)},${parseInt(rgb.slice(3, 5), 16)},${parseInt(rgb.slice(5, 7), 16)},${(alpha(c) / 255).toFixed(3)}) ${(i / 32) * 100}%`);
  }
  return `linear-gradient(90deg, ${parts.join(', ')})`;
});
const curvePath = computed(() => {
  if (props.kind !== 'number' || !stops.value.length) return '';
  const c = curveOf(stops.value as Stop<number>[]);
  const top = Math.max(props.max ?? 1, ...c.values);
  const pts: string[] = [];
  for (let i = 0; i <= 64; i++) { const v = sampleCurve(c, i / 64); pts.push(`${(i / 64) * 200},${38 - (v / top) * 36}`); }
  return 'M' + pts.join(' L');
});
const marks = computed(() => times.value.map((t) => t * 100));
</script>
<template>
  <div class="stops">
    <template v-if="!modelValue">
      <button class="btn sm" @click="enable"><Icon name="plus" /> {{ kind === 'color' ? 'Задать градиент' : 'Задать кривую' }}</button>
    </template>
    <template v-else>
      <div class="stops-preview">
        <div v-if="kind === 'color'" class="bar checker"><span :style="{ background: gradientCss }"></span></div>
        <svg v-else class="bar curve" viewBox="0 0 200 40" preserveAspectRatio="none"><path :d="curvePath" /></svg>
        <span v-for="(m, i) in marks" :key="i" class="mark" :style="{ left: m + '%' }"></span>
      </div>
      <div v-for="(s, i) in stops" :key="i" class="stop-row">
        <NumInput v-if="explicit" class="at" :model-value="s.at" :step="0.05" :min="0" :max="1" @update:model-value="setAt(i, $event)" title="Доля жизни 0-1" />
        <span v-else class="at hint" title="Точки распределены равномерно">{{ times[i].toFixed(2) }}</span>
        <PColorInput v-if="kind === 'color'" :model-value="s.value as string" @update:model-value="setValue(i, $event)" />
        <NumInput v-else :model-value="s.value as number" :step="0.05" :min="0" @update:model-value="setValue(i, $event ?? 0)" />
        <button class="btn sm icon" title="Убрать точку" @click="remove(i)"><Icon name="times" /></button>
      </div>
      <div class="row">
        <button class="btn sm" @click="add"><Icon name="plus" /> Точка</button>
        <label class="btn sm" title="Свои доли жизни у точек; без них - равномерно"><input type="checkbox" :checked="explicit" @change="toggleExplicit" /> Доли</label>
        <span class="grow"></span>
        <button class="btn sm" @click="disable" title="Убрать (по умолчанию)"><Icon name="trash" /></button>
      </div>
    </template>
  </div>
</template>

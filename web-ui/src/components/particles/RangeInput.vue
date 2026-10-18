<script setup lang="ts">
// Диапазон [min, max]: случайное значение на частицу. Одинаковые - точное значение (в файл пишется числом).
import { snapshot, touch } from '../../store';
import type { Range } from '../../particles/model';
const props = defineProps<{ modelValue: Range; step?: number; min?: number; max?: number; int?: boolean; unit?: string }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: Range): void }>();
function set(i: 0 | 1, ev: Event) {
  const raw = (ev.target as HTMLInputElement).value;
  let n = Number(raw); if (raw.trim() === '' || Number.isNaN(n)) return;
  if (props.int) n = Math.round(n);
  if (props.min != null) n = Math.max(props.min, n);
  if (props.max != null) n = Math.min(props.max, n);
  const v: Range = [...props.modelValue] as Range;
  const wasSame = v[0] === v[1];
  v[i] = n;
  // пока значения совпадали, правка «от» двигает и «до»: точное значение остаётся точным
  if (wasSame && i === 0) v[1] = n;
  emit('update:modelValue', v); touch();
}
</script>
<template>
  <div class="range-input" :title="modelValue[0] === modelValue[1] ? 'Точное значение; задайте «до», чтобы было случайным' : 'Случайное значение от и до на каждую частицу'">
    <input type="number" :value="modelValue[0]" :step="step ?? 0.01" :min="min" :max="max" @focus="snapshot()" @input="set(0, $event)" />
    <span class="dash" :class="{ same: modelValue[0] === modelValue[1] }">…</span>
    <input type="number" :value="modelValue[1]" :step="step ?? 0.01" :min="min" :max="max" @focus="snapshot()" @input="set(1, $event)" />
    <span v-if="unit" class="hint unit">{{ unit }}</span>
  </div>
</template>

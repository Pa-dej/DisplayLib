<script setup lang="ts">
// Вектор чисел: v-model на массив, правка покомпонентно с одним снимком undo на фокус
import { snapshot, touch } from '../../store';
const props = defineProps<{ modelValue: number[]; labels?: string[]; step?: number; min?: number; max?: number; int?: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: number[]): void }>();
function set(i: number, ev: Event) {
  const raw = (ev.target as HTMLInputElement).value;
  let n = Number(raw); if (Number.isNaN(n)) return;
  if (props.int) n = Math.round(n);
  if (props.min != null) n = Math.max(props.min, n);
  if (props.max != null) n = Math.min(props.max, n);
  const v = [...props.modelValue]; v[i] = n;
  emit('update:modelValue', v); touch();
}
</script>
<template>
  <div class="vec" :class="{ two: modelValue.length === 2 }">
    <input v-for="(x, i) in modelValue" :key="i" type="number" :value="x" :step="step ?? 0.05" :min="min" :max="max" :placeholder="labels?.[i]" :title="labels?.[i]" @focus="snapshot()" @input="set(i, $event)" />
  </div>
</template>

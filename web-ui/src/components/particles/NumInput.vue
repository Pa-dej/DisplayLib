<script setup lang="ts">
// Число: снимок undo на фокус, правка на ввод. nullable - пустое поле = null (значение по умолчанию).
import { snapshot, touch } from '../../store';
const props = defineProps<{ modelValue: number | null; step?: number; min?: number; max?: number; int?: boolean; nullable?: boolean; placeholder?: string }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: number | null): void }>();
function set(ev: Event) {
  const raw = (ev.target as HTMLInputElement).value.trim();
  if (raw === '' && props.nullable) { emit('update:modelValue', null); touch(); return; }
  let n = Number(raw); if (raw === '' || Number.isNaN(n)) return;
  if (props.int) n = Math.round(n);
  if (props.min != null) n = Math.max(props.min, n);
  if (props.max != null) n = Math.min(props.max, n);
  emit('update:modelValue', n); touch();
}
</script>
<template>
  <input type="number" :value="modelValue ?? ''" :step="step ?? 0.01" :min="min" :max="max" :placeholder="placeholder" @focus="snapshot()" @input="set" />
</template>

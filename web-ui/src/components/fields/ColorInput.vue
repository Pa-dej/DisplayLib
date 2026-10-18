<script setup lang="ts">
// RGB [r,g,b] 0-255 + необязательная альфа
import { computed } from 'vue';
import { snapshot, touch } from '../../store';
import ColorPicker from './ColorPicker.vue';
import { parseAnyColor } from '../../color/parse';
const props = defineProps<{ modelValue: number[]; alpha?: number | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: number[]): void; (e: 'update:alpha', v: number): void }>();
const hex = computed(() => '#' + props.modelValue.map((c) => Math.max(0, Math.min(255, Math.round(c))).toString(16).padStart(2, '0')).join(''));
const hasAlpha = computed(() => props.alpha !== undefined && props.alpha !== null);
const pickerValue = computed(() => hex.value + (hasAlpha.value ? Math.max(0, Math.min(255, Math.round(props.alpha!))).toString(16).padStart(2, '0') : ''));
function fromPicker(h: string) {
  const c = parseAnyColor(h); if (!c) return;
  emit('update:modelValue', [c.r, c.g, c.b]);
  if (hasAlpha.value) emit('update:alpha', Math.round(c.a * 255));
  touch();
}
function setC(i: number, ev: Event) {
  const n = Math.max(0, Math.min(255, Math.round(Number((ev.target as HTMLInputElement).value) || 0)));
  const v = [...props.modelValue]; v[i] = n; emit('update:modelValue', v); touch();
}
function setA(ev: Event) { emit('update:alpha', Math.max(0, Math.min(255, Math.round(Number((ev.target as HTMLInputElement).value) || 0)))); touch(); }
</script>
<template>
  <div class="color-row" :class="{ 'with-alpha': alpha !== undefined && alpha !== null }">
    <ColorPicker :size="28" :model-value="pickerValue" :alpha="hasAlpha" @update:model-value="fromPicker" />
    <input v-for="i in 3" :key="i" type="number" min="0" max="255" step="1" :value="modelValue[i - 1]" :title="['R', 'G', 'B'][i - 1]" @focus="snapshot()" @input="setC(i - 1, $event)" />
    <template v-if="alpha !== undefined && alpha !== null">
      <span class="hint alpha-label" title="Прозрачность 0-255 (ниже 26 клиент не рисует фон)">α</span>
      <input type="number" min="0" max="255" step="1" class="alpha" :value="alpha" title="Прозрачность 0-255 (ниже 26 клиент не рисует фон)" @focus="snapshot()" @input="setA" />
    </template>
  </div>
</template>

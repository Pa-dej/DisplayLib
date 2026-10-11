<script setup lang="ts">
// RGB [r,g,b] 0-255 + необязательная альфа
import { computed } from 'vue';
import { snapshot, touch } from '../../store';
const props = defineProps<{ modelValue: number[]; alpha?: number | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: number[]): void; (e: 'update:alpha', v: number): void }>();
const hex = computed(() => '#' + props.modelValue.map((c) => Math.max(0, Math.min(255, Math.round(c))).toString(16).padStart(2, '0')).join(''));
function fromHex(ev: Event) {
  const h = (ev.target as HTMLInputElement).value;
  const v = [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  emit('update:modelValue', v); touch();
}
function setC(i: number, ev: Event) {
  const n = Math.max(0, Math.min(255, Math.round(Number((ev.target as HTMLInputElement).value) || 0)));
  const v = [...props.modelValue]; v[i] = n; emit('update:modelValue', v); touch();
}
function setA(ev: Event) { emit('update:alpha', Math.max(0, Math.min(255, Math.round(Number((ev.target as HTMLInputElement).value) || 0)))); touch(); }
</script>
<template>
  <div class="color-row" :class="{ 'with-alpha': alpha !== undefined && alpha !== null }">
    <input type="color" :value="hex" @focus="snapshot()" @input="fromHex" />
    <input v-for="i in 3" :key="i" type="number" min="0" max="255" step="1" :value="modelValue[i - 1]" :title="['R', 'G', 'B'][i - 1]" @focus="snapshot()" @input="setC(i - 1, $event)" />
    <template v-if="alpha !== undefined && alpha !== null">
      <span class="hint alpha-label" title="Прозрачность 0-255 (ниже 26 клиент не рисует фон)">α</span>
      <input type="number" min="0" max="255" step="1" class="alpha" :value="alpha" title="Прозрачность 0-255 (ниже 26 клиент не рисует фон)" @focus="snapshot()" @input="setA" />
    </template>
  </div>
</template>

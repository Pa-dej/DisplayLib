<script setup lang="ts">
// Цвет как строка файла: "#rrggbb", "#rrggbbaa" или имя. Пипетка + прозрачность 0-255 + текстовое поле.
import { computed } from 'vue';
import { snapshot, touch } from '../../store';
import { parseColor, alpha, cssHex, colorToString } from '../../particles/colors';
const props = defineProps<{ modelValue: string; noAlpha?: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>();
const parsed = computed(() => parseColor(props.modelValue));
const hex = computed(() => (parsed.value == null ? '#ffffff' : cssHex(parsed.value)));
const a = computed(() => (parsed.value == null ? 255 : alpha(parsed.value)));
function fromPicker(ev: Event) {
  const rgb = parseInt((ev.target as HTMLInputElement).value.slice(1), 16);
  emit('update:modelValue', colorToString(((a.value << 24) | rgb) >>> 0)); touch();
}
function fromAlpha(ev: Event) {
  const n = Math.max(0, Math.min(255, Math.round(Number((ev.target as HTMLInputElement).value) || 0)));
  const rgb = parsed.value == null ? 0xffffff : parsed.value & 0xffffff;
  emit('update:modelValue', colorToString(((n << 24) | rgb) >>> 0)); touch();
}
function fromText(ev: Event) { emit('update:modelValue', (ev.target as HTMLInputElement).value.trim()); touch(); }
</script>
<template>
  <div class="pcolor">
    <span class="swatch"><span :style="{ background: hex, opacity: a / 255 }"></span><input type="color" :value="hex" @focus="snapshot()" @input="fromPicker" /></span>
    <input type="text" class="txt" :value="modelValue" :class="{ bad: parsed == null }" spellcheck="false" :title="parsed == null ? 'Не цвет: нужен #rrggbb, #rrggbbaa или имя (red, gold, …)' : '#rrggbb, #rrggbbaa или имя'" @focus="snapshot()" @change="fromText" />
    <template v-if="!noAlpha">
      <span class="hint" title="Непрозрачность 0-255 (ниже 26 клиент не рисует)">α</span>
      <input type="number" class="alpha" min="0" max="255" step="1" :value="a" @focus="snapshot()" @input="fromAlpha" />
    </template>
  </div>
</template>

<script setup lang="ts">
// Цвет как строка файла: "#rrggbb", "#rrggbbaa" или имя. Образец открывает выбор цвета (с прозрачностью);
// в текстовое поле можно вставить любую запись - rgb(), hsl(), "255, 128, 0" - она станет #hex.
import { computed } from 'vue';
import { snapshot, touch } from '../../store';
import { parseColor } from '../../particles/colors';
import { parseAnyColor, rgbaToHex } from '../../color/parse';
import { NAMED_COLORS } from '../../render/text';
import ColorPicker from '../fields/ColorPicker.vue';
const props = defineProps<{ modelValue: string; noAlpha?: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>();
const valid = computed(() => parseColor(props.modelValue) != null);
function set(v: string) { emit('update:modelValue', v); touch(); }
function fromText(ev: Event) {
  const raw = (ev.target as HTMLInputElement).value.trim();
  if (parseColor(raw) != null || NAMED_COLORS[raw.toLowerCase()]) { set(raw); return; }
  // любая другая запись (rgb(), hsl(), #f80, 255,128,0) - в #rrggbb(aa), который понимает плагин
  const c = parseAnyColor(raw);
  if (c) set(rgbaToHex(props.noAlpha ? { ...c, a: 1 } : c));
  else set(raw);
}
</script>
<template>
  <div class="pcolor">
    <ColorPicker :size="28" :model-value="modelValue" :alpha="!noAlpha" @update:model-value="set" />
    <input type="text" class="txt" :value="modelValue" :class="{ bad: !valid }" spellcheck="false" :title="valid ? '#rrggbb, #rrggbbaa или имя; можно вставить rgb(), hsl(), 255, 128, 0' : 'Не цвет: нужен #rrggbb, #rrggbbaa или имя (red, gold, …)'" @focus="snapshot()" @change="fromText" />
  </div>
</template>

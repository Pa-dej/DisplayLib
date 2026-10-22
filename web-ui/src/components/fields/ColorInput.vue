<script setup lang="ts">
// RGB [r,g,b] 0-255 + необязательная альфа - в том же виде, что цвета частиц: образец (выбор цвета с
// прозрачностью) и одно текстовое поле #rrggbb / #rrggbbaa. В поле можно вставить любую запись -
// rgb(), hsl(), имя, "255, 128, 0" - она разбирается в RGB(A).
import { computed, ref } from 'vue';
import { snapshot, touch } from '../../store';
import ColorPicker from './ColorPicker.vue';
import { parseAnyColor } from '../../color/parse';
const props = defineProps<{ modelValue: number[]; alpha?: number | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: number[]): void; (e: 'update:alpha', v: number): void }>();
const h2 = (n: number) => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0');
const hasAlpha = computed(() => props.alpha !== undefined && props.alpha !== null);
const text = computed(() => '#' + props.modelValue.map(h2).join('') + (hasAlpha.value ? h2(props.alpha!) : ''));
const bad = ref(false);
function apply(raw: string) {
  const c = parseAnyColor(raw);
  if (!c) { bad.value = true; return; }
  bad.value = false;
  emit('update:modelValue', [c.r, c.g, c.b]);
  if (hasAlpha.value) emit('update:alpha', Math.round(c.a * 255));
  touch();
}
function fromText(ev: Event) {
  const el = ev.target as HTMLInputElement;
  apply(el.value);
  if (!bad.value) el.value = text.value; // показать в каноническом виде
}
</script>
<template>
  <div class="pcolor">
    <ColorPicker :size="28" :model-value="text" :alpha="hasAlpha" @update:model-value="apply" />
    <input type="text" class="txt" :value="text" :class="{ bad }" spellcheck="false"
      :title="hasAlpha ? '#rrggbbaa: последние две цифры - прозрачность (ниже 1a клиент не рисует фон); можно вставить rgb(), hsl(), имя, 255, 128, 0' : '#rrggbb; можно вставить rgb(), hsl(), имя, 255, 128, 0'"
      @focus="snapshot()" @change="fromText" />
  </div>
</template>

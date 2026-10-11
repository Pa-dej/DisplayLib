<script setup lang="ts">
// Цвет текста/тинт спрайта: имя цвета Minecraft или #hex; null/undefined - без цвета (белый).
import { NAMED_COLORS, cssColor } from '../../render/text';
import { snapshot, touch } from '../../store';
const props = defineProps<{ modelValue: string | null | undefined; none?: string }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string | null): void }>();
const names = Object.keys(NAMED_COLORS);
function set(ev: Event) {
  const v = (ev.target as HTMLInputElement | HTMLSelectElement).value;
  emit('update:modelValue', v === '' || v === '#' ? null : v); touch();
}
</script>
<template>
  <div class="row" style="gap:3px">
    <input type="color" :value="cssColor(modelValue ?? undefined)" @focus="snapshot()" @input="set" />
    <select :value="modelValue && !modelValue.startsWith('#') ? modelValue : (modelValue ? '#' : '')" @change="set" title="Именованный цвет">
      <option value="">{{ props.none ?? 'без цвета' }}</option>
      <option value="#" disabled>#hex</option>
      <option v-for="n in names" :key="n" :value="n">{{ n }}</option>
    </select>
  </div>
</template>
<style scoped>
input[type=color] { width: 26px; }
select { width: 96px; }
</style>

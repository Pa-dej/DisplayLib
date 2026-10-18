<script setup lang="ts">
// Список строк (цвета, предметы, блоки): строки с полем по слоту, «+» добавляет копию последней.
import { snapshot, touch } from '../../store';
import Icon from '../Icon.vue';
const props = defineProps<{ modelValue: string[]; add: string; min?: number; title?: string }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string[]): void }>();
function set(i: number, v: string) { const a = [...props.modelValue]; a[i] = v; emit('update:modelValue', a); touch(); }
function push() { snapshot(); emit('update:modelValue', [...props.modelValue, props.modelValue[props.modelValue.length - 1] ?? props.add]); touch(); }
function del(i: number) { snapshot(); emit('update:modelValue', props.modelValue.filter((_, j) => j !== i)); touch(); }
</script>
<template>
  <div class="plist">
    <div v-for="(v, i) in modelValue" :key="i" class="plist-row">
      <slot :value="v" :index="i" :set="(x: string) => set(i, x)" />
      <button class="btn sm icon" :disabled="modelValue.length <= (min ?? 0)" title="Убрать" @click="del(i)"><Icon name="times" /></button>
    </div>
    <button class="btn sm" @click="push" :title="title"><Icon name="plus" /> Добавить</button>
  </div>
</template>

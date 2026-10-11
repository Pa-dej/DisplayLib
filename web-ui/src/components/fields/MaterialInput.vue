<script setup lang="ts">
// Material с автодополнением по спрайтам предметов
import { computed, ref } from 'vue';
import { ITEM_NAMES, hasItem, itemLabel } from '../../render/items';
import itemsJson from '../../items.json';
import { snapshot, touch } from '../../store';

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>();
const open = ref(false);
const q = ref('');
const list = computed(() => {
  const s = q.value.toLowerCase().replace(/ /g, '_');
  if (!s) return ITEM_NAMES.slice(0, 40);
  return ITEM_NAMES.filter((n) => n.includes(s) || itemLabel(n).toLowerCase().includes(q.value.toLowerCase())).slice(0, 40);
});
const known = computed(() => hasItem(props.modelValue));
function close() { setTimeout(() => (open.value = false), 150); }
function pick(n: string) { snapshot(); emit('update:modelValue', n.toUpperCase()); touch(); open.value = false; }
function onInput(ev: Event) { const v = (ev.target as HTMLInputElement).value; q.value = v; emit('update:modelValue', v.toUpperCase()); touch(); }
function style(n: string) { const e = (itemsJson as any).items[n]; return e ? { backgroundImage: 'url(assets/items/icons.png)', backgroundPosition: `-${e.x}px -${e.y}px` } : {}; }
</script>
<template>
  <div class="row">
    <span class="item-icon" :style="style(modelValue.toLowerCase())" :title="itemLabel(modelValue.toLowerCase())"></span>
    <div class="autocomplete grow">
      <input type="text" :value="modelValue" @focus="snapshot(); open = true; q = ''" @blur="close" @input="onInput" placeholder="DIAMOND_SWORD" :style="known ? {} : { borderColor: 'var(--vp-c-warning-1)' }" :title="known ? '' : 'Нет спрайта для предпросмотра (в игре может работать)'" />
      <div v-if="open" class="list">
        <div v-for="n in list" :key="n" @mousedown.prevent="pick(n)"><span class="item-icon" style="width:16px;height:16px;background-size:512px auto" :style="{ ...style(n), backgroundSize: '512px auto', backgroundPosition: `-${(itemsJson as any).items[n].x / 2}px -${(itemsJson as any).items[n].y / 2}px` }"></span>{{ n.toUpperCase() }} <span class="hint">{{ itemLabel(n) }}</span></div>
      </div>
    </div>
  </div>
</template>

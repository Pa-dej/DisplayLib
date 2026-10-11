<script setup lang="ts">
// Имя спрайта атласа с поиском по всем атласам (при выборе атлас подставляется сам) и превью.
import { computed, ref } from 'vue';
import { searchSprites, atlasLabel, spriteImage, spriteIndex, spriteState, texturePath } from '../../render/sprites';
import { snapshot, touch } from '../../store';
import { hasItem } from '../../render/items';

const props = defineProps<{ modelValue: string; atlas?: string | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void; (e: 'update:atlas', v: string | null): void }>();
const open = ref(false);
const q = ref('');
// ищем по всем атласам, пока атлас не задан явно; при выборе атлас подставляется сам
const list = computed(() => { void spriteIndex.loaded; return searchSprites(q.value, props.atlas); });
// «barrel» есть в списке предметов, но плоской item/barrel.png в клиенте нет (3D-блок) - объясняем, почему только block/
const note = computed(() => {
  if (!spriteIndex.loaded || props.atlas) return '';
  const s = q.value.trim().toLowerCase().replace(/ /g, '_');
  if (!s || s.includes('/')) return '';
  const items = spriteIndex.byAtlas['minecraft:items'] ?? [];
  const blocks = spriteIndex.byAtlas['minecraft:blocks'] ?? [];
  const exactItem = items.includes('item/' + s);
  const blockFaces = blocks.filter((n) => n.startsWith('block/' + s)).length;
  if (!exactItem && blockFaces && hasItem(s)) return `У ${s} нет плоской item-текстуры (3D-блок): в тексте доступны только грани block/${s}…`;
  return '';
});
const preview = computed(() => { void spriteState.revision; return props.modelValue ? spriteImage(props.modelValue, props.atlas) : null; });
const previewUrl = computed(() => preview.value?.canvas?.toDataURL() ?? '');
function close() { setTimeout(() => (open.value = false), 150); }
function pick(h: { atlas: string; sprite: string }) {
  snapshot();
  emit('update:modelValue', h.sprite);
  // items/blocks плагин выводит из item/…, block/… сам; для остальных атласов атлас нужен явно
  const auto = (h.atlas === 'minecraft:items' && h.sprite.startsWith('item/')) || (h.atlas === 'minecraft:blocks' && h.sprite.startsWith('block/'));
  if (!props.atlas || props.atlas !== h.atlas) emit('update:atlas', auto && !props.atlas ? null : h.atlas);
  touch(); open.value = false;
}
function onInput(ev: Event) { const v = (ev.target as HTMLInputElement).value; q.value = v; emit('update:modelValue', v); touch(); }
function iconOf(h: { atlas: string; sprite: string }): string { void spriteState.revision; return spriteImage(h.sprite, h.atlas).canvas?.toDataURL() ?? ''; }
</script>
<template>
  <div class="row">
    <span class="item-icon sprite-icon" :style="previewUrl ? { backgroundImage: `url(${previewUrl})` } : {}" :title="preview?.done && !preview.exact ? 'Нет ' + texturePath(modelValue, atlas) + ' - в игре будет «missing texture»' : texturePath(modelValue, atlas)"></span>
    <div class="autocomplete grow">
      <input type="text" :value="modelValue" @focus="snapshot(); open = true; q = ''" @blur="close" @input="onInput" placeholder="Имя: heart, mace, hud/heart/full…" :style="preview && preview.done && !preview.canvas ? { borderColor: 'var(--vp-c-warning-1)' } : {}" />
      <div v-if="open && (list.length || note)" class="list">
        <div v-if="note" class="note">{{ note }}</div>
        <div v-for="h in list" :key="h.atlas + '|' + h.sprite" @mousedown.prevent="pick(h)"><span class="item-icon sprite-icon" style="width:16px;height:16px" :style="iconOf(h) ? { backgroundImage: `url(${iconOf(h)})` } : {}"></span>{{ h.sprite }} <span class="hint">{{ atlasLabel(h.atlas) }}</span></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// Иконка Font Awesome 5 Solid из src/icons/*.svg (inline, красится через currentColor).
import { computed } from 'vue';
const files = import.meta.glob('../icons/*.svg', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
const props = defineProps<{ name: string; size?: number }>();
const svg = computed(() => {
  const raw = files['../icons/' + props.name + '.svg'];
  if (!raw) return '';
  // убираем комментарий лицензии, добавляем fill=currentColor и размеры
  return raw.replace(/<!--.*?-->/s, '').replace('<svg ', `<svg fill="currentColor" width="${props.size ?? 14}" height="${props.size ?? 14}" aria-hidden="true" `);
});
</script>
<template><span class="icon" v-html="svg"></span></template>

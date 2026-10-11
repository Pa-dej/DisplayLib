<script setup lang="ts">
// Код с подсветкой Jumper. readonly - просто <pre>; иначе поверх подсветки лежит прозрачная textarea
// (классический приём: текст textarea невидим, виден подсвеченный слой под ним; скролл синхронизирован).
import { computed, ref } from 'vue';
import { highlightJumper } from '../../highlight';
const props = defineProps<{ modelValue: string; readonly?: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>();
const html = computed(() => highlightJumper(props.modelValue) + '\n');
const pre = ref<HTMLElement | null>(null);
function sync(e: Event) { const t = e.target as HTMLTextAreaElement; if (pre.value) { pre.value.scrollTop = t.scrollTop; pre.value.scrollLeft = t.scrollLeft; } }
function onKey(e: KeyboardEvent) {
  // Tab - отступ 4 пробела, как в примерах
  if (e.key !== 'Tab') return;
  e.preventDefault();
  const t = e.target as HTMLTextAreaElement; const s = t.selectionStart, en = t.selectionEnd;
  const v = t.value.slice(0, s) + '    ' + t.value.slice(en);
  emit('update:modelValue', v);
  requestAnimationFrame(() => { t.selectionStart = t.selectionEnd = s + 4; });
}
</script>
<template>
  <pre v-if="readonly" class="code hl" v-html="html"></pre>
  <div v-else class="code-edit">
    <pre ref="pre" class="code hl" aria-hidden="true" v-html="html"></pre>
    <textarea class="code hl-input" :value="modelValue" spellcheck="false" autocapitalize="off" autocomplete="off" @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value); sync($event)" @scroll="sync" @keydown="onKey"></textarea>
  </div>
</template>

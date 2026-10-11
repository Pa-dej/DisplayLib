<script setup lang="ts">
// Однострочное поле текста, в которое Shift+Enter вставляет перевод строки (\n - новая строка в TextDisplay).
// Enter без Shift ничего не делает (как у input). Высота растёт по содержимому.
import { onMounted, ref, watch, nextTick } from 'vue';
const props = defineProps<{ modelValue: string; placeholder?: string }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void; (e: 'focus'): void }>();
const el = ref<HTMLTextAreaElement | null>(null);
function fit() { const t = el.value; if (!t) return; t.style.height = '28px'; t.style.height = Math.max(28, t.scrollHeight) + 'px'; }
function onInput(ev: Event) { emit('update:modelValue', (ev.target as HTMLTextAreaElement).value); fit(); }
function onKey(e: KeyboardEvent) {
  if (e.key !== 'Enter') return;
  if (!e.shiftKey) { e.preventDefault(); return; }
  // Shift+Enter: вставить \n вручную (единообразно во всех браузерах)
  e.preventDefault();
  const t = el.value!; const s = t.selectionStart, en = t.selectionEnd;
  const v = t.value.slice(0, s) + '\n' + t.value.slice(en);
  emit('update:modelValue', v);
  nextTick(() => { t.selectionStart = t.selectionEnd = s + 1; fit(); });
}
onMounted(fit);
watch(() => props.modelValue, () => nextTick(fit));
</script>
<template>
  <textarea ref="el" class="line" rows="1" :value="modelValue" :placeholder="placeholder" :title="'Shift+Enter - новая строка'" spellcheck="false" @focus="emit('focus')" @input="onInput" @keydown="onKey"></textarea>
</template>

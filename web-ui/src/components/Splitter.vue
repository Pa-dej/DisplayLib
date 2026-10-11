<script setup lang="ts">
// Перетаскиваемый разделитель панелей: меняет CSS-переменную на :root и хранит её в localStorage.
import { onMounted, ref } from 'vue';

const props = defineProps<{
  /** имя CSS-переменной, например --sidebar-w */
  cssVar: string;
  dir: 'v' | 'h';
  /** знак: +1 если панель растёт при движении вправо/вниз, -1 если наоборот */
  sign?: 1 | -1;
  min?: number;
  max?: number;
}>();

const active = ref(false);
const key = 'displaylib.panel.' + props.cssVar;
const root = document.documentElement;

function current(): number {
  return parseFloat(getComputedStyle(root).getPropertyValue(props.cssVar)) || 0;
}
function apply(v: number) {
  const lim = Math.min(props.max ?? 2000, Math.max(props.min ?? 80, v));
  root.style.setProperty(props.cssVar, lim + 'px');
}

onMounted(() => {
  const saved = localStorage.getItem(key);
  if (saved) apply(parseFloat(saved));
});

function onDown(e: PointerEvent) {
  if (e.button !== 0) return;
  e.preventDefault();
  const start = props.dir === 'v' ? e.clientX : e.clientY;
  const base = current();
  const sign = props.sign ?? 1;
  active.value = true;
  document.body.classList.add('resizing');
  document.body.style.cursor = props.dir === 'v' ? 'col-resize' : 'row-resize';
  const move = (ev: PointerEvent) => {
    const d = (props.dir === 'v' ? ev.clientX : ev.clientY) - start;
    apply(base + d * sign);
  };
  const up = () => {
    active.value = false;
    document.body.classList.remove('resizing');
    document.body.style.cursor = '';
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', up);
    localStorage.setItem(key, String(current()));
  };
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', up);
}
function reset() {
  root.style.removeProperty(props.cssVar);
  localStorage.removeItem(key);
}
</script>
<template>
  <div class="splitter" :class="[dir, { active }]" @pointerdown="onDown" @dblclick="reset" title="Тянуть - изменить размер, двойной клик - сбросить"></div>
</template>

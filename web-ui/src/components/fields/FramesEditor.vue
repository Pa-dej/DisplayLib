<script setup lang="ts">
// Редактор кадров текста - общий для кнопок экранов (frames / hoveredFrames) и частиц (вариант с кадрами):
// лента миниатюр (клик - выбрать, перетаскивание - порядок, ▶ - проиграть) и правка выбранного кадра.
import { computed, ref, watch } from 'vue';
import FrameStrip from './FrameStrip.vue';
import RichTextInput from './RichTextInput.vue';
import type { RichText } from '../../model/screen';

const props = defineProps<{ frames: RichText[]; interval: number; overLife?: boolean; tint?: string | null; placeholder?: string }>();
const emit = defineEmits<{ (e: 'set', k: number, v: RichText | null): void; (e: 'add'): void; (e: 'dup', k: number): void; (e: 'del', k: number): void; (e: 'move', from: number, to: number): void }>();

const sel = ref(0);
const cur = computed(() => Math.min(sel.value, Math.max(0, props.frames.length - 1)));
watch(() => props.frames.length, (n, old) => { if (old != null && n > old) sel.value = n - 1; else if (sel.value >= n) sel.value = Math.max(0, n - 1); });
function dup(k: number) { emit('dup', k); sel.value = k + 1; }
function del(k: number) { emit('del', k); sel.value = Math.max(0, k - 1); }
function move(f: number, t: number) { emit('move', f, t); sel.value = t; }
</script>

<template>
  <div class="frames-editor">
    <FrameStrip :items="frames" :selected="cur" :interval="interval" :over-life="!!overLife" :tint="tint"
      @select="sel = $event" @add="emit('add')" @dup="dup" @del="del" @move="move" />
    <div v-if="frames[cur] != null" class="fs-editor">
      <RichTextInput compact :model-value="frames[cur]" :placeholder="placeholder ?? 'Кадр'" @update:model-value="emit('set', cur, $event)" />
    </div>
  </div>
</template>

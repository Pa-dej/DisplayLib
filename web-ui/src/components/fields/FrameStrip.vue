<script setup lang="ts">
// Лента кадров текста частицы: миниатюры как в игре (тот же рендер текста), выбранный кадр правится
// ниже. Перетаскивание миниатюр меняет порядок; «▶» проигрывает ленту с частотой frameInterval.
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import Icon from '../Icon.vue';
import type { RichText } from '../../model/screen';
import { buildTextTexture } from '../../render/text';
import { spriteState } from '../../render/sprites';
import { useReorder } from '../../reorder';

const props = defineProps<{ items: RichText[]; selected: number; interval: number; overLife: boolean; tint?: string | null }>();
const emit = defineEmits<{ (e: 'select', i: number): void; (e: 'add'): void; (e: 'dup', i: number): void; (e: 'del', i: number): void; (e: 'move', from: number, to: number): void }>();

// миниатюры: canvas текста → data URL, кэш по содержимому
const cache = new Map<string, string>();
function thumb(t: RichText): string {
  void spriteState.revision;
  const key = JSON.stringify(t) + '|' + (props.tint ?? '') + '|' + spriteState.revision;
  let url = cache.get(key);
  if (url == null) {
    try {
      const c = props.tint;
      const tinted: RichText = !c ? t : typeof t === 'string' ? [{ text: t, color: c }] : t.map((x) => (x.color || x.player !== undefined ? x : { ...x, color: c }));
      const r = buildTextTexture(tinted, 'CENTERED', [0, 0, 0], 0, 4);
      url = (r.texture.image as HTMLCanvasElement).toDataURL();
      r.texture.dispose();
    } catch { url = ''; }
    if (cache.size > 300) cache.clear();
    cache.set(key, url);
  }
  return url;
}
const thumbs = computed(() => { void spriteState.revision; return props.items.map(thumb); });

const dnd = useReorder((from, to) => emit('move', from, to));

// проигрывание
const playing = ref(false);
const shown = ref(-1);
let timer = 0;
function toggle() {
  playing.value = !playing.value;
  clearInterval(timer);
  if (!playing.value) { shown.value = -1; return; }
  let i = 0;
  const ms = props.overLife ? 120 : Math.max(50, props.interval * 50);
  shown.value = 0;
  timer = window.setInterval(() => { i = (i + 1) % Math.max(1, props.items.length); shown.value = i; }, ms);
}
watch(() => props.items.length, () => { if (playing.value) { toggle(); toggle(); } });
onBeforeUnmount(() => clearInterval(timer));
const big = computed(() => thumbs.value[shown.value >= 0 ? shown.value : props.selected] ?? '');
</script>

<template>
  <div class="frame-strip">
    <div class="fs-preview checker-dark" :title="playing ? 'Проигрывается' : 'Выбранный кадр'">
      <img v-if="big" :src="big" alt="" />
      <button class="btn sm icon fs-play" :title="playing ? 'Стоп' : 'Проиграть кадры'" @click="toggle"><Icon :name="playing ? 'pause' : 'play'" :size="11" /></button>
    </div>
    <div class="fs-list">
      <button v-for="(u, i) in thumbs" :key="i" type="button" class="fs-thumb" :class="[{ on: i === selected, now: i === shown }, dnd.rowClass(i)]" data-row
        draggable="true" @dragstart="dnd.start(i, $event)" @dragend="dnd.end()" @dragover="dnd.over(i, $event)" @dragleave="dnd.leave(i)" @drop="dnd.drop(i, $event)"
        :title="`Кадр ${i + 1} - клик: править, перетащить: порядок`" @click="emit('select', i)">
        <img v-if="u" :src="u" alt="" draggable="false" />
        <span class="n">{{ i + 1 }}</span>
      </button>
      <button type="button" class="fs-thumb fs-add" title="Новый кадр (копия последнего)" @click="emit('add')"><Icon name="plus" /></button>
    </div>
    <div class="fs-actions">
      <span class="hint">Кадр {{ selected + 1 }} из {{ items.length }}</span>
      <span class="grow"></span>
      <button class="btn sm icon" title="Дублировать кадр" @click="emit('dup', selected)"><Icon name="clone" :size="12" /></button>
      <button class="btn sm icon" title="Сдвинуть влево" :disabled="selected <= 0" @click="emit('move', selected, selected - 1)"><Icon name="arrow-up" :size="11" style="transform: rotate(-90deg)" /></button>
      <button class="btn sm icon" title="Сдвинуть вправо" :disabled="selected >= items.length - 1" @click="emit('move', selected, selected + 1)"><Icon name="arrow-up" :size="11" style="transform: rotate(90deg)" /></button>
      <button class="btn sm icon" title="Убрать кадр" :disabled="items.length <= 1" @click="emit('del', selected)"><Icon name="trash" :size="12" /></button>
    </div>
  </div>
</template>

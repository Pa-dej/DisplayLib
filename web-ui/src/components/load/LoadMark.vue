<script setup lang="ts">
// Маркер у поля инспектора: эта опция заметно нагружает. Подсказка - насколько станет легче с дешёвым
// вариантом; клик применяет его (Ctrl+Z - вернуть).
import { computed } from 'vue';
import { particleLoadState, applyImpact, type ImpactKey } from '../../load/particleState';
import { loadColor } from '../../load/settings';

const props = defineProps<{ k: ImpactKey }>();
const imp = computed(() => particleLoadState.impacts[props.k] ?? null);
const level = computed(() => (!imp.value ? 0 : imp.value.drop >= 0.35 ? 2 : 1));
const title = computed(() => {
  const i = imp.value; if (!i) return '';
  return `Нагружает: ${i.detail || 'заметная доля нагрузки'}.\nЕсли ${i.suggestion}: шкала ${i.scoreFrom} → ${i.scoreTo}.`
    + (i.changesLook ? '\nЭффект при этом будет выглядеть иначе - это компромисс.' : '\nВнешне почти незаметно.')
    + '\nКлик - применить (Ctrl+Z - вернуть).';
});
</script>
<template>
  <button v-if="imp" type="button" class="load-mark" :class="'l' + level" :title="title" :style="{ '--mc': loadColor(level === 2 ? 0.95 : 0.6) }" @click.stop.prevent="applyImpact(k)">
    <span class="dot"></span>−{{ Math.round(imp.drop * 100) }}%
  </button>
</template>

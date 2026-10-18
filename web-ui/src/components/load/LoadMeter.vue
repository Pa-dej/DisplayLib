<script setup lang="ts">
// Кольцо нагрузки: заполняется от зелёного к красному по самому узкому месту (сервер, сеть, клиент).
// Клик - подробности: из чего складывается, советы, допущения (сколько игроков, запусков).
import { computed, onBeforeUnmount, ref } from 'vue';
import Icon from '../Icon.vue';
import type { LoadReport } from '../../load/load';
import { loadAssumptions, loadColor, loadLabel } from '../../load/settings';

const props = defineProps<{ report: LoadReport | null; kind: 'particles' | 'screen'; busy?: boolean }>();
const open = ref(false);
const root = ref<HTMLElement | null>(null);

const R = 19, C = 2 * Math.PI * R;
const score = computed(() => props.report?.score ?? 0);
const color = computed(() => loadColor(score.value));
const dash = computed(() => `${Math.max(0.02, score.value) * C} ${C}`);
const parts = computed(() => (props.report ? [
  { key: 'cpu', icon: 'cog', name: 'Сервер', ...props.report.cpu },
  { key: 'net', icon: 'exchange-alt', name: 'Сеть', ...props.report.net },
  { key: 'client', icon: 'cube', name: 'Клиент', ...props.report.client },
] : []));
const worst = computed(() => parts.value.reduce((a, b) => (b.score > a.score ? b : a), parts.value[0]));

function onDoc(e: PointerEvent) { if (!root.value?.contains(e.target as Node)) close(); }
function toggle() { open.value ? close() : (open.value = true, document.addEventListener('pointerdown', onDoc, true)); }
function close() { open.value = false; document.removeEventListener('pointerdown', onDoc, true); }
onBeforeUnmount(close);
function num(ev: Event, key: 'players' | 'launches', max: number) {
  const n = Math.round(Number((ev.target as HTMLInputElement).value));
  if (n >= 1) loadAssumptions[key] = Math.min(max, n);
}
</script>

<template>
  <div ref="root" class="load-meter" :class="{ open }">
    <button class="load-ring" :title="report ? `Нагрузка: ${loadLabel(score)} (≈${Math.round(score * 100)}%), узкое место - ${worst?.name.toLowerCase()}. Примерная оценка; клик - подробности` : 'Оценка нагрузки'" @click="toggle">
      <svg width="46" height="46" viewBox="0 0 46 46">
        <circle class="track" cx="23" cy="23" :r="R" />
        <circle class="arc" cx="23" cy="23" :r="R" :stroke="color" :stroke-dasharray="dash" transform="rotate(-90 23 23)" />
      </svg>
      <span class="val" :style="{ color }">{{ report ? Math.round(score * 100) : '…' }}</span>
      <span class="cap">{{ report ? loadLabel(score) : 'нагрузка' }}</span>
    </button>
    <div v-if="open && report" class="load-pop">
      <div class="load-head">
        <b>Нагрузка: <span :style="{ color }">{{ loadLabel(score) }}</span></b>
        <span class="hint">≈ прикидка{{ busy ? ', считаю…' : '' }}</span>
        <button class="btn sm icon" title="Закрыть" @click="close"><Icon name="times" /></button>
      </div>
      <div class="load-parts">
        <div v-for="p in parts" :key="p.key" class="load-part" :class="{ worst: p.key === worst?.key }">
          <span class="nm"><Icon :name="p.icon" :size="11" /> {{ p.name }}</span>
          <span class="bar"><span :style="{ width: Math.max(3, p.score * 100) + '%', background: loadColor(p.score) }"></span></span>
          <span class="v">{{ p.value }}</span>
          <span class="d hint">{{ p.detail }}</span>
        </div>
      </div>
      <div v-if="report.factors.length" class="load-sec">
        <div class="t">Время сервера складывается из</div>
        <div v-for="f in report.factors.slice(0, 5)" :key="f.label" class="load-factor" :title="f.tip ?? ''">
          <span class="fl">{{ f.label }}</span>
          <span class="fb"><span :style="{ width: Math.max(2, f.share * 100) + '%' }"></span></span>
          <span class="fp">{{ Math.round(f.share * 100) }}%</span>
        </div>
      </div>
      <div v-if="report.tips.length || report.factors.some((f) => f.tip && f.share > 0.25)" class="load-sec">
        <div class="t">Как облегчить</div>
        <ul>
          <li v-for="t in [...report.factors.filter((f) => f.tip && f.share > 0.25).map((f) => f.tip!), ...report.tips]" :key="t">{{ t }}</li>
        </ul>
      </div>
      <div class="load-sec assume">
        <label v-if="kind === 'particles'" title="Сколько игроков видят эффект (при viewers: owner - всегда 1)">Игроков рядом <input type="number" min="1" max="200" :value="loadAssumptions.players" @change="num($event, 'players', 200)" /></label>
        <label v-else title="Приватный: у скольких игроков экран открыт одновременно. Публичный: сколько игроков рядом">Игроков <input type="number" min="1" max="500" :value="loadAssumptions.players" @change="num($event, 'players', 500)" /></label>
        <label v-if="kind === 'particles'" title="Сколько запусков эффекта идёт одновременно">Запусков <input type="number" min="1" max="100" :value="loadAssumptions.launches" @change="num($event, 'launches', 100)" /></label>
      </div>
      <p v-for="n in report.notes" :key="n" class="hint note">{{ n }}</p>
    </div>
  </div>
</template>

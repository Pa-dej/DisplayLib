<script setup lang="ts">
// Предпросмотр эффекта: та же физика и те же кадры, что у плагина; по умолчанию - как увидит игрок.
import { onMounted, onBeforeUnmount, ref, watch, computed } from 'vue';
import Icon from '../Icon.vue';
import Splitter from '../Splitter.vue';
import CodeView from '../fields/CodeView.vue';
import { ParticleScene, type PlayOptions } from '../../particles/scene';
import type { Stats } from '../../particles/runtime';
import { pstate, currentEffect, currentEffectText, currentEffectErrors } from '../../particles/store';
import { cloneEffect } from '../../particles/model';

const canvas = ref<HTMLCanvasElement | null>(null);
let scene: ParticleScene | null = null;
const OPTS_KEY = 'displaylib.particles.view';
function loadOpts(): Partial<PlayOptions> { try { return JSON.parse(localStorage.getItem(OPTS_KEY) || '{}'); } catch { return {}; } }
const saved = loadOpts();
const playing = ref(true);
const loop = ref(saved.loop ?? true);
const speed = ref(saved.speed ?? 1);
const asGame = ref(saved.asGame ?? true);
const wall = ref(saved.wall ?? false);
const height = ref(saved.height ?? 1.5);
const seed = ref(1);
const stats = ref<(Stats & { tick: number }) | null>(null);
const tab = ref<'jmc' | 'none'>('jmc');

const forever = computed(() => currentEffect.value?.emitter.duration === -1);

onMounted(() => {
  scene = new ParticleScene(canvas.value!);
  scene.setOptions({ loop: loop.value, speed: speed.value, asGame: asGame.value, wall: wall.value, height: height.value, seed: seed.value, playing: playing.value });
  scene.resetCamera();
  scene.onStats = (s) => { stats.value = s; };
  scene.setEffect(currentEffect.value ? cloneEffect(currentEffect.value) : null);
  (window as any).__dlp = { scene, pstate };
});
onBeforeUnmount(() => scene?.dispose());

// любая правка эффекта - перезапуск (с тем же зерном: разница видна сразу, а не из-за другого разброса)
let pending = 0;
watch(() => [pstate.revision, pstate.current], () => {
  cancelAnimationFrame(pending);
  pending = requestAnimationFrame(() => scene?.setEffect(currentEffect.value ? cloneEffect(currentEffect.value) : null));
});
watch([playing, loop, speed, asGame, wall, height, seed], () => {
  scene?.setOptions({ playing: playing.value, loop: loop.value, speed: speed.value, asGame: asGame.value, wall: wall.value, height: height.value, seed: seed.value });
  try { localStorage.setItem(OPTS_KEY, JSON.stringify({ loop: loop.value, speed: speed.value, asGame: asGame.value, wall: wall.value, height: height.value })); } catch { /* */ }
});

function restart() { scene?.restart(); playing.value = true; }
function reseed() { seed.value = (seed.value % 100000) + 1 + Math.floor(Math.random() * 1000); }
function step() { playing.value = false; scene?.stepOnce(); }
const SPEEDS = [0.1, 0.25, 0.5, 1, 2];
</script>

<template>
  <div class="preview" tabindex="0">
    <canvas ref="canvas"></canvas>
    <div class="preview-toolbar">
      <span class="btn-group">
        <button class="btn sm icon" :title="playing ? 'Пауза' : 'Играть'" @click="playing = !playing"><Icon :name="playing ? 'pause' : 'play'" /></button>
        <button class="btn sm icon" title="Один тик (1/20 с)" @click="step"><Icon name="step-forward" /></button>
        <button class="btn sm icon" title="Запустить заново" @click="restart"><Icon name="sync-alt" /></button>
        <button v-if="forever" class="btn sm icon" title="Остановить испускание (как stop): выпущенные доживают" @click="scene?.stopEmitting()"><Icon name="stop" /></button>
      </span>
      <span class="btn-group" title="Скорость воспроизведения">
        <button v-for="s in SPEEDS" :key="s" class="btn sm" :class="{ primary: speed === s }" @click="speed = s">{{ s }}×</button>
      </span>
      <label class="btn sm" title="Повторять эффект, когда он закончится"><input type="checkbox" v-model="loop" /> Повтор</label>
      <button class="btn sm" title="Другой случайный разброс" @click="reseed"><Icon name="random" /> Разброс</button>
      <label class="btn sm" title="Как в игре: кадры плагина и интерполяция клиента (с задержкой на тик). Выключено - точная траектория по тикам, без кадров"><input type="checkbox" v-model="asGame" /> <Icon name="street-view" /> Как в игре</label>
      <label class="btn sm" title="Стена из блоков в 2 блоках по +X - посмотреть отскок (collide)"><input type="checkbox" v-model="wall" /> <Icon name="th" /> Стена</label>
      <label class="btn sm" title="Высота точки запуска над полом, блоков">↕ <input type="number" v-model.number="height" min="0" max="20" step="0.25" style="width:56px;height:20px;padding:0 4px" /></label>
      <button class="btn sm" title="Камера по умолчанию" @click="scene?.resetCamera()"><Icon name="camera" /></button>
    </div>
    <div v-if="stats" class="pstats">
      <div><b>{{ stats.alive }}</b> частиц</div>
      <div title="Пакетов в секунду на одного игрока: спавн, движение, метаданные, удаление"><b>{{ stats.packetsPerSecond }}</b> пакетов/с</div>
      <div title="Среднее число тиков между пакетами движения (больше - меньше трафика)">кадр ≈ <b>{{ stats.avgSegment.toFixed(1) }}</b> т</div>
      <div class="hint">{{ (stats.tick / 20).toFixed(1) }} с · всего {{ stats.spawned }}</div>
    </div>
    <div v-if="currentEffectErrors.length" class="perrors callout danger">{{ currentEffectErrors.join('; ') }}</div>
    <div class="preview-legend">ЛКМ - вращать · СКМ/ПКМ - сдвиг · колесо - зум · пол - твёрдые блоки (y &lt; 0) для collide</div>
  </div>
  <Splitter v-if="tab !== 'none'" css-var="--codebar-h" dir="h" :sign="-1" :min="80" :max="900" />
  <div class="codebar" :class="{ collapsed: tab === 'none' }">
    <div class="tabs">
      <span class="tab" :class="{ active: tab === 'jmc' }" @click="tab = 'jmc'"><Icon name="file-alt" /> particles/{{ currentEffect?.id ?? '' }}.jmc</span>
      <span class="tab" :class="{ active: tab === 'none' }" @click="tab = 'none'"><Icon name="chevron-down" /> Скрыть</span>
    </div>
    <CodeView v-if="tab === 'jmc'" :model-value="currentEffectText" readonly />
  </div>
</template>

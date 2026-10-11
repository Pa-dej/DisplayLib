<script setup lang="ts">
import Icon from './Icon.vue';
import type { HoverAnimation } from '../model/screen';
import { defaultHoverAnimation } from '../model/screen';
import VecInput from './fields/VecInput.vue';
import { snapshot, touch } from '../store';

const props = defineProps<{ anim: HoverAnimation; nested?: boolean }>();
const emit = defineEmits<{ (e: 'remove'): void }>();
const TYPES = ['PRESET', 'SCALE', 'TRANSLATE', 'ROTATE', 'TRANSFORM', 'COMBINED'] as const;
const EASINGS = ['LINEAR', 'EASE_IN', 'EASE_OUT', 'EASE_IN_OUT', 'BOUNCE', 'ELASTIC'] as const;
const PRESETS = ['BOUNCE', 'SCALE', 'PULSE', 'GLOW', 'WOBBLE', 'LIFT'] as const;
function set<K extends keyof HoverAnimation>(k: K, v: HoverAnimation[K]) { snapshot(); props.anim[k] = v; touch(); }
function addEffect() { snapshot(); props.anim.effects.push({ ...defaultHoverAnimation(), type: 'SCALE' }); touch(); }
function removeEffect(i: number) { snapshot(); props.anim.effects.splice(i, 1); touch(); }
</script>
<template>
  <div :style="nested ? 'padding-left:10px;border-left:2px solid var(--vp-c-divider);margin:6px 0' : ''">
    <div class="field"><label>Тип</label>
      <div class="row"><select class="grow" :value="anim.type" @change="set('type', ($event.target as HTMLSelectElement).value as any)"><option v-for="t in TYPES" :key="t">{{ t }}</option></select>
        <button class="btn sm icon danger" title="Убрать анимацию" @click="emit('remove')"><Icon name="times" /></button></div></div>
    <template v-if="anim.type === 'PRESET'">
      <div class="field"><label>Пресет</label><select :value="anim.preset" @change="set('preset', ($event.target as HTMLSelectElement).value as any)"><option v-for="p in PRESETS" :key="p">{{ p }}</option></select></div>
      <div class="field"><label>Сила</label><input type="number" step="0.05" :value="anim.intensity" @focus="snapshot()" @input="anim.intensity = Number(($event.target as HTMLInputElement).value); touch()" /></div>
      <div v-if="anim.preset === 'GLOW'" class="hint">GLOW работает только у предметных кнопок</div>
    </template>
    <div v-if="anim.type === 'SCALE' || anim.type === 'TRANSFORM'" class="field"><label>Масштаб</label><VecInput v-model="anim.scale" :labels="['x', 'y', 'z']" /></div>
    <div v-if="anim.type === 'TRANSLATE'" class="field"><label>Смещение</label><VecInput v-model="anim.offset" :labels="['x', 'y', 'z']" :step="0.01" /></div>
    <template v-if="anim.type === 'ROTATE'">
      <div class="field"><label>Градусы</label><VecInput v-model="anim.rotation" :labels="['x', 'y', 'z']" :step="1" /></div>
      <div class="field"><label>Ось</label><VecInput v-model="anim.axis" :labels="['x', 'y', 'z']" :step="1" /></div>
    </template>
    <div v-if="anim.type === 'TRANSFORM'" class="field"><label>Translation</label><VecInput v-model="anim.translation" :labels="['x', 'y', 'z']" :step="0.01" /></div>
    <div class="field"><label>Длительность</label><div class="row"><input type="number" min="1" step="1" :value="anim.duration" @focus="snapshot()" @input="anim.duration = Math.max(1, Math.round(Number(($event.target as HTMLInputElement).value))); touch()" /><span class="hint">Тиков (в игре x2)</span></div></div>
    <div class="field"><label>Easing</label><select :value="anim.easing" @change="set('easing', ($event.target as HTMLSelectElement).value as any)"><option v-for="e in EASINGS" :key="e">{{ e }}</option></select></div>
    <div class="field"><label>Задержка</label><input type="number" min="0" step="1" :value="anim.delay" @focus="snapshot()" @input="anim.delay = Math.max(0, Math.round(Number(($event.target as HTMLInputElement).value))); touch()" /></div>
    <div class="field"><label></label><label class="row"><input type="checkbox" :checked="anim.reverseOnExit" @change="set('reverseOnExit', ($event.target as HTMLInputElement).checked)" /> Возвращать при уходе взгляда</label></div>
    <div class="field"><label></label><label class="row"><input type="checkbox" :checked="anim.loop" @change="set('loop', ($event.target as HTMLInputElement).checked)" /> Зациклить
      <input v-if="anim.loop" type="number" step="1" style="width:70px" :value="anim.loopCount" title="-1 = бесконечно" @focus="snapshot()" @input="anim.loopCount = Math.round(Number(($event.target as HTMLInputElement).value)); touch()" /><span v-if="anim.loop" class="hint">-1 = ∞</span></label></div>
    <template v-if="anim.type === 'COMBINED'">
      <div class="hint">Вложенные эффекты:</div>
      <AnimationEditor v-for="(e, i) in anim.effects" :key="i" :anim="e" nested @remove="removeEffect(i)" />
      <button class="btn sm" @click="addEffect"><Icon name="plus" /> Эффект</button>
    </template>
  </div>
</template>

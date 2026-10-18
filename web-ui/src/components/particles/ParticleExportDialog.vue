<script setup lang="ts">
// Быстрый экспорт эффекта: текст particles/<id>.jmc с подсветкой - скопировать или скачать (как у экранов).
import { computed, onMounted, ref } from 'vue';
import Icon from '../Icon.vue';
import CodeView from '../fields/CodeView.vue';
import { currentEffect, currentEffectText, currentEffectErrors, saveCurrentEffect, saveAllEffects, pstate } from '../../particles/store';

const emit = defineEmits<{ (e: 'close'): void }>();
const copied = ref(false);
const text = computed(() => currentEffectText.value);
const fileName = computed(() => (currentEffect.value?.id.split('/').pop() ?? 'effect') + '.jmc');
const command = computed(() => '/displaylib particle ' + (currentEffect.value?.id ?? ''));
const root = ref<HTMLElement | null>(null);
onMounted(() => root.value?.focus());

async function copy(t = text.value) {
  try { await navigator.clipboard.writeText(t); }
  catch {
    const ta = document.createElement('textarea');
    ta.value = t; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove();
  }
  copied.value = true; setTimeout(() => (copied.value = false), 1500);
}
function save() { saveCurrentEffect(); emit('close'); }
async function zip() { await saveAllEffects(); emit('close'); }
</script>
<template>
  <div ref="root" class="modal-bg" tabindex="-1" @click.self="emit('close')" @keydown.esc="emit('close')">
    <div class="modal">
      <h2>Экспорт <code>particles/{{ fileName }}</code></h2>
      <div v-if="currentEffectErrors.length" class="callout danger"><b>Плагин не примет:</b> {{ currentEffectErrors.join('; ') }}</div>
      <CodeView class="export-code" :model-value="text" readonly />
      <div class="row hint">Положите файл в <code>plugins/DisplayLib/particles/</code>, затем <code>/displaylib reload</code> и
        <code class="cmd" title="Скопировать команду" @click="copy(command)">{{ command }}</code></div>
      <div class="row" style="justify-content:flex-end">
        <span class="hint grow">{{ text.split('\n').length }} строк</span>
        <button class="btn" @click="emit('close')">Закрыть</button>
        <button v-if="pstate.docs.length > 1" class="btn" @click="zip" title="Все открытые эффекты: particles/*.jmc"><Icon name="layer-group" /> Zip ({{ pstate.docs.length }})</button>
        <button class="btn" @click="copy()" :disabled="!text"><Icon :name="copied ? 'check' : 'copy'" /> {{ copied ? 'Скопировано' : 'Копировать' }}</button>
        <button class="btn primary" @click="save" :disabled="!text"><Icon name="download" /> Скачать</button>
      </div>
    </div>
  </div>
</template>
<style scoped>
.export-code { min-height: 360px; max-height: 60vh; resize: vertical; border: 1px solid var(--vp-c-divider); border-radius: 8px; }
.cmd { cursor: pointer; }
.cmd:hover { color: var(--vp-c-brand-1); }
</style>

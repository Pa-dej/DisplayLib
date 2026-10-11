<script setup lang="ts">
import Icon from './Icon.vue';
// Экспорт экрана: окно с текстом .jmc (и скриптом, если есть) - скопировать или скачать.
import { computed, ref } from 'vue';
import { currentDoc, currentScreen, notify, scriptTextOf, scriptNameOf } from '../store';
import { screenToJmc } from '../jmc/writer';
import { download } from '../fs';

const emit = defineEmits<{ (e: 'close'): void }>();
const verbose = ref(false);
const tab = ref<'jmc' | 'jmp'>('jmc');
const copied = ref(false);

const fileName = computed(() => tab.value === 'jmc' ? (currentScreen.value?.id ?? 'screen') + '.jmc' : currentDoc.value ? scriptNameOf(currentDoc.value) : 'script.jmp');
const jmc = computed(() => currentDoc.value ? screenToJmc(currentDoc.value.screen, { verbose: verbose.value }) : '');
const script = computed(() => currentDoc.value ? scriptTextOf(currentDoc.value) : '');
const text = computed(() => tab.value === 'jmc' ? jmc.value : script.value);

async function copy() {
  try {
    await navigator.clipboard.writeText(text.value);
    copied.value = true; setTimeout(() => (copied.value = false), 1500);
  } catch {
    const ta = document.getElementById('export-text') as HTMLTextAreaElement | null;
    ta?.select(); document.execCommand('copy');
    copied.value = true; setTimeout(() => (copied.value = false), 1500);
  }
}
function save() { download(fileName.value, text.value); notify('Файл ' + fileName.value + ' скачан'); }
function onKey(e: KeyboardEvent) { if (e.key === 'Escape') emit('close'); }
</script>
<template>
  <div class="modal-bg" @click.self="emit('close')" @keydown="onKey">
    <div class="modal">
      <h2>Экспорт <code>{{ fileName }}</code></h2>
      <div class="row">
        <div class="tabs" style="border:0;padding:0">
          <span class="tab" :class="{ active: tab === 'jmc' }" @click="tab = 'jmc'">Экран .jmc</span>
          <span class="tab" :class="{ active: tab === 'jmp' }" @click="tab = 'jmp'">Скрипт {{ currentDoc ? scriptNameOf(currentDoc) : '' }}</span>
        </div>
        <span class="grow"></span>
        <label v-if="tab === 'jmc'" class="row hint" title="Выписать и поля со значениями по умолчанию"><input type="checkbox" v-model="verbose" /> Все поля</label>
      </div>
      <textarea id="export-text" class="code" style="min-height:360px;resize:vertical" :value="text" readonly spellcheck="false" @focus="($event.target as HTMLTextAreaElement).select()"></textarea>
      <div class="row" style="justify-content:flex-end">
        <span class="hint grow">{{ text.length }} символов, {{ text.split('\n').length }} строк</span>
        <button class="btn" @click="emit('close')">Закрыть</button>
        <button class="btn" @click="copy" :disabled="!text"><Icon :name="copied ? 'check' : 'copy'" /> {{ copied ? 'Скопировано' : 'Копировать' }}</button>
        <button class="btn primary" @click="save" :disabled="!text"><Icon name="download" /> Скачать</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import Icon from './components/Icon.vue';
import { onMounted, ref } from 'vue';
import Sidebar from './components/Sidebar.vue';
import Preview from './components/Preview.vue';
import Inspector from './components/Inspector.vue';
import ImportDialog from './components/ImportDialog.vue';
import Splitter from './components/Splitter.vue';
import ExportDialog from './components/ExportDialog.vue';
import { state, openFolder, saveCurrent, saveAll, undo, redo, newScreen, currentDoc, notify, fail } from './store';
import { fsSupported } from './fs';
import { buildIconPack } from './pack';
import { font } from './render/font';
import { loadSheet } from './render/items';
import { loadSpriteIndex } from './render/sprites';

const importOpen = ref(false);
const exportOpen = ref(false);
const packBusy = ref(false);
async function makePack() {
  packBusy.value = true;
  try {
    const r = await buildIconPack((m) => notify(m));
    const url = URL.createObjectURL(r.blob);
    const a = document.createElement('a'); a.href = url; a.download = 'DisplayLib-icons.zip'; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
    notify(`Ресурспак: ${r.icons} иконок`);
  } catch (e) { fail('Не удалось собрать ресурспак: ' + (e as Error).message); }
  finally { packBusy.value = false; }
}
const ready = ref(false);
const dark = ref(document.documentElement.classList.contains('dark'));

function toggleTheme() {
  dark.value = !dark.value;
  document.documentElement.classList.toggle('dark', dark.value);
  localStorage.setItem('displaylib.theme', dark.value ? 'dark' : 'light');
}


onMounted(async () => {
  await Promise.all([font.load(), loadSheet(), loadSpriteIndex()]);
  ready.value = true;
  if (fsSupported) await openFolder(true);
  if (!state.docs.length) newScreen('new_screen');
  window.addEventListener('keydown', (e) => {
    const tag = (e.target as HTMLElement)?.tagName;
    const typing = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT';
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') { e.preventDefault(); e.shiftKey ? saveAll() : saveCurrent(); }
    else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z' && !typing) { e.preventDefault(); e.shiftKey ? redo() : undo(); }
    else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y' && !typing) { e.preventDefault(); redo(); }
  });
});
</script>

<template>
  <div class="layout">
    <header class="topbar">
      <div class="brand"><span class="logo"><svg viewBox="0 0 512 512" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M488 352h-40V96c0-17.67-14.33-32-32-32H192v96h160v328c0 13.25 10.75 24 24 24h48c13.25 0 24-10.75 24-24v-40h40c13.25 0 24-10.75 24-24v-48c0-13.26-10.75-24-24-24M160 24c0-13.26-10.75-24-24-24H88C74.75 0 64 10.74 64 24v40H24C10.75 64 0 74.74 0 88v48c0 13.25 10.75 24 24 24h40v256c0 17.67 14.33 32 32 32h224v-96H160z"/></svg></span>DisplayLib <span class="badge brand">Screen Editor</span></div>
      <div class="btn-group">
        <button class="btn" v-if="fsSupported" @click="openFolder(false)" :disabled="state.busy"><Icon name="folder-open" /> {{ state.folder ? state.folderName : 'Открыть папку плагина' }}</button>
        <button class="btn" @click="newScreen()"><Icon name="plus" /> Экран</button>
        <button class="btn" @click="importOpen = true"><Icon name="file-import" /> Импорт .jmc</button>
        <button class="btn" @click="exportOpen = true" :disabled="!currentDoc"><Icon name="file-export" /> Экспорт</button>
        <button class="btn" @click="makePack" :disabled="packBusy" :title="'Собрать DisplayLib-icons.zip: иконки предметов для 3D-блоков (item/barrel и т.п.)'"><Icon name="box" /> Ресурспак</button>
        <button class="btn primary" @click="saveCurrent" :disabled="!currentDoc"><Icon :name="state.folder ? 'save' : 'download'" /> {{ state.folder ? 'Сохранить' : 'Скачать' }} <kbd style="opacity:.7">Ctrl+S</kbd></button>
      </div>
      <div class="spacer"></div>
      <span class="status" :class="{ error: !!state.error }">{{ state.error || state.message }}</span>
      <span class="badge" :class="font.loaded ? 'brand' : 'warn'" :title="font.loaded ? 'Шрифт Minecraft из assets/minecraft' : 'Положите font/*.json и textures/font/*.png в public/assets/minecraft (см. README)'">{{ font.loaded ? 'Шрифт MC' : 'Шрифт: запасной' }}</span>
      <button class="btn icon" @click="toggleTheme" :title="dark ? 'Светлая тема' : 'Тёмная тема'"><Icon :name="dark ? 'sun' : 'moon'" /></button>
    </header>
    <Sidebar />
    <Splitter css-var="--sidebar-w" dir="v" :min="160" :max="600" />
    <main class="main"><Preview v-if="ready" /></main>
    <Splitter css-var="--inspector-w" dir="v" :sign="-1" :min="260" :max="700" />
    <Inspector />
    <ImportDialog v-if="importOpen" @close="importOpen = false" />
    <ExportDialog v-if="exportOpen" @close="exportOpen = false" />
  </div>
</template>

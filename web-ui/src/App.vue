<script setup lang="ts">
import Icon from './components/Icon.vue';
import { onMounted, ref, computed } from 'vue';
import Sidebar from './components/Sidebar.vue';
import Preview from './components/Preview.vue';
import Inspector from './components/Inspector.vue';
import ImportDialog from './components/ImportDialog.vue';
import AssetBrowser from './components/AssetBrowser.vue';
import Splitter from './components/Splitter.vue';
import ExportDialog from './components/ExportDialog.vue';
import { installClipboard } from './clipboard';
import { state, saveCurrent, saveAll, undo, redo, canUndo, canRedo, newScreen, currentDoc, restoreSession, notify, fail } from './store';
import { font } from './render/font';
import { loadSheet } from './render/items';
import { loadSpriteIndex } from './render/sprites';
import { editorMode } from './mode';
import ParticleSidebar from './components/particles/ParticleSidebar.vue';
import ParticleInspector from './components/particles/ParticleInspector.vue';
import ParticlePreview from './components/particles/ParticlePreview.vue';
import ParticleImportDialog from './components/particles/ParticleImportDialog.vue';
import ParticleExportDialog from './components/particles/ParticleExportDialog.vue';
import { pstate, addEffect, saveCurrentEffect, saveAllEffects, restoreEffects, pcanUndo, pcanRedo, currentEffect } from './particles/store';

const particles = computed(() => editorMode.value === 'particles');
const undoable = computed(() => (particles.value ? pcanUndo.value : canUndo.value));
const redoable = computed(() => (particles.value ? pcanRedo.value : canRedo.value));
function save(all: boolean) { if (particles.value) { all ? saveAllEffects() : saveCurrentEffect(); } else { all ? saveAll() : saveCurrent(); } }

const importOpen = ref(false);
const assetsOpen = ref(false);
const exportOpen = ref(false);
const pimportOpen = ref(false);
const pexportOpen = ref(false);
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
  restoreSession();
  if (!state.docs.length) newScreen('new_screen');
  restoreEffects();
  installClipboard();
  window.addEventListener('keydown', (e) => {
    // e.code не зависит от раскладки (KeyZ и на русской); в textarea (скрипт, импорт) - родной undo браузера
    const tag = (e.target as HTMLElement)?.tagName;
    const inTextarea = tag === 'TEXTAREA';
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.code === 'KeyS') { e.preventDefault(); save(e.shiftKey); }
    else if (mod && e.code === 'KeyE') { e.preventDefault(); if (particles.value) { if (currentEffect.value) pexportOpen.value = true; } else if (currentDoc.value) exportOpen.value = true; }
    else if (mod && e.code === 'KeyZ' && !inTextarea) { e.preventDefault(); e.shiftKey ? redo() : undo(); }
    else if (mod && e.code === 'KeyY' && !inTextarea) { e.preventDefault(); redo(); }
  });
});
</script>

<template>
  <div class="layout">
    <header class="topbar">
      <div class="brand"><span class="logo"><svg viewBox="0 0 512 512" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M488 352h-40V96c0-17.67-14.33-32-32-32H192v96h160v328c0 13.25 10.75 24 24 24h48c13.25 0 24-10.75 24-24v-40h40c13.25 0 24-10.75 24-24v-48c0-13.26-10.75-24-24-24M160 24c0-13.26-10.75-24-24-24H88C74.75 0 64 10.74 64 24v40H24C10.75 64 0 74.74 0 88v48c0 13.25 10.75 24 24 24h40v256c0 17.67 14.33 32 32 32h224v-96H160z"/></svg></span>DisplayLib <span class="badge brand">{{ particles ? 'Particle Editor' : 'Screen Editor' }}</span></div>
      <div class="seg mode-switch">
        <button :class="{ on: !particles }" @click="editorMode = 'screens'" title="Интерактивные экраны (screens/*.jmc)"><Icon name="object-group" /> Экраны</button>
        <button :class="{ on: particles }" @click="editorMode = 'particles'" title="Эффекты частиц (particles/*.jmc)"><Icon name="fire" /> Частицы</button>
      </div>
      <div v-if="!particles" class="btn-group">
        <button class="btn" @click="newScreen()"><Icon name="plus" /> Экран</button>
        <button class="btn" @click="importOpen = true"><Icon name="file-import" /> Импорт .jmc</button>
        <button class="btn" @click="exportOpen = true" :disabled="!currentDoc"><Icon name="file-export" /> Экспорт</button>
        <button class="btn" @click="assetsOpen = true" :disabled="!currentDoc" title="Все спрайты клиента по категориям; клик добавляет спрайт на экран"><Icon name="icons" /> Ассеты</button>
        <button class="btn primary" @click="saveCurrent" :disabled="!currentDoc" title="Скачать screens/<id>.jmc (и scripts/<id>.jmp, если нужен)"><Icon name="download" /> Скачать <kbd style="opacity:.7">Ctrl+S</kbd></button>
        <button class="btn" @click="saveAll" :disabled="!state.docs.length" title="Все экраны и скрипты одним zip: screens/*.jmc + scripts/*.jmp (Ctrl+Shift+S)"><Icon name="layer-group" /> Zip</button>
      </div>
      <div v-else class="btn-group">
        <button class="btn" @click="addEffect()"><Icon name="plus" /> Эффект</button>
        <button class="btn" @click="pimportOpen = true"><Icon name="file-import" /> Импорт .jmc</button>
        <button class="btn" @click="pexportOpen = true" :disabled="!currentEffect" title="Текст .jmc с подсветкой: скопировать или скачать (Ctrl+E)"><Icon name="file-export" /> Экспорт</button>
        <button class="btn primary" @click="saveCurrentEffect" :disabled="!currentEffect" title="Скачать <id>.jmc - положить в plugins/DisplayLib/particles"><Icon name="download" /> Скачать <kbd style="opacity:.7">Ctrl+S</kbd></button>
        <button class="btn" @click="saveAllEffects" :disabled="!pstate.docs.length" title="Все эффекты одним zip: particles/*.jmc (Ctrl+Shift+S)"><Icon name="layer-group" /> Zip</button>
      </div>
      <div class="btn-group" style="margin-left:6px">
        <button class="btn icon" @click="undo()" :disabled="!undoable" title="Отменить (Ctrl+Z)"><Icon name="undo" /></button>
        <button class="btn icon" @click="redo()" :disabled="!redoable" title="Вернуть (Ctrl+Y / Ctrl+Shift+Z)"><Icon name="redo" /></button>
      </div>
      <div class="spacer"></div>
      <span class="status" :class="{ error: !!state.error }">{{ state.error || state.message }}</span>
      <button class="btn icon" @click="toggleTheme" :title="dark ? 'Светлая тема' : 'Тёмная тема'"><Icon :name="dark ? 'sun' : 'moon'" /></button>
    </header>
    <template v-if="!particles">
      <Sidebar />
      <Splitter css-var="--sidebar-w" dir="v" :min="160" :max="600" />
      <main class="main"><Preview v-if="ready" /></main>
      <Splitter css-var="--inspector-w" dir="v" :sign="-1" :min="260" :max="700" />
      <Inspector />
    </template>
    <template v-else>
      <ParticleSidebar />
      <Splitter css-var="--sidebar-w" dir="v" :min="160" :max="600" />
      <main class="main"><ParticlePreview v-if="ready" /></main>
      <Splitter css-var="--inspector-w" dir="v" :sign="-1" :min="260" :max="700" />
      <ParticleInspector />
    </template>
    <ParticleImportDialog v-if="pimportOpen" @close="pimportOpen = false" />
    <ParticleExportDialog v-if="pexportOpen" @close="pexportOpen = false" />
    <ImportDialog v-if="importOpen" @close="importOpen = false" />
    <AssetBrowser v-if="assetsOpen" @close="assetsOpen = false" />
    <ExportDialog v-if="exportOpen" @close="exportOpen = false" />
  </div>
</template>

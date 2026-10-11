<script setup lang="ts">
import { ref } from 'vue';
import { loadScreenText, state, notify, touch } from '../store';
const emit = defineEmits<{ (e: 'close'): void }>();
const text = ref('');
const name = ref(''); // пусто - имя файла по id экрана из текста
const error = ref('');
function doImport() {
  try {
    const n = name.value.trim();
    const doc = loadScreenText(n ? (n.endsWith('.jmc') ? n : n + '.jmc') : null, text.value);
    doc.savedText = null; doc.dirty = true; // импортированный - новый, ещё не на диске
    state.current = doc.path; touch();
    notify('Импортирован экран ' + doc.screen.id + (doc.warnings.length ? ' (с предупреждениями)' : ''));
    emit('close');
  } catch (e) { error.value = (e as Error).message; }
}
async function fromFile(ev: Event) {
  const f = (ev.target as HTMLInputElement).files?.[0]; if (!f) return;
  name.value = f.name; text.value = await f.text();
}
</script>
<template>
  <div class="modal-bg" @click.self="emit('close')">
    <div class="modal">
      <h2>Импорт экрана .jmc</h2>
      <div class="row"><input type="text" v-model="name" style="width:220px" placeholder="Имя файла: по id экрана" /><input type="file" accept=".jmc,.txt" @change="fromFile" /></div>
      <textarea v-model="text" placeholder='String id = "shop"; ... dyn widgets = [ ... ];' spellcheck="false"></textarea>
      <div v-if="error" class="callout danger">{{ error }}</div>
      <div class="row" style="justify-content:flex-end"><button class="btn" @click="emit('close')">Отмена</button><button class="btn primary" @click="doImport" :disabled="!text.trim()">Импортировать</button></div>
    </div>
  </div>
</template>

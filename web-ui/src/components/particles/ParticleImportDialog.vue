<script setup lang="ts">
import { ref } from 'vue';
import { loadEffectText } from '../../particles/store';
import { notify } from '../../store';
const emit = defineEmits<{ (e: 'close'): void }>();
const text = ref('');
const name = ref('');
const error = ref('');
function doImport() {
  try {
    const d = loadEffectText(text.value, name.value.trim() || 'imported.jmc');
    notify('Импортирован эффект ' + d.effect.id + (d.warnings.length ? ' (с предупреждениями)' : ''));
    emit('close');
  } catch (e) { error.value = (e as Error).message; }
}
async function fromFiles(ev: Event) {
  const fs = [...((ev.target as HTMLInputElement).files ?? [])]; if (!fs.length) return;
  if (fs.length === 1) { name.value = fs[0].name; text.value = await fs[0].text(); return; }
  // несколько файлов - сразу все
  const bad: string[] = [];
  for (const f of fs) { try { loadEffectText(await f.text(), f.name); } catch (e) { bad.push(f.name + ': ' + (e as Error).message); } }
  if (bad.length) error.value = bad.join('\n'); else { notify(`Импортировано эффектов: ${fs.length}`); emit('close'); }
}
</script>
<template>
  <div class="modal-bg" @click.self="emit('close')">
    <div class="modal">
      <h2>Импорт эффекта частиц .jmc</h2>
      <div class="row"><input type="file" accept=".jmc,.txt" multiple @change="fromFiles" /></div>
      <textarea v-model="text" placeholder='String id = "sparkle"; dyn render = { type: "TEXT", text: "★" }; ...' spellcheck="false"></textarea>
      <div v-if="error" class="callout danger" style="white-space:pre-wrap">{{ error }}</div>
      <div class="row" style="justify-content:flex-end"><button class="btn" @click="emit('close')">Отмена</button><button class="btn primary" @click="doImport" :disabled="!text.trim()">Импортировать</button></div>
    </div>
  </div>
</template>

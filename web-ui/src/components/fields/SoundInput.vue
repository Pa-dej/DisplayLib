<script setup lang="ts">
// Звук виджета: null - по умолчанию плагина, 'none' - выключен, иначе { sound, volume, pitch }.
import type { SoundSpec } from '../../model/screen';
import { snapshot, touch } from '../../store';
const props = defineProps<{ modelValue: SoundSpec | 'none' | null; defaultHint: string; placeholder?: string }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: SoundSpec | 'none' | null): void }>();
const SOUNDS = ['ui.button.click', 'block.dispenser.fail', 'block.note_block.pling', 'block.note_block.bell', 'block.note_block.hat', 'entity.experience_orb.pickup', 'entity.item.pickup', 'block.lever.click', 'block.stone_button.click_on', 'block.wooden_button.click_on', 'block.chest.open', 'entity.player.levelup', 'ui.toast.in', 'block.amethyst_block.chime', 'entity.arrow.hit_player'];
function mode(): 'default' | 'none' | 'custom' { return props.modelValue == null ? 'default' : props.modelValue === 'none' ? 'none' : 'custom'; }
function setMode(m: string) {
  snapshot();
  emit('update:modelValue', m === 'default' ? null : m === 'none' ? 'none' : { sound: 'ui.button.click', volume: 0.5, pitch: 1 });
  touch();
}
function upd(patch: Partial<SoundSpec>) { if (props.modelValue && props.modelValue !== 'none') { emit('update:modelValue', { ...props.modelValue, ...patch }); touch(); } }
const num = (e: Event) => Number((e.target as HTMLInputElement).value);
</script>
<template>
  <div class="row wrap" style="gap:4px">
    <select :value="mode()" @change="setMode(($event.target as HTMLSelectElement).value)" style="width:110px">
      <option value="default">{{ defaultHint }}</option>
      <option value="none">нет</option>
      <option value="custom">свой</option>
    </select>
    <template v-if="modelValue && modelValue !== 'none'">
      <input type="text" list="sound-list" class="grow" :value="modelValue.sound" :placeholder="placeholder ?? 'ui.button.click'" @focus="snapshot()" @input="upd({ sound: ($event.target as HTMLInputElement).value.trim() })" />
      <input type="number" step="0.1" min="0" max="2" style="width:56px" :value="modelValue.volume" title="Громкость" @focus="snapshot()" @input="upd({ volume: num($event) })" />
      <input type="number" step="0.1" min="0.5" max="2" style="width:56px" :value="modelValue.pitch" title="Высота 0.5–2" @focus="snapshot()" @input="upd({ pitch: num($event) })" />
      <datalist id="sound-list"><option v-for="s in SOUNDS" :key="s" :value="s" /></datalist>
    </template>
  </div>
</template>

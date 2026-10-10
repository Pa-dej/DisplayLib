<script setup lang="ts">
// Глиф: любая текстура клиента как картинка произвольного размера в тексте (шрифт из ресурспака).
// Поиск по всем текстурам; height/ascent правятся в общем реестре глифов (glyphs.jmc).
import { computed, ref } from 'vue';
import { searchSprites, atlasLabel, spriteImage, spriteIndex, spriteState, texturePath } from '../../render/sprites';
import { glyphRegistry, ensureGlyph, glyphMetrics, defaultAscent } from '../../render/glyphs';
import { snapshot, touch } from '../../store';
import Icon from '../Icon.vue';

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>();
const open = ref(false);
const q = ref('');
const list = computed(() => { void spriteIndex.loaded; return searchSprites(q.value, null).filter((h) => !h.pack); });
const spec = computed(() => { void glyphRegistry.dirty; void spriteState.revision; return props.modelValue ? ensureGlyph(props.modelValue) : null; });
const metrics = computed(() => { void spriteState.revision; void glyphRegistry.dirty; return props.modelValue ? glyphMetrics(props.modelValue) : null; });
const previewUrl = computed(() => { void spriteState.revision; return props.modelValue ? spriteImage(props.modelValue, 'minecraft:blocks').canvas?.toDataURL() ?? '' : ''; });
/** Путь текстуры из найденного спрайта: textures/<path>.png без префикса и расширения. */
function pathOf(h: { atlas: string; sprite: string }): string { return texturePath(h.sprite, h.atlas).replace(/^assets\/minecraft\/textures\//, '').replace(/\.png$/, ''); }
function close() { setTimeout(() => (open.value = false), 150); }
function pick(h: { atlas: string; sprite: string }) { snapshot(); emit('update:modelValue', pathOf(h)); touch(); open.value = false; }
function onInput(ev: Event) { const v = (ev.target as HTMLInputElement).value; q.value = v; emit('update:modelValue', v); touch(); }
function setHeight(v: number) { const g = spec.value; if (!g) return; snapshot(); g.height = Math.max(1, Math.round(v)); g.ascent = defaultAscent(g.height); glyphRegistry.dirty = true; touch(); }
function setAscent(v: number) { const g = spec.value; if (!g) return; snapshot(); g.ascent = Math.round(v); glyphRegistry.dirty = true; touch(); }
function iconOf(h: { atlas: string; sprite: string }): string { void spriteState.revision; return spriteImage(h.sprite, h.atlas).canvas?.toDataURL() ?? ''; }
</script>
<template>
  <div class="glyph-input">
    <div class="row">
      <span class="item-icon sprite-icon" :style="previewUrl ? { backgroundImage: `url(${previewUrl})` } : {}" :title="'textures/' + modelValue + '.png'"></span>
      <div class="autocomplete grow">
        <input type="text" :value="modelValue" @focus="snapshot(); open = true; q = ''" @blur="close" @input="onInput" placeholder="Путь в textures/: gui/sprites/widget/button" />
        <div v-if="open && list.length" class="list">
          <div v-for="h in list" :key="h.atlas + '|' + h.sprite" @mousedown.prevent="pick(h)"><span class="item-icon sprite-icon" style="width:16px;height:16px" :style="iconOf(h) ? { backgroundImage: `url(${iconOf(h)})` } : {}"></span>{{ pathOf(h) }} <span class="hint">{{ atlasLabel(h.atlas) }}</span></div>
        </div>
      </div>
    </div>
    <div v-if="spec" class="row hint" style="gap:6px;margin-top:3px">
      <Icon name="sliders-h" :size="11" /> Высота <input type="number" min="1" step="1" style="width:56px" :value="spec.height" @focus="snapshot()" @change="setHeight(Number(($event.target as HTMLInputElement).value))" title="Высота картинки в текстовых px (строка = 10)" />
      Ascent <input type="number" step="1" style="width:56px" :value="spec.ascent" @focus="snapshot()" @change="setAscent(Number(($event.target as HTMLInputElement).value))" title="Подъём над базовой линией; height/2 + 2 = по центру строки" />
      <span v-if="metrics">→ {{ metrics.w }}×{{ metrics.h }} px</span>
    </div>
  </div>
</template>

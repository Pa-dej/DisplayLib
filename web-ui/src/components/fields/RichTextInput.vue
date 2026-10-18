<script setup lang="ts">
// Текст: простая строка или сегменты { text, color } / { sprite, atlas, color? }. §-коды в строке клиент тоже понимает.
// Сегмент считается спрайтовым, пока у него есть поле sprite (даже пустое) - стирание имени его не превращает в текст.
import { computed, nextTick } from 'vue';
import type { RichText, Segment } from '../../model/screen';
import { NAMED_COLORS, cssColor } from '../../render/text';
import { snapshot, touch } from '../../store';
import SpriteInput from './SpriteInput.vue';
import LineInput from './LineInput.vue';
import TintInput from './TintInput.vue';
import ColorPicker from './ColorPicker.vue';
import Icon from '../Icon.vue';
import { useReorder, moveItem } from '../../reorder';
import { ATLASES } from '../../render/sprites';

const props = defineProps<{ modelValue: RichText | null; nullable?: boolean; placeholder?: string; /** кнопки «сегмент/спрайт/голова» иконками в строке */ compact?: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: RichText | null): void }>();

const isSegments = computed(() => Array.isArray(props.modelValue));
const segs = computed<Segment[]>(() => (Array.isArray(props.modelValue) ? props.modelValue : []));
const names = Object.keys(NAMED_COLORS);
const isSprite = (s: Segment) => s.sprite !== undefined;
const isHead = (s: Segment) => s.player !== undefined;

// Несколько правок подряд в одном тике (спрайт + атлас при выборе из списка) накапливаются в черновике:
// props.modelValue обновится только после перерисовки, иначе вторая правка затёрла бы первую.
let draft: Segment[] | null = null;
function edit(fn: (a: Segment[]) => void): void {
  const a = (draft ?? segs.value).map((s) => ({ ...s }));
  fn(a);
  draft = a;
  emit('update:modelValue', a);
  touch();
  nextTick(() => { draft = null; });
}

function setPlainValue(v: string) { emit('update:modelValue', v); touch(); }
/** Строку превращаем в первый сегмент, если она не пуста. */
function baseSegments(): Segment[] {
  if (Array.isArray(props.modelValue)) return props.modelValue;
  const t = typeof props.modelValue === 'string' ? props.modelValue : '';
  return t ? [{ text: t }] : [];
}
function add() { snapshot(); emit('update:modelValue', [...baseSegments(), { text: '', color: 'gray' }]); touch(); }
function addHead() { snapshot(); emit('update:modelValue', [...baseSegments(), { text: '', player: 'Steve' }]); touch(); }
function setPlayer(i: number, v: string) { edit((a) => { a[i].player = v; a[i].text = ''; }); }
function setHat(i: number, v: boolean) { edit((a) => { if (v) delete a[i].hat; else a[i].hat = false; }); }
function addSprite() { snapshot(); emit('update:modelValue', [...baseSegments(), { text: '', sprite: 'item/iron_ingot' }]); touch(); }
function upd(i: number, patch: Partial<Segment>) { edit((a) => { Object.assign(a[i], patch); if (!a[i].color) delete a[i].color; }); }
function setAtlas(i: number, v: string) { edit((a) => { if (v.trim()) a[i].atlas = v.trim(); else delete a[i].atlas; }); }
function setSprite(i: number, v: string) { edit((a) => { a[i].sprite = v; a[i].text = ''; }); }
const dnd = useReorder((from, to) => { snapshot(); const a = segs.value.map((x) => ({ ...x })); moveItem(a, from, to); emit('update:modelValue', a); touch(); });
function del(i: number) {
  snapshot();
  const a = segs.value.filter((_, j) => j !== i);
  // остался один текстовый сегмент без цвета - обратно в строку
  emit('update:modelValue', a.length === 0 ? '' : a.length === 1 && !isSprite(a[0]) && !a[0].color ? a[0].text : a);
  touch();
}
function clear() { snapshot(); emit('update:modelValue', null); touch(); }
function setColor(i: number, ev: Event) {
  const v = (ev.target as HTMLInputElement | HTMLSelectElement).value;
  upd(i, { color: v === '' ? undefined : v });
}
</script>
<template>
  <div>
    <div v-if="modelValue === null" class="row">
      <span class="hint grow">{{ placeholder ?? 'Не задано' }}</span>
      <button class="btn sm" @click="snapshot(); emit('update:modelValue', ''); touch()">Задать</button>
    </div>
    <template v-else>
      <div v-if="!isSegments" class="row">
        <LineInput class="grow" :model-value="modelValue as string" :placeholder="placeholder" @focus="snapshot()" @update:model-value="setPlainValue" />
        <template v-if="compact">
          <button class="btn sm icon" title="Цветной сегмент: текст разными цветами" @click="add"><Icon name="font" /></button>
          <button class="btn sm icon" title="Спрайт атласа (предмет, блок, иконка…) - квадрат 8×8" @click="addSprite"><Icon name="icons" /></button>
          <button class="btn sm icon" title="Голова игрока по нику - квадрат 8×8" @click="addHead"><Icon name="street-view" /></button>
        </template>
        <button v-if="nullable" class="btn sm icon" title="Убрать" @click="clear"><Icon name="times" /></button>
      </div>
      <div v-else class="segments">
        <div v-for="(s, i) in segs" :key="i" class="segment" :class="[{ sprite: isSprite(s) || isHead(s) }, dnd.rowClass(i)]" data-row @dragover="dnd.over(i, $event)" @dragleave="dnd.leave(i)" @drop="dnd.drop(i, $event)">
          <span class="grip" title="Перетащить, чтобы изменить порядок" draggable="true" @dragstart="dnd.start(i, $event)" @dragend="dnd.end()"><Icon name="grip-vertical" :size="10" /></span>
          <template v-if="isHead(s)">
            <input type="text" class="grow" :value="s.player" placeholder="Ник игрока" title="Голова игрока (объект player, клиент 1.21.9+): текстуру клиент берёт по нику сам" @focus="snapshot()" @input="setPlayer(i, ($event.target as HTMLInputElement).value)" />
            <label class="row hint" title="Слой шапки скина"><input type="checkbox" :checked="s.hat !== false" @change="snapshot(); setHat(i, ($event.target as HTMLInputElement).checked)" /> шапка</label>
            <TintInput :model-value="s.color" none="без тинта" @update:model-value="upd(i, { color: $event ?? undefined })" />
          </template>
          <template v-else-if="isSprite(s)">
            <SpriteInput :model-value="s.sprite!" :atlas="s.atlas" @update:model-value="setSprite(i, $event)" @update:atlas="setAtlas(i, $event ?? '')" />
            <input type="text" list="atlas-list-seg" :value="s.atlas ?? ''" @focus="snapshot()" @input="setAtlas(i, ($event.target as HTMLInputElement).value)" placeholder="Атлас: авто" title="Атлас (пусто - по имени: item/… → items, иначе blocks)" style="width:86px" />
            <TintInput :model-value="s.color" none="без тинта" title="Тинт спрайта: текстура умножается на цвет (как у текста)" @update:model-value="upd(i, { color: $event ?? undefined })" />
          </template>
          <template v-else>
            <LineInput :model-value="s.text" placeholder="Текст" @focus="snapshot()" @update:model-value="upd(i, { text: $event })" />
            <div class="row" style="gap:3px">
              <ColorPicker :model-value="cssColor(s.color)" @update:model-value="upd(i, { color: $event })" />
              <select :value="s.color && !s.color.startsWith('#') ? s.color : ''" @change="setColor(i, $event)" style="width:56px" title="Именованный цвет">
                <option value="">#hex</option>
                <option v-for="n in names" :key="n" :value="n">{{ n }}</option>
              </select>
            </div>
          </template>
          <button class="btn sm icon" title="Удалить сегмент" @click="del(i)"><Icon name="times" /></button>
        </div>
      </div>
      <div v-if="!compact || isSegments" class="row" style="margin-top:4px">
        <button class="btn sm" :class="{ icon: compact }" title="Добавить цветной текстовый сегмент (Shift+Enter в тексте - новая строка)" @click="add"><Icon name="font" /><template v-if="!compact"> Сегмент</template></button>
        <button class="btn sm" :class="{ icon: compact }" title="Вставить спрайт атласа (предмет, блок, иконку HUD, эффект…) - всегда квадрат 8×8" @click="addSprite"><Icon name="icons" /><template v-if="!compact"> Спрайт</template></button>
        <button class="btn sm" :class="{ icon: compact }" title="Вставить голову игрока по нику (объект player, клиент 1.21.9+) - квадрат 8×8" @click="addHead"><Icon name="street-view" /><template v-if="!compact"> Голова</template></button>
        <datalist id="atlas-list-seg"><option v-for="a in ATLASES" :key="a.id" :value="a.id">{{ a.hint }}</option></datalist>
        <span class="grow"></span>
        <button v-if="nullable && isSegments" class="btn sm" @click="clear">Убрать</button>
      </div>
    </template>
  </div>
</template>

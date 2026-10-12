<script setup lang="ts">
// Обзор всех спрайтов клиента по категориям (атласам) с поиском; клик по спрайту создаёт SPRITE_BUTTON.
import Icon from './Icon.vue';
import { computed, ref, watch } from 'vue';
import { spriteIndex, texturePath } from '../render/sprites';
import { addWidget, currentScreen, notify, touch } from '../store';

const emit = defineEmits<{ (e: 'close'): void }>();

const CATS: { id: string; atlas: string; label: string; prefix?: string }[] = [
  { id: 'items', atlas: 'minecraft:items', label: 'Предметы' },
  { id: 'blocks', atlas: 'minecraft:blocks', label: 'Блоки', prefix: 'block/' },
  { id: 'gui', atlas: 'minecraft:gui', label: 'Интерфейс' },
  { id: 'effects', atlas: 'minecraft:gui', label: 'Эффекты', prefix: 'mob_effect/' },
  { id: 'particles', atlas: 'minecraft:particles', label: 'Частицы' },
  { id: 'paintings', atlas: 'minecraft:paintings', label: 'Картины' },
  { id: 'banner', atlas: 'minecraft:banner_patterns', label: 'Узоры знамён' },
  { id: 'shield', atlas: 'minecraft:shield_patterns', label: 'Узоры щитов' },
  { id: 'trims', atlas: 'minecraft:armor_trims', label: 'Узоры брони' },
  { id: 'heads', atlas: '', label: 'Головы игроков' },
];
const HEAD_WORDS = ['player', 'head', 'skull', 'голов', 'игрок', 'skin', 'скин', 'ник'];
const headHint = computed(() => cat.value.id !== 'heads' && HEAD_WORDS.some((w) => q.value.trim().toLowerCase().includes(w)));
const headName = ref('Steve');
const headHat = ref(true);
const headFace = computed(() => 'https://mc-heads.net/avatar/' + encodeURIComponent(headName.value.trim() || 'Steve') + '/64' + (headHat.value ? '' : '/nohelm'));
function pickHead() {
  if (!currentScreen.value) return;
  const w = addWidget('SPRITE_BUTTON');
  w.player = headName.value.trim() || 'Steve'; w.hat = headHat.value; w.sprite = '';
  w.scale = [1, 1, 1]; w.tolerance = [0.1, 0.1]; w.backgroundAlpha = 0;
  touch();
  notify('Голова ' + w.player + ' добавлена');
  emit('close');
}
const cat = ref(CATS[0]);
const q = ref('');
const sub = ref('');     // подпапка (для интерфейса: hud, icon, widget, …)
const limit = ref(400);

/** Все имена категории (атлас + префикс). */
const names = computed<string[]>(() => {
  void spriteIndex.loaded;
  if (!cat.value.atlas) return [];
  const all = spriteIndex.byAtlas[cat.value.atlas] ?? [];
  const c = cat.value;
  if (c.id === 'gui') return all.filter((n) => !n.startsWith('mob_effect/'));
  return c.prefix ? all.filter((n) => n.startsWith(c.prefix!)) : all;
});
/** Подпапки первого уровня внутри категории (без префикса категории). */
const subs = computed<string[]>(() => {
  const p = cat.value.prefix ?? '';
  const set = new Set<string>();
  for (const n of names.value) { const rest = n.slice(p.length); const i = rest.indexOf('/'); if (i > 0) set.add(rest.slice(0, i)); }
  return [...set].sort();
});
const list = computed<string[]>(() => {
  const p = cat.value.prefix ?? '';
  const s = q.value.trim().toLowerCase().replace(/ /g, '_');
  let out = names.value;
  if (sub.value) out = out.filter((n) => n.slice(p.length).startsWith(sub.value + '/'));
  if (s) {
    const starts: string[] = [], inside: string[] = [];
    for (const n of out) { const base = n.replace(/^.*\//, ''); if (base.startsWith(s)) starts.push(n); else if (n.includes(s)) inside.push(n); }
    out = [...starts, ...inside];
  }
  return out;
});
const shown = computed(() => list.value.slice(0, limit.value));
watch([cat, q, sub], () => { limit.value = 400; });
watch(cat, () => { sub.value = ''; });

function short(n: string): string { return n.replace(new RegExp('^' + (cat.value.prefix ?? '')), ''); }
function pick(n: string) {
  if (!currentScreen.value) return;
  const w = addWidget('SPRITE_BUTTON');
  w.sprite = n;
  // items/blocks плагин выводит из префикса, остальные атласы - явно
  const auto = (cat.value.atlas === 'minecraft:items' && n.startsWith('item/')) || (cat.value.atlas === 'minecraft:blocks' && n.startsWith('block/'));
  w.atlas = auto ? null : cat.value.atlas;
  w.scale = [1, 1, 1]; w.tolerance = [0.1, 0.1]; w.backgroundAlpha = 0;
  touch();
  notify('Спрайт ' + n + ' добавлен');
  emit('close');
}
function onKey(e: KeyboardEvent) { if (e.key === 'Escape') emit('close'); }
</script>
<template>
  <div class="modal-bg" @click.self="emit('close')" @keydown="onKey">
    <div class="modal assets">
      <div class="row">
        <h2 style="margin:0">Ассеты</h2>
        <span class="hint">{{ list.length }} из {{ names.length }}</span>
        <span class="grow"></span>
        <input type="text" v-model="q" placeholder="Поиск по имени…" style="width:260px" autofocus />
        <button class="btn icon" title="Закрыть (Esc)" @click="emit('close')"><Icon name="times" /></button>
      </div>
      <div class="tabs" style="border:0;padding:0;flex-wrap:wrap">
        <span v-for="c in CATS" :key="c.id" class="tab" :class="{ active: cat.id === c.id }" @click="cat = c">{{ c.label }}</span>
      </div>
      <div v-if="subs.length > 1" class="row wrap" style="gap:4px">
        <button class="btn sm" :class="{ primary: !sub }" @click="sub = ''">все</button>
        <button v-for="s in subs" :key="s" class="btn sm" :class="{ primary: sub === s }" @click="sub = s">{{ s }}</button>
      </div>
      <div v-if="headHint" class="callout" style="margin:4px 0">Головы игроков - не спрайты атласа, у них нет имени: это объект <code>player</code>, клиент берёт скин по нику. Смотри вкладку <a href="#" @click.prevent="cat = CATS[CATS.length - 1]">Головы игроков</a> или сегмент «Голова» в тексте.</div>
      <div v-if="cat.id === 'heads'" class="head-pick">
        <img :src="headFace" alt="" width="64" height="64" @error="($event.target as HTMLImageElement).style.visibility = 'hidden'" @load="($event.target as HTMLImageElement).style.visibility = 'visible'" />
        <div class="stack">
          <div class="row"><input type="text" v-model="headName" placeholder="Ник игрока" style="width:220px" @keydown.enter="pickHead" /><label class="row"><input type="checkbox" v-model="headHat" /> шапка</label><button class="btn primary" @click="pickHead"><Icon name="plus" /> Добавить голову</button></div>
          <div class="hint">В файле экрана: <code>player: "{{ headName || 'Steve' }}"</code> у кнопки или <code>{ player: "{{ headName || 'Steve' }}" }</code> сегментом в тексте. Квадрат 8×8, как спрайт; текстуру клиент (1.21.9+) берёт по нику сам - профиль/текстуру задать нельзя. Превью - с mc-heads.net.</div>
        </div>
      </div>
      <div v-else-if="!spriteIndex.loaded" class="hint">Индекс спрайтов не загружен (assets/sprite-index.json).</div>
      <div v-if="cat.id !== 'heads'" class="asset-grid">
        <button v-for="n in shown" :key="n" class="asset" :title="n + ' · ' + cat.atlas" @click="pick(n)">
          <img :src="texturePath(n, cat.atlas)" loading="lazy" decoding="async" alt="" />
          <span>{{ short(n) }}</span>
        </button>
      </div>
      <div v-if="cat.id !== 'heads' && list.length > shown.length" class="row" style="justify-content:center"><button class="btn" @click="limit += 400">Показать ещё ({{ list.length - shown.length }})</button></div>
      <div v-if="cat.id !== 'heads'" class="hint">Клик по спрайту добавляет на текущий экран SPRITE_BUTTON с этим спрайтом. Имена - как в файле экрана: items/blocks без атласа, остальные с атласом.</div>
    </div>
  </div>
</template>

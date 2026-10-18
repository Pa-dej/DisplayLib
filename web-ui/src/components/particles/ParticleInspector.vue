<script setup lang="ts">
// Свойства эффекта частиц: общее, вид (render), испускание (emitter), движение (motion), анимация.
// Подписи - имена полей в файле, чтобы правку в редакторе было легко найти в .jmc и наоборот.
import { computed } from 'vue';
import Icon from '../Icon.vue';
import NumInput from './NumInput.vue';
import RangeInput from './RangeInput.vue';
import PColorInput from './PColorInput.vue';
import StopsEditor from './StopsEditor.vue';
import ListInput from './ListInput.vue';
import RichTextInput from '../fields/RichTextInput.vue';
import MaterialInput from '../fields/MaterialInput.vue';
import VecInput from '../fields/VecInput.vue';
import ColorInput from '../fields/ColorInput.vue';
import { snapshot, touch } from '../../store';
import { currentEffect, currentEffectDoc, currentEffectErrors, setEffectId } from '../../particles/store';
import { RENDER_TYPES, SHAPES, BILLBOARDS, EASINGS, burstCount, type RenderType, type Stop } from '../../particles/model';
import type { RichText, Vec3 } from '../../model/screen';
import { ITEM_NAMES } from '../../render/items';

const e = computed(() => currentEffect.value!);
const r = computed(() => e.value.render);
const em = computed(() => e.value.emitter);
const mo = computed(() => e.value.motion);
const an = computed(() => e.value.animation);

const RENDER_HINT: Record<RenderType, string> = {
  PIXEL: 'Цветной квадрат (TextDisplay с пробелом и фоном) - самый дешёвый для клиента, цвет плавно меняется.',
  TEXT: 'Символ или текст (TextDisplay): звёздочки, черепа, ноты; цвет текста можно анимировать.',
  ITEM: 'Предмет (ItemDisplay): монеты, изумруды, еда. Цвет и прозрачность не меняются.',
  BLOCK: 'Блок (BlockDisplay): кубики-угли, камни. Цвет и прозрачность не меняются.',
};

function edit(fn: () => void) { snapshot(); fn(); touch(); }

function setType(t: RenderType) {
  edit(() => {
    const x = r.value; x.type = t;
    if (t === 'TEXT' && x.text == null && !x.variants.length) x.text = '★';
    if (t === 'ITEM' && !x.items.length) x.items = ['GOLD_NUGGET'];
    if (t === 'BLOCK' && !x.blocks.length) x.blocks = ['minecraft:glowstone'];
    if (t === 'PIXEL' && !x.colors.length) x.colors = ['#ffffff'];
  });
}
const textual = computed(() => r.value.type === 'TEXT' || r.value.type === 'PIXEL');

// ---- emitter: всплеск / N тиков / пока не остановят
type Mode = 'burst' | 'ticks' | 'forever';
const mode = computed<Mode>(() => (em.value.duration === 0 ? 'burst' : em.value.duration < 0 ? 'forever' : 'ticks'));
function setMode(m: Mode) {
  edit(() => {
    const x = em.value;
    x.duration = m === 'burst' ? 0 : m === 'forever' ? -1 : x.duration > 0 ? x.duration : 40;
    if (m !== 'burst' && !(x.rate > 0)) x.rate = 1;
  });
}

// ---- motion.direction
const outward = computed(() => mo.value.direction === 'OUTWARD');
function setOutward(v: boolean) { edit(() => { mo.value.direction = v ? 'OUTWARD' : [0, 1, 0]; }); }
const DIRS: { label: string; v: Vec3 }[] = [{ label: '↑', v: [0, 1, 0] }, { label: '↓', v: [0, -1, 0] }, { label: '+X', v: [1, 0, 0] }, { label: '+Z', v: [0, 0, 1] }];

// ---- текст: основной и варианты
const variants = computed<RichText[]>(() => r.value.variants);
function addVariant() { edit(() => { r.value.variants = [...r.value.variants, '✦']; }); }
function delVariant(i: number) { edit(() => { r.value.variants = r.value.variants.filter((_, j) => j !== i); }); }
function setVariant(i: number, v: RichText | null) { const a = [...r.value.variants]; a[i] = v ?? ''; r.value.variants = a; }

// ---- фон текста
function setBg(on: boolean) { edit(() => { r.value.background = on ? [0, 0, 0] : null; r.value.backgroundAlpha = on ? 64 : null; }); }

const COLOR_DEFAULT: Stop<string>[] = [{ at: null, value: '#ffffff' }, { at: null, value: '#ffffff00' }];
const OPACITY_DEFAULT: Stop<number>[] = [{ at: null, value: 1 }, { at: null, value: 0 }];
const blockNames = ITEM_NAMES;
</script>

<template>
  <aside class="inspector pinspector">
    <template v-if="!currentEffect"><div class="empty">Нет открытого эффекта. Создайте новый или откройте пример слева.</div></template>
    <template v-else>
      <div v-if="currentEffectDoc?.warnings.length" class="callout warn"><b>При чтении файла:</b><br />{{ currentEffectDoc.warnings.join('; ') }}</div>
      <div v-if="currentEffectErrors.length" class="callout danger"><b>Плагин не примет:</b><br />{{ currentEffectErrors.join('; ') }}</div>

      <div class="section">
        <h3>Эффект</h3>
        <div class="field"><label title="Имя файла particles/<id>.jmc и имя в /displaylib particle <id>">id</label>
          <input type="text" :value="e.id" @focus="snapshot()" @change="setEffectId(($event.target as HTMLInputElement).value); touch()" /></div>
        <div class="field"><label title="Кто видит частицы: все игроки рядом или только тот, для кого запущен эффект">viewers</label>
          <div class="seg">
            <button :class="{ on: e.viewers === 'ALL' }" @click="edit(() => (e.viewers = 'ALL'))">Все рядом</button>
            <button :class="{ on: e.viewers === 'OWNER' }" @click="edit(() => (e.viewers = 'OWNER'))">Только владелец</button>
          </div></div>
        <div class="field"><label title="Максимум тиков между пакетами движения (1-59). Меньше - точнее путь, больше - меньше трафика. На прямых участках плагин сам растягивает кадр до этого значения">keyframeTicks</label>
          <NumInput v-model="e.keyframeTicks" :int="true" :step="1" :min="1" :max="59" /></div>
      </div>

      <div class="section">
        <h3>Вид <span class="hint">render</span></h3>
        <div class="seg wide">
          <button v-for="t in RENDER_TYPES" :key="t" :class="{ on: r.type === t }" @click="setType(t)">{{ t }}</button>
        </div>
        <p class="hint">{{ RENDER_HINT[r.type] }}</p>

        <template v-if="r.type === 'TEXT'">
          <div class="field stack"><label>text</label><RichTextInput v-model="r.text" nullable placeholder="★" /></div>
          <div class="field stack"><label title="Случайный на каждую частицу (вместе с text)">variants</label>
            <div class="plist">
              <div v-for="(v, i) in variants" :key="i" class="plist-row">
                <RichTextInput :model-value="v" @update:model-value="setVariant(i, $event)" />
                <button class="btn sm icon" title="Убрать" @click="delVariant(i)"><Icon name="times" /></button>
              </div>
              <button class="btn sm" @click="addVariant"><Icon name="plus" /> Вариант</button>
            </div>
          </div>
        </template>
        <div v-if="r.type === 'ITEM'" class="field stack"><label title="Случайный на каждую частицу">items</label>
          <ListInput v-model="r.items" add="GOLD_NUGGET" :min="1" v-slot="{ value, set }">
            <MaterialInput class="grow" :model-value="value" @update:model-value="set" />
          </ListInput></div>
        <div v-if="r.type === 'BLOCK'" class="field stack"><label title="Случайный на каждую частицу; можно с состоянием: oak_log[axis=x]">blocks</label>
          <ListInput v-model="r.blocks" add="minecraft:stone" :min="1" v-slot="{ value, set }">
            <input type="text" class="grow" list="pblock-names" :value="value" @focus="snapshot()" @input="set(($event.target as HTMLInputElement).value)" />
          </ListInput>
          <datalist id="pblock-names"><option v-for="n in blockNames" :key="n" :value="'minecraft:' + n" /></datalist></div>
        <div v-if="textual" class="field stack"><label :title="r.type === 'PIXEL' ? 'Цвет квадрата; несколько - случайный на частицу' : 'Цвет текста; несколько - случайный на частицу'">colors</label>
          <ListInput v-model="r.colors" add="#ffffff" v-slot="{ value, set }">
            <PColorInput :model-value="value" @update:model-value="set" />
          </ListInput>
          <span v-if="!r.colors.length" class="hint">{{ r.type === 'PIXEL' ? 'Без цвета - белый' : 'Без цвета - как в тексте' }}</span></div>

        <div class="field"><label title="Как частица поворачивается к игроку">billboard</label>
          <select :value="r.billboard" @focus="snapshot()" @change="r.billboard = ($event.target as HTMLSelectElement).value as any; touch()">
            <option v-for="b in BILLBOARDS" :key="b" :value="b">{{ b }}</option>
          </select></div>
        <div class="field"><label title="Своя яркость 0-15 (15 - светится в темноте); пусто - освещение мира">brightness</label>
          <NumInput v-model="r.brightness" :int="true" :step="1" :min="0" :max="15" nullable placeholder="мир" /></div>
        <div class="field"><label title="Светящийся контур (glowing)">glow</label><input type="checkbox" :checked="r.glow" @change="edit(() => (r.glow = !r.glow))" /></div>
        <template v-if="r.type === 'TEXT'">
          <div class="field"><label>shadow</label><input type="checkbox" :checked="r.shadow" @change="edit(() => (r.shadow = !r.shadow))" /></div>
          <div class="field"><label>background</label>
            <div class="row"><input type="checkbox" :checked="!!r.background" @change="setBg(!r.background)" />
              <ColorInput v-if="r.background" :model-value="r.background" @update:model-value="r.background = $event as any" :alpha="r.backgroundAlpha ?? 255" @update:alpha="r.backgroundAlpha = $event" /></div></div>
        </template>
      </div>

      <div class="section">
        <h3>Испускание <span class="hint">emitter</span></h3>
        <div class="field"><label title="Откуда вылетают частицы">shape</label>
          <div class="seg"><button v-for="s in SHAPES" :key="s" :class="{ on: em.shape === s }" @click="edit(() => (em.shape = s))">{{ s }}</button></div></div>
        <div v-if="em.shape === 'SPHERE' || em.shape === 'CIRCLE'" class="field"><label>radius</label><NumInput v-model="em.radius" :step="0.05" :min="0" /></div>
        <div v-if="em.shape === 'BOX'" class="field"><label title="Полные размеры коробки x, y, z">size</label><VecInput :model-value="em.size" @update:model-value="em.size = $event as any" :min="0" :labels="['x', 'y', 'z']" /></div>
        <div v-if="em.shape !== 'POINT'" class="field"><label title="Только по поверхности формы (по контуру для круга)">surface</label><input type="checkbox" :checked="em.surface" @change="edit(() => (em.surface = !em.surface))" /></div>
        <div class="field"><label>Когда</label>
          <div class="seg">
            <button :class="{ on: mode === 'burst' }" @click="setMode('burst')" title="Все частицы сразу">Всплеск</button>
            <button :class="{ on: mode === 'ticks' }" @click="setMode('ticks')" title="Испускать rate частиц за тик в течение duration тиков">N тиков</button>
            <button :class="{ on: mode === 'forever' }" @click="setMode('forever')" title="duration: -1 - пока не остановят (stop / handle.stop())">Пока не остановят</button>
          </div></div>
        <div class="field"><label :title="mode === 'burst' ? 'Частиц во всплеске' : 'Частиц сразу в начале (кроме потока)'">count</label>
          <NumInput v-model="em.count" :int="true" :step="1" :min="0" nullable :placeholder="String(burstCount({ ...em, count: null }))" /></div>
        <template v-if="mode !== 'burst'">
          <div v-if="mode === 'ticks'" class="field"><label title="Тиков испускания (20 = 1 с)">duration</label><NumInput v-model="em.duration" :int="true" :step="1" :min="1" /></div>
          <div class="field"><label title="Частиц за тик; дробное - в среднем (0.25 - одна в 4 тика)">rate</label><NumInput v-model="em.rate" :step="0.05" :min="0" /></div>
        </template>
      </div>

      <div class="section">
        <h3>Движение <span class="hint">motion</span></h3>
        <div class="field"><label title="Время жизни частицы, тиков">lifetime</label><RangeInput v-model="mo.lifetime" :int="true" :step="1" :min="1" unit="тик" /></div>
        <div class="field"><label title="Начальная скорость, блоков за тик (0.1 = 2 блока/с)">speed</label><RangeInput v-model="mo.speed" :step="0.01" :min="0" unit="бл/т" /></div>
        <div class="field"><label title="Ось разлёта или от центра формы (OUTWARD)">direction</label>
          <div class="row">
            <label class="btn sm"><input type="checkbox" :checked="outward" @change="setOutward(!outward)" /> От центра</label>
            <template v-if="!outward"><button v-for="d in DIRS" :key="d.label" class="btn sm" @click="edit(() => (mo.direction = [...d.v] as Vec3))">{{ d.label }}</button></template>
          </div></div>
        <div v-if="!outward" class="field"><label></label><VecInput :model-value="mo.direction as number[]" @update:model-value="mo.direction = $event as Vec3" :labels="['x', 'y', 'z']" /></div>
        <div class="field"><label title="Разброс от оси, градусов (180 - во все стороны)">spread</label>
          <div class="row"><input type="range" min="0" max="180" step="1" :value="mo.spread" @pointerdown="snapshot()" @focus="snapshot()" @input="mo.spread = Number(($event.target as HTMLInputElement).value); touch()" class="grow" /><NumInput v-model="mo.spread" :step="1" :min="0" :max="180" style="width:64px" /></div></div>
        <div class="field"><label title="Ускорение вниз, блоков/тик² (0.04 - как у предметов; минус - всплывает)">gravity</label><NumInput v-model="mo.gravity" :step="0.005" /></div>
        <div class="field"><label title="Сопротивление воздуха: доля скорости, теряемая за тик (0-1)">drag</label><NumInput v-model="mo.drag" :step="0.01" :min="0" :max="1" /></div>
        <div class="field"><label title="Притяжение к точке запуска (минус - отталкивание)">attract</label><NumInput v-model="mo.attract" :step="0.005" /></div>
        <div v-if="mo.attract !== 0" class="field"><label title="Тянуть к вертикальной оси через точку запуска, а не к точке">attractAxis</label><input type="checkbox" :checked="mo.attractAxis" @change="edit(() => (mo.attractAxis = !mo.attractAxis))" /></div>
        <div class="field"><label title="Плавное случайное блуждание, блоков/тик²">wander</label><NumInput v-model="mo.wander" :step="0.002" :min="0" /></div>
        <div class="field"><label title="Сталкиваться с блоками (сервер проверяет блоки на пути - дороже)">collide</label><input type="checkbox" :checked="mo.collide" @change="edit(() => (mo.collide = !mo.collide))" /></div>
        <template v-if="mo.collide">
          <div class="field"><label title="Упругость отскока 0-1">bounce</label><NumInput v-model="mo.bounce" :step="0.05" :min="0" :max="1" /></div>
          <div class="field"><label title="Трение при скольжении по блоку 0-1">friction</label><NumInput v-model="mo.friction" :step="0.05" :min="0" :max="1" /></div>
        </template>
      </div>

      <div class="section">
        <h3>Анимация <span class="hint">animation</span></h3>
        <div class="field"><label title="Начальный размер, блоков">scale</label><RangeInput v-model="an.scale" :step="0.01" :min="0" /></div>
        <div class="field"><label title="Размер к концу жизни - множитель начального (0 - исчезает, 1 - не меняется)">endScale</label><NumInput v-model="an.endScale" :step="0.05" :min="0" /></div>
        <div v-if="an.endScale !== 1" class="field"><label>easing</label>
          <select :value="an.easing" @focus="snapshot()" @change="an.easing = ($event.target as HTMLSelectElement).value as any; touch()"><option v-for="x in EASINGS" :key="x" :value="x">{{ x }}</option></select></div>
        <div class="field"><label title="Тиков на рост от 0 до размера в начале жизни">fadeIn</label><NumInput v-model="an.fadeIn" :int="true" :step="1" :min="0" /></div>
        <div class="field"><label title="Начальный поворот в плоскости частицы, градусов">rotation</label><RangeInput v-model="an.rotation" :step="5" unit="°" /></div>
        <div class="field"><label title="Вращение, градусов за тик">spin</label><RangeInput v-model="an.spin" :step="1" unit="°/т" /></div>
        <template v-if="textual">
          <div class="field stack"><label title="Цвет по доле жизни (смешивание в OkLab, как в плагине). Заменяет colors">color</label>
            <StopsEditor v-model="an.color as any" kind="color" :defaults="COLOR_DEFAULT" /></div>
          <div class="field stack"><label title="Множитель непрозрачности по доле жизни">opacity</label>
            <StopsEditor v-model="an.opacity as any" kind="number" :defaults="OPACITY_DEFAULT" :max="1" /></div>
        </template>
        <p v-else class="hint">Цвет и прозрачность по жизни есть только у PIXEL и TEXT.</p>
      </div>
    </template>
  </aside>
</template>

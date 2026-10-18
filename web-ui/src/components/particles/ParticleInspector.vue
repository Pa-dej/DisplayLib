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
import ColorPicker from '../fields/ColorPicker.vue';
import LoadMark from '../load/LoadMark.vue';
import FramesEditor from '../fields/FramesEditor.vue';
import { useReorder, moveItem } from '../../reorder';
import { compile, transformPlan } from '../../particles/sim';
import { parseColor } from '../../particles/colors';
import { cloneEffect } from '../../particles/model';
import { snapshot, touch } from '../../store';
import { currentEffect, currentEffectDoc, currentEffectErrors, setEffectId } from '../../particles/store';
import { RENDER_TYPES, SHAPES, BILLBOARDS, EASINGS, burstCount, isFrames, framesOf, textVariants, type RenderType, type Stop, type TextVariant, type FramesText } from '../../particles/model';
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
// В файле есть text (один) и variants (случайный из списка); в редакторе это один список: один вариант
// пишется как text, несколько - как variants. Любой вариант может быть анимацией кадров - как frames у
// текстовых кнопок экранов ({ frames: [...], frameInterval }), первый кадр - как текст.
const variants = computed<TextVariant[]>(() => (currentEffect.value ? textVariants(r.value) : []));
function storeVariants(all: TextVariant[]) {
  if (all.length === 1) { r.value.text = all[0]; r.value.variants = []; }
  else { r.value.text = null; r.value.variants = all; }
}
const copy = <T,>(x: T): T => JSON.parse(JSON.stringify(x));
function addVariant() { edit(() => storeVariants([...variants.value, '✦'])); }
function delVariant(i: number) { edit(() => storeVariants(variants.value.filter((_, j) => j !== i))); }
function setVariant(i: number, v: TextVariant | null) { const a = [...variants.value]; a[i] = v ?? ''; storeVariants(a); }
const dndVariants = useReorder((from, to) => moveVariant(from, to));
function moveVariant(from: number, to: number) { edit(() => { const a = [...variants.value]; moveItem(a, from, to); storeVariants(a); }); }
function dupVariant(i: number) { edit(() => { const a = [...variants.value]; a.splice(i + 1, 0, copy(a[i])); storeVariants(a); }); }
/** Вариант → кадры (текст становится первым кадром, второй - его копия) и обратно (остаётся первый кадр). */
function toggleFrames(i: number) {
  edit(() => {
    const a = [...variants.value]; const v = a[i];
    a[i] = isFrames(v) ? (v.frames[0] ?? '') : framesOf([v, copy(v)]);
    storeVariants(a);
  });
}
// кадры варианта i
function framesEdit(i: number, fn: (f: FramesText) => void, history = true) {
  const run = () => { const a = [...variants.value]; const v = a[i]; if (!isFrames(v)) return; const f = copy(v); fn(f); a[i] = f; storeVariants(a); };
  history ? edit(run) : (run(), touch());
}
function setFrame(i: number, k: number, t: RichText | null) { framesEdit(i, (f) => { f.frames[k] = t ?? ''; }, false); }
function addFrame(i: number) { framesEdit(i, (f) => { f.frames.push(copy(f.frames[f.frames.length - 1] ?? '')); }); }
function dupFrame(i: number, k: number) { framesEdit(i, (f) => { f.frames.splice(k + 1, 0, copy(f.frames[k])); }); }
function delFrame(i: number, k: number) { framesEdit(i, (f) => { if (f.frames.length > 1) f.frames.splice(k, 1); }); }
function moveFrame(i: number, from: number, to: number) { framesEdit(i, (f) => { moveItem(f.frames, from, to); }); }
const frameTint = computed(() => { if (!currentEffect.value) return null; const c = r.value.colors[0] ?? an.value.color?.[0]?.value ?? null; return c && c.startsWith('#') ? c.slice(0, 7) : c; });

// ---- фон текста
function setBg(on: boolean) { edit(() => { r.value.background = on ? [0, 0, 0] : null; r.value.backgroundAlpha = on ? 64 : null; }); }

// ---- цвет: постоянный (render.colors, случайный из списка) или градиент по жизни (animation.color).
// В плагине градиент заменяет colors, поэтому это выбор, а не два поля; спрятанное помним, чтобы
// переключение туда-обратно ничего не теряло.
const gradientMode = computed(() => an.value.color != null);
const stash = new WeakMap<object, { colors: string[]; color: Stop<string>[] | null }>();
function setColorMode(gradient: boolean) {
  if (gradient === gradientMode.value) return;
  edit(() => {
    const prev = stash.get(e.value) ?? { colors: [], color: null };
    if (gradient) {
      stash.set(e.value, { ...prev, colors: r.value.colors });
      const base = r.value.colors[0] ?? '#ffffff';
      const c = parseColor(base) ?? 0xffffffff;
      an.value.color = prev.color ?? [{ at: null, value: base }, { at: null, value: '#' + (c & 0xffffff).toString(16).padStart(6, '0') + '00' }];
      r.value.colors = [];
    } else {
      stash.set(e.value, { ...prev, color: an.value.color });
      r.value.colors = prev.colors.length ? prev.colors : [an.value.color?.[0]?.value ?? '#ffffff'];
      an.value.color = null;
    }
  });
}

// ---- нагрузка: кадры вида на среднюю частицу (как LiveParticle) - сейчас, с линейной кривой и с выбранной
const load = computed(() => {
  void currentEffect.value; // пересчёт при правке
  const eff = e.value;
  const mean = (x: [number, number]) => (x[0] + x[1]) / 2;
  const life = Math.max(1, Math.round(mean(eff.motion.lifetime)));
  const colored = textual.value;
  const start = parseColor(eff.render.colors[0] ?? '#ffffff') ?? 0xffffffff;
  const count = (easing: typeof eff.animation.easing) => {
    const x = cloneEffect(eff); x.animation.easing = easing;
    return transformPlan(compile(x), mean(eff.animation.scale), start, colored, (Math.abs(eff.animation.spin[0]) + Math.abs(eff.animation.spin[1])) / 2, life).length;
  };
  return { current: count(eff.animation.easing), linear: count('LINEAR'), eased: count(eff.animation.easing === 'LINEAR' ? 'EASE_IN' : eff.animation.easing) };
});

const COLOR_DEFAULT: Stop<string>[] = [{ at: null, value: '#ffffff' }, { at: null, value: '#ffffff00' }];
const OPACITY_DEFAULT: Stop<number>[] = [{ at: null, value: 1 }, { at: null, value: 0 }];
const blockNames = ITEM_NAMES;
</script>

<template>
  <aside class="inspector pinspector">
    <template v-if="!currentEffect"><div class="empty">Нет открытого эффекта. Создайте новый или откройте пример слева.</div></template>
    <div v-else :key="currentEffectDoc?.key" class="pinspector-body">
      <div v-if="currentEffectDoc?.warnings.length" class="callout warn"><b>При чтении файла:</b><br />{{ currentEffectDoc.warnings.join('; ') }}</div>
      <div v-if="currentEffectErrors.length" class="callout danger"><b>Плагин не примет:</b><br />{{ currentEffectErrors.join('; ') }}</div>

      <div class="section">
        <h3>Эффект</h3>
        <div class="field"><label title="Имя файла particles/<id>.jmc и имя в /displaylib particle <id>">id</label>
          <input type="text" :value="e.id" @focus="snapshot()" @change="setEffectId(($event.target as HTMLInputElement).value); touch()" /></div>
        <div class="field"><label title="Кто видит частицы: все игроки рядом или только тот, для кого запущен эффект">viewers</label>
          <div class="seg">
            <button :class="{ on: e.viewers === 'ALL' }" @click="edit(() => (e.viewers = 'ALL'))">Все рядом</button>
            <button :class="{ on: e.viewers === 'OWNER' }" @click="edit(() => (e.viewers = 'OWNER'))" title="Только игрок, для которого запущен эффект">Владелец</button>
          </div></div>
        <div class="field"><label title="Максимум тиков между пакетами движения (1-59). Меньше - точнее путь, больше - меньше трафика. На прямых участках плагин сам растягивает кадр до этого значения">keyframeTicks<LoadMark k="keyframeTicks" /></label>
          <NumInput v-model="e.keyframeTicks" :int="true" :step="1" :min="1" :max="59" /></div>
      </div>

      <div class="section">
        <h3>Вид <span class="hint">render</span></h3>
        <div class="seg wide">
          <button v-for="t in RENDER_TYPES" :key="t" :class="{ on: r.type === t }" @click="setType(t)">{{ t }}</button>
        </div>
        <p class="hint">{{ RENDER_HINT[r.type] }}</p>

        <template v-if="r.type === 'TEXT'">
          <div class="field stack"><label title="Один вариант - у всех частиц он; несколько - на каждую частицу случайный. Любой вариант можно сделать анимацией кадров, как frames у кнопок экранов">Текст частицы <span class="hint">{{ variants.length > 1 ? 'случайный из ' + variants.length : '' }}</span></label>
            <div class="frames variants">
              <div v-for="(v, i) in variants" :key="i" class="variant-card" :class="dndVariants.rowClass(i)" data-row @dragover="dndVariants.over(i, $event)" @dragleave="dndVariants.leave(i)" @drop="dndVariants.drop(i, $event)">
                <div class="variant-head">
                  <span class="grip" title="Перетащить, чтобы изменить порядок" draggable="true" @dragstart="dndVariants.start(i, $event)" @dragend="dndVariants.end()"><Icon name="grip-vertical" :size="10" /></span>
                  <span class="kind">{{ i + 1 }}</span>
                  <span class="hint grow">{{ variants.length > 1 ? 'вариант ' + (i + 1) : 'текст' }}{{ isFrames(v) ? ' · ' + v.frames.length + ' кадр(ов)' : '' }}</span>
                  <button class="btn sm frames-toggle" :class="{ on: isFrames(v) }" :title="isFrames(v) ? 'Убрать анимацию: останется первый кадр' : 'Анимация кадрами: текст сменяется каждые N тиков (как frames у кнопок экранов)'" @click="toggleFrames(i)"><Icon name="images" :size="12" /> Кадры</button>
                  <button class="btn sm icon" title="Дублировать вариант" @click="dupVariant(i)"><Icon name="clone" :size="12" /></button>
                  <button class="btn sm icon" title="Убрать вариант" :disabled="variants.length <= 1" @click="delVariant(i)"><Icon name="trash" :size="12" /></button>
                </div>
                <template v-if="isFrames(v)">
                  <FramesEditor :frames="v.frames" :interval="v.frameInterval" :over-life="v.framesOverLife" :tint="frameTint"
                    @set="(k, t) => setFrame(i, k, t)" @add="addFrame(i)" @dup="(k) => dupFrame(i, k)" @del="(k) => delFrame(i, k)" @move="(f, t) => moveFrame(i, f, t)" />
                  <div class="frames-opts">
                    <div class="seg">
                      <button :class="{ on: !v.framesOverLife }" title="Кадры сменяются по кругу каждые frameInterval тиков" @click="framesEdit(i, (f) => (f.framesOverLife = false))">По кругу</button>
                      <button :class="{ on: v.framesOverLife }" title="Кадры проходят один раз за жизнь частицы - как дым и искры Minecraft" @click="framesEdit(i, (f) => (f.framesOverLife = true))">За жизнь</button>
                    </div>
                    <label v-if="!v.framesOverLife" class="row" title="Тиков на кадр (20 = 1 с). Каждая смена - пакет каждому игроку">
                      <NumInput :model-value="v.frameInterval" :int="true" :step="1" :min="1" style="width:58px" @update:model-value="framesEdit(i, (f) => (f.frameInterval = Math.max(1, $event ?? 1)), false)" />
                      <span class="hint">т · {{ (20 / Math.max(1, v.frameInterval)).toFixed(v.frameInterval > 20 ? 2 : 1) }}/с</span><LoadMark k="frameInterval" />
                    </label>
                    <label class="row hint" title="Каждая частица начинает со случайного кадра (frameStart: random)"><input type="checkbox" :checked="v.frameRandomStart" @change="framesEdit(i, (f) => (f.frameRandomStart = !f.frameRandomStart))" /> случайный старт</label>
                  </div>
                </template>
                <RichTextInput v-else compact :model-value="v" placeholder="★" @update:model-value="setVariant(i, $event)" />
              </div>
              <div class="row"><button class="btn sm" @click="addVariant"><Icon name="plus" /> Вариант</button></div>
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
        <template v-if="textual">
          <div class="field"><label title="Постоянный - цвет на всю жизнь (из списка - случайный на частицу); по жизни - градиент от рождения к смерти">Цвет<LoadMark k="color" /></label>
            <div class="seg">
              <button :class="{ on: !gradientMode }" @click="setColorMode(false)">Постоянный</button>
              <button :class="{ on: gradientMode }" @click="setColorMode(true)">По жизни</button>
            </div></div>
          <div v-if="!gradientMode" class="field stack"><label :title="r.type === 'PIXEL' ? 'Цвет квадрата; несколько - случайный на частицу' : 'Цвет текста; несколько - случайный на частицу'">colors <span class="hint">{{ r.colors.length > 1 ? 'случайный из ' + r.colors.length : '' }}</span></label>
            <ListInput v-model="r.colors" add="#ffffff" v-slot="{ value, set }">
              <PColorInput :model-value="value" @update:model-value="set" />
            </ListInput>
            <span v-if="!r.colors.length" class="hint">{{ r.type === 'PIXEL' ? 'Без цвета - белый' : 'Без цвета - как в тексте' }}</span></div>
          <div v-else class="field stack"><label title="Цвет по доле жизни: смешивание в OkLab, как в плагине">color <span class="hint">по доле жизни</span></label>
            <StopsEditor v-model="an.color as any" kind="color" :defaults="COLOR_DEFAULT" /></div>
        </template>
        <div class="field"><label title="Как частица поворачивается к игроку">billboard</label>
          <select :value="r.billboard" @focus="snapshot()" @change="r.billboard = ($event.target as HTMLSelectElement).value as any; touch()">
            <option v-for="b in BILLBOARDS" :key="b" :value="b">{{ b }}</option>
          </select></div>
        <div class="field"><label title="Своя яркость 0-15 (15 - светится в темноте); пусто - освещение мира">brightness</label>
          <NumInput v-model="r.brightness" :int="true" :step="1" :min="0" :max="15" nullable placeholder="мир" /></div>
        <template v-if="!textual">
          <div class="field"><label title="Светящийся контур (glowing): виден сквозь блоки">glow</label><input type="checkbox" :checked="r.glow" @change="edit(() => (r.glow = !r.glow))" /></div>
          <div v-if="r.glow" class="field"><label title="Цвет контура (glowColorOverride); без цвета - белый">glowColor</label>
            <div class="row"><ColorPicker :size="28" :model-value="r.glowColor ?? '#ffffff'" @update:model-value="r.glowColor = $event; touch()" />
              <span class="hint grow">{{ r.glowColor ?? 'белый' }}</span>
              <button v-if="r.glowColor" class="btn sm" @click="edit(() => (r.glowColor = null))">Белый</button></div></div>
        </template>
        <template v-if="r.type === 'TEXT'">
          <div class="field"><label>shadow</label><input type="checkbox" :checked="r.shadow" @change="edit(() => (r.shadow = !r.shadow))" /></div>
          <div class="field"><label>background</label>
            <div class="row"><input type="checkbox" :checked="!!r.background" @change="setBg(!r.background)" />
              <ColorInput v-if="r.background" :model-value="r.background" @update:model-value="r.background = $event as any" :alpha="r.backgroundAlpha ?? 255" @update:alpha="r.backgroundAlpha = $event" /></div></div>
        </template>
      </div>

      <div class="section">
        <h3>Испускание <span class="hint">emitter</span></h3>
        <div class="field stack"><label title="Откуда вылетают частицы">shape</label>
          <div class="seg"><button v-for="s in SHAPES" :key="s" :class="{ on: em.shape === s }" @click="edit(() => (em.shape = s))">{{ s }}</button></div></div>
        <div v-if="em.shape === 'SPHERE' || em.shape === 'CIRCLE'" class="field"><label>radius</label><NumInput v-model="em.radius" :step="0.05" :min="0" /></div>
        <div v-if="em.shape === 'BOX'" class="field"><label title="Полные размеры коробки x, y, z">size</label><VecInput :model-value="em.size" @update:model-value="em.size = $event as any" :min="0" :labels="['x', 'y', 'z']" /></div>
        <div v-if="em.shape !== 'POINT'" class="field"><label title="Только по поверхности формы (по контуру для круга)">surface</label><input type="checkbox" :checked="em.surface" @change="edit(() => (em.surface = !em.surface))" /></div>
        <div class="field stack"><label title="Всплеск - все частицы сразу; N тиков - поток rate частиц за тик; бесконечно - пока не остановят">Когда</label>
          <div class="seg">
            <button :class="{ on: mode === 'burst' }" @click="setMode('burst')" title="Все частицы сразу">Всплеск</button>
            <button :class="{ on: mode === 'ticks' }" @click="setMode('ticks')" title="Испускать rate частиц за тик в течение duration тиков">N тиков</button>
            <button :class="{ on: mode === 'forever' }" @click="setMode('forever')" title="duration: -1 - пока не остановят (stop / handle.stop())">Бесконечно</button>
          </div></div>
        <div class="field"><label :title="mode === 'burst' ? 'Частиц во всплеске' : 'Частиц сразу в начале (кроме потока)'">count<LoadMark k="count" /></label>
          <NumInput v-model="em.count" :int="true" :step="1" :min="0" nullable :placeholder="String(burstCount({ ...em, count: null }))" /></div>
        <template v-if="mode !== 'burst'">
          <div v-if="mode === 'ticks'" class="field"><label title="Тиков испускания (20 = 1 с)">duration</label><NumInput v-model="em.duration" :int="true" :step="1" :min="1" /></div>
          <div class="field"><label title="Частиц за тик; дробное - в среднем (0.25 - одна в 4 тика)">rate<LoadMark k="rate" /></label><NumInput v-model="em.rate" :step="0.05" :min="0" /></div>
        </template>
      </div>

      <div class="section">
        <h3>Движение <span class="hint">motion</span></h3>
        <div class="field"><label title="Время жизни частицы, тиков">lifetime<LoadMark k="lifetime" /></label><RangeInput v-model="mo.lifetime" :int="true" :step="1" :min="1" unit="тик" /></div>
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
        <div class="field"><label title="Притяжение к точке запуска (минус - отталкивание)">attract<LoadMark k="attract" /></label><NumInput v-model="mo.attract" :step="0.005" /></div>
        <div v-if="mo.attract !== 0" class="field"><label title="Тянуть к вертикальной оси через точку запуска, а не к точке">attractAxis</label><input type="checkbox" :checked="mo.attractAxis" @change="edit(() => (mo.attractAxis = !mo.attractAxis))" /></div>
        <div class="field"><label title="Плавное случайное блуждание, блоков/тик²">wander<LoadMark k="wander" /></label><NumInput v-model="mo.wander" :step="0.002" :min="0" /></div>
        <div class="field"><label title="Сталкиваться с блоками (сервер проверяет блоки на пути - дороже)">collide<LoadMark k="collide" /></label><input type="checkbox" :checked="mo.collide" @change="edit(() => (mo.collide = !mo.collide))" /></div>
        <template v-if="mo.collide">
          <div class="field"><label title="Упругость отскока 0-1">bounce</label><NumInput v-model="mo.bounce" :step="0.05" :min="0" :max="1" /></div>
          <div class="field"><label title="Трение при скольжении по блоку 0-1">friction</label><NumInput v-model="mo.friction" :step="0.05" :min="0" :max="1" /></div>
        </template>
      </div>

      <div class="section">
        <h3>Анимация <span class="hint">animation</span></h3>
        <div class="field"><label title="Начальный размер, блоков">scale</label><RangeInput v-model="an.scale" :step="0.01" :min="0" /></div>
        <div class="field"><label title="Размер к концу жизни - множитель начального (0 - исчезает, 1 - не меняется)">endScale</label><NumInput v-model="an.endScale" :step="0.05" :min="0" /></div>
        <template v-if="an.endScale !== 1">
          <div class="field"><label title="Как размер идёт к endScale">Кривая размера<LoadMark k="easing" /></label>
            <div class="seg">
              <button :class="{ on: an.easing === 'LINEAR' }" @click="edit(() => (an.easing = 'LINEAR'))" title="Размер меняется по прямой - клиент ведёт его сам, один пакет на всю жизнь">Линейная</button>
              <button :class="{ on: an.easing !== 'LINEAR' }" @click="an.easing === 'LINEAR' && edit(() => (an.easing = 'EASE_IN'))" title="Своя кривая (easing): плавнее, но сервер шлёт кадры чаще">Своя</button>
            </div></div>
          <template v-if="an.easing !== 'LINEAR'">
            <div class="field"><label>easing</label>
              <select :value="an.easing" @focus="snapshot()" @change="an.easing = ($event.target as HTMLSelectElement).value as any; touch()"><option v-for="x in EASINGS.filter((x) => x !== 'LINEAR')" :key="x" :value="x">{{ x }}</option></select></div>
            <div class="callout warn load-warn"><b>Больше нагрузки.</b> Клиент умеет вести размер только по прямой, поэтому кривую сервер разбивает на кадры:
              ≈{{ load.eased }} пакет(а) вида на частицу вместо {{ load.linear }}. Для сотен частиц это заметно — оставьте «Линейная», если разница не видна.</div>
          </template>
        </template>
        <div class="field"><label title="Тиков на рост от 0 до размера в начале жизни">fadeIn<LoadMark k="fadeIn" /></label><NumInput v-model="an.fadeIn" :int="true" :step="1" :min="0" /></div>
        <div class="field"><label title="Начальный поворот в плоскости частицы, градусов">rotation</label><RangeInput v-model="an.rotation" :step="5" unit="°" /></div>
        <div class="field"><label title="Вращение, градусов за тик">spin<LoadMark k="spin" /></label><RangeInput v-model="an.spin" :step="1" unit="°/т" /></div>
        <template v-if="textual">
          <div class="field stack"><label title="Множитель непрозрачности по доле жизни">opacity<LoadMark k="opacity" /></label>
            <StopsEditor v-model="an.opacity as any" kind="number" :defaults="OPACITY_DEFAULT" :max="1" /></div>
        </template>
        <p v-else class="hint">Цвет и прозрачность по жизни есть только у PIXEL и TEXT.</p>
        <p class="hint" title="Сколько раз за жизнь частицы сервер шлёт размер/поворот/цвет (для средней частицы). Позиция считается отдельно - см. «пакетов/с» в предпросмотре">Кадров вида на частицу: ≈{{ load.current }}</p>
      </div>
    </div>
  </aside>
</template>

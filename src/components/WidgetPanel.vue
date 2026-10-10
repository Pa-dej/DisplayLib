<script setup lang="ts">
import Icon from './Icon.vue';
import { computed } from 'vue';
import type { Widget, ActionType } from '../model/screen';
import { defaultHoverAnimation } from '../model/screen';
import VecInput from './fields/VecInput.vue';
import ColorInput from './fields/ColorInput.vue';
import RichTextInput from './fields/RichTextInput.vue';
import MaterialInput from './fields/MaterialInput.vue';
import SpriteInput from './fields/SpriteInput.vue';
import { ATLASES } from '../render/sprites';
import AnimationEditor from './AnimationEditor.vue';
import { state, currentScreen, currentScriptInfo, screenIds, snapshot, touch, removeWidget, duplicateWidget, ensureHandler, showScript } from '../store';
import { textSizeBlocks } from '../render/scene';
import { calibration } from '../render/metrics';

const props = defineProps<{ widget: Widget }>();
const w = computed(() => props.widget);
const size = computed(() => { void state.revision; void calibration.px; return w.value.type !== 'ITEM_BUTTON' ? textSizeBlocks(w.value) : { w: calibration.itemSize * w.value.scale[0], h: calibration.itemSize * w.value.scale[1] }; });
const action = computed<ActionType>(() => w.value.onClick?.action ?? 'NONE');
const funcs = computed(() => currentScriptInfo.value?.functions ?? []);
const idDup = computed(() => !!w.value.id && (currentScreen.value?.widgets.filter((x) => x.id === w.value.id).length ?? 0) > 1);
const fnMissing = computed(() => action.value === 'RUN_SCRIPT' && !!w.value.onClick?.function && !!currentScriptInfo.value && !funcs.value.includes(w.value.onClick.function));
/** Как плагин: будет ли виджет обсчитываться на тиках (рабочий клик или видимая реакция на наведение). */
const interactive = computed(() => {
  const x = w.value; void state.revision;
  const clickable = action.value === 'SWITCH_SCREEN' ? !!x.onClick?.target && currentScreen.value?.screenType !== 'PUBLIC'
    : action.value === 'CLOSE_SCREEN' ? currentScreen.value?.screenType !== 'PUBLIC'
    : action.value === 'RUN_SCRIPT' ? !!x.onClick?.function && (!currentScriptInfo.value || funcs.value.includes(x.onClick.function))
    : false;
  if (clickable || x.tooltip != null || (x.hoverAnimation && currentScreen.value?.screenType !== 'PUBLIC')) return true;
  if (x.type === 'ITEM_BUTTON') return x.glowOnHover;
  if (x.type === 'SPRITE_BUTTON' && x.hoveredSprite && x.hoveredSprite !== x.sprite) return true;
  if (x.hoveredText != null && x.hoveredText !== '') return true;
  if (x.backgroundAlpha !== x.hoveredBackgroundAlpha) return true;
  return x.backgroundAlpha > 0 && x.backgroundColor.join() !== x.hoveredBackgroundColor.join();
});

function setType(t: string) {
  snapshot(); w.value.type = t as any;
  if (t === 'ITEM_BUTTON' && !w.value.material) w.value.material = 'DIAMOND';
  if (t === 'SPRITE_BUTTON') { if (!w.value.sprite) w.value.sprite = 'item/diamond_sword'; if (w.value.scale[0] < 1) w.value.scale = [2, 2, 1]; }
  touch();
}
function toggleHoveredSprite() { snapshot(); w.value.hoveredSprite = w.value.hoveredSprite ? null : w.value.sprite; touch(); }
function setAction(a: string) {
  snapshot();
  if (a === 'NONE') w.value.onClick = null;
  else { const c = w.value.onClick ?? { action: 'NONE' as ActionType }; c.action = a as ActionType; if (a === 'RUN_SCRIPT' && !c.function) c.function = handlerName(w.value.id); if (a === 'SWITCH_SCREEN' && !c.target) c.target = screenIds.value.find((x) => x !== currentScreen.value?.id) ?? ''; w.value.onClick = c; }
  touch();
}
function text(ev: Event) { return (ev.target as HTMLInputElement).value; }
/** Имя обработчика по умолчанию = id виджета (в файле тогда пишется onClick: true, без строки). */
function handlerName(id: string): string {
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(id) ? id : 'onClick';
}
function toggleAnim() { snapshot(); w.value.hoverAnimation = w.value.hoverAnimation ? null : { ...defaultHoverAnimation(), preset: 'LIFT' }; touch(); }
function toggleGlowColor() { snapshot(); w.value.glowColor = w.value.glowColor ? null : [255, 255, 255]; touch(); }
function toggleTooltipColor() { snapshot(); w.value.tooltipColor = w.value.tooltipColor ? null : [134, 135, 136]; touch(); }
function fmt(n: number) { return (Math.round(n * 1000) / 1000).toString(); }
</script>
<template>
  <div>
    <div class="section">
      <h2><span>Виджет</span><span class="btn-group"><button class="btn sm" @click="duplicateWidget(w.uid)" title="Дублировать"><Icon name="clone" /></button><button class="btn sm danger" @click="removeWidget(w.uid)"><Icon name="trash" /> Удалить</button></span></h2>
      <div class="field"><label>id</label><div><input type="text" :value="w.id" @focus="snapshot()" @input="w.id = text($event); touch()" placeholder="Для screen.widget(id)" /><div v-if="idDup" class="hint" style="color:var(--vp-c-danger-1)">Такой id уже есть у другого виджета</div></div></div>
      <div class="field"><label>Тип</label><select :value="w.type" @change="setType(($event.target as HTMLSelectElement).value)"><option value="TEXT_BUTTON">TEXT_BUTTON - текст</option><option value="ITEM_BUTTON">ITEM_BUTTON - предмет</option><option value="SPRITE_BUTTON">SPRITE_BUTTON - спрайт атласа</option></select></div>
    </div>

    <div class="section">
      <h3>Положение<span class="hint">Размер ≈ {{ fmt(size.w) }} × {{ fmt(size.h) }} бл.</span></h3>
      <div class="field"><label>position</label><VecInput v-model="w.position" :labels="['X вправо', 'Y вверх', 'Z от игрока (−0.01 = ближе)']" /></div>
      <div class="field"><label>scale</label><VecInput v-model="w.scale" :labels="['x', 'y', 'z']" :step="0.05" /></div>
      <div class="field"><label>translation</label><VecInput v-model="w.translation" :labels="['x', 'y', 'z']" :step="0.01" /></div>
      <div class="field"><label>tolerance</label><VecInput v-model="w.tolerance" :labels="['Полуширина', 'Полувысота']" :step="0.01" :min="0" /></div>
      <div class="hint">tolerance - зона наведения (голубой прямоугольник): центр в position + translation, у текста и спрайтов - по центру нарисованного текста</div>
    </div>

    <div class="section" v-if="w.type === 'SPRITE_BUTTON'">
      <h3>Спрайт</h3>
      <div class="field"><label>atlas</label><div><input type="text" list="atlas-list" :value="w.atlas ?? ''" @focus="snapshot()" @input="w.atlas = text($event).trim() || null; touch()" placeholder="Авто: item/… → items, иначе blocks" /><datalist id="atlas-list"><option v-for="a in ATLASES" :key="a.id" :value="a.id">{{ a.hint }} · напр. {{ a.example }}</option></datalist></div></div>
      <div class="field"><label>sprite</label><SpriteInput v-model="w.sprite" :atlas="w.atlas" @update:atlas="w.atlas = $event" /></div>
      <div class="field"><label>hoveredSprite</label><div class="row"><SpriteInput v-if="w.hoveredSprite != null" v-model="w.hoveredSprite" :atlas="w.atlas" @update:atlas="w.atlas = $event" class="grow" /><span v-else class="hint grow">Как sprite</span><button class="btn sm" @click="toggleHoveredSprite">{{ w.hoveredSprite != null ? 'Убрать' : 'Задать' }}</button></div></div>
      <div class="field"><label>Фон</label><ColorInput v-model="w.backgroundColor" :alpha="w.backgroundAlpha" @update:alpha="w.backgroundAlpha = $event" /></div>
      <div class="field"><label>Фон hover</label><ColorInput v-model="w.hoveredBackgroundColor" :alpha="w.hoveredBackgroundAlpha" @update:alpha="w.hoveredBackgroundAlpha = $event" /></div>
      <div class="hint">Спрайт - квадрат 8×8 текстовых пикселей (0.2 блока при scale 1), рисуется клиентом 1.21.9+ как объект в тексте. Имя спрайта = путь к png внутри папки атласа без расширения: items/blocks - <code>item/apple</code>, <code>block/stone</code> (из <code>textures/</code>); gui - <code>hud/heart/full</code> (из <code>textures/gui/sprites/</code>); иконки эффектов - тоже gui: <code>mob_effect/speed</code>; particles - <code>heart</code>; paintings, banner_patterns, shield_patterns, armor_trims аналогично. Голое имя без атласа (<code>mace</code>, <code>bricks</code>, <code>speed</code>) плагин дополняет сам: предмет → <code>item/mace</code>, блок → <code>block/bricks</code>, эффект → <code>mob_effect/speed</code> (gui). Подсказки берутся из скопированных текстур (<code>npm run sprites</code>).</div>
    </div>

    <div class="section" v-if="w.type === 'TEXT_BUTTON'">
      <h3>Текст</h3>
      <div class="field stack"><label>text</label><RichTextInput v-model="w.text" placeholder="Текст кнопки" /></div>
      <div class="field stack"><label>hoveredText <span class="hint">При наведении (по умолчанию тот же)</span></label><RichTextInput v-model="w.hoveredText" nullable placeholder="Как text" /></div>
      <div class="field"><label>alignment</label><select :value="w.alignment" @change="snapshot(); w.alignment = ($event.target as HTMLSelectElement).value as any; touch()"><option>LEFT</option><option>CENTERED</option><option>RIGHT</option></select></div>
      <div class="field"><label>Фон</label><ColorInput v-model="w.backgroundColor" :alpha="w.backgroundAlpha" @update:alpha="w.backgroundAlpha = $event" /></div>
      <div class="field"><label>Фон hover</label><ColorInput v-model="w.hoveredBackgroundColor" :alpha="w.hoveredBackgroundAlpha" @update:alpha="w.hoveredBackgroundAlpha = $event" /></div>
      <div class="hint">Строки длиннее 200 px переносятся, как в игре; §-коды (§6, §l) работают внутри текста</div>
    </div>

    <div class="section" v-if="w.type === 'ITEM_BUTTON'">
      <h3>Предмет</h3>
      <div class="field"><label>material</label><MaterialInput v-model="w.material" /></div>
      <div class="field"><label></label><label class="row"><input type="checkbox" :checked="w.glowOnHover" @change="snapshot(); w.glowOnHover = ($event.target as HTMLInputElement).checked; touch()" /> Свечение при наведении</label></div>
      <div class="field"><label>glowColor</label><div class="row"><ColorInput v-if="w.glowColor" v-model="w.glowColor" /><button class="btn sm" @click="toggleGlowColor">{{ w.glowColor ? 'Убрать' : 'Задать' }}</button></div></div>
    </div>

    <div class="section">
      <h3>Подсказка</h3>
      <div class="field stack"><label>tooltip <span class="hint">title над хотбаром при наведении</span></label><RichTextInput v-model="w.tooltip" nullable placeholder="Нет подсказки" /></div>
      <template v-if="w.tooltip != null">
        <div class="field"><label>tooltipDelay</label><div class="row"><input type="number" min="0" step="1" :value="w.tooltipDelay" @focus="snapshot()" @input="w.tooltipDelay = Math.max(0, Math.round(Number(text($event)))); touch()" /><span class="hint">Тиков</span></div></div>
        <div class="field"><label>tooltipColor</label><div class="row"><ColorInput v-if="w.tooltipColor" v-model="w.tooltipColor" /><button class="btn sm" @click="toggleTooltipColor">{{ w.tooltipColor ? 'Убрать' : 'Задать' }}</button></div></div>
      </template>
    </div>

    <div class="section">
      <h3>По клику</h3>
      <div class="field"><label>onClick</label><select :value="action" @change="setAction(($event.target as HTMLSelectElement).value)">
        <option value="NONE">Ничего</option><option value="RUN_SCRIPT">Функция скрипта</option><option value="SWITCH_SCREEN">Открыть экран</option><option value="CLOSE_SCREEN">Закрыть экран</option></select></div>
      <div class="hint" :style="interactive ? {} : { color: 'var(--vp-c-text-3)' }">{{ interactive ? '● интерактивный: плагин следит за взглядом игрока' : '○ надпись: нет действия и реакции на наведение - плагин не обсчитывает наведение (пустая функция вида void f(dyn w, dyn p) {} тоже считается «ничего»)' }}</div>
      <div v-if="action === 'RUN_SCRIPT' && w.onClick" class="field"><label>function</label>
        <div><input type="text" list="fn-list" :value="w.onClick.function ?? ''" @focus="snapshot()" @input="w.onClick!.function = text($event); touch()" placeholder="Имя функции" />
          <datalist id="fn-list"><option v-for="f in funcs" :key="f" :value="f" /></datalist>
          <div class="hint" v-if="fnMissing" style="color:var(--vp-c-warning-1)">В скрипте такой функции нет - <button class="btn sm" @click="ensureHandler(w.onClick!.function!)">Добавить <code>void {{ w.onClick.function }}(dyn widget, dyn player)</code></button></div>
          <div class="hint" v-else>Сигнатура: <code>void {{ w.onClick.function || 'name' }}(dyn widget, dyn player)</code>{{ w.onClick.function && w.onClick.function === w.id ? ' · в файле: onClick: true (имя = id)' : '' }} · <a href="#" @click.prevent="showScript()">Открыть скрипт</a></div></div></div>
      <div v-if="action === 'SWITCH_SCREEN' && w.onClick" class="field"><label>switchTo</label>
        <div><input type="text" list="screen-list" :value="w.onClick.target ?? ''" @focus="snapshot()" @input="w.onClick!.target = text($event); touch()" placeholder="id экрана" />
          <datalist id="screen-list"><option v-for="s in screenIds" :key="s" :value="s" /></datalist>
          <div class="hint" v-if="currentScreen?.screenType === 'PUBLIC'" style="color:var(--vp-c-warning-1)">На публичном экране переключение не работает</div></div></div>
      <div v-if="action === 'CLOSE_SCREEN' && currentScreen?.screenType === 'PUBLIC'" class="hint" style="color:var(--vp-c-warning-1)">Публичный экран кликом не закрывается</div>
    </div>

    <div class="section">
      <h3>Hover-анимация<button class="btn sm" @click="toggleAnim">{{ w.hoverAnimation ? 'Убрать' : 'Добавить' }}</button></h3>
      <AnimationEditor v-if="w.hoverAnimation" :anim="w.hoverAnimation" @remove="toggleAnim" />
      <div v-if="w.hoverAnimation && currentScreen?.screenType === 'PUBLIC'" class="hint" style="color:var(--vp-c-warning-1)">На публичных экранах hover-анимации отключены</div>
    </div>
  </div>
</template>

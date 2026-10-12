<script setup lang="ts">
import Icon from './Icon.vue';
import { useReorder, moveItem } from '../reorder';
import { computed } from 'vue';
import type { Widget, ActionType, RichText } from '../model/screen';
import { defaultHoverAnimation } from '../model/screen';
import VecInput from './fields/VecInput.vue';
import ColorInput from './fields/ColorInput.vue';
import RichTextInput from './fields/RichTextInput.vue';
import MaterialInput from './fields/MaterialInput.vue';
import SpriteInput from './fields/SpriteInput.vue';
import TintInput from './fields/TintInput.vue';
import SoundInput from './fields/SoundInput.vue';
import { ATLASES } from '../render/sprites';
import AnimationEditor from './AnimationEditor.vue';
import { state, currentScreen, currentScriptInfo, screenIds, snapshot, touch, removeWidget, duplicateWidget, ensureHandler, showScript } from '../store';
import { textSizeBlocks } from '../render/scene';
import { clickWorks, hasHoverVisual } from '../lint';
import { calibration } from '../render/metrics';

const props = defineProps<{ widget: Widget }>();
const w = computed(() => props.widget);
type FrameKey = 'frames' | 'hoveredFrames';
function addFrame(k: FrameKey) { snapshot(); const last = w.value[k][w.value[k].length - 1]; w.value[k].push(last != null ? cloneRich(last) : (k === 'frames' ? cloneRich(w.value.text) : '')); if (k === 'frames') w.value.text = w.value.frames[0]; touch(); }
function removeFrame(k: FrameKey, i: number) { snapshot(); w.value[k].splice(i, 1); if (k === 'frames' && w.value.frames.length) w.value.text = w.value.frames[0]; touch(); }
function setFrame(k: FrameKey, i: number, v: RichText | null) { w.value[k][i] = v ?? ''; if (k === 'frames' && i === 0) w.value.text = w.value.frames[0]; touch(); }
function moveFrame(k: FrameKey, from: number, to: number) { snapshot(); moveItem(w.value[k], from, to); if (k === 'frames') w.value.text = w.value.frames[0]; touch(); }
const dndFrames = useReorder((f, t) => moveFrame('frames', f, t));
const dndHoveredFrames = useReorder((f, t) => moveFrame('hoveredFrames', f, t));
function cloneRich(r: RichText): RichText { return typeof r === 'string' ? r : r.map((s) => ({ ...s })); }
const size = computed(() => { void state.revision; void calibration.px; return w.value.type !== 'ITEM_BUTTON' ? textSizeBlocks(w.value) : { w: calibration.itemSize * w.value.scale[0], h: calibration.itemSize * w.value.scale[1] }; });
const action = computed<ActionType>(() => w.value.onClick?.action ?? 'NONE');
const funcs = computed(() => currentScriptInfo.value?.functions ?? []);
const idDup = computed(() => !!w.value.id && (currentScreen.value?.widgets.filter((x) => x.id === w.value.id).length ?? 0) > 1);
const fnMissing = computed(() => action.value === 'RUN_SCRIPT' && !!w.value.onClick?.function && !!currentScriptInfo.value && !funcs.value.includes(w.value.onClick.function));
/** Как плагин: будет ли виджет обсчитываться на тиках (рабочий клик или видимая реакция на наведение). */
const interactive = computed(() => {
  const x = w.value; const s = currentScreen.value; void state.revision;
  if (!s) return false;
  if (x.tolerance[0] <= 0 || x.tolerance[1] <= 0) return false;
  return clickWorks(x, s, currentScriptInfo.value, screenIds.value).ok || hasHoverVisual(x, s);
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
function setSkull(v: string) { const s = v.trim(); w.value.skull = s || null; if (s && w.value.material !== 'PLAYER_HEAD') w.value.material = 'PLAYER_HEAD'; touch(); }
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
      <div class="field"><label>rotation</label><div class="row">
        <input type="range" min="-180" max="180" step="1" class="grow" :value="w.rotation" title="Поворот в плоскости экрана: вправо - против часовой, влево - по часовой" @pointerdown="snapshot()" @input="w.rotation = Number(text($event)); touch()" @dblclick="snapshot(); w.rotation = 0; touch()" />
        <input type="number" step="1" style="width:64px" :value="w.rotation" @focus="snapshot()" @input="w.rotation = Number(text($event)) || 0; touch()" /><span class="hint">°</span>
      </div></div>
      <div class="field"><label>tolerance</label><VecInput v-model="w.tolerance" :labels="['Полуширина', 'Полувысота']" :step="0.01" :min="0" /></div>
      <div class="hint">tolerance - зона наведения (голубой прямоугольник): центр в position + translation, у текста и спрайтов - по центру нарисованного текста</div>
    </div>

    <div class="section" v-if="w.type === 'SPRITE_BUTTON'">
      <h3>Спрайт</h3>
      <div class="field"><label>atlas</label><div><input type="text" list="atlas-list" :value="w.atlas ?? ''" @focus="snapshot()" @input="w.atlas = text($event).trim() || null; touch()" placeholder="Авто: item/… → items, иначе blocks" /><datalist id="atlas-list"><option v-for="a in ATLASES" :key="a.id" :value="a.id">{{ a.hint }} · напр. {{ a.example }}</option></datalist></div></div>
      <div class="field"><label>sprite</label><SpriteInput v-model="w.sprite" :atlas="w.atlas" @update:atlas="w.atlas = $event" /></div>
      <div class="field"><label>hoveredSprite</label><div class="row"><SpriteInput v-if="w.hoveredSprite != null" v-model="w.hoveredSprite" :atlas="w.atlas" @update:atlas="w.atlas = $event" class="grow" /><span v-else class="hint grow">Как sprite</span><button class="btn sm" @click="toggleHoveredSprite">{{ w.hoveredSprite != null ? 'Убрать' : 'Задать' }}</button></div></div>
      <div class="field"><label>spriteColor</label><TintInput v-model="w.spriteColor" none="без тинта" /></div>
      <div class="field"><label>hoveredSpriteColor</label><TintInput v-model="w.hoveredSpriteColor" none="как spriteColor" /></div>
      <div class="hint">Тинт - цвет текста, на который клиент умножает текстуру: белые/серые иконки (HUD, эффекты, узоры) красятся чисто, цветные предметы лишь затемняются.</div>
      <div class="field"><label>Фон</label><ColorInput v-model="w.backgroundColor" :alpha="w.backgroundAlpha" @update:alpha="w.backgroundAlpha = $event" /></div>
      <div class="field"><label>Фон hover</label><ColorInput v-model="w.hoveredBackgroundColor" :alpha="w.hoveredBackgroundAlpha" @update:alpha="w.hoveredBackgroundAlpha = $event" /></div>
      <div class="hint">Спрайт - квадрат 8×8 текстовых пикселей (0.2 блока при scale 1), рисуется клиентом 1.21.9+ как объект в тексте. Имя спрайта = путь к png внутри папки атласа без расширения: items/blocks - <code>item/apple</code>, <code>block/stone</code> (из <code>textures/</code>); gui - <code>hud/heart/full</code> (из <code>textures/gui/sprites/</code>); иконки эффектов - тоже gui: <code>mob_effect/speed</code>; particles - <code>heart</code>; paintings, banner_patterns, shield_patterns, armor_trims аналогично. Голое имя без атласа (<code>mace</code>, <code>bricks</code>, <code>speed</code>) плагин дополняет сам: предмет → <code>item/mace</code>, блок → <code>block/bricks</code>, эффект → <code>mob_effect/speed</code> (gui). Подсказки берутся из скопированных текстур (<code>npm run sprites</code>).</div>
    </div>

    <div class="section" v-if="w.type === 'TEXT_BUTTON'">
      <h3>Текст</h3>
      <div class="field stack"><label>text</label><RichTextInput v-model="w.text" placeholder="Текст кнопки" /></div>
      <div class="field stack"><label>hoveredText <span class="hint">При наведении (по умолчанию тот же)</span></label><RichTextInput v-model="w.hoveredText" nullable placeholder="Как text" /></div>
      <div class="field stack"><label>frames <span class="hint">Кадры анимации: сменяются каждые N тиков; text = первый кадр</span></label>
        <div class="frames">
          <div v-for="(fr, i) in w.frames" :key="i" class="frame-row" :class="dndFrames.rowClass(i)" data-row @dragover="dndFrames.over(i, $event)" @dragleave="dndFrames.leave(i)" @drop="dndFrames.drop(i, $event)">
            <span class="grip" title="Перетащить, чтобы изменить порядок" draggable="true" @dragstart="dndFrames.start(i, $event)" @dragend="dndFrames.end()"><Icon name="grip-vertical" :size="10" /></span>
            <span class="kind">{{ i + 1 }}</span>
            <RichTextInput class="grow" :model-value="fr" @update:model-value="setFrame('frames', i, $event)" placeholder="Кадр" />
            <button class="btn sm icon" title="Удалить кадр" @click="removeFrame('frames', i)"><Icon name="times" /></button>
          </div>
          <div class="row">
            <button class="btn sm" @click="addFrame('frames')"><Icon name="plus" /> Кадр</button>
            <template v-if="w.frames.length"><label class="hint">frameInterval</label><input type="number" min="1" step="1" style="width:64px" :value="w.frameInterval" @focus="snapshot()" @input="w.frameInterval = Math.max(1, Math.round(Number(text($event)))); touch()" /><span class="hint">тиков</span></template>
          </div>
        </div>
      </div>
      <div class="field stack"><label>hoveredFrames <span class="hint">Кадры при наведении (вместо hoveredText)</span></label>
        <div class="frames">
          <div v-for="(fr, i) in w.hoveredFrames" :key="i" class="frame-row" :class="dndHoveredFrames.rowClass(i)" data-row @dragover="dndHoveredFrames.over(i, $event)" @dragleave="dndHoveredFrames.leave(i)" @drop="dndHoveredFrames.drop(i, $event)">
            <span class="grip" title="Перетащить, чтобы изменить порядок" draggable="true" @dragstart="dndHoveredFrames.start(i, $event)" @dragend="dndHoveredFrames.end()"><Icon name="grip-vertical" :size="10" /></span>
            <span class="kind">{{ i + 1 }}</span>
            <RichTextInput class="grow" :model-value="fr" @update:model-value="setFrame('hoveredFrames', i, $event)" placeholder="Кадр" />
            <button class="btn sm icon" title="Удалить кадр" @click="removeFrame('hoveredFrames', i)"><Icon name="times" /></button>
          </div>
          <div class="row">
            <button class="btn sm" @click="addFrame('hoveredFrames')"><Icon name="plus" /> Кадр</button>
            <template v-if="w.hoveredFrames.length"><label class="hint">hoveredFrameInterval</label><input type="number" min="0" step="1" style="width:64px" :value="w.hoveredFrameInterval > 0 ? w.hoveredFrameInterval : ''" :placeholder="String(w.frameInterval)" @focus="snapshot()" @input="w.hoveredFrameInterval = Math.round(Number(text($event))) || -1; touch()" /><span class="hint">тиков</span></template>
          </div>
        </div>
      </div>
      <div class="field"><label>lineWidth</label><div class="row"><input type="number" min="1" step="10" style="width:72px" :value="w.lineWidth" @focus="snapshot()" @input="w.lineWidth = Math.max(1, Math.round(Number(text($event))) || 200); touch()" /><span class="hint">px шрифта; дальше текст переносится (клиент: 200 ≈ 33 символа)</span></div></div>
      <div class="field"><label>alignment</label><select :value="w.alignment" @change="snapshot(); w.alignment = ($event.target as HTMLSelectElement).value as any; touch()"><option>LEFT</option><option>CENTERED</option><option>RIGHT</option></select></div>
      <div class="field"><label>Фон</label><ColorInput v-model="w.backgroundColor" :alpha="w.backgroundAlpha" @update:alpha="w.backgroundAlpha = $event" /></div>
      <div class="field"><label>Фон hover</label><ColorInput v-model="w.hoveredBackgroundColor" :alpha="w.hoveredBackgroundAlpha" @update:alpha="w.hoveredBackgroundAlpha = $event" /></div>
      <div class="hint">Строки длиннее 200 px переносятся, как в игре; §-коды (§6, §l) работают внутри текста</div>
    </div>

    <div class="section" v-if="w.type === 'ITEM_BUTTON'">
      <h3>Предмет</h3>
      <div class="field"><label>material</label><MaterialInput v-model="w.material" /></div>
      <div class="field"><label>itemTransform</label><div class="row"><select :value="w.itemTransform" @change="snapshot(); w.itemTransform = ($event.target as HTMLSelectElement).value; touch()">
        <option value="GUI">GUI - иконка как в инвентаре</option>
        <option value="FIXED">FIXED - объёмно, как в рамке (½ блока)</option>
        <option value="NONE">NONE - объёмно, полный размер</option>
        <option value="HEAD">HEAD - как на голове</option>
        <option value="GROUND">GROUND - как на земле (½)</option>
        <option value="THIRDPERSON_RIGHTHAND">THIRDPERSON_RIGHTHAND</option>
        <option value="FIRSTPERSON_RIGHTHAND">FIRSTPERSON_RIGHTHAND</option>
      </select></div></div>
      <div class="field" v-if="w.itemTransform !== 'GUI'"><label>tilt</label><div class="row"><VecInput v-model="w.tilt" :labels="['вокруг X (кивок)', 'вокруг Y (разворот)']" :step="5" /><span class="hint">° - изометрия: 30 / 45</span></div></div>
      <div class="field"><label>skull</label><div class="row">
        <input type="text" class="grow" :value="w.skull ?? ''" placeholder="Имя, UUID, @viewer, base64-текстура или URL" @focus="snapshot()" @input="setSkull(text($event))" />
        <button class="btn sm" v-if="w.skull" title="Голова того, кто смотрит на экран (только приватные)" @click="snapshot(); setSkull('@viewer')">@viewer</button>
      </div></div>
      <div class="hint">Голова игрока: укажи <code>skull</code> - материал сам станет PLAYER_HEAD. Имя/UUID плагин дотягивает с Mojang асинхронно; значение <code>Value</code> с minecraft-heads.com (base64) или URL <code>textures.minecraft.net/texture/…</code> ставится сразу и работает без сети. Объёмный вид - transform FIXED/NONE + tilt. Превью голов по имени грузится с mc-heads.net; по текстуре - если сервис знает этот хеш, иначе показывается Стив.</div>
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
      <h3>Звук</h3>
      <div class="field"><label>clickSound</label><SoundInput v-model="w.clickSound" default-hint="по умолчанию" /></div>
      <div class="field"><label>hoverSound</label><SoundInput v-model="w.hoverSound" default-hint="нет" /></div>
      <div class="hint">Ключи звуков клиента (<code>ui.button.click</code>, <code>block.note_block.pling</code>…). По умолчанию клик: у текста/спрайта - <code>block.dispenser.fail</code> (pitch 2), у предмета - <code>ui.button.click</code>; звук наведения считается hover-реакцией.</div>
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

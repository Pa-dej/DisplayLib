<script setup lang="ts">
import Icon from './Icon.vue';
import { computed, ref, watch } from 'vue';
import type { Screen } from '../model/screen';
import { defaultBackground, ANCHOR_PRESETS, anchorPresetName, type Vec2 } from '../model/screen';
import VecInput from './fields/VecInput.vue';
import ColorInput from './fields/ColorInput.vue';
import { state, currentDoc, snapshot, touch, deleteCurrent, showScript, scriptNameOf, scriptStatus, scaleScreen, currentIssues } from '../store';
import { calibration } from '../render/metrics';
import { layout } from '../render/text';

const props = defineProps<{ screen: Screen }>();
const s = computed(() => props.screen);
function text(ev: Event) { return (ev.target as HTMLInputElement).value; }
function num(ev: Event) { return Number((ev.target as HTMLInputElement).value); }
const bgSize = computed(() => {
  void state.revision; void calibration.px;
  const b = s.value.background; if (!b) return null;
  const L = layout(b.text);
  return { w: L.bgW * calibration.px * b.scale[0], h: L.bgH * calibration.px * b.scale[1] };
});
// Якорь сдвигает только фон (так и в плагине). С галочкой «виджеты вместе с фоном» редактор сдвигает
// и все виджеты на ту же величину, чтобы раскладка относительно фона не менялась.
const anchorFollow = ref(localStorage.getItem('displaylib.anchorFollow') !== '0');
watch(anchorFollow, (v) => localStorage.setItem('displaylib.anchorFollow', v ? '1' : '0'));
function anchorOffset(a: Vec2): [number, number] {
  const sz = bgSize.value; if (!sz) return [0, 0];
  return [-(a[0] - 0.5) * sz.w, -a[1] * sz.h];
}
function setAnchor(v: Vec2, snap = true) {
  const b = s.value.background; if (!b) return;
  if (snap) snapshot(); // из VecInput снимок уже сделан по фокусу
  const before = anchorOffset(b.anchor);
  b.anchor = [v[0], v[1]];
  if (anchorFollow.value) {
    const after = anchorOffset(b.anchor);
    const dx = after[0] - before[0], dy = after[1] - before[1];
    const r = (n: number) => Math.round(n * 1000) / 1000;
    for (const w of s.value.widgets) w.position = [r(w.position[0] + dx), r(w.position[1] + dy), w.position[2]];
  }
  touch();
}
function toggleBg() { snapshot(); s.value.background = s.value.background ? null : defaultBackground(); touch(); }
function fmt(n: number) { return (Math.round(n * 100) / 100).toString(); }
const scaleK = ref(1);
function applyScale() { if (scaleK.value > 0 && scaleK.value !== 1) { scaleScreen(scaleK.value); scaleK.value = 1; } }
const idBad = computed(() => !/^[A-Za-z0-9_\-]+$/.test(s.value.id));
</script>
<template>
  <div>
    <div class="section">
      <h2><span>Экран</span><button class="btn sm danger" @click="deleteCurrent" title="Удалить файл экрана"><Icon name="trash" /> Удалить</button></h2>
      <div class="field"><label>id</label><div><input type="text" :value="s.id" @focus="snapshot()" @input="s.id = text($event); touch()" /><div class="hint">Файл screens/{{ s.id }}.jmc</div><div v-if="idBad" class="hint" style="color:var(--vp-c-danger-1)">Только латиница, цифры, _ и -</div></div></div>
      <div class="field"><label>Тип</label><select :value="s.screenType" @change="snapshot(); s.screenType = ($event.target as HTMLSelectElement).value as any; touch()"><option value="PRIVATE">PRIVATE - для одного игрока</option><option value="PUBLIC">PUBLIC - стоит в мире</option></select></div>
      <div class="hint" v-if="s.screenType === 'PUBLIC'">Публичный: открывается командой <code>/dlib openpublic {{ s.id }} x y z</code>, глобал <code>player</code> В скрипте равен null, игрок приходит аргументом обработчика; hover-анимации, switchTo и close не работают.</div>
      <div class="hint" v-else>Приватный: <code>/dlib open {{ s.id }}</code> - экран появляется в 2 блоках перед игроком.</div>
    </div>

    <div class="section" v-if="currentIssues.length">
      <h3>Проверка<span class="hint">{{ currentIssues.length }}</span></h3>
      <div v-for="(i, k) in currentIssues" :key="k" class="issue-row" :class="i.level" @click="i.uid && (state.selected = i.uid)" :title="i.uid ? 'Выбрать виджет' : ''">
        <span class="mark">{{ i.level === 'error' ? '✖' : i.level === 'warn' ? '!' : '○' }}</span><span>{{ i.text }}</span>
      </div>
      <div class="hint">Правила те же, что у плагина при загрузке экрана (ScreenAnalyzer): статичные виджеты не обсчитываются, это нормально для надписей.</div>
    </div>
    <div class="section">
      <h3>Размер экрана</h3>
      <div class="field"><label>Масштаб ×</label><div class="row">
        <input type="number" min="0.05" step="0.05" v-model.number="scaleK" style="width:80px" @keydown.enter="applyScale" />
        <button class="btn sm" @click="applyScale" :disabled="!(scaleK > 0) || scaleK === 1">Применить</button>
        <button class="btn sm" title="Уменьшить вдвое" @click="scaleK = 0.5; applyScale()">½</button>
        <button class="btn sm" title="Увеличить вдвое" @click="scaleK = 2; applyScale()">×2</button>
      </div></div>
      <div class="hint">Умножает позиции, масштабы, tolerance и translation всех виджетов и фона, а также смещения hover-анимаций - пропорции экрана сохраняются. Относительные величины (scale анимации, повороты) не меняются. Отменяется через Ctrl+Z.</div>
    </div>

    <div class="section">
      <h3>Поведение</h3>
      <div class="field"><label>tickRate</label><div class="row"><input type="number" min="1" max="20" step="1" :value="s.tickRate" @focus="snapshot()" @input="s.tickRate = Math.max(1, Math.min(20, Math.round(num($event)))); touch()" /><span class="hint">Тиков между обновлениями</span></div></div>
      <div class="field"><label>interactionRadius</label><div class="row"><input type="number" step="0.5" :value="s.interactionRadius" @focus="snapshot()" @input="s.interactionRadius = num($event); touch()" /><span class="hint">-1 = без ограничения</span></div></div>
      <div class="field" v-if="s.screenType === 'PRIVATE'"><label>closeDistance</label><div class="row"><input type="number" step="0.5" :value="s.closeDistance" @focus="snapshot()" @input="s.closeDistance = num($event); touch()" /><span class="hint">-1 = не закрывать</span></div></div>
      <div class="field"><label>rangeCheckInterval</label><div class="row"><input type="number" min="1" step="1" :value="s.rangeCheckInterval" @focus="snapshot()" @input="s.rangeCheckInterval = Math.max(1, Math.round(num($event))); touch()" /><span class="hint">Обновлений</span></div></div>
    </div>

    <div class="section" v-if="s.screenType === 'PRIVATE'">
      <h3>Morph</h3>
      <div class="field"><label>morph</label><label class="row"><input type="checkbox" :checked="s.morph.enabled" @change="snapshot(); s.morph.enabled = ($event.target as HTMLInputElement).checked; touch()" /> <span class="hint">{{ s.morph.enabled ? `анимация ${s.morph.duration} тиков, ${s.morph.steps > 0 ? s.morph.steps : Math.max(1, Math.floor(s.morph.duration / 2))} шагов` : 'выключен - смена экрана мгновенная' }}</span></label></div>
      <div class="hint">Длительность, шаги и превью переходов - на вкладке <b>Morph</b> вверху панели.</div>
    </div>

    <div class="section">
      <h3>Скрипт</h3>
      <div class="field"><label>script</label><div><input type="text" :value="s.script ?? ''" @focus="snapshot()" @input="s.script = text($event).trim() || null; touch()" placeholder="Имя.jmp (в scripts/)" /></div></div>
      <div class="row wrap">
        <button class="btn sm" @click="showScript()">Открыть скрипт</button>
        <span class="hint" v-if="currentDoc">scripts/{{ scriptNameOf(currentDoc) }} · {{ { saved: 'Экспортирован', dirty: 'Есть правки', 'template-needed': 'Шаблон, уйдёт в экспорт', template: 'Шаблон, не нужен (нет вызовов функций)' }[scriptStatus(currentDoc)] }}</span>
      </div>
      <div class="hint">У каждого экрана есть скрипт <code>scripts/&lt;id&gt;.jmp</code> - как и разметка. Пока его не правили, это живой шаблон по кнопкам экрана: хуки <code>onOpen()</code>/<code>onClose()</code> И обработчик <code>void &lt;имя&gt;(dyn widget, dyn player)</code> На каждый onClick. Код правится во вкладке скрипта под предпросмотром; скачивается вместе с экраном («Скачать» / Ctrl+S) или через «Экспорт», если на экране есть вызовы функций или текст правили.</div>
    </div>

    <div class="section">
      <h3>Фон<button class="btn sm" @click="toggleBg">{{ s.background ? 'Убрать' : 'Добавить' }}</button></h3>
      <template v-if="s.background">
        <div class="field"><label>Цвет</label><ColorInput v-model="s.background.color" :alpha="s.background.alpha" @update:alpha="s.background!.alpha = $event" /></div>
        <div class="field"><label>scale</label><div><VecInput v-model="s.background.scale" :labels="['x', 'y', 'z']" :step="0.5" /><div class="hint" v-if="bgSize">≈ {{ fmt(bgSize.w) }} × {{ fmt(bgSize.h) }} блока ([8, 4, 1] ≈ 1×1 для текста " ")</div></div></div>
        <div class="field"><label>position</label><VecInput v-model="s.background.position" :labels="['x', 'y', 'z']" /></div>
        <div class="field"><label>Якорь</label><div class="row" style="align-items:flex-start">
          <div class="anchor-grid">
            <button v-for="p in ANCHOR_PRESETS" :key="p.name" class="btn sm icon" :class="{ primary: anchorPresetName(s.background.anchor) === p.name }" :title="p.name" @click="setAnchor(p.value)">{{ p.label }}</button>
          </div>
          <div class="grow">
            <VecInput :model-value="s.background.anchor" :labels="['x', 'y']" :step="0.05" @update:model-value="setAnchor($event as Vec2, false)" />
            <label class="row hint" style="margin-top:4px"><input type="checkbox" v-model="anchorFollow" /> Виджеты вместе с фоном</label>
            <div class="hint">Какая точка фона стоит в начале координат (position [0,0,0] виджетов): доли ширины и высоты, x слева направо, y снизу вверх. [0.5, 0] - низ по центру (как раньше), [0.5, 0.5] - центр.</div>
          </div>
        </div></div>
        <div class="field"><label>translation</label><div><VecInput v-model="s.background.translation" :labels="['x', 'y', 'z']" :step="0.01" /><div class="hint">Дополнительный сдвиг поверх якоря; по x плагин сам добавляет -scaleX/80</div></div></div>
        <div class="field"><label>text</label><input type="text" :value="s.background.text" @focus="snapshot()" @input="s.background!.text = text($event); touch()" /></div>
      </template>
    </div>
  </div>
</template>

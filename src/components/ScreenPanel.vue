<script setup lang="ts">
import Icon from './Icon.vue';
import { computed } from 'vue';
import type { Screen } from '../model/screen';
import { defaultBackground } from '../model/screen';
import VecInput from './fields/VecInput.vue';
import ColorInput from './fields/ColorInput.vue';
import { state, currentDoc, snapshot, touch, deleteCurrent, showScript, scriptNameOf, scriptStatus } from '../store';
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
function toggleBg() { snapshot(); s.value.background = s.value.background ? null : defaultBackground(); touch(); }
function fmt(n: number) { return (Math.round(n * 100) / 100).toString(); }
const idBad = computed(() => !/^[A-Za-z0-9_\-]+$/.test(s.value.id));
</script>
<template>
  <div>
    <div class="section">
      <h2><span>Экран</span><button class="btn sm danger" @click="deleteCurrent" title="Удалить файл экрана"><Icon name="trash" /> Удалить</button></h2>
      <div class="field"><label>id</label><div><input type="text" :value="s.id" @focus="snapshot()" @input="s.id = text($event); touch()" /><div class="hint">Файл screens/{{ s.id }}.jmc{{ currentDoc && currentDoc.path !== s.id + '.jmc' && currentDoc.savedText !== null ? ' (сейчас ' + currentDoc.path + ', при сохранении переименуется)' : '' }}</div><div v-if="idBad" class="hint" style="color:var(--vp-c-danger-1)">Только латиница, цифры, _ и -</div></div></div>
      <div class="field"><label>Тип</label><select :value="s.screenType" @change="snapshot(); s.screenType = ($event.target as HTMLSelectElement).value as any; touch()"><option value="PRIVATE">PRIVATE - для одного игрока</option><option value="PUBLIC">PUBLIC - стоит в мире</option></select></div>
      <div class="hint" v-if="s.screenType === 'PUBLIC'">Публичный: открывается командой <code>/dlib openpublic {{ s.id }} x y z</code>, глобал <code>player</code> В скрипте равен null, игрок приходит аргументом обработчика; hover-анимации, switchTo и close не работают.</div>
      <div class="hint" v-else>Приватный: <code>/dlib open {{ s.id }}</code> - экран появляется в 2 блоках перед игроком.</div>
    </div>

    <div class="section">
      <h3>Поведение</h3>
      <div class="field"><label>tickRate</label><div class="row"><input type="number" min="1" max="20" step="1" :value="s.tickRate" @focus="snapshot()" @input="s.tickRate = Math.max(1, Math.min(20, Math.round(num($event)))); touch()" /><span class="hint">Тиков между обновлениями</span></div></div>
      <div class="field"><label>interactionRadius</label><div class="row"><input type="number" step="0.5" :value="s.interactionRadius" @focus="snapshot()" @input="s.interactionRadius = num($event); touch()" /><span class="hint">-1 = без ограничения</span></div></div>
      <div class="field" v-if="s.screenType === 'PRIVATE'"><label>closeDistance</label><div class="row"><input type="number" step="0.5" :value="s.closeDistance" @focus="snapshot()" @input="s.closeDistance = num($event); touch()" /><span class="hint">-1 = не закрывать</span></div></div>
      <div class="field"><label>rangeCheckInterval</label><div class="row"><input type="number" min="1" step="1" :value="s.rangeCheckInterval" @focus="snapshot()" @input="s.rangeCheckInterval = Math.max(1, Math.round(num($event))); touch()" /><span class="hint">Обновлений</span></div></div>
    </div>

    <div class="section">
      <h3>Скрипт</h3>
      <div class="field"><label>script</label><div><input type="text" list="script-list" :value="s.script ?? ''" @focus="snapshot()" @input="s.script = text($event).trim() || null; touch()" placeholder="Имя.jmp (в scripts/)" />
        <datalist id="script-list"><option v-for="p in state.scripts" :key="p" :value="p" /></datalist></div></div>
      <div class="row wrap">
        <button class="btn sm" @click="showScript()">Открыть скрипт</button>
        <span class="hint" v-if="currentDoc">scripts/{{ scriptNameOf(currentDoc) }} · {{ { saved: 'Файл на диске', dirty: 'Есть правки', 'template-needed': 'Шаблон, запишется при сохранении', template: 'Шаблон, не нужен (нет вызовов функций)' }[scriptStatus(currentDoc)] }}</span>
      </div>
      <div class="hint">У каждого экрана есть скрипт <code>scripts/&lt;id&gt;.jmp</code> - как и разметка. Пока его не правили, это живой шаблон по кнопкам экрана: хуки <code>onOpen()</code>/<code>onClose()</code> И обработчик <code>void &lt;имя&gt;(dyn widget, dyn player)</code> На каждый onClick. Код правится во вкладке скрипта под предпросмотром; на диск уходит вместе с экраном (Ctrl+S), если на экране есть вызовы функций или текст правили.</div>
    </div>

    <div class="section">
      <h3>Фон<button class="btn sm" @click="toggleBg">{{ s.background ? 'Убрать' : 'Добавить' }}</button></h3>
      <template v-if="s.background">
        <div class="field"><label>Цвет</label><ColorInput v-model="s.background.color" :alpha="s.background.alpha" @update:alpha="s.background!.alpha = $event" /></div>
        <div class="field"><label>scale</label><div><VecInput v-model="s.background.scale" :labels="['x', 'y', 'z']" :step="0.5" /><div class="hint" v-if="bgSize">≈ {{ fmt(bgSize.w) }} × {{ fmt(bgSize.h) }} блока ([8, 4, 1] ≈ 1×1 для текста " ")</div></div></div>
        <div class="field"><label>position</label><VecInput v-model="s.background.position" :labels="['x', 'y', 'z']" /></div>
        <div class="field"><label>translation</label><div><VecInput v-model="s.background.translation" :labels="['x', 'y', 'z']" :step="0.01" /><div class="hint">По x плагин сам добавляет -scaleX/80; якорь фона - низ, [0, -h/2, 0] центрирует</div></div></div>
        <div class="field"><label>text</label><input type="text" :value="s.background.text" @focus="snapshot()" @input="s.background!.text = text($event); touch()" /></div>
      </template>
    </div>
  </div>
</template>

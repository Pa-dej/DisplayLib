<script setup lang="ts">
// Вкладка Morph: настройки перехода текущего экрана, переходы на другие открытые экраны (цели запоминаются
// на экран в localStorage), что именно переедет / исчезнет / появится при выбранной паре.
import { computed, ref, watch } from 'vue';
import Icon from './Icon.vue';
import { state, currentScreen, currentDoc, snapshot, touch } from '../store';
import { planMorph } from '../render/morph';

const s = computed(() => currentScreen.value!);
const num = (e: Event) => Number((e.target as HTMLInputElement).value);
const others = computed(() => state.docs.filter((d) => d.path !== state.current));
/** Экраны, на которые ведут кнопки switchTo текущего экрана - самые вероятные цели. */
const linked = computed(() => {
  const out = new Map<string, string[]>();
  for (const w of s.value.widgets) {
    const t = w.onClick?.action === 'SWITCH_SCREEN' ? w.onClick.target : null; if (!t) continue;
    const d = state.docs.find((x) => x.screen.id === t); if (!d || d.path === state.current) continue;
    out.set(d.path, [...(out.get(d.path) ?? []), w.id || '(без id)']);
  }
  return out;
});
const key = () => 'displaylib.morphTarget.' + s.value.id;
const target = ref('');
function restore() {
  let saved = '';
  try { saved = localStorage.getItem(key()) ?? ''; } catch { /* ignore */ }
  const ok = (p: string) => !!p && others.value.some((d) => d.path === p);
  target.value = ok(saved) ? saved : ok([...linked.value.keys()][0] ?? '') ? [...linked.value.keys()][0] : others.value[0]?.path ?? '';
}
watch(() => [state.current, state.docs.length], restore, { immediate: true });
watch(target, (v) => { try { if (v) localStorage.setItem(key(), v); } catch { /* ignore */ } });
const targetDoc = computed(() => state.docs.find((d) => d.path === target.value) ?? null);
const plan = computed(() => { void state.revision; return targetDoc.value ? planMorph(s.value, targetDoc.value.screen) : null; });
const playing = computed(() => state.morphTo != null);
function play(path: string) { state.morphTo = path; }
/** Туда и обратно: после перехода редактор переключится на цель; запомним, что надо вернуться. */
function roundTrip() {
  const back = state.current!; const to = target.value; if (!to) return;
  const stop = watch(() => state.current, (cur) => { if (cur === to) { stop(); setTimeout(() => { state.morphTo = back; }, 400); } });
  state.morphTo = to;
}
const tMorph = computed(() => targetDoc.value?.screen.morph ?? null);
</script>
<template>
  <div v-if="!currentScreen" class="empty">Нет открытого экрана</div>
  <template v-else>
    <h2 class="row" style="justify-content:space-between">Morph <span class="hint">{{ s.id }}</span></h2>
    <div class="section">
      <h3>Этот экран</h3>
      <div class="field"><label>morph</label><label class="row"><input type="checkbox" :checked="s.morph.enabled" @change="snapshot(); s.morph.enabled = ($event.target as HTMLInputElement).checked; touch()" /> <span class="hint">Анимировать переход <b>на этот</b> экран (switchTo) и его закрытие</span></label></div>
      <template v-if="s.morph.enabled">
        <div class="field"><label>duration</label><div class="row"><input type="range" min="1" max="59" step="1" class="grow" :value="s.morph.duration" @pointerdown="snapshot()" @input="s.morph.duration = num($event); touch()" /><input type="number" min="1" max="59" step="1" style="width:64px" :value="s.morph.duration" @focus="snapshot()" @input="s.morph.duration = Math.max(1, Math.min(59, Math.round(num($event)))); touch()" /><span class="hint" style="white-space:nowrap">{{ (s.morph.duration * 50 / 1000).toFixed(2) }} с</span></div></div>
        <div class="field"><label>steps</label><div class="row"><input type="number" min="0" max="59" step="1" style="width:64px" :value="s.morph.steps > 0 ? s.morph.steps : ''" placeholder="duration / 2" @focus="snapshot()" @input="s.morph.steps = Math.round(num($event)) || -1; touch()" /><span class="hint">шагов смены текста, цвета текста и предмета</span></div></div>
      </template>
      <div v-if="s.screenType === 'PUBLIC'" class="callout warn">Публичные экраны не переключаются - morph тут не действует.</div>
    </div>

    <div class="section">
      <h3>Переход <span class="hint" style="margin-left:6px;text-transform:none">настройки берутся у экрана-цели</span></h3>
      <div v-if="!others.length" class="hint">Откройте второй экран (импорт или «+ Экран»), чтобы посмотреть переход.</div>
      <template v-else>
        <div class="field"><label>цель</label><select v-model="target" class="grow">
          <option v-for="d in others" :key="d.path" :value="d.path">{{ d.screen.id }}{{ linked.has(d.path) ? ' ← ' + linked.get(d.path)!.join(', ') : '' }}</option>
        </select></div>
        <div class="row" style="gap:6px;flex-wrap:wrap">
          <button class="btn primary sm" :disabled="!target || playing" title="Проиграть переход и переключиться на цель" @click="play(target)"><Icon name="exchange-alt" /> {{ s.id }} → {{ targetDoc?.screen.id }}</button>
          <button class="btn sm" :disabled="!target || playing" title="Туда и сразу обратно" @click="roundTrip"><Icon name="sync-alt" /> Туда-обратно</button>
        </div>
        <div v-if="tMorph && !tMorph.enabled" class="callout warn" style="margin-top:6px">У экрана «{{ targetDoc?.screen.id }}» morph выключен - в игре переход будет мгновенным; превью проигрывает его с длительностью {{ tMorph.duration }} тиков, чтобы было видно, что куда переедет.</div>
        <div v-if="plan" class="morph-plan">
          <div class="hint" style="margin-top:8px"><b>{{ plan.matched.length }}</b> переедут · <b>{{ plan.fadeOut.length }}</b> исчезнут · <b>{{ plan.fadeIn.length }}</b> появятся · фон: {{ plan.bg.from && plan.bg.to ? 'переезжает' : plan.bg.from ? 'исчезает' : plan.bg.to ? 'появляется' : 'нет' }}</div>
          <div v-for="m in plan.matched" :key="m.from.uid" class="plan-row ok" @click="state.selected = m.from.uid" title="Выбрать виджет"><Icon name="exchange-alt" :size="10" /> <span class="mono">{{ m.from.id }}</span><span class="hint">{{ m.from.type === 'ITEM_BUTTON' ? 'предмет' : 'текст/спрайт' }}{{ m.from.position.join(',') !== m.to.position.join(',') ? ' · едет' : '' }}{{ typeof m.from.text === 'string' && typeof m.to.text === 'string' && m.from.text !== m.to.text ? ' · текст меняется' : '' }}</span></div>
          <div v-for="w in plan.fadeOut" :key="w.uid" class="plan-row out" @click="state.selected = w.uid" title="Выбрать виджет"><Icon name="compress" :size="10" /> <span class="mono">{{ w.id || '(без id)' }}</span><span class="hint">исчезнет{{ w.id && targetDoc?.screen.widgets.some((x) => x.id === w.id) ? ' - на цели тот же id, но другой тип сущности' : '' }}</span></div>
          <div v-for="w in plan.fadeIn" :key="w.uid" class="plan-row in"><Icon name="expand" :size="10" /> <span class="mono">{{ w.id || '(без id)' }}</span><span class="hint">появится</span></div>
          <div class="hint" style="margin-top:6px">Чтобы виджет переезжал, а не мигал, дайте ему на обоих экранах одинаковый <code>id</code> и тип сущности (текст и спрайт - одна сущность, предмет - другая).</div>
        </div>
      </template>
    </div>

    <div class="section">
      <h3>Как это работает</h3>
      <div class="hint">Клиент сам интерполирует позицию, масштаб, поворот и цвет фона за <code>duration</code> тиков. Текст, цвет текста и предмет он не интерполирует - плагин меняет их по <code>steps</code> шагам: общие начало и конец строки остаются, середина старого текста «съедается», нового - «вырастает». Виджеты только старого экрана сжимаются в ноль, только нового - вырастают из нуля. Прозрачность текста не анимируется (ограничение клиента).</div>
    </div>
  </template>
</template>
<style scoped>
.plan-row { display: flex; align-items: center; gap: 6px; font-size: 12px; padding: 3px 4px; border-radius: 4px; cursor: pointer; }
.plan-row:hover { background: var(--vp-c-bg-alt); }
.plan-row.ok { color: var(--vp-c-brand-1); }
.plan-row.out { color: var(--vp-c-text-3); }
.plan-row.in { color: var(--vp-c-text-2); cursor: default; }
.mono { font-family: var(--vp-font-family-mono); }
</style>

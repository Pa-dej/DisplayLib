<script setup lang="ts">
// Градиент цвета или кривая числа по доле жизни частицы (0 - рождение, 1 - смерть), правка мышью.
// Градиент: ручки цветов под полосой двигаются влево-вправо (неравномерная смена цвета), двойной клик по
// полосе - новый цвет. Кривая: точки двигаются по доле и значению; ромб между точками - узел кривой
// Безье, через который проходит отрезок (изгиб), двойной клик по нему - снова прямая.
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { snapshot, touch } from '../../store';
import type { Stop } from '../../particles/model';
import { gradientOf, curveOf, sampleGradient, sampleCurve, bentPoint, alpha, cssHex, colorToString, parseColor } from '../../particles/colors';
import PColorInput from './PColorInput.vue';
import NumInput from './NumInput.vue';
import Icon from '../Icon.vue';

type V = string | number;
const props = defineProps<{ modelValue: Stop<V>[] | null; kind: 'color' | 'number'; defaults: Stop<V>[]; max?: number }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: Stop<V>[] | null): void }>();

const stops = computed(() => props.modelValue ?? []);
const explicit = computed(() => stops.value.length > 0 && stops.value.every((s) => s.at != null));
const times = computed(() => stops.value.map((s, i, a) => (explicit.value ? (s.at as number) : a.length === 1 ? 0 : i / (a.length - 1))));
const selected = ref(0);
const sel = computed(() => Math.min(selected.value, stops.value.length - 1));
const r3 = (x: number) => Math.round(x * 1000) / 1000;

function set(v: Stop<V>[] | null) { emit('update:modelValue', v); touch(); }
/** Копия точек с явными долями (перетаскивание по времени делает их явными). */
function withTimes(): Stop<V>[] { const t = times.value; return stops.value.map((s, i) => ({ ...s, at: r3(t[i]) })); }
function enable() { snapshot(); set(props.defaults.map((s) => ({ ...s }))); selected.value = 0; }
function disable() { snapshot(); set(null); }
function even() { snapshot(); set(stops.value.map((s) => ({ ...s, at: null }))); }
function setAt(i: number, v: number | null) {
  const a = withTimes();
  const lo = i > 0 ? (a[i - 1].at as number) : 0, hi = i < a.length - 1 ? (a[i + 1].at as number) : 1;
  a[i].at = r3(Math.max(lo, Math.min(hi, v ?? 0)));
  set(a);
}
function setValue(i: number, v: V) { const a = stops.value.map((s) => ({ ...s })); a[i].value = v; set(a); }
function remove(i: number) {
  if (stops.value.length <= 1) return;
  snapshot();
  const a = stops.value.filter((_, j) => j !== i).map((s) => ({ ...s }));
  if (a.length) a[0].bend = undefined;
  set(a); selected.value = Math.max(0, i - 1);
}
/** Новая точка на доле t: цвет/значение - как сейчас на этом месте, чтобы ничего не скакнуло. */
function insertAt(t: number, value?: number) {
  snapshot();
  const a = withTimes();
  let i = a.findIndex((s) => (s.at as number) > t); if (i < 0) i = a.length;
  let v: V;
  if (props.kind === 'color') { const g = gradientOf(stops.value as Stop<string>[]); v = g ? colorToString(sampleGradient(g, t)) : '#ffffff'; }
  else v = value ?? sampleCurve(curveOf(stops.value as Stop<number>[]), t);
  if (i < a.length) a[i].bend = undefined; // отрезок разрезан - изгиб сбрасывается
  a.splice(i, 0, { at: r3(t), value: typeof v === 'number' ? r3(v) : v });
  set(a); selected.value = i;
}
function addButton() {
  // в середину самого длинного промежутка
  const t = times.value;
  if (t.length < 2) { insertAt(t.length ? (t[0] < 0.5 ? 1 : 0) : 0); return; }
  let best = 1; for (let i = 2; i < t.length; i++) if (t[i] - t[i - 1] > t[best] - t[best - 1]) best = i;
  insertAt((t[best] + t[best - 1]) / 2);
}

// ---------------------------------------------------------------- размер области рисования
const box = ref<HTMLElement | null>(null);
// Ширина рисунка = ширина блока. Блок появляется позже компонента (после «Задать кривую»), поэтому
// следим за ref, а не только за монтированием - иначе SVG оставался 280 px, а мышь мерилась по всей ширине.
const W = ref(280);
const ro = new ResizeObserver(() => { if (box.value) W.value = Math.max(120, box.value.clientWidth); });
watch(box, (el, old) => { if (old) ro.unobserve(old); if (el) { ro.observe(el); W.value = Math.max(120, el.clientWidth); } }, { flush: 'post' });
onBeforeUnmount(() => ro.disconnect());

/**
 * Перетаскивание с одним снимком undo. fn получает долю 0..1 по x и по y (сверху вниз) - с поправкой на
 * точку захвата: объект не прыгает центром под курсор, а едет вместе с ним. Правки - не чаще кадра.
 * `at` - где объект сейчас (те же доли), чтобы посчитать поправку.
 */
const dragging = ref(false);
function drag(e: PointerEvent, at: [number, number], fn: (x: number, y: number) => void) {
  if (e.button !== 0) return;
  e.preventDefault(); e.stopPropagation();
  snapshot();
  const el = box.value!;
  const frac = (ev: PointerEvent): [number, number] => {
    const r = el.getBoundingClientRect();
    return [(ev.clientX - r.left - PAD_X) / (r.width - 2 * PAD_X), (ev.clientY - r.top - PAD_Y) / (H - 2 * PAD_Y)];
  };
  const [x0, y0] = frac(e);
  const dx = at[0] - x0, dy = at[1] - y0;
  let last: PointerEvent | null = null, raf = 0;
  dragging.value = true;
  const apply = () => { raf = 0; if (last) { const [x, y] = frac(last); fn(x + dx, y + dy); last = null; } };
  const move = (ev: PointerEvent) => { last = ev; if (!raf) raf = requestAnimationFrame(apply); };
  const up = () => {
    if (raf) { cancelAnimationFrame(raf); apply(); }
    dragging.value = false;
    window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', up);
  };
  window.addEventListener('pointermove', move); window.addEventListener('pointerup', up); window.addEventListener('pointercancel', up);
}

// ---------------------------------------------------------------- градиент
const gradientCss = computed(() => {
  if (props.kind !== 'color' || !stops.value.length) return '';
  const g = gradientOf(stops.value as Stop<string>[]); if (!g) return '';
  const parts: string[] = [];
  for (let i = 0; i <= 48; i++) {
    const c = sampleGradient(g, i / 48), rgb = cssHex(c);
    parts.push(`rgba(${parseInt(rgb.slice(1, 3), 16)},${parseInt(rgb.slice(3, 5), 16)},${parseInt(rgb.slice(5, 7), 16)},${(alpha(c) / 255).toFixed(3)}) ${(i / 48) * 100}%`);
  }
  return `linear-gradient(90deg, ${parts.join(', ')})`;
});
function handleCss(v: V) { const c = parseColor(String(v)); return c == null ? '#fff' : `rgba(${(c >>> 16) & 255},${(c >>> 8) & 255},${c & 255},${alpha(c) / 255})`; }
function dragHandle(e: PointerEvent, i: number) { selected.value = i; drag(e, [times.value[i], 0], (x) => setAt(i, x)); }
function barDbl(e: MouseEvent) {
  const r = box.value!.getBoundingClientRect();
  insertAt(Math.max(0, Math.min(1, (e.clientX - r.left - PAD_X) / (r.width - 2 * PAD_X))));
}

// ---------------------------------------------------------------- кривая
const H = 150, PAD_X = 10, PAD_Y = 12;
// Верх шкалы: max (у opacity - 1) или наибольшее значение; пока тянем - не меняется, иначе шкала
// «уезжала» из-под курсора и точки прыгали.
const liveTop = computed(() => props.max ?? Math.max(1, ...stops.value.map((s) => Number(s.value))));
const frozenTop = ref(1);
watch(dragging, (d) => { if (d) frozenTop.value = liveTop.value; });
const top = computed(() => (dragging.value ? frozenTop.value : liveTop.value));
const yFrac = (v: number) => 1 - v / top.value;
const px = (t: number) => PAD_X + t * (W.value - 2 * PAD_X);
const py = (v: number) => H - PAD_Y - (v / top.value) * (H - 2 * PAD_Y);
const curvePath = computed(() => {
  if (props.kind !== 'number' || !stops.value.length) return '';
  const t = times.value, a = stops.value as Stop<number>[];
  const pts: string[] = [`M${px(0)},${py(a[0].value)}`, `L${px(t[0])},${py(a[0].value)}`];
  for (let i = 1; i < a.length; i++) {
    const b = a[i].bend;
    for (let k = 1; k <= 32; k++) {
      const s = k / 32;
      const [u, v] = b ? bentPoint(a[i - 1].value, a[i].value, b[0], b[1], s) : [s, a[i - 1].value + (a[i].value - a[i - 1].value) * s];
      pts.push(`L${px(t[i - 1] + (t[i] - t[i - 1]) * u)},${py(v)}`);
    }
  }
  pts.push(`L${px(1)},${py(a[a.length - 1].value)}`);
  return pts.join(' ');
});
const areaPath = computed(() => (curvePath.value ? curvePath.value + ` L${px(1)},${py(0)} L${px(0)},${py(0)} Z` : ''));
/** Узлы изгиба: середина каждого отрезка (на кривой - при параметре 1/2). */
const nodes = computed(() => {
  if (props.kind !== 'number') return [];
  const t = times.value, a = stops.value as Stop<number>[];
  const out: { i: number; x: number; y: number; bent: boolean }[] = [];
  for (let i = 1; i < a.length; i++) {
    if (t[i] - t[i - 1] < 1e-6) continue;
    const b = a[i].bend ?? [0.5, 0];
    const [u, v] = bentPoint(a[i - 1].value, a[i].value, b[0], b[1], 0.5);
    out.push({ i, x: px(t[i - 1] + (t[i] - t[i - 1]) * u), y: py(v), bent: !!a[i].bend && Math.abs(a[i].bend![1]) > 1e-9 });
  }
  return out;
});
function dragPoint(e: PointerEvent, i: number) {
  selected.value = i;
  drag(e, [times.value[i], yFrac(Number(stops.value[i].value))], (x, y) => {
    const a = withTimes();
    const lo = i > 0 ? (a[i - 1].at as number) : 0, hi = i < a.length - 1 ? (a[i + 1].at as number) : 1;
    a[i].at = r3(Math.max(lo, Math.min(hi, x)));
    a[i].value = r3(Math.max(0, Math.min(top.value, (1 - y) * top.value)));
    set(a);
  });
}
function dragNode(e: PointerEvent, i: number) {
  const n = nodes.value.find((k) => k.i === i)!;
  drag(e, [(n.x - PAD_X) / (W.value - 2 * PAD_X), (n.y - PAD_Y) / (H - 2 * PAD_Y)], (x, y) => {
    const a = withTimes();
    const t0 = a[i - 1].at as number, t1 = a[i].at as number;
    const u = Math.max(0.25, Math.min(0.75, (x - t0) / (t1 - t0)));
    const v = Math.max(0, Math.min(top.value, (1 - y) * top.value));
    const v0 = a[i - 1].value as number, v1 = a[i].value as number;
    a[i].bend = [r3(u), r3(v - (v0 + (v1 - v0) * u))];
    set(a);
  });
}
function straighten(i: number) { snapshot(); const a = stops.value.map((s) => ({ ...s })); a[i].bend = undefined; set(a); }
function curveDbl(e: MouseEvent) {
  const r = box.value!.getBoundingClientRect();
  const t = Math.max(0, Math.min(1, (e.clientX - r.left - PAD_X) / (r.width - 2 * PAD_X)));
  const v = Math.max(0, Math.min(top.value, (1 - (e.clientY - r.top - PAD_Y) / (H - 2 * PAD_Y)) * top.value));
  insertAt(t, v);
}
const gridY = computed(() => [0, 0.25, 0.5, 0.75, 1].map((k) => ({ y: py(k * top.value), label: +(k * top.value).toFixed(2) })));
</script>

<template>
  <div class="stops">
    <template v-if="!modelValue">
      <button class="btn sm" @click="enable"><Icon name="plus" /> {{ kind === 'color' ? 'Задать градиент' : 'Задать кривую' }}</button>
    </template>
    <template v-else>
      <!-- градиент: полоса + ручки -->
      <div v-if="kind === 'color'" ref="box" class="grad-edit" @dblclick="barDbl" title="Двойной клик - новый цвет; ручки двигаются влево-вправо">
        <div class="grad-bar checker"><span :style="{ background: gradientCss }"></span></div>
        <div class="grad-handles">
          <button v-for="(s, i) in stops" :key="i" class="grad-handle" :class="{ on: i === sel }" :style="{ left: `calc(${PAD_X}px + ${times[i]} * (100% - ${2 * PAD_X}px))` }"
            :title="`${(times[i] * 100).toFixed(0)}% жизни: ${s.value}`" @pointerdown="dragHandle($event, i)" @dblclick.stop="remove(i)" @keydown.delete="remove(i)" @click="selected = i">
            <span class="checker"><span :style="{ background: handleCss(s.value) }"></span></span>
          </button>
        </div>
      </div>
      <!-- кривая -->
      <div v-else ref="box" class="curve-edit" @dblclick="curveDbl">
        <svg :width="W" :height="H">
          <g class="grid">
            <line v-for="g in gridY" :key="g.y" :x1="PAD_X" :x2="W - PAD_X" :y1="g.y" :y2="g.y" />
            <line v-for="k in 3" :key="'v' + k" :x1="px(k / 4)" :x2="px(k / 4)" :y1="PAD_Y" :y2="H - PAD_Y" />
            <text v-for="g in gridY.filter((_, j) => j % 2 === 0)" :key="'t' + g.y" :x="PAD_X + 2" :y="g.y - 2">{{ g.label }}</text>
          </g>
          <path class="area" :d="areaPath" />
          <path class="line" :d="curvePath" />
          <g v-for="n in nodes" :key="'n' + n.i" class="node" :class="{ bent: n.bent }" @pointerdown="dragNode($event, n.i)" @dblclick.stop="straighten(n.i)">
            <circle class="hit" :cx="n.x" :cy="n.y" r="10" />
            <rect :x="n.x - 5" :y="n.y - 5" width="10" height="10" :transform="`rotate(45 ${n.x} ${n.y})`" />
            <title>Изгиб отрезка: тяните; двойной клик - прямая</title>
          </g>
          <g v-for="(s, i) in stops" :key="'p' + i" class="pt" :class="{ on: i === sel }" @pointerdown="dragPoint($event, i)" @dblclick.stop="remove(i)">
            <circle class="hit" :cx="px(times[i])" :cy="py(Number(s.value))" r="11" />
            <circle :cx="px(times[i])" :cy="py(Number(s.value))" r="6" />
            <title>{{ (times[i] * 100).toFixed(0) }}% жизни: {{ s.value }} (двойной клик - убрать)</title>
          </g>
        </svg>
      </div>

      <div v-if="stops[sel]" class="stop-row">
        <NumInput class="at" :model-value="r3(times[sel])" :step="0.05" :min="0" :max="1" title="Доля жизни 0-1" @update:model-value="setAt(sel, $event)" />
        <PColorInput v-if="kind === 'color'" :model-value="stops[sel].value as string" @update:model-value="setValue(sel, $event)" />
        <NumInput v-else :model-value="stops[sel].value as number" :step="0.05" :min="0" @update:model-value="setValue(sel, $event ?? 0)" />
        <button class="btn sm icon" title="Убрать точку" :disabled="stops.length <= 1" @click="remove(sel)"><Icon name="times" /></button>
      </div>
      <div class="row">
        <button class="btn sm" @click="addButton"><Icon name="plus" /> Точка</button>
        <button class="btn sm" :disabled="!explicit" title="Распределить точки равномерно (без долей в файле)" @click="even"><Icon name="arrows-alt-h" /> Равномерно</button>
        <span class="grow"></span>
        <button class="btn sm icon" @click="disable" title="Убрать (по умолчанию)"><Icon name="trash" /></button>
      </div>
      <p class="hint stops-hint">{{ kind === 'color' ? 'Ручки - влево/вправо; двойной клик по полосе - новый цвет, по ручке - убрать.' : 'Точки - мышью; ромб - изгиб (Безье), двойной клик по нему - прямая; двойной клик по пустому - новая точка, по точке - убрать.' }}</p>
    </template>
  </div>
</template>

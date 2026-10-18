<script setup lang="ts">
// Свой выбор цвета вместо браузерного <input type="color">: квадрат насыщенность/яркость, оттенок,
// прозрачность, поля HEX / RGB / HSL, пипетка, цвета Minecraft и недавние. Ctrl+V в открытом окне
// (или в любом поле окна) понимает #hex, rgb(), hsl(), "255, 128, 0", §c и имена - см. color/parse.ts.
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import Icon from '../Icon.vue';
import { snapshot } from '../../store';
import { NAMED_COLORS } from '../../render/text';
import { parseAnyColor, rgbaToHex, rgbToHsv, hsvToRgb, rgbToHsl, hslToRgb, type RGBA } from '../../color/parse';

const props = defineProps<{ modelValue: string | null | undefined; alpha?: boolean; title?: string; size?: number }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>();

// ---- состояние: HSV + альфа (оттенок не теряется на сером и чёрном)
const h = ref(0), s = ref(0), v = ref(1), a = ref(1);
const rgb = computed(() => hsvToRgb({ h: h.value, s: s.value, v: v.value }));
const rgba = computed<RGBA>(() => ({ ...rgb.value, a: props.alpha ? a.value : 1 }));
const hex = computed(() => rgbaToHex(rgba.value, !!props.alpha));
const shown = computed(() => parseAnyColor(props.modelValue ?? '') ?? null);
const triggerCss = computed(() => (shown.value ? `rgba(${shown.value.r},${shown.value.g},${shown.value.b},${props.alpha ? shown.value.a : 1})` : 'transparent'));

let lastEmitted = '';
function syncFrom(value: string | null | undefined) {
  const c = parseAnyColor(value ?? '');
  if (!c) return;
  const cur = hsvToRgb({ h: h.value, s: s.value, v: v.value });
  if (cur.r !== c.r || cur.g !== c.g || cur.b !== c.b) {
    const hsv = rgbToHsv(c);
    // серый/чёрный: оттенок (и насыщенность у чёрного) оставляем прежними
    if (hsv.s > 0 && hsv.v > 0) h.value = hsv.h;
    if (hsv.v > 0) s.value = hsv.s;
    v.value = hsv.v;
  }
  a.value = c.a;
}
watch(() => props.modelValue, (val) => { if (val !== lastEmitted) syncFrom(val); }, { immediate: true });

function commit() {
  const out = hex.value;
  if (out === lastEmitted && out === props.modelValue) return;
  lastEmitted = out;
  emit('update:modelValue', out);
}
function setRgba(c: RGBA) {
  lastEmitted = '';
  syncFrom(rgbaToHex(c, true));
  if (!props.alpha) a.value = 1;
  commit();
}

// ---- окно
const open = ref(false);
const trigger = ref<HTMLElement | null>(null);
const pop = ref<HTMLElement | null>(null);
const pos = ref({ left: 0, top: 0 });
const before = ref('');
function place() {
  const t = trigger.value?.getBoundingClientRect(); if (!t) return;
  const W = 264, H = pop.value?.offsetHeight ?? 420;
  let left = t.left, top = t.bottom + 6;
  if (left + W > window.innerWidth - 8) left = window.innerWidth - W - 8;
  if (top + H > window.innerHeight - 8) top = Math.max(8, t.top - H - 6);
  pos.value = { left: Math.max(8, left), top };
}
async function toggle() {
  if (open.value) { close(); return; }
  snapshot();
  syncFrom(props.modelValue);
  before.value = hex.value;
  open.value = true;
  await nextTick(); place();
  document.addEventListener('pointerdown', onOutside, true);
  document.addEventListener('keydown', onKey, true);
  window.addEventListener('resize', place);
  pop.value?.focus();
}
function close() {
  if (!open.value) return;
  open.value = false;
  document.removeEventListener('pointerdown', onOutside, true);
  document.removeEventListener('keydown', onKey, true);
  window.removeEventListener('resize', place);
  if (hex.value !== before.value) remember(hex.value);
}
function onOutside(e: PointerEvent) {
  const t = e.target as Node;
  if (pop.value?.contains(t) || trigger.value?.contains(t)) return;
  close();
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') { e.stopPropagation(); close(); trigger.value?.focus(); }
  else if (e.key === 'Enter' && (e.target as HTMLElement)?.tagName !== 'INPUT') close();
}
onBeforeUnmount(close);

// Ctrl+V в окне: в поле ввода - если весь буфер это цвет, берём его целиком; вне поля - всегда
function onPaste(e: ClipboardEvent) {
  const text = e.clipboardData?.getData('text/plain') ?? '';
  const c = parseAnyColor(text);
  if (!c) { if ((e.target as HTMLElement)?.tagName !== 'INPUT') flash('Не цвет: ' + text.slice(0, 40)); return; }
  e.preventDefault();
  snapshot();
  setRgba(props.alpha ? c : { ...c, a: 1 });
  flash('Вставлено ' + rgbaToHex(c, !!props.alpha));
}
// Ctrl+V прямо на кнопке-образце, не открывая окно
function onTriggerPaste(e: ClipboardEvent) { if (!open.value) { const c = parseAnyColor(e.clipboardData?.getData('text/plain') ?? ''); if (c) { e.preventDefault(); snapshot(); setRgba(props.alpha ? c : { ...c, a: 1 }); } } }
const note = ref('');
let noteTimer = 0;
function flash(t: string) { note.value = t; clearTimeout(noteTimer); noteTimer = window.setTimeout(() => (note.value = ''), 1800); }

// ---- перетаскивание по квадрату и полосам
function drag(e: PointerEvent, el: HTMLElement, fn: (x: number, y: number) => void) {
  snapshot();
  const move = (ev: PointerEvent) => {
    const r = el.getBoundingClientRect();
    fn(Math.min(1, Math.max(0, (ev.clientX - r.left) / r.width)), Math.min(1, Math.max(0, (ev.clientY - r.top) / r.height)));
    commit();
  };
  move(e);
  el.setPointerCapture(e.pointerId);
  const up = () => { el.removeEventListener('pointermove', move); el.removeEventListener('pointerup', up); el.removeEventListener('pointercancel', up); };
  el.addEventListener('pointermove', move); el.addEventListener('pointerup', up); el.addEventListener('pointercancel', up);
}
const svEl = ref<HTMLElement | null>(null), hueEl = ref<HTMLElement | null>(null), alphaEl = ref<HTMLElement | null>(null);
const onSv = (e: PointerEvent) => drag(e, svEl.value!, (x, y) => { s.value = x; v.value = 1 - y; });
const onHue = (e: PointerEvent) => drag(e, hueEl.value!, (x) => { h.value = Math.min(359.9, x * 360); });
const onAlpha = (e: PointerEvent) => drag(e, alphaEl.value!, (x) => { a.value = Math.round(x * 255) / 255; });
function svKey(e: KeyboardEvent) {
  const d = e.shiftKey ? 0.1 : 0.01;
  const m: Record<string, () => void> = {
    ArrowLeft: () => (s.value = Math.max(0, s.value - d)), ArrowRight: () => (s.value = Math.min(1, s.value + d)),
    ArrowUp: () => (v.value = Math.min(1, v.value + d)), ArrowDown: () => (v.value = Math.max(0, v.value - d)),
  };
  if (m[e.key]) { e.preventDefault(); snapshot(); m[e.key](); commit(); }
}
function sliderKey(e: KeyboardEvent, which: 'h' | 'a') {
  const k = e.key === 'ArrowLeft' || e.key === 'ArrowDown' ? -1 : e.key === 'ArrowRight' || e.key === 'ArrowUp' ? 1 : 0;
  if (!k) return;
  e.preventDefault(); snapshot();
  if (which === 'h') h.value = (h.value + k * (e.shiftKey ? 10 : 1) + 360) % 360;
  else a.value = Math.min(1, Math.max(0, a.value + (k * (e.shiftKey ? 16 : 1)) / 255));
  commit();
}

// ---- поля
type Mode = 'hex' | 'rgb' | 'hsl';
const mode = ref<Mode>((localStorage.getItem('displaylib.colorMode') as Mode) || 'hex');
watch(mode, (m) => { try { localStorage.setItem('displaylib.colorMode', m); } catch { /* */ } });
const hsl = computed(() => rgbToHsl(rgb.value));
function onHexInput(ev: Event) {
  const c = parseAnyColor((ev.target as HTMLInputElement).value);
  if (c) setRgba(props.alpha ? c : { ...c, a: 1 });
}
function setRgbPart(i: 0 | 1 | 2, ev: Event) {
  const n = Number((ev.target as HTMLInputElement).value); if (!Number.isFinite(n)) return;
  const c = { ...rgb.value }; (['r', 'g', 'b'] as const).forEach((k, j) => { if (j === i) c[k] = Math.max(0, Math.min(255, Math.round(n))); });
  setRgba({ ...c, a: a.value });
}
function setHslPart(i: 0 | 1 | 2, ev: Event) {
  const n = Number((ev.target as HTMLInputElement).value); if (!Number.isFinite(n)) return;
  const x = { ...hsl.value };
  if (i === 0) x.h = ((n % 360) + 360) % 360; else if (i === 1) x.s = Math.max(0, Math.min(100, n)) / 100; else x.l = Math.max(0, Math.min(100, n)) / 100;
  const c = hslToRgb(x);
  const hv = rgbToHsv(c);
  h.value = x.h; s.value = hv.s; v.value = hv.v; commit();
}
function setAlphaPct(ev: Event) {
  const n = Number((ev.target as HTMLInputElement).value); if (!Number.isFinite(n)) return;
  a.value = Math.round((Math.max(0, Math.min(100, n)) / 100) * 255) / 255; commit();
}

// ---- пипетка (Chrome/Edge), копирование, образцы
const hasEyeDropper = typeof window !== 'undefined' && 'EyeDropper' in window;
async function eyedrop() {
  try {
    const r = await new (window as any).EyeDropper().open();
    const c = parseAnyColor(r.sRGBHex); if (c) { snapshot(); setRgba({ ...c, a: a.value }); }
  } catch { /* отмена */ }
}
async function copy() { try { await navigator.clipboard.writeText(hex.value); flash('Скопировано ' + hex.value); } catch { flash(hex.value); } }
const MC = Object.entries(NAMED_COLORS);
const RECENT_KEY = 'displaylib.recentColors';
const recent = ref<string[]>(loadRecent());
function loadRecent(): string[] { try { return JSON.parse(localStorage.getItem(RECENT_KEY) || '[]').slice(0, 12); } catch { return []; } }
function remember(c: string) {
  recent.value = [c, ...recent.value.filter((x) => x !== c)].slice(0, 12);
  try { localStorage.setItem(RECENT_KEY, JSON.stringify(recent.value)); } catch { /* */ }
}
function pick(c: string) { const p = parseAnyColor(c); if (p) { snapshot(); setRgba(props.alpha ? p : { ...p, a: 1 }); } }

const hueCss = computed(() => `hsl(${h.value}, 100%, 50%)`);
const solidCss = computed(() => `rgb(${rgb.value.r}, ${rgb.value.g}, ${rgb.value.b})`);
const newCss = computed(() => `rgba(${rgb.value.r}, ${rgb.value.g}, ${rgb.value.b}, ${props.alpha ? a.value : 1})`);
const oldCss = computed(() => { const c = parseAnyColor(before.value); return c ? `rgba(${c.r},${c.g},${c.b},${props.alpha ? c.a : 1})` : 'transparent'; });
function revert() { pick(before.value); }
</script>

<template>
  <button ref="trigger" type="button" class="cp-trigger" :class="{ open }" :style="{ width: (size ?? 26) + 'px', height: (size ?? 26) + 'px' }"
    :title="(title ? title + '\n' : '') + 'Клик - выбрать цвет; Ctrl+V - вставить #hex, rgb(), hsl()…'" @click="toggle" @paste="onTriggerPaste">
    <span :style="{ background: triggerCss }"></span>
  </button>
  <Teleport to="body">
    <div v-if="open" ref="pop" class="cp-pop" tabindex="-1" :style="{ left: pos.left + 'px', top: pos.top + 'px' }" @paste="onPaste">
      <div ref="svEl" class="cp-sv" :style="{ background: hueCss }" tabindex="0" @pointerdown.prevent="onSv" @keydown="svKey" title="Насыщенность и яркость (стрелки, Shift - шаг больше)">
        <div class="cp-sv-white"></div><div class="cp-sv-black"></div>
        <span class="cp-knob" :style="{ left: s * 100 + '%', top: (1 - v) * 100 + '%', background: solidCss }"></span>
      </div>
      <div class="cp-row">
        <div class="cp-compare" title="Было / стало; клик по «было» - вернуть">
          <span class="checker"></span>
          <span class="old" :style="{ background: oldCss }" @click="revert"></span>
          <span class="new" :style="{ background: newCss }"></span>
        </div>
        <div class="cp-sliders">
          <div ref="hueEl" class="cp-hue" tabindex="0" @pointerdown.prevent="onHue" @keydown="sliderKey($event, 'h')" title="Оттенок">
            <span class="cp-thumb" :style="{ left: (h / 360) * 100 + '%', background: hueCss }"></span>
          </div>
          <div v-if="alpha" ref="alphaEl" class="cp-alpha checker" tabindex="0" @pointerdown.prevent="onAlpha" @keydown="sliderKey($event, 'a')" title="Непрозрачность (в игре ниже 26 из 255 не рисуется)">
            <span class="cp-alpha-fill" :style="{ background: `linear-gradient(90deg, rgba(${rgb.r},${rgb.g},${rgb.b},0), ${solidCss})` }"></span>
            <span class="cp-thumb" :style="{ left: a * 100 + '%', background: newCss }"></span>
          </div>
        </div>
        <button v-if="hasEyeDropper" class="btn sm icon" title="Пипетка: взять цвет с экрана" @click="eyedrop"><Icon name="eye-dropper" /></button>
      </div>

      <div class="cp-fields">
        <div class="seg cp-modes">
          <button v-for="m in (['hex', 'rgb', 'hsl'] as const)" :key="m" :class="{ on: mode === m }" @click="mode = m">{{ m.toUpperCase() }}</button>
        </div>
        <div v-if="mode === 'hex'" class="cp-inputs one">
          <input type="text" :value="hex" spellcheck="false" @focus="snapshot(); ($event.target as HTMLInputElement).select()" @change="onHexInput" @keydown.enter="onHexInput" title="Любая запись: #f80, #ff8800cc, rgb(255 136 0), hsl(32 100% 50%), 255, 136, 0, gold, §6" />
        </div>
        <div v-else-if="mode === 'rgb'" class="cp-inputs">
          <label v-for="(k, i) in (['r', 'g', 'b'] as const)" :key="k"><input type="number" min="0" max="255" :value="rgb[k]" @focus="snapshot()" @input="setRgbPart(i as 0 | 1 | 2, $event)" /><span>{{ k.toUpperCase() }}</span></label>
          <label v-if="alpha"><input type="number" min="0" max="100" :value="Math.round(a * 100)" @focus="snapshot()" @input="setAlphaPct" /><span>A%</span></label>
        </div>
        <div v-else class="cp-inputs">
          <label><input type="number" min="0" max="360" :value="Math.round(hsl.h)" @focus="snapshot()" @input="setHslPart(0, $event)" /><span>H°</span></label>
          <label><input type="number" min="0" max="100" :value="Math.round(hsl.s * 100)" @focus="snapshot()" @input="setHslPart(1, $event)" /><span>S%</span></label>
          <label><input type="number" min="0" max="100" :value="Math.round(hsl.l * 100)" @focus="snapshot()" @input="setHslPart(2, $event)" /><span>L%</span></label>
          <label v-if="alpha"><input type="number" min="0" max="100" :value="Math.round(a * 100)" @focus="snapshot()" @input="setAlphaPct" /><span>A%</span></label>
        </div>
        <button class="btn sm icon" title="Скопировать" @click="copy"><Icon name="copy" /></button>
      </div>

      <div class="cp-swatches" title="Цвета Minecraft (имена в файле)">
        <button v-for="[n, c] in MC" :key="n" class="cp-sw" :style="{ background: c }" :title="n + ' ' + c" @click="pick(c)"></button>
      </div>
      <div v-if="recent.length" class="cp-swatches recent" title="Недавние">
        <button v-for="c in recent" :key="c" class="cp-sw checker" :title="c" @click="pick(c)"><span :style="{ background: c }"></span></button>
      </div>
      <div class="cp-hint">{{ note || 'Ctrl+V - вставить любой формат: #hex, rgb(), hsl(), 255, 128, 0, §c, gold' }}</div>
    </div>
  </Teleport>
</template>

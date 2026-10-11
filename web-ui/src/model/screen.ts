// Модель экрана DisplayLib. Значения по умолчанию - те же, что в ScreenDefinition,
// WidgetDefinition и HoverAnimation плагина (см. claude/03-jmc-reference.md).

export type Vec3 = [number, number, number];
export type Vec2 = [number, number];
export type RGB = [number, number, number];

export type ScreenType = 'PRIVATE' | 'PUBLIC';
export type WidgetType = 'TEXT_BUTTON' | 'ITEM_BUTTON' | 'SPRITE_BUTTON';
export type Alignment = 'LEFT' | 'CENTERED' | 'RIGHT';
export type ActionType = 'NONE' | 'SWITCH_SCREEN' | 'CLOSE_SCREEN' | 'RUN_SCRIPT';

/**
 * Сегмент форматированного текста: { text, color }; color - имя ("green") или "#RRGGBB".
 * Или спрайт атласа (клиент 1.21.9+): { sprite: "item/iron_ingot", atlas?: "minecraft:items" } -
 * рисуется квадратом 8×8 текстовых пикселей.
 */
export interface Segment {
  text: string;
  color?: string;
  sprite?: string;
  atlas?: string;
}

/** Текст: строка или сегменты. */
export type RichText = string | Segment[];

export interface ClickAction {
  action: ActionType;
  /** SWITCH_SCREEN */
  target?: string;
  /** RUN_SCRIPT: функция скрипта name(widget, player) */
  function?: string;
}

export type AnimationType = 'PRESET' | 'SCALE' | 'TRANSLATE' | 'ROTATE' | 'TRANSFORM' | 'COMBINED';
export type Easing = 'LINEAR' | 'EASE_IN' | 'EASE_OUT' | 'EASE_IN_OUT' | 'BOUNCE' | 'ELASTIC';
export type Preset = 'BOUNCE' | 'SCALE' | 'PULSE' | 'GLOW' | 'WOBBLE' | 'LIFT';

export interface HoverAnimation {
  type: AnimationType;
  duration: number;
  easing: Easing;
  reverseOnExit: boolean;
  preset: Preset;
  intensity: number;
  scale: Vec3;
  offset: Vec3;
  rotation: Vec3;
  axis: Vec3;
  translation: Vec3;
  delay: number;
  loop: boolean;
  loopCount: number;
  effects: HoverAnimation[];
}

export interface SoundSpec { sound: string; volume: number; pitch: number }

export interface Widget {
  /** Внутренний ключ редактора (не попадает в файл). */
  uid: string;
  /** Флаги редактора (в файл не пишутся, хранятся в localStorage по id экрана и виджета):
   *  hidden - не рисуется и не участвует в привязках; locked - не выбирается и не двигается в сцене. */
  hidden?: boolean;
  locked?: boolean;
  id: string;
  type: WidgetType;
  position: Vec3;
  scale: Vec3;
  tolerance: Vec2;
  translation: Vec3;
  /** Поворот в плоскости экрана, градусы; + = против часовой стрелки, вокруг центра виджета. */
  rotation: number;
  /** Ширина переноса TextDisplay, px шрифта (клиент: 200). */
  lineWidth: number;
  tooltip: RichText | null;
  tooltipColor: RGB | null;
  tooltipDelay: number;
  /** Звук клика: null - дефолт плагина, 'none' - выключен. */
  clickSound: SoundSpec | 'none' | null;
  /** Звук при наведении: null - нет. */
  hoverSound: SoundSpec | 'none' | null;
  onClick: ClickAction | null;
  hoverAnimation: HoverAnimation | null;
  // TEXT_BUTTON
  text: RichText;
  hoveredText: RichText | null;
  /** Кадры текстовой анимации (пусто - нет); text = первый кадр */
  frames: RichText[];
  frameInterval: number;
  hoveredFrames: RichText[];
  /** -1 = как frameInterval */
  hoveredFrameInterval: number;
  alignment: Alignment;
  backgroundColor: RGB;
  backgroundAlpha: number;
  hoveredBackgroundColor: RGB;
  hoveredBackgroundAlpha: number;
  // ITEM_BUTTON
  material: string;
  glowOnHover: boolean;
  glowColor: RGB | null;
  // SPRITE_BUTTON (TextDisplay со спрайтом атласа; фон/hover - как у текста)
  sprite: string;
  /** null - по умолчанию клиента/плагина: items для item/..., иначе blocks */
  atlas: string | null;
  hoveredSprite: string | null;
  /** Тинт спрайта (имя цвета или #hex); null - без тинта (белый). */
  spriteColor: string | null;
  /** Тинт при наведении; null - как spriteColor. */
  hoveredSpriteColor: string | null;
}

export interface Background {
  color: RGB;
  alpha: number;
  scale: Vec3;
  position: Vec3;
  translation: Vec3;
  text: string;
  /** Точка фона в начале координат экрана: доли ширины/высоты, x слева направо, y снизу вверх. [0.5, 0] - низ по центру. */
  anchor: Vec2;
}

/** Пресеты якоря фона (как в плагине: ScreenDefinition.BackgroundDefinition.anchorPreset). */
export const ANCHOR_PRESETS: { name: string; value: Vec2; label: string }[] = [
  { name: 'TOP_LEFT', value: [0, 1], label: '↖' }, { name: 'TOP', value: [0.5, 1], label: '↑' }, { name: 'TOP_RIGHT', value: [1, 1], label: '↗' },
  { name: 'LEFT', value: [0, 0.5], label: '←' }, { name: 'CENTER', value: [0.5, 0.5], label: '•' }, { name: 'RIGHT', value: [1, 0.5], label: '→' },
  { name: 'BOTTOM_LEFT', value: [0, 0], label: '↙' }, { name: 'BOTTOM', value: [0.5, 0], label: '↓' }, { name: 'BOTTOM_RIGHT', value: [1, 0], label: '↘' },
];
export function anchorPresetName(a: Vec2): string | null {
  const p = ANCHOR_PRESETS.find((x) => Math.abs(x.value[0] - a[0]) < 1e-6 && Math.abs(x.value[1] - a[1]) < 1e-6);
  return p ? p.name : null;
}

/** Анимация смены/закрытия экрана: steps = -1 означает duration / 2 (как в плагине). */
export interface Morph { enabled: boolean; duration: number; steps: number }
export function defaultMorph(): Morph { return { enabled: false, duration: 10, steps: -1 }; }

export interface Screen {
  id: string;
  screenType: ScreenType;
  tickRate: number;
  interactionRadius: number;
  closeDistance: number;
  rangeCheckInterval: number;
  background: Background | null;
  morph: Morph;
  script: string | null;
  widgets: Widget[];
}

let uidCounter = 0;
export function newUid(): string {
  uidCounter += 1;
  return 'w' + Date.now().toString(36) + '_' + uidCounter;
}

export function defaultHoverAnimation(): HoverAnimation {
  return {
    type: 'PRESET',
    duration: 10,
    easing: 'EASE_OUT',
    reverseOnExit: true,
    preset: 'SCALE',
    intensity: 1.2,
    scale: [1.1, 1.1, 1.0],
    offset: [0, 0.02, 0],
    rotation: [0, 15, 0],
    axis: [0, 0, 1],
    translation: [0, 0, 0],
    delay: 0,
    loop: false,
    loopCount: -1,
    effects: [],
  };
}

export function defaultBackground(): Background {
  return {
    color: [0, 0, 0],
    alpha: 160,
    scale: [10, 4, 1],
    position: [0, 0, 0],
    translation: [0, 0, 0],
    text: ' ',
    anchor: [0.5, 0],
  };
}

export function defaultWidget(type: WidgetType = 'TEXT_BUTTON'): Widget {
  return {
    uid: newUid(),
    id: '',
    type,
    position: [0, 0, 0],
    scale: type === 'TEXT_BUTTON' ? [0.5, 0.5, 0.5] : type === 'SPRITE_BUTTON' ? [2, 2, 1] : [0.25, 0.25, 0.05],
    tolerance: type === 'TEXT_BUTTON' ? [0.1, 0.05] : type === 'SPRITE_BUTTON' ? [0.2, 0.2] : [0.12, 0.12],
    translation: [0, 0, 0],
    rotation: 0,
    lineWidth: 200,
    tooltip: null,
    tooltipColor: null,
    tooltipDelay: 0,
    clickSound: null,
    hoverSound: null,
    onClick: null,
    hoverAnimation: null,
    text: type === 'TEXT_BUTTON' ? 'Кнопка' : '',
    hoveredText: null,
    frames: [],
    frameInterval: 10,
    hoveredFrames: [],
    hoveredFrameInterval: -1,
    alignment: 'CENTERED',
    backgroundColor: [60, 60, 60],
    backgroundAlpha: type === 'TEXT_BUTTON' ? 180 : 0,
    hoveredBackgroundColor: [80, 80, 80],
    hoveredBackgroundAlpha: type === 'TEXT_BUTTON' ? 220 : 0,
    material: 'DIAMOND',
    glowOnHover: true,
    glowColor: null,
    sprite: 'item/diamond_sword',
    atlas: null,
    hoveredSprite: null,
    spriteColor: null,
    hoveredSpriteColor: null,
  };
}

/** Значения по умолчанию виджета в плагине (для сравнения при записи: что не менялось - не пишем). */
export const WIDGET_DEFAULTS = {
  position: [0, 0, 0] as Vec3,
  scale: [0.15, 0.15, 0.15] as Vec3,
  tolerance: [0.06, 0.06] as Vec2,
  translation: [0, 0, 0] as Vec3,
  rotation: 0,
  lineWidth: 200,
  tooltipColor: [134, 135, 136] as RGB,
  tooltipDelay: 0,
  alignment: 'CENTERED' as Alignment,
  backgroundColor: [0, 0, 0] as RGB,
  backgroundAlpha: 0,
  hoveredBackgroundColor: [60, 60, 60] as RGB,
  hoveredBackgroundAlpha: 0,
  glowOnHover: true,
};

export function defaultScreen(id = 'new_screen'): Screen {
  return {
    id,
    screenType: 'PRIVATE',
    tickRate: 4,
    interactionRadius: -1,
    closeDistance: -1,
    rangeCheckInterval: 10,
    background: defaultBackground(),
    morph: defaultMorph(),
    script: null,
    widgets: [],
  };
}

export function cloneDeep<T>(v: T): T {
  return JSON.parse(JSON.stringify(v));
}

export function richToPlain(t: RichText | null | undefined): string {
  if (t == null) return '';
  if (typeof t === 'string') return t;
  return t.map((s) => (s.sprite !== undefined ? '[' + s.sprite + ']' : s.text)).join('');
}

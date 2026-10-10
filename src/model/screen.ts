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
  /** глиф: прямоугольная картинка из ресурспака (шрифт displaylib:icons), имя = путь в textures/ без .png */
  glyph?: string;
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

export interface Widget {
  /** Внутренний ключ редактора (не попадает в файл). */
  uid: string;
  id: string;
  type: WidgetType;
  position: Vec3;
  scale: Vec3;
  tolerance: Vec2;
  translation: Vec3;
  tooltip: RichText | null;
  tooltipColor: RGB | null;
  tooltipDelay: number;
  onClick: ClickAction | null;
  hoverAnimation: HoverAnimation | null;
  // TEXT_BUTTON
  text: RichText;
  hoveredText: RichText | null;
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
}

export interface Background {
  color: RGB;
  alpha: number;
  scale: Vec3;
  position: Vec3;
  translation: Vec3;
  text: string;
}

export interface Screen {
  id: string;
  screenType: ScreenType;
  tickRate: number;
  interactionRadius: number;
  closeDistance: number;
  rangeCheckInterval: number;
  background: Background | null;
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
    axis: [0, 1, 0],
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
    tooltip: null,
    tooltipColor: null,
    tooltipDelay: 0,
    onClick: null,
    hoverAnimation: null,
    text: type === 'TEXT_BUTTON' ? 'Кнопка' : '',
    hoveredText: null,
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
  };
}

/** Значения по умолчанию виджета в плагине (для сравнения при записи: что не менялось - не пишем). */
export const WIDGET_DEFAULTS = {
  position: [0, 0, 0] as Vec3,
  scale: [0.15, 0.15, 0.15] as Vec3,
  tolerance: [0.06, 0.06] as Vec2,
  translation: [0, 0, 0] as Vec3,
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
  return t.map((s) => (s.sprite !== undefined ? '[' + s.sprite + ']' : s.glyph !== undefined ? '[' + s.glyph + ']' : s.text)).join('');
}

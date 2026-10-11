// Геометрия Display-сущностей, как её рисует клиент Minecraft (DisplayRenderer).
//
// TextDisplay (TextDisplayRenderer.renderInner):
//   matrix.rotate(PI, Y); matrix.scale(-0.025, -0.025, -0.025)   -> 1 текстовый пиксель = 1/40 блока
//   lineHeight = font.lineHeight (9) + 1 = 10
//   k = ширина самой длинной строки в пикселях, l = lines * 10
//   matrix.translate(1 - k/2, -l, 0)
//   фон: прямоугольник от (-1, -1) до (k, l) в координатах шрифта
//   строки: x = 0 | (k - w)/2 | k - w по выравниванию, y = i * 10
// В локальных осях сущности (x вправо для зрителя, y вверх, якорь - низ по центру):
//   фон занимает x ∈ [-k/2, k/2 + 1] px, y ∈ [0, l + 1] px: 1 px слева и сверху, центр фона
//   на +0.5 px (поэтому плагин сдвигает фон на -scaleX/80 = -0.5 px * scaleX).
// Затем применяется transformation: translation + scale относительно якоря.
//
// ItemDisplay с ItemDisplayTransform.GUI: предмет-спрайт занимает ~1 блок при scale 1, блоки
// рисуются в изометрии в масштабе 0.625 (как в инвентаре). Якорь - центр предмета.
//
// Чего клиент под рукой нет, то вынесено в калибровку: знак оси X для position и translation
// (в документации плагина оба "вправо"), размер предмета, пиксель, высота строки.

import { reactive, watch } from 'vue';

export interface Calibration {
  /** Размер текстового пикселя в блоках. */
  px: number;
  /** Высота строки в пикселях. */
  lineHeight: number;
  /** Отступ фона вокруг текста, px. Клиент: прямоугольник от (-1, -1) до (k, l) в координатах
   *  шрифта (y вниз), то есть 1 px слева и 1 px сверху, 0 справа и снизу; плюс сдвиг на +1 px
   *  вправо через translate(1 - k/2). */
  bgLeft: number;
  bgRight: number;
  bgTop: number;
  bgBottom: number;
  /** Знак оси X для position виджета (+1: +x = вправо для игрока). */
  positionXSign: 1 | -1;
  /** Знак оси X для translation сущности. */
  translationXSign: 1 | -1;
  /** Размер предмета-спрайта в блоках при scale 1. */
  itemSize: number;
  /** Смещение виджетов к игроку по глубине (ScreenSupport.WIDGET_DEPTH_OFFSET / ITEM_WIDGET_DEPTH_OFFSET). */
  textDepthOffset: number;
  itemDepthOffset: number;
  /** Тень текста (TextDisplay.shadow; плагин не включает). */
  shadow: boolean;
  /** Камера: глаза игрока над центром экрана и расстояние до экрана. */
  eyeAboveCenter: number;
  /** advance спрайта-объекта в тексте, px (квадрат 8×8 + промежуток) */
  spriteAdvance: number;
  eyeDistance: number;
  fov: number;
  /** версия набора констант: при повышении устаревшие значения из localStorage перезаписываются */
  version: number;
}

export const DEFAULT_CALIBRATION: Calibration = {
  px: 0.025,
  lineHeight: 10,
  bgLeft: 1,
  bgRight: 0,
  bgTop: 1,
  // клиент 1.21.11/26.1 (DisplayRenderer.TextDisplayRenderer.submitInner): l = lines*10 - 1, фон от (-1,-1)
  // до (k, l) - т.е. высота фона ровно lines*10, а не lines*10+1 как в 1.20; проверено по байткоду
  bgBottom: -1,
  positionXSign: 1,
  translationXSign: 1,
  itemSize: 1,
  textDepthOffset: 0.001,
  itemDepthOffset: 0.01,
  shadow: false,
  // defaultLocationFor: экран в 2 блоках перед игроком на высоте height/2 (0.9); глаза на 1.62
  eyeAboveCenter: 0.72, // 1.62 - 0.9
  spriteAdvance: 8, // AtlasGlyphProvider: GlyphInfo.simple(8.0f) - без промежутка
  eyeDistance: 2,
  fov: 70,
  version: 3,
};

const KEY = 'displaylib.calibration';

function load(): Calibration {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const saved = JSON.parse(raw) as Partial<Calibration>;
      const c = { ...DEFAULT_CALIBRATION, ...saved };
      // v2: высота фона lines*10 (bgBottom -1) по байткоду клиента 1.21.11/26.1
      if ((saved.version ?? 1) < 2) { c.bgBottom = DEFAULT_CALIBRATION.bgBottom; }
      if ((saved.version ?? 1) < 3) { c.spriteAdvance = DEFAULT_CALIBRATION.spriteAdvance; }
      c.version = DEFAULT_CALIBRATION.version;
      return c;
    }
  } catch { /* приватный режим и т.п. */ }
  return { ...DEFAULT_CALIBRATION };
}

export const calibration = reactive<Calibration>(load());

watch(calibration, (c) => { try { localStorage.setItem(KEY, JSON.stringify(c)); } catch { /* ignore */ } }, { deep: true });

export function resetCalibration(): void {
  Object.assign(calibration, DEFAULT_CALIBRATION);
}

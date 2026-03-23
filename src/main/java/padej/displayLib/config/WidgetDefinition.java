package padej.displayLib.config;

import java.util.Map;

/**
 * Определение виджета из YAML конфигурации.
 * 
 * <p>Виджет - это интерактивный элемент экрана, который может отображать текст,
 * предметы и реагировать на клики игрока.</p>
 * 
 * <h2>Типы виджетов:</h2>
 * <ul>
 * <li><b>TEXT_BUTTON</b> - Текстовая кнопка с поддержкой форматирования</li>
 * <li><b>ITEM_BUTTON</b> - Кнопка с отображением предмета Minecraft</li>
 * </ul>
 * 
 * <h2>Пример YAML конфигурации TEXT_BUTTON:</h2>
 * <pre>{@code
 * - id: "my_button"                      # Уникальный ID виджета
 *   type: TEXT_BUTTON                    # Тип виджета
 *   text: "Нажми меня"                   # Обычный текст
 *   hoveredText: "Кликни!"               # Текст при наведении
 *   position: [0.0, 0.5, 0.0]           # Позиция [x, y, z]
 *   scale: [0.2, 0.2, 0.2]              # Размер [x, y, z]
 *   tolerance: [0.08, 0.08]             # Область клика [гор., верт.]
 *   backgroundColor: [50, 50, 50]        # RGB цвет фона
 *   backgroundAlpha: 180                 # Прозрачность фона
 *   hoveredBackgroundColor: [80, 80, 80] # RGB цвет при наведении
 *   hoveredBackgroundAlpha: 200          # Прозрачность при наведении
 *   alignment: CENTERED                  # Выравнивание: LEFT, CENTERED, RIGHT
 *   tooltip: "Подсказка"                 # Текст подсказки
 *   tooltipColor: [255, 255, 0]          # RGB цвет подсказки
 *   tooltipDelay: 10                     # Задержка показа в тиках
 *   onClick:                             # Действие при клике
 *     action: RUN_SCRIPT                 # Тип действия
 *     function: "onButtonClick"          # Lua функция
 * }</pre>
 * 
 * <h2>Пример YAML конфигурации ITEM_BUTTON:</h2>
 * <pre>{@code
 * - id: "sword_button"
 *   type: ITEM_BUTTON
 *   material: DIAMOND_SWORD              # Материал предмета
 *   position: [1.0, 0.0, 0.0]
 *   scale: [0.3, 0.3, 0.3]
 *   glowOnHover: true                    # Свечение при наведении
 *   glowColor: [0, 255, 255]             # RGB цвет свечения
 *   tooltip: "Алмазный меч"
 *   onClick:
 *     action: SWITCH_SCREEN
 *     target: "weapon_menu"              # ID целевого экрана
 * }</pre>
 * 
 * <h2>Форматированный текст:</h2>
 * <p>Для TEXT_BUTTON можно использовать форматированный текст с цветами и стилями:</p>
 * <pre>{@code
 * formattedText:
 *   - text: "Красный "
 *     color: "#FF0000"
 *     bold: true
 *   - text: "и синий"
 *     color: "blue"
 *     italic: true
 * }</pre>
 * 
 * @author DisplayLib
 * @version 1.0
 * @see ScreenDefinition
 */
public class WidgetDefinition {
    /** Уникальный идентификатор виджета для доступа из Lua API */
    private String id;
    
    /** Тип виджета (TEXT_BUTTON или ITEM_BUTTON) */
    private WidgetType type;
    
    /** Позиция виджета [x, y, z] относительно центра экрана */
    private float[] position = {0.0f, 0.0f, 0.0f};
    
    /** Размер виджета [scaleX, scaleY, scaleZ] */
    private float[] scale = {0.15f, 0.15f, 0.15f};
    
    /** Область клика [горизонтальная, вертикальная] в блоках */
    private float[] tolerance = {0.06f, 0.06f};
    
    /** Точная настройка позиции [x, y, z] для устранения артефактов */
    private float[] translation = {0.0f, 0.0f, 0.0f};
    
    // ===== Поля для TEXT_BUTTON =====
    
    /** Основной текст виджета (для TEXT_BUTTON) */
    private String text;
    
    /** Текст при наведении курсора (для TEXT_BUTTON) */
    private String hoveredText;
    
    /** 
     * Форматированный текст с поддержкой цветов и стилей (для TEXT_BUTTON).
     * Может быть строкой или массивом объектов с полями text, color, bold, italic и т.д.
     */
    private Object formattedText;
    
    /** 
     * Форматированный текст при наведении (для TEXT_BUTTON).
     * Аналогично formattedText, но отображается при hover.
     */
    private Object formattedHoveredText;
    
    /** Выравнивание текста (LEFT, CENTERED, RIGHT) */
    private TextAlignment alignment = TextAlignment.CENTERED;
    
    /** RGB цвет фона [красный, зеленый, синий] (0-255) */
    private int[] backgroundColor = {0, 0, 0};
    
    /** Прозрачность фона (0-255, где 0 = прозрачный, 255 = непрозрачный) */
    private int backgroundAlpha = 0;
    
    /** RGB цвет фона при наведении [красный, зеленый, синий] (0-255) */
    private int[] hoveredBackgroundColor = {60, 60, 60};
    
    /** Прозрачность фона при наведении (0-255) */
    private int hoveredBackgroundAlpha = 0;
    
    // ===== Поля для ITEM_BUTTON =====
    
    /** Материал предмета для отображения (для ITEM_BUTTON) */
    private String material;
    
    /** Включить свечение при наведении (для ITEM_BUTTON) */
    private boolean glowOnHover = true;
    
    /** RGB цвет свечения [красный, зеленый, синий] (0-255) */
    private int[] glowColor;
    
    // ===== Общие свойства =====
    
    /** 
     * Текст подсказки, отображаемой при наведении.
     * Может быть строкой или массивом объектов с полями text, color, bold, italic и т.д.
     */
    private Object tooltip;
    
    /** RGB цвет текста подсказки [красный, зеленый, синий] (0-255) */
    private int[] tooltipColor = {134, 135, 136};
    
    /** Задержка показа подсказки в тиках (20 тиков = 1 секунда) */
    private int tooltipDelay = 0;
    
    /** Действие при клике на виджет */
    private ClickAction onClick;
    
    public WidgetDefinition() {}
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public WidgetType getType() { return type; }
    public void setType(WidgetType type) { this.type = type; }
    
    public float[] getPosition() { return position; }
    public void setPosition(float[] position) { this.position = position; }
    
    public float[] getScale() { return scale; }
    public void setScale(float[] scale) { this.scale = scale; }
    
    public float[] getTolerance() { return tolerance; }
    public void setTolerance(float[] tolerance) { this.tolerance = tolerance; }
    
    public float[] getTranslation() { return translation; }
    public void setTranslation(float[] translation) { this.translation = translation; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public String getHoveredText() { return hoveredText; }
    public void setHoveredText(String hoveredText) { this.hoveredText = hoveredText; }
    
    public Object getFormattedText() { return formattedText; }
    public void setFormattedText(Object formattedText) { this.formattedText = formattedText; }
    
    public Object getFormattedHoveredText() { return formattedHoveredText; }
    public void setFormattedHoveredText(Object formattedHoveredText) { this.formattedHoveredText = formattedHoveredText; }
    
    public TextAlignment getAlignment() { return alignment; }
    public void setAlignment(TextAlignment alignment) { this.alignment = alignment; }
    
    public int[] getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int[] backgroundColor) { this.backgroundColor = backgroundColor; }
    
    public int getBackgroundAlpha() { return backgroundAlpha; }
    public void setBackgroundAlpha(int backgroundAlpha) { this.backgroundAlpha = backgroundAlpha; }
    
    public int[] getHoveredBackgroundColor() { return hoveredBackgroundColor; }
    public void setHoveredBackgroundColor(int[] hoveredBackgroundColor) { this.hoveredBackgroundColor = hoveredBackgroundColor; }
    
    public int getHoveredBackgroundAlpha() { return hoveredBackgroundAlpha; }
    public void setHoveredBackgroundAlpha(int hoveredBackgroundAlpha) { this.hoveredBackgroundAlpha = hoveredBackgroundAlpha; }
    
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    
    public boolean isGlowOnHover() { return glowOnHover; }
    public void setGlowOnHover(boolean glowOnHover) { this.glowOnHover = glowOnHover; }
    
    public int[] getGlowColor() { return glowColor; }
    public void setGlowColor(int[] glowColor) { this.glowColor = glowColor; }
    
    public Object getTooltip() { return tooltip; }
    public void setTooltip(Object tooltip) { this.tooltip = tooltip; }
    
    public int[] getTooltipColor() { return tooltipColor; }
    public void setTooltipColor(int[] tooltipColor) { this.tooltipColor = tooltipColor; }
    
    public int getTooltipDelay() { return tooltipDelay; }
    public void setTooltipDelay(int tooltipDelay) { this.tooltipDelay = tooltipDelay; }
    
    public ClickAction getOnClick() { return onClick; }
    public void setOnClick(ClickAction onClick) { this.onClick = onClick; }
    
    /**
     * Типы виджетов определяют способ отображения и взаимодействия.
     * 
     * <ul>
     * <li><b>TEXT_BUTTON</b> - Текстовая кнопка с поддержкой форматирования,
     *     цветов, стилей и настраиваемого фона.</li>
     * <li><b>ITEM_BUTTON</b> - Кнопка с отображением предмета Minecraft,
     *     поддерживает свечение и различные материалы.</li>
     * </ul>
     */
    public enum WidgetType {
        /** Текстовая кнопка с форматированием */
        TEXT_BUTTON,
        /** Кнопка с предметом */
        ITEM_BUTTON
    }
    
    /**
     * Выравнивание текста в TEXT_BUTTON виджетах.
     * 
     * <ul>
     * <li><b>LEFT</b> - Выравнивание по левому краю</li>
     * <li><b>CENTERED</b> - Выравнивание по центру (по умолчанию)</li>
     * <li><b>RIGHT</b> - Выравнивание по правому краю</li>
     * </ul>
     */
    public enum TextAlignment {
        /** Выравнивание по левому краю */
        LEFT,
        /** Выравнивание по центру */
        CENTERED, 
        /** Выравнивание по правому краю */
        RIGHT
    }
    
    /**
     * Действие при клике на виджет.
     * 
     * <p>Определяет что происходит когда игрок кликает на виджет.</p>
     * 
     * <p><b>Типы действий:</b></p>
     * <ul>
     * <li><b>NONE</b> - Никакого действия</li>
     * <li><b>SWITCH_SCREEN</b> - Переключение на другой экран</li>
     * <li><b>RUN_SCRIPT</b> - Выполнение Lua функции</li>
     * <li><b>CLOSE_SCREEN</b> - Закрытие текущего экрана</li>
     * </ul>
     * 
     * <p><b>Примеры YAML:</b></p>
     * <pre>{@code
     * # Переключение экрана
     * onClick:
     *   action: SWITCH_SCREEN
     *   target: "main_menu"
     * 
     * # Выполнение Lua функции
     * onClick:
     *   action: RUN_SCRIPT
     *   function: "handleButtonClick"
     * 
     * # Закрытие экрана
     * onClick:
     *   action: CLOSE_SCREEN
     * }</pre>
     */
    public static class ClickAction {
        /** Тип действия при клике */
        private ActionType action;
        
        /** Целевой экран для SWITCH_SCREEN */
        private String target;
        
        /** Путь к скрипту для RUN_SCRIPT (устаревшее, используйте function) */
        private String script;
        
        /** Имя Lua функции для RUN_SCRIPT */
        private String function;
        
        /** Дополнительные параметры (зарезервировано для будущего использования) */
        private Map<String, Object> parameters;
        
        public ClickAction() {}
        
        // Getters and setters
        public ActionType getAction() { return action; }
        public void setAction(ActionType action) { this.action = action; }
        
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        
        public String getScript() { return script; }
        public void setScript(String script) { this.script = script; }
        
        public String getFunction() { return function; }
        public void setFunction(String function) { this.function = function; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        /**
         * Типы действий при клике на виджет.
         */
        public enum ActionType {
            /** Никакого действия */
            NONE,
            /** Переключиться на другой экран */
            SWITCH_SCREEN,
            /** Выполнить Lua скрипт */
            RUN_SCRIPT,
            /** Закрыть текущий экран */
            CLOSE_SCREEN
        }
    }
}
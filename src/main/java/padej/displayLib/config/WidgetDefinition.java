package padej.displayLib.config;

import java.util.Map;

/**
 * Data class для описания виджета из YAML
 */
public class WidgetDefinition {
    private String id;
    private WidgetType type;
    private float[] position = {0.0f, 0.0f, 0.0f}; // x, y, z offset
    private float[] scale = {0.15f, 0.15f, 0.15f}; // scaleX, scaleY, scaleZ
    private float[] tolerance = {0.06f, 0.06f}; // horizontal, vertical
    private float[] translation = {0.0f, 0.0f, 0.0f}; // translation offset for fine-tuning
    
    // Для text_button
    private String text;
    private String hoveredText;
    private TextAlignment alignment = TextAlignment.CENTERED; // По умолчанию центрированный
    private int[] backgroundColor = {0, 0, 0};
    private int backgroundAlpha = 0;
    private int[] hoveredBackgroundColor = {60, 60, 60};
    private int hoveredBackgroundAlpha = 0;
    
    // Для item_button
    private String material;
    private boolean glowOnHover = true;
    private int[] glowColor;
    
    // Общие свойства
    private String tooltip;
    private int[] tooltipColor = {134, 135, 136}; // RGB
    private int tooltipDelay = 0;
    
    // Обработчики событий
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
    
    public String getTooltip() { return tooltip; }
    public void setTooltip(String tooltip) { this.tooltip = tooltip; }
    
    public int[] getTooltipColor() { return tooltipColor; }
    public void setTooltipColor(int[] tooltipColor) { this.tooltipColor = tooltipColor; }
    
    public int getTooltipDelay() { return tooltipDelay; }
    public void setTooltipDelay(int tooltipDelay) { this.tooltipDelay = tooltipDelay; }
    
    public ClickAction getOnClick() { return onClick; }
    public void setOnClick(ClickAction onClick) { this.onClick = onClick; }
    
    /**
     * Типы виджетов
     */
    public enum WidgetType {
        TEXT_BUTTON,
        ITEM_BUTTON
    }
    
    /**
     * Выравнивание текста в TEXT_BUTTON
     */
    public enum TextAlignment {
        LEFT,
        CENTERED, 
        RIGHT
    }
    
    /**
     * Действие при клике
     */
    public static class ClickAction {
        private ActionType action;
        private String target; // для switch_screen
        private String script; // для run_script
        private String function; // функция в скрипте
        private Map<String, Object> parameters; // дополнительные параметры
        
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
        
        public enum ActionType {
            NONE,
            SWITCH_SCREEN,
            RUN_SCRIPT,
            CLOSE_SCREEN
        }
    }
}
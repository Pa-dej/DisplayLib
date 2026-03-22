package padej.displayLib.config;

import java.util.List;
import java.util.Map;

/**
 * Data class для описания экрана из YAML
 */
public class ScreenDefinition {
    private String id;
    private int tickRate = 4; // По умолчанию обновление каждые 4 тика (5 раз в секунду)
    private ScreenType screenType = ScreenType.PERSONAL; // По умолчанию PERSONAL
    private double interactionRadius = 8.0; // Радиус взаимодействия для GLOBAL экранов
    private int rangeCheckInterval = 10; // Интервал проверки расстояния в тиках
    private BackgroundDefinition background;
    private Map<String, String> scripts; // on_open, on_close -> путь к скрипту
    private List<WidgetDefinition> widgets;
    
    public ScreenDefinition() {}
    
    public ScreenDefinition(String id, int tickRate, ScreenType screenType, double interactionRadius, 
                           int rangeCheckInterval, BackgroundDefinition background, 
                           Map<String, String> scripts, List<WidgetDefinition> widgets) {
        this.id = id;
        this.tickRate = tickRate;
        this.screenType = screenType;
        this.interactionRadius = interactionRadius;
        this.rangeCheckInterval = rangeCheckInterval;
        this.background = background;
        this.scripts = scripts;
        this.widgets = widgets;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public int getTickRate() { return tickRate; }
    public void setTickRate(int tickRate) { 
        // Ограничиваем значение от 1 до 20
        this.tickRate = Math.max(1, Math.min(20, tickRate)); 
    }
    
    public BackgroundDefinition getBackground() { return background; }
    public void setBackground(BackgroundDefinition background) { this.background = background; }
    
    public Map<String, String> getScripts() { return scripts; }
    public void setScripts(Map<String, String> scripts) { this.scripts = scripts; }
    
    public List<WidgetDefinition> getWidgets() { return widgets; }
    public void setWidgets(List<WidgetDefinition> widgets) { this.widgets = widgets; }
    
    public ScreenType getScreenType() { return screenType; }
    public void setScreenType(ScreenType screenType) { this.screenType = screenType; }
    
    public double getInteractionRadius() { return interactionRadius; }
    public void setInteractionRadius(double interactionRadius) { this.interactionRadius = interactionRadius; }
    
    public int getRangeCheckInterval() { return rangeCheckInterval; }
    public void setRangeCheckInterval(int rangeCheckInterval) { this.rangeCheckInterval = rangeCheckInterval; }
    
    /**
     * Типы экранов
     */
    public enum ScreenType {
        PERSONAL,
        GLOBAL
    }
    
    /**
     * Определение фона экрана
     */
    public static class BackgroundDefinition {
        private int[] color = {0, 0, 0}; // RGB
        private int alpha = 160;
        private float[] scale = {10.0f, 4.0f, 1.0f}; // width, height, depth
        private float[] position = {0.0f, 0.0f, 0.0f}; // x, y, z offset from screen location
        private String text = " "; // текст фона
        private float[] translation = {0.0f, 0.0f, 0.0f}; // translation offset for fine-tuning
        
        public BackgroundDefinition() {}
        
        // Getters and setters
        public int[] getColor() { return color; }
        public void setColor(int[] color) { this.color = color; }
        
        public int getAlpha() { return alpha; }
        public void setAlpha(int alpha) { this.alpha = alpha; }
        
        public float[] getScale() { return scale; }
        public void setScale(float[] scale) { this.scale = scale; }
        
        public float[] getPosition() { return position; }
        public void setPosition(float[] position) { this.position = position; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public float[] getTranslation() { return translation; }
        public void setTranslation(float[] translation) { this.translation = translation; }
    }
}
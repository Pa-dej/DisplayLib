package padej.displayLib.config;

import java.util.List;
import java.util.Map;

/**
 * Data class для описания экрана из YAML
 */
public class ScreenDefinition {
    private String id;
    private BackgroundDefinition background;
    private Map<String, String> scripts; // on_open, on_close -> путь к скрипту
    private List<WidgetDefinition> widgets;
    
    public ScreenDefinition() {}
    
    public ScreenDefinition(String id, BackgroundDefinition background, 
                           Map<String, String> scripts, List<WidgetDefinition> widgets) {
        this.id = id;
        this.background = background;
        this.scripts = scripts;
        this.widgets = widgets;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public BackgroundDefinition getBackground() { return background; }
    public void setBackground(BackgroundDefinition background) { this.background = background; }
    
    public Map<String, String> getScripts() { return scripts; }
    public void setScripts(Map<String, String> scripts) { this.scripts = scripts; }
    
    public List<WidgetDefinition> getWidgets() { return widgets; }
    public void setWidgets(List<WidgetDefinition> widgets) { this.widgets = widgets; }
    
    /**
     * Определение фона экрана
     */
    public static class BackgroundDefinition {
        private int[] color = {0, 0, 0}; // RGB
        private int alpha = 160;
        private float[] scale = {10.0f, 4.0f, 1.0f}; // width, height, depth
        private String text = " "; // текст фона
        
        public BackgroundDefinition() {}
        
        // Getters and setters
        public int[] getColor() { return color; }
        public void setColor(int[] color) { this.color = color; }
        
        public int getAlpha() { return alpha; }
        public void setAlpha(int alpha) { this.alpha = alpha; }
        
        public float[] getScale() { return scale; }
        public void setScale(float[] scale) { this.scale = scale; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}
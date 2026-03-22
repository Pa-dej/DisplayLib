package padej.displayLib.config;

import padej.displayLib.DisplayLib;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Загрузчик экранов из YAML файлов
 */
public class ScreenLoader {
    private final DisplayLib plugin;
    private final Path screensDirectory;
    private final Yaml yaml;

    public ScreenLoader(DisplayLib plugin) {
        this.plugin = plugin;
        this.screensDirectory = plugin.getDataFolder().toPath().resolve("screens");
        this.yaml = new Yaml();
        
        try {
            Files.createDirectories(screensDirectory);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create screens directory", e);
        }
    }

    /**
     * Загрузить все экраны из папки screens
     */
    public Map<String, ScreenDefinition> loadAllScreens() {
        Map<String, ScreenDefinition> screens = new HashMap<>();
        
        try {
            if (!Files.exists(screensDirectory)) {
                return screens;
            }
            
            Files.walk(screensDirectory)
                    .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .forEach(path -> {
                        try {
                            ScreenDefinition screen = loadScreenFromFile(path);
                            if (screen != null) {
                                screens.put(screen.getId(), screen);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to load screen: " + path, e);
                        }
                    });
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load screens", e);
        }
        
        return screens;
    }

    /**
     * Загрузить конкретный экран по ID
     */
    public ScreenDefinition loadScreen(String screenId) {
        try {
            Path screenFile = screensDirectory.resolve(screenId + ".yml");
            if (!Files.exists(screenFile)) {
                screenFile = screensDirectory.resolve(screenId + ".yaml");
            }
            
            if (Files.exists(screenFile)) {
                return loadScreenFromFile(screenFile);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load screen: " + screenId, e);
        }
        
        return null;
    }

    /**
     * Загрузить экран из файла
     */
    private ScreenDefinition loadScreenFromFile(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            // Загружаем как Map сначала для обработки
            Map<String, Object> data = yaml.load(input);
            
            if (data == null) {
                return null;
            }
            
            // Создаем ScreenDefinition вручную из Map
            ScreenDefinition screen = new ScreenDefinition();
            
            // Основные поля
            if (data.containsKey("id")) {
                screen.setId((String) data.get("id"));
            } else {
                // Если ID не указан в файле, используем имя файла
                String fileName = file.getFileName().toString();
                String id = fileName.substring(0, fileName.lastIndexOf('.'));
                screen.setId(id);
            }
            
            if (data.containsKey("tick_rate")) {
                screen.setTickRate(((Number) data.get("tick_rate")).intValue());
            }
            
            if (data.containsKey("screen_type")) {
                String typeStr = (String) data.get("screen_type");
                try {
                    ScreenDefinition.ScreenType type = ScreenDefinition.ScreenType.valueOf(typeStr);
                    screen.setScreenType(type);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid screen_type '" + typeStr + "' in " + file + ", using PRIVATE");
                    screen.setScreenType(ScreenDefinition.ScreenType.PRIVATE);
                }
            }
            
            if (data.containsKey("interaction_radius")) {
                screen.setInteractionRadius(((Number) data.get("interaction_radius")).doubleValue());
            }
            
            if (data.containsKey("range_check_interval")) {
                screen.setRangeCheckInterval(((Number) data.get("range_check_interval")).intValue());
            }
            
            // Background
            if (data.containsKey("background")) {
                Map<String, Object> bgData = (Map<String, Object>) data.get("background");
                ScreenDefinition.BackgroundDefinition bg = new ScreenDefinition.BackgroundDefinition();
                
                if (bgData.containsKey("color")) {
                    Object colorObj = bgData.get("color");
                    if (colorObj instanceof java.util.List) {
                        java.util.List<Number> colorList = (java.util.List<Number>) colorObj;
                        int[] color = new int[3];
                        for (int i = 0; i < Math.min(3, colorList.size()); i++) {
                            color[i] = colorList.get(i).intValue();
                        }
                        bg.setColor(color);
                    }
                }
                
                if (bgData.containsKey("alpha")) {
                    bg.setAlpha(((Number) bgData.get("alpha")).intValue());
                }
                
                if (bgData.containsKey("scale")) {
                    Object scaleObj = bgData.get("scale");
                    if (scaleObj instanceof java.util.List) {
                        java.util.List<Number> scaleList = (java.util.List<Number>) scaleObj;
                        float[] scale = new float[3];
                        for (int i = 0; i < Math.min(3, scaleList.size()); i++) {
                            scale[i] = scaleList.get(i).floatValue();
                        }
                        bg.setScale(scale);
                    }
                }
                
                if (bgData.containsKey("position")) {
                    Object posObj = bgData.get("position");
                    if (posObj instanceof java.util.List) {
                        java.util.List<Number> posList = (java.util.List<Number>) posObj;
                        float[] position = new float[3];
                        for (int i = 0; i < Math.min(3, posList.size()); i++) {
                            position[i] = posList.get(i).floatValue();
                        }
                        bg.setPosition(position);
                    }
                }
                
                if (bgData.containsKey("text")) {
                    bg.setText((String) bgData.get("text"));
                }
                
                if (bgData.containsKey("translation")) {
                    Object transObj = bgData.get("translation");
                    if (transObj instanceof java.util.List) {
                        java.util.List<Number> transList = (java.util.List<Number>) transObj;
                        float[] translation = new float[3];
                        for (int i = 0; i < Math.min(3, transList.size()); i++) {
                            translation[i] = transList.get(i).floatValue();
                        }
                        bg.setTranslation(translation);
                    }
                }
                
                screen.setBackground(bg);
            }
            
            // Scripts
            if (data.containsKey("scripts")) {
                Map<String, String> scripts = (Map<String, String>) data.get("scripts");
                screen.setScripts(scripts);
            }
            
            // Widgets
            if (data.containsKey("widgets")) {
                java.util.List<Map<String, Object>> widgetsData = (java.util.List<Map<String, Object>>) data.get("widgets");
                java.util.List<WidgetDefinition> widgets = new java.util.ArrayList<>();
                
                for (Map<String, Object> widgetData : widgetsData) {
                    WidgetDefinition widget = parseWidget(widgetData);
                    if (widget != null) {
                        widgets.add(widget);
                    }
                }
                
                screen.setWidgets(widgets);
            }
            
            return screen;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error parsing YAML file: " + file, e);
            return null;
        }
    }

    /**
     * Получить путь к папке экранов
     */
    public Path getScreensDirectory() {
        return screensDirectory;
    }

    /**
     * Создать примеры экранов вручную (для команды examples)
     */
    public void createExampleScreensManually() {
        // Базовая реализация - можно расширить позже
        plugin.getLogger().info("Creating example screens...");
    }

    /**
     * Парсинг виджета из Map
     */
    private WidgetDefinition parseWidget(Map<String, Object> data) {
        try {
            WidgetDefinition widget = new WidgetDefinition();
            
            // Основные поля
            if (data.containsKey("id")) {
                Object idObj = data.get("id");
                if (idObj instanceof String) {
                    widget.setId((String) idObj);
                }
            }
            
            if (data.containsKey("type")) {
                Object typeObj = data.get("type");
                if (typeObj instanceof String) {
                    String typeStr = (String) typeObj;
                    try {
                        WidgetDefinition.WidgetType type = WidgetDefinition.WidgetType.valueOf(typeStr);
                        widget.setType(type);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid widget type: " + typeStr);
                        return null;
                    }
                }
            }
            
            // Текстовые поля
            if (data.containsKey("text")) {
                Object textObj = data.get("text");
                if (textObj instanceof String) {
                    widget.setText((String) textObj);
                }
            }
            
            if (data.containsKey("hoveredText")) {
                Object hoveredTextObj = data.get("hoveredText");
                if (hoveredTextObj instanceof String) {
                    widget.setHoveredText((String) hoveredTextObj);
                }
            }
            
            // Форматированный текст (может быть массивом)
            if (data.containsKey("formattedText")) {
                widget.setFormattedText(data.get("formattedText"));
            }
            
            if (data.containsKey("formattedHoveredText")) {
                widget.setFormattedHoveredText(data.get("formattedHoveredText"));
            }
            
            if (data.containsKey("material")) {
                Object materialObj = data.get("material");
                if (materialObj instanceof String) {
                    widget.setMaterial((String) materialObj);
                }
            }
            
            // Позиция
            if (data.containsKey("position")) {
                Object posObj = data.get("position");
                if (posObj instanceof java.util.List) {
                    java.util.List<Number> posList = (java.util.List<Number>) posObj;
                    float[] position = new float[3];
                    for (int i = 0; i < Math.min(3, posList.size()); i++) {
                        position[i] = posList.get(i).floatValue();
                    }
                    widget.setPosition(position);
                }
            }
            
            // Масштаб
            if (data.containsKey("scale")) {
                Object scaleObj = data.get("scale");
                if (scaleObj instanceof java.util.List) {
                    java.util.List<Number> scaleList = (java.util.List<Number>) scaleObj;
                    float[] scale = new float[3];
                    for (int i = 0; i < Math.min(3, scaleList.size()); i++) {
                        scale[i] = scaleList.get(i).floatValue();
                    }
                    widget.setScale(scale);
                }
            }
            
            // Толерантность
            if (data.containsKey("tolerance")) {
                Object tolObj = data.get("tolerance");
                if (tolObj instanceof java.util.List) {
                    java.util.List<Number> tolList = (java.util.List<Number>) tolObj;
                    float[] tolerance = new float[2];
                    for (int i = 0; i < Math.min(2, tolList.size()); i++) {
                        tolerance[i] = tolList.get(i).floatValue();
                    }
                    widget.setTolerance(tolerance);
                }
            }
            
            // Цвета фона
            if (data.containsKey("backgroundColor")) {
                Object colorObj = data.get("backgroundColor");
                if (colorObj instanceof java.util.List) {
                    java.util.List<Number> colorList = (java.util.List<Number>) colorObj;
                    int[] color = new int[3];
                    for (int i = 0; i < Math.min(3, colorList.size()); i++) {
                        color[i] = colorList.get(i).intValue();
                    }
                    widget.setBackgroundColor(color);
                }
            }
            
            if (data.containsKey("hoveredBackgroundColor")) {
                Object colorObj = data.get("hoveredBackgroundColor");
                if (colorObj instanceof java.util.List) {
                    java.util.List<Number> colorList = (java.util.List<Number>) colorObj;
                    int[] color = new int[3];
                    for (int i = 0; i < Math.min(3, colorList.size()); i++) {
                        color[i] = colorList.get(i).intValue();
                    }
                    widget.setHoveredBackgroundColor(color);
                }
            }
            
            // Альфа
            if (data.containsKey("backgroundAlpha")) {
                widget.setBackgroundAlpha(((Number) data.get("backgroundAlpha")).intValue());
            }
            
            if (data.containsKey("hoveredBackgroundAlpha")) {
                widget.setHoveredBackgroundAlpha(((Number) data.get("hoveredBackgroundAlpha")).intValue());
            }
            
            // Alignment
            if (data.containsKey("alignment")) {
                Object alignmentObj = data.get("alignment");
                if (alignmentObj instanceof String) {
                    String alignmentStr = (String) alignmentObj;
                    try {
                        WidgetDefinition.TextAlignment alignment = WidgetDefinition.TextAlignment.valueOf(alignmentStr);
                        widget.setAlignment(alignment);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid alignment: " + alignmentStr);
                    }
                }
            }
            
            // Tooltip
            if (data.containsKey("tooltip")) {
                Object tooltipObj = data.get("tooltip");
                if (tooltipObj instanceof String) {
                    widget.setTooltip((String) tooltipObj);
                }
            }
            
            if (data.containsKey("tooltipColor")) {
                Object colorObj = data.get("tooltipColor");
                if (colorObj instanceof java.util.List) {
                    java.util.List<Number> colorList = (java.util.List<Number>) colorObj;
                    int[] color = new int[3];
                    for (int i = 0; i < Math.min(3, colorList.size()); i++) {
                        color[i] = colorList.get(i).intValue();
                    }
                    widget.setTooltipColor(color);
                }
            }
            
            if (data.containsKey("tooltipDelay")) {
                widget.setTooltipDelay(((Number) data.get("tooltipDelay")).intValue());
            }
            
            // onClick
            if (data.containsKey("onClick")) {
                Object onClickObj = data.get("onClick");
                if (onClickObj instanceof Map) {
                    Map<String, Object> onClickData = (Map<String, Object>) onClickObj;
                    WidgetDefinition.ClickAction clickAction = new WidgetDefinition.ClickAction();
                    
                    if (onClickData.containsKey("action")) {
                        Object actionObj = onClickData.get("action");
                        if (actionObj instanceof String) {
                            String actionStr = (String) actionObj;
                            try {
                                WidgetDefinition.ClickAction.ActionType actionType = WidgetDefinition.ClickAction.ActionType.valueOf(actionStr);
                                clickAction.setAction(actionType);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid onClick action: " + actionStr);
                            }
                        }
                    }
                    
                    if (onClickData.containsKey("function")) {
                        Object functionObj = onClickData.get("function");
                        if (functionObj instanceof String) {
                            clickAction.setFunction((String) functionObj);
                        }
                    }
                    
                    if (onClickData.containsKey("screen")) {
                        Object screenObj = onClickData.get("screen");
                        if (screenObj instanceof String) {
                            clickAction.setTarget((String) screenObj);
                        }
                    }
                    
                    widget.setOnClick(clickAction);
                }
            }
            
            return widget;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error parsing widget", e);
            return null;
        }
    }
}
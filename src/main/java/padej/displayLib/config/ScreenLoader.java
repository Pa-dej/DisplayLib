package padej.displayLib.config;

import padej.displayLib.DisplayLib;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Загрузчик экранов из YAML файлов.
 * Использует Bukkit ConfigurationSection вместо прямого SnakeYAML,
 * чтобы избежать конфликтов classloader с Paper.
 */
public class ScreenLoader {
    private final DisplayLib plugin;
    private final Path screensDirectory;

    public ScreenLoader(DisplayLib plugin) {
        this.plugin = plugin;
        this.screensDirectory = plugin.getDataFolder().toPath().resolve("screens");

        try {
            Files.createDirectories(screensDirectory);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create screens directory", e);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Map<String, ScreenDefinition> loadAllScreens() {
        Map<String, ScreenDefinition> screens = new HashMap<>();

        // Check if directory is empty and create examples if needed
        boolean isEmpty = true;
        try {
            isEmpty = Files.list(screensDirectory)
                    .noneMatch(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check if screens directory is empty", e);
        }
        
        if (isEmpty) {
            plugin.getLogger().info("Screens directory is empty, creating example screens...");
            createExampleScreens();
            plugin.getLogger().info("Example screens created, now loading them...");
        }

        try {
            Files.walk(screensDirectory)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .forEach(path -> {
                        try {
                            ScreenDefinition screen = loadScreen(path);
                            if (screen != null && screen.getId() != null) {
                                screens.put(screen.getId(), screen);
                                plugin.getLogger().info("Loaded screen: " + screen.getId());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to load screen from " + path, e);
                        }
                    });
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load screens", e);
        }

        plugin.getLogger().info("Finished loading screens. Total loaded: " + screens.size());
        return screens;
    }

    public ScreenDefinition loadScreen(String screenId) {
        Path file = screensDirectory.resolve(screenId + ".yml");
        if (!Files.exists(file)) file = screensDirectory.resolve(screenId + ".yaml");
        if (!Files.exists(file)) {
            plugin.getLogger().warning("Screen file not found: " + screenId);
            return null;
        }
        try {
            return loadScreen(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load screen: " + screenId, e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Internal: parse YAML manually from Map<String,Object>
    // (avoids ClassCastException from bare new Yaml().load())
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public ScreenDefinition loadScreen(Path filePath) throws IOException {
        // Используем Bukkit YamlConfiguration — он уже в classpath Paper,
        // возвращает нормальные Java типы и не конфликтует с Paper's SnakeYAML.
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(filePath.toFile());

        ScreenDefinition screen = new ScreenDefinition();

        // id
        screen.setId(config.getString("id"));
        if (screen.getId() == null || screen.getId().isBlank()) {
            throw new IllegalArgumentException("Screen id is missing in " + filePath);
        }

        // background
        if (config.isConfigurationSection("background")) {
            var bgSec = config.getConfigurationSection("background");
            ScreenDefinition.BackgroundDefinition bg = new ScreenDefinition.BackgroundDefinition();

            bg.setText(bgSec.getString("text", " "));
            bg.setAlpha(bgSec.getInt("alpha", 160));

            List<?> color = bgSec.getList("color", List.of(0, 0, 0));
            bg.setColor(toIntArray(color, new int[]{0, 0, 0}));

            List<?> scale = bgSec.getList("scale", List.of(10.0, 4.0, 1.0));
            bg.setScale(toFloatArray(scale, new float[]{10f, 4f, 1f}));

            screen.setBackground(bg);
        }

        // scripts
        if (config.isConfigurationSection("scripts")) {
            var scriptSec = config.getConfigurationSection("scripts");
            Map<String, String> scripts = new HashMap<>();
            scripts.put("on_open", scriptSec.getString("on_open"));
            scripts.put("on_close", scriptSec.getString("on_close"));
            screen.setScripts(scripts);
        }

        // widgets
        var rawWidgets = config.getMapList("widgets");
        List<WidgetDefinition> widgets = new ArrayList<>();
        for (Map<?, ?> raw : rawWidgets) {
            try {
                widgets.add(parseWidget((Map<String, Object>) raw));
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping bad widget in " + filePath + ": " + e.getMessage());
            }
        }
        screen.setWidgets(widgets);

        return screen;
    }

    @SuppressWarnings("unchecked")
    private WidgetDefinition parseWidget(Map<String, Object> raw) {
        WidgetDefinition w = new WidgetDefinition();

        w.setId((String) raw.get("id"));

        String typeStr = (String) raw.getOrDefault("type", "ITEM_BUTTON");
        try {
            w.setType(WidgetDefinition.WidgetType.valueOf(typeStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            w.setType(WidgetDefinition.WidgetType.ITEM_BUTTON);
        }

        w.setMaterial((String) raw.getOrDefault("material", "STONE"));
        w.setText((String) raw.getOrDefault("text", ""));
        w.setHoveredText((String) raw.getOrDefault("hoveredText", w.getText()));
        w.setTooltip((String) raw.get("tooltip"));

        w.setPosition(toFloatArray(raw.get("position"), new float[]{0f, 0f, 0f}));
        w.setScale(toFloatArray(raw.get("scale"), new float[]{0.15f, 0.15f, 0.15f}));
        w.setTolerance(toFloatArray(raw.get("tolerance"), new float[]{0.06f, 0.06f}));
        w.setBackgroundColor(toIntArray(raw.get("backgroundColor"), new int[]{40, 40, 40}));
        w.setBackgroundAlpha(toInt(raw.get("backgroundAlpha"), 150));
        w.setHoveredBackgroundColor(toIntArray(raw.get("hoveredBackgroundColor"), new int[]{60, 60, 60}));
        w.setHoveredBackgroundAlpha(toInt(raw.get("hoveredBackgroundAlpha"), 180));
        w.setTooltipColor(toIntArray(raw.get("tooltipColor"), new int[]{252, 215, 32}));
        w.setTooltipDelay(toInt(raw.get("tooltipDelay"), 30));
        w.setGlowOnHover(toBool(raw.get("glowOnHover"), true));

        if (raw.get("glowColor") != null) {
            w.setGlowColor(toIntArray(raw.get("glowColor"), null));
        }

        // onClick
        if (raw.get("onClick") instanceof Map<?, ?> onClickRaw) {
            Map<String, Object> onClickMap = (Map<String, Object>) onClickRaw;
            WidgetDefinition.ClickAction action = new WidgetDefinition.ClickAction();

            String actionStr = (String) onClickMap.getOrDefault("action", "NONE");
            try {
                action.setAction(WidgetDefinition.ClickAction.ActionType.valueOf(actionStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                action.setAction(WidgetDefinition.ClickAction.ActionType.NONE);
            }

            action.setTarget((String) onClickMap.get("target"));
            action.setScript((String) onClickMap.get("script"));
            action.setFunction((String) onClickMap.get("function"));
            w.setOnClick(action);
        }

        return w;
    }

    // -------------------------------------------------------------------------
    // Type coercions
    // -------------------------------------------------------------------------

    private float[] toFloatArray(Object raw, float[] fallback) {
        if (raw instanceof List<?> list) {
            float[] arr = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = toFloat(list.get(i), fallback != null && i < fallback.length ? fallback[i] : 0f);
            }
            return arr;
        }
        return fallback;
    }

    private int[] toIntArray(Object raw, int[] fallback) {
        if (raw instanceof List<?> list) {
            int[] arr = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = toInt(list.get(i), fallback != null && i < fallback.length ? fallback[i] : 0);
            }
            return arr;
        }
        return fallback;
    }

    private float toFloat(Object v, float fallback) {
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof String s) { try { return Float.parseFloat(s); } catch (Exception ignored) {} }
        return fallback;
    }

    private int toInt(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) { try { return Integer.parseInt(s); } catch (Exception ignored) {} }
        return fallback;
    }

    private boolean toBool(Object v, boolean fallback) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    // -------------------------------------------------------------------------
    // Example files
    // -------------------------------------------------------------------------

    /**
     * Public method to manually create example screens (for testing/debugging)
     */
    public void createExampleScreensManually() {
        plugin.getLogger().info("Manually creating example screens...");
        createExampleScreens();
        plugin.getLogger().info("Example screens created manually.");
    }
    
    private void createExampleScreens() {
        createExampleMainScreen();
        createExampleBranch1Screen();
    }

    private void createExampleMainScreen() {
        String content = """
                id: main_menu
                background:
                  color: [0, 0, 0]
                  alpha: 160
                  scale: [10.0, 4.0, 1.0]
                  text: " "
                
                scripts:
                  on_open: "scripts/main_menu/on_open.lua"
                  on_close: "scripts/main_menu/on_close.lua"
                
                widgets:
                  - id: btn_branch1
                    type: ITEM_BUTTON
                    material: COMPASS
                    position: [-0.42, 0.30, 0.0]
                    scale: [0.15, 0.15, 0.15]
                    tolerance: [0.06, 0.06]
                    tooltip: "Ветка 1"
                    glowOnHover: true
                    onClick:
                      action: SWITCH_SCREEN
                      target: branch1
                
                  - id: btn_branch2
                    type: ITEM_BUTTON
                    material: MAP
                    position: [-0.42, 0.47, 0.0]
                    scale: [0.15, 0.15, 0.15]
                    tolerance: [0.06, 0.06]
                    tooltip: "Ветка 2"
                    glowOnHover: true
                    onClick:
                      action: NONE
                
                  - id: label_title
                    type: TEXT_BUTTON
                    text: "Главное меню"
                    hoveredText: "Главное меню"
                    position: [0.0, 0.85, 0.0]
                    scale: [0.5, 0.5, 0.5]
                    backgroundAlpha: 0
                    hoveredBackgroundAlpha: 0
                    onClick:
                      action: NONE
                """;
        writeFile("main_menu.yml", content);
    }

    private void createExampleBranch1Screen() {
        String content = """
                id: branch1
                background:
                  color: [20, 20, 40]
                  alpha: 160
                  scale: [8.0, 3.0, 1.0]
                  text: " "
                
                widgets:
                  - id: btn_back
                    type: TEXT_BUTTON
                    text: "⏴"
                    hoveredText: "⏴"
                    position: [-0.35, 0.8, 0.0]
                    scale: [0.75, 0.75, 0.75]
                    tolerance: [0.04, 0.04]
                    backgroundAlpha: 0
                    hoveredBackgroundAlpha: 0
                    onClick:
                      action: SWITCH_SCREEN
                      target: main_menu
                
                  - id: btn_close
                    type: TEXT_BUTTON
                    text: "⏺"
                    hoveredText: "⏺"
                    position: [0.35, 0.8, 0.0]
                    scale: [0.75, 0.75, 0.75]
                    tolerance: [0.035, 0.035]
                    backgroundAlpha: 0
                    hoveredBackgroundAlpha: 0
                    onClick:
                      action: CLOSE_SCREEN
                
                  - id: label_branch1
                    type: TEXT_BUTTON
                    text: "Ветка 1"
                    hoveredText: "Ветка 1"
                    position: [0.0, 0.5, 0.0]
                    scale: [0.6, 0.6, 0.6]
                    backgroundAlpha: 0
                    hoveredBackgroundAlpha: 0
                    onClick:
                      action: NONE
                """;
        writeFile("branch1.yml", content);
    }

    private void writeFile(String filename, String content) {
        Path filePath = screensDirectory.resolve(filename);
        try {
            Files.writeString(filePath, content);
            plugin.getLogger().info("Created example screen: " + filename);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create example screen: " + filename, e);
        }
    }

    public Path getScreensDirectory() {
        return screensDirectory;
    }
}
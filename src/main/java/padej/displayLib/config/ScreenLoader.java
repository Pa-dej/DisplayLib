package padej.displayLib.config;

import padej.displayLib.DisplayLib;
import org.bukkit.plugin.java.JavaPlugin;

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
    private final JavaPlugin plugin;
    private final Path screensDirectory;

    public ScreenLoader(DisplayLib plugin) {
        this.plugin = (JavaPlugin) plugin;
        this.screensDirectory = plugin.getDataFolder().toPath().resolve("screens");

        plugin.getLogger().info("ScreenLoader: Initializing with data folder: " + plugin.getDataFolder().getAbsolutePath());
        plugin.getLogger().info("ScreenLoader: Screens directory: " + screensDirectory.toString());

        try {
            Files.createDirectories(screensDirectory);
            plugin.getLogger().info("ScreenLoader: Screens directory created/verified");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "ScreenLoader: Failed to create screens directory", e);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Map<String, ScreenDefinition> loadAllScreens() {
        Map<String, ScreenDefinition> screens = new HashMap<>();

        plugin.getLogger().info("ScreenLoader: Starting to load screens from " + screensDirectory.toString());
        
        // Ensure directories exist
        try {
            Files.createDirectories(screensDirectory);
            Files.createDirectories(getScriptsDirectory());
            plugin.getLogger().info("ScreenLoader: Directories created/verified");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create directories", e);
        }

        // Load existing screens (no automatic fallback creation)
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
        if (screens.isEmpty()) {
            plugin.getLogger().info("No screens found. Use '/displaylib examples' to create demo screens.");
        }
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
            scripts.put("file", scriptSec.getString("file"));
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
        plugin.getLogger().info("ScreenLoader: Manually creating example screens...");
        
        try {
            // Ensure directories exist
            Files.createDirectories(screensDirectory);
            Files.createDirectories(getScriptsDirectory());
            
            createExampleScreens();
            plugin.getLogger().info("ScreenLoader: Example screens created manually - SUCCESS");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "ScreenLoader: Failed to create example screens manually", e);
        }
    }
    
    private void createExampleScreens() {
        plugin.getLogger().info("ScreenLoader: Creating example screens...");
        createExampleMainScreen();
        plugin.getLogger().info("ScreenLoader: Main screen created");
        
        createExampleSimpleDemo();
        plugin.getLogger().info("ScreenLoader: Simple demo screen created");
        
        createExampleLuaScripts();
        plugin.getLogger().info("ScreenLoader: Lua scripts created");
        
        plugin.getLogger().info("ScreenLoader: All example files creation completed");
    }

    private void createExampleSimpleDemo() {
        String content = """
                id: simple_demo
                background:
                  color: [20, 30, 50]
                  alpha: 180
                  scale: [8.0, 5.0, 1.0]
                  text: " "

                scripts:
                  file: "simple_demo.lua"

                widgets:
                  - id: title
                    type: TEXT_BUTTON
                    text: "Lua API Demo"
                    hoveredText: "Демонстрация Lua API"
                    position: [0.0, 0.85, 0.0]
                    scale: [0.6, 0.6, 0.6]
                    tolerance: [0.15, 0.08]
                    backgroundColor: [60, 80, 120]
                    backgroundAlpha: 200
                    onClick:
                      action: NONE

                  - id: btn_heal
                    type: ITEM_BUTTON
                    material: GOLDEN_APPLE
                    position: [-0.42, 0.3, 0.0]
                    scale: [0.15, 0.15, 0.15]
                    tolerance: [0.06, 0.06]
                    tooltip: "Восстановить здоровье"
                    glowOnHover: true
                    onClick:
                      action: RUN_SCRIPT
                      function: "btn_heal_click"

                  - id: counter_label
                    type: TEXT_BUTTON
                    text: "Счетчик: 0"
                    hoveredText: "Счетчик: 0"
                    position: [0.0, 0.3, 0.0]
                    scale: [0.4, 0.4, 0.4]
                    tolerance: [0.12, 0.06]
                    backgroundColor: [40, 80, 40]
                    backgroundAlpha: 150
                    onClick:
                      action: NONE

                  - id: btn_increment
                    type: ITEM_BUTTON
                    material: GREEN_CONCRETE
                    position: [-0.15, 0.1, 0.0]
                    scale: [0.12, 0.12, 0.12]
                    tolerance: [0.06, 0.06]
                    tooltip: "Увеличить"
                    glowOnHover: true
                    onClick:
                      action: RUN_SCRIPT
                      function: "btn_increment_click"

                  - id: btn_decrement
                    type: ITEM_BUTTON
                    material: RED_CONCRETE
                    position: [0.15, 0.1, 0.0]
                    scale: [0.12, 0.12, 0.12]
                    tolerance: [0.06, 0.06]
                    tooltip: "Уменьшить"
                    glowOnHover: true
                    onClick:
                      action: RUN_SCRIPT
                      function: "btn_decrement_click"

                  - id: btn_close
                    type: TEXT_BUTTON
                    text: "⏺"
                    hoveredText: "Закрыть"
                    position: [0.35, 0.8, 0.0]
                    scale: [0.75, 0.75, 0.75]
                    tolerance: [0.035, 0.035]
                    backgroundAlpha: 0
                    hoveredBackgroundAlpha: 50
                    onClick:
                      action: CLOSE_SCREEN
                """;
        writeFile("simple_demo.yml", content);
    }

    private void createExampleMainScreen() {
        String content = """
                id: main_menu
                background:
                  color: [0, 0, 0]
                  alpha: 160
                  scale: [8.0, 4.0, 1.0]
                  text: " "

                scripts:
                  file: "main_menu.lua"

                widgets:
                  - id: label_title
                    type: TEXT_BUTTON
                    text: "Главное меню"
                    hoveredText: "Главное меню"
                    position: [0.0, 0.85, 0.0]
                    scale: [0.6, 0.6, 0.6]
                    tolerance: [0.15, 0.08]
                    backgroundAlpha: 0
                    hoveredBackgroundAlpha: 0
                    onClick:
                      action: NONE

                  - id: btn_demo
                    type: ITEM_BUTTON
                    material: COMMAND_BLOCK
                    position: [-0.42, 0.3, 0.0]
                    scale: [0.15, 0.15, 0.15]
                    tolerance: [0.06, 0.06]
                    tooltip: "Lua API Demo"
                    glowOnHover: true
                    glowColor: [255, 215, 0]
                    onClick:
                      action: SWITCH_SCREEN
                      target: simple_demo

                  - id: btn_info
                    type: ITEM_BUTTON
                    material: BOOK
                    position: [-0.42, 0.47, 0.0]
                    scale: [0.15, 0.15, 0.15]
                    tolerance: [0.06, 0.06]
                    tooltip: "Информация"
                    glowOnHover: true
                    onClick:
                      action: RUN_SCRIPT
                      function: "btn_info_click"

                  - id: btn_close
                    type: TEXT_BUTTON
                    text: "⏺"
                    hoveredText: "Закрыть"
                    position: [0.35, 0.8, 0.0]
                    scale: [0.75, 0.75, 0.75]
                    tolerance: [0.035, 0.035]
                    backgroundAlpha: 0
                    hoveredBackgroundAlpha: 50
                    onClick:
                      action: CLOSE_SCREEN
                """;
        writeFile("main_menu.yml", content);
    }

    private void createExampleLuaScripts() {
        // Создаем скрипт для главного меню
        String mainMenuScript = "-- Main Menu Script\\n" +
                "function on_open()\\n" +
                "    log.info(\\\"Main menu opened for \\\" .. player.name())\\n" +
                "    player.message(\\\"§6Добро пожаловать в DisplayLib!\\\")\\n" +
                "    player.sound(\\\"ui.button.click\\\", 1.0, 1.0)\\n" +
                "end\\n\\n" +
                "function on_close()\\n" +
                "    log.info(\\\"Main menu closed for \\\" .. player.name())\\n" +
                "    player.message(\\\"§7До свидания!\\\")\\n" +
                "end\\n\\n" +
                "function btn_info_click()\\n" +
                "    player.message(\\\"§b=== DisplayLib Info ===\\\")\\n" +
                "    player.message(\\\"§7Плагин для создания 3D интерфейсов\\\")\\n" +
                "    player.message(\\\"§7С поддержкой Lua скриптов\\\")\\n" +
                "    player.sound(\\\"block.note_block.chime\\\", 1.0, 1.0)\\n" +
                "end";
        writeFile("main_menu.lua", mainMenuScript);

        // Создаем простой демо скрипт
        String simpleDemoScript = "-- Simple Demo Script\\n" +
                "local counter_value = 0\\n\\n" +
                "function on_open()\\n" +
                "    log.info(\\\"Simple demo opened for \\\" .. player.name())\\n" +
                "    player.message(\\\"§6Добро пожаловать в демо!\\\")\\n" +
                "    update_counter()\\n" +
                "end\\n\\n" +
                "function on_close()\\n" +
                "    log.info(\\\"Simple demo closed\\\")\\n" +
                "    player.message(\\\"§7До свидания!\\\")\\n" +
                "end\\n\\n" +
                "function btn_heal_click()\\n" +
                "    local old_health = player.health()\\n" +
                "    player.health(20)\\n" +
                "    player.message(\\\"§a❤ Здоровье восстановлено!\\\")\\n" +
                "    player.sound(\\\"entity.player.levelup\\\", 1.0, 1.0)\\n" +
                "end\\n\\n" +
                "function btn_increment_click()\\n" +
                "    counter_value = storage.get(\\\"counter\\\", 0) + 1\\n" +
                "    storage.set(\\\"counter\\\", counter_value)\\n" +
                "    update_counter()\\n" +
                "    player.sound(\\\"block.note_block.harp\\\", 1.0, 1.0)\\n" +
                "    player.message(\\\"§a+1 Счетчик: \\\" .. counter_value)\\n" +
                "end\\n\\n" +
                "function btn_decrement_click()\\n" +
                "    counter_value = storage.get(\\\"counter\\\", 0)\\n" +
                "    if counter_value > 0 then\\n" +
                "        counter_value = counter_value - 1\\n" +
                "        storage.set(\\\"counter\\\", counter_value)\\n" +
                "        update_counter()\\n" +
                "        player.sound(\\\"block.note_block.bass\\\", 1.0, 0.8)\\n" +
                "        player.message(\\\"§c-1 Счетчик: \\\" .. counter_value)\\n" +
                "    else\\n" +
                "        player.message(\\\"§cСчетчик уже равен нулю!\\\")\\n" +
                "    end\\n" +
                "end\\n\\n" +
                "function update_counter()\\n" +
                "    local counter = storage.get(\\\"counter\\\", 0)\\n" +
                "    local label = screen.widget(\\\"counter_label\\\")\\n" +
                "    if label then\\n" +
                "        label.text(\\\"Счетчик: \\\" .. counter)\\n" +
                "    end\\n" +
                "end";
        writeFile("simple_demo.lua", simpleDemoScript);
    }

    private void writeFile(String filename, String content) {
        try {
            Path filePath;
            if (filename.endsWith(".lua")) {
                // Lua scripts go to scripts directory
                Path scriptsDir = getScriptsDirectory();
                filePath = scriptsDir.resolve(filename);
                plugin.getLogger().info("ScreenLoader: Writing Lua script to " + filePath.toString());
            } else {
                // YAML files go to screens directory
                filePath = screensDirectory.resolve(filename);
                plugin.getLogger().info("ScreenLoader: Writing YAML screen to " + filePath.toString());
            }
            
            // Ensure parent directory exists
            Files.createDirectories(filePath.getParent());
            
            // Write file with UTF-8 encoding
            Files.write(filePath, content.getBytes("UTF-8"));
            
            // Verify file was created
            if (Files.exists(filePath)) {
                long fileSize = Files.size(filePath);
                plugin.getLogger().info("ScreenLoader: Successfully created fallback file: " + filename + " (size: " + fileSize + " bytes)");
            } else {
                plugin.getLogger().severe("ScreenLoader: File was not created: " + filename);
            }
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "ScreenLoader: Failed to create fallback file: " + filename, e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "ScreenLoader: Unexpected error creating file: " + filename, e);
        }
    }

    public Path getScreensDirectory() {
        return screensDirectory;
    }

    private Path getScriptsDirectory() {
        Path scriptsDir = plugin.getDataFolder().toPath().resolve("scripts");
        plugin.getLogger().info("ScreenLoader: Scripts directory: " + scriptsDir.toString());
        
        try {
            Files.createDirectories(scriptsDir);
            plugin.getLogger().info("ScreenLoader: Scripts directory created/verified");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "ScreenLoader: Failed to create scripts directory", e);
        }
        return scriptsDir;
    }
}
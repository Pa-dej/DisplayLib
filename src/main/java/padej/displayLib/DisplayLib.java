package padej.displayLib;

import padej.displayLib.commands.DisplayLibCommand;
import padej.displayLib.config.ScreenRegistry;
import padej.displayLib.lua.LuaEngine;
import padej.displayLib.render.particles.DisplayParticle;
import padej.displayLib.render.shapes.Highlight;
import padej.displayLib.test_events.*;
import padej.displayLib.ui.UIManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Главный класс плагина DisplayLib.
 * 
 * <p>DisplayLib - это система для создания интерактивных 3D экранов в Minecraft
 * с поддержкой YAML конфигурации и Lua скриптов.</p>
 * 
 * <h2>Основные возможности:</h2>
 * <ul>
 * <li><b>YAML экраны</b> - Создание экранов через конфигурационные файлы</li>
 * <li><b>Lua скрипты</b> - Программирование логики экранов на Lua</li>
 * <li><b>Интерактивные виджеты</b> - Кнопки, текст, предметы с поддержкой кликов</li>
 * <li><b>Персональные и публичные экраны</b> - Экраны для одного игрока или для всех</li>
 * <li><b>Система хранения данных</b> - Сохранение состояния между сессиями</li>
 * <li><b>Таймеры и анимации</b> - Динамические эффекты и отложенные действия</li>
 * </ul>
 * 
 * <h2>Структура файлов:</h2>
 * <pre>
 * plugins/DisplayLib/
 * ├── screens/          # YAML файлы экранов
 * │   ├── main_menu.yml
 * │   └── settings.yml
 * ├── scripts/          # Lua скрипты
 * │   ├── main_menu.lua
 * │   └── common.lua
 * └── config.yml        # Настройки плагина (hot-reload, test-listeners)
 * </pre>
 * 
 * <h2>Команды:</h2>
 * <ul>
 * <li><b>/displaylib reload</b> - Перезагрузить экраны и скрипты</li>
 * <li><b>/displaylib open &lt;screen&gt; [player]</b> - Открыть экран</li>
 * <li><b>/displaylib close [player]</b> - Закрыть экран</li>
 * <li><b>/displaylib list</b> - Список доступных экранов</li>
 * <li><b>/displaylib examples</b> - Создать примеры файлов</li>
 * </ul>
 * 
 * @author DisplayLib Team
 * @version 2.0.0
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public final class DisplayLib extends JavaPlugin {

    public static final List<DisplayParticle> DISPLAY_PARTICLES = new ArrayList<>();
    
    /** Экземпляр плагина; кэшируется, так как getInstance() вызывается из горячих путей */
    private static DisplayLib instance;
    
    private ScreenRegistry screenRegistry;
    private LuaEngine luaEngine;

    @Override
    public void onEnable() {
        instance = this;
        
        // config.yml: hot-reload, test-listeners
        saveDefaultConfig();
        
        // Инициализация новой системы экранов
        screenRegistry = new ScreenRegistry(this);
        screenRegistry.initialize();
        
        // Инициализация Lua движка
        luaEngine = new LuaEngine(this);
        
        // Инициализация UIManager с реестром экранов
        UIManager.getInstance().initialize(screenRegistry, luaEngine);

        // Регистрация команд
        DisplayLibCommand commandExecutor = new DisplayLibCommand(this);
        getCommand("displaylib").setExecutor(commandExecutor);
        getCommand("displaylib").setTabCompleter(commandExecutor);

        // Демонстрационные слушатели (пакет test_events) реагируют на каждый клик каждого игрока,
        // поэтому в обычной работе выключены. Включаются в config.yml: test-listeners: true
        if (getConfig().getBoolean("test-listeners", false)) {
            registerTestListeners();
            getLogger().info("Test listeners enabled (test-listeners: true)");
        }

        Highlight.removeAllSelections();
        Highlight.startColorUpdateTask();
        startParticleTask();
        
        getLogger().info("DisplayLib enabled with new YAML-based screen system!");
    }

    @Override
    public void onDisable() {
        // Остановка screen registry
        if (screenRegistry != null) {
            screenRegistry.shutdown();
        }
        
        UIManager manager = UIManager.getInstance();
        if (manager.hasActiveScreens()) {
            getLogger().info("Cleaning up active UI screens...");
            manager.cleanup();
        }

        DISPLAY_PARTICLES.clear();
        instance = null;
    }

    public static JavaPlugin getInstance() {
        DisplayLib plugin = instance;
        return plugin != null ? plugin : JavaPlugin.getPlugin(DisplayLib.class);
    }
    
    private void registerTestListeners() {
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ApplyHighlightToBlockTest(), this);
        pluginManager.registerEvents(new CreateDisplayParticleFirstTest(), this);
        pluginManager.registerEvents(new CreateDisplayParticleSecondTest(), this);
        pluginManager.registerEvents(new CreateDisplayParticleThirdTest(), this);
        pluginManager.registerEvents(new CreateTestUI(), this);
        pluginManager.registerEvents(new GizmoTest(), this);
        pluginManager.registerEvents(new PointDetectFirstTest(), this);
        pluginManager.registerEvents(new PointDetectSecondTest(), this);
        pluginManager.registerEvents(new RotationRelativeToCenterPointTest(), this);
        pluginManager.registerEvents(new SmoothMotionAndRotationTest(), this);
    }
    
    public ScreenRegistry getScreenRegistry() {
        return screenRegistry;
    }
    
    public LuaEngine getLuaEngine() {
        return luaEngine;
    }

    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (DISPLAY_PARTICLES.isEmpty()) return;
                for (DisplayParticle displayParticle : new ArrayList<>(DISPLAY_PARTICLES)) {
                    displayParticle.update();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }
}

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

@SuppressWarnings("unused")
public final class DisplayLib extends JavaPlugin {

    public static final List<DisplayParticle> DISPLAY_PARTICLES = new ArrayList<>();
    private ScreenRegistry screenRegistry;
    private LuaEngine luaEngine;

    @Override
    public void onEnable() {
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

        getServer().getPluginManager().registerEvents(new ApplyHighlightToBlockTest(), this);
        getServer().getPluginManager().registerEvents(new CreateDisplayParticleFirstTest(), this);
        getServer().getPluginManager().registerEvents(new CreateDisplayParticleSecondTest(), this);
        getServer().getPluginManager().registerEvents(new CreateDisplayParticleThirdTest(), this);
        getServer().getPluginManager().registerEvents(new CreateTestUI(), this);
        getServer().getPluginManager().registerEvents(new GizmoTest(), this);
        getServer().getPluginManager().registerEvents(new PointDetectFirstTest(), this);
        getServer().getPluginManager().registerEvents(new PointDetectSecondTest(), this);
        getServer().getPluginManager().registerEvents(new RotationRelativeToCenterPointTest(), this);
        getServer().getPluginManager().registerEvents(new SmoothMotionAndRotationTest(), this);

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
    }

    public static JavaPlugin getInstance() {
        return JavaPlugin.getPlugin(DisplayLib.class);
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

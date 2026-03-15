package padej.displayLib.lua;

import padej.displayLib.DisplayLib;
import padej.displayLib.lua.api.*;
import padej.displayLib.ui.ScreenInstance;
import org.bukkit.entity.Player;
import org.luaj.vm2.Globals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Контекст выполнения Lua скрипта для конкретного экрана и игрока
 */
public class LuaContext {
    private final Globals globals;
    private final ScreenInstance screen;
    private final Player player;
    private final DisplayLib plugin;
    
    // API объекты
    private final PlayerAPI playerAPI;
    private final ScreenAPI screenAPI;
    private final StorageAPI storageAPI;
    private final TimerAPI timerAPI;
    private final LogAPI logAPI;
    
    // Persistent data - живет пока экран открыт
    private final Map<String, Object> persistentData = new HashMap<>();
    
    // Отслеживание загруженных скриптов
    private final Set<String> loadedScripts = new HashSet<>();
    
    public LuaContext(Globals globals, ScreenInstance screen, Player player, DisplayLib plugin) {
        this.globals = globals;
        this.screen = screen;
        this.player = player;
        this.plugin = plugin;
        
        // Создаем API объекты
        this.playerAPI = new PlayerAPI(player);
        this.screenAPI = new ScreenAPI(screen, this);
        this.storageAPI = new StorageAPI(player, plugin);
        this.timerAPI = new TimerAPI(plugin);
        this.logAPI = new LogAPI(plugin);
    }
    
    // Getters
    public Globals getGlobals() { return globals; }
    public ScreenInstance getScreen() { return screen; }
    public Player getPlayer() { return player; }
    public DisplayLib getPlugin() { return plugin; }
    
    public PlayerAPI getPlayerAPI() { return playerAPI; }
    public ScreenAPI getScreenAPI() { return screenAPI; }
    public StorageAPI getStorageAPI() { return storageAPI; }
    public TimerAPI getTimerAPI() { return timerAPI; }
    public LogAPI getLogAPI() { return logAPI; }
    
    // Persistent data methods
    public Object getPersistentData(String key) {
        return persistentData.get(key);
    }
    
    public void setPersistentData(String key, Object value) {
        if (value == null) {
            persistentData.remove(key);
        } else {
            persistentData.put(key, value);
        }
    }
    
    /**
     * Очистка при закрытии экрана
     */
    public void cleanup() {
        timerAPI.cancelAllTimers();
        persistentData.clear();
        loadedScripts.clear();
    }
    
    /**
     * Проверить, загружен ли скрипт в этот контекст
     */
    public boolean isScriptLoaded(String scriptPath) {
        return loadedScripts.contains(scriptPath);
    }
    
    /**
     * Отметить скрипт как загруженный
     */
    public void markScriptLoaded(String scriptPath) {
        loadedScripts.add(scriptPath);
    }
}
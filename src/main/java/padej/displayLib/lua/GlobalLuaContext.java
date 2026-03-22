package padej.displayLib.lua;

import padej.displayLib.DisplayLib;
import padej.displayLib.lua.api.*;
import padej.displayLib.ui.GlobalScreenInstance;
import org.bukkit.entity.Player;
import org.luaj.vm2.Globals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Контекст выполнения Lua скрипта для глобального экрана
 */
public class GlobalLuaContext {
    private final Globals globals;
    private final GlobalScreenInstance screen;
    private final Player player;
    private final DisplayLib plugin;
    
    // API объекты
    private final PlayerAPI playerAPI;
    private final GlobalScreenAPI screenAPI;
    private final StorageAPI storageAPI;
    private final TimerAPI timerAPI;
    private final LogAPI logAPI;
    
    // Persistent data - живет пока экран открыт
    private final Map<String, Object> persistentData = new HashMap<>();
    
    // Отслеживание загруженных скриптов
    private final Set<String> loadedScripts = new HashSet<>();
    
    public GlobalLuaContext(Globals globals, GlobalScreenInstance screen, Player player, DisplayLib plugin) {
        this.globals = globals;
        this.screen = screen;
        this.player = player;
        this.plugin = plugin;
        
        // Создаем API объекты
        this.playerAPI = player != null ? new PlayerAPI(player) : null;
        this.screenAPI = new GlobalScreenAPI(screen, this);
        this.storageAPI = new StorageAPI(player, plugin);
        this.timerAPI = new TimerAPI(plugin);
        this.logAPI = new LogAPI(plugin);
    }
    
    // Getters
    public Globals getGlobals() { return globals; }
    public GlobalScreenInstance getScreen() { return screen; }
    public Player getPlayer() { return player; }
    public DisplayLib getPlugin() { return plugin; }
    
    public PlayerAPI getPlayerAPI() { return playerAPI; }
    public GlobalScreenAPI getScreenAPI() { return screenAPI; }
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
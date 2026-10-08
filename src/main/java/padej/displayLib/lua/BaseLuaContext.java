package padej.displayLib.lua;

import padej.displayLib.DisplayLib;
import padej.displayLib.lua.api.LogAPI;
import padej.displayLib.lua.api.PlayerAPI;
import padej.displayLib.lua.api.StorageAPI;
import padej.displayLib.lua.api.TimerAPI;
import padej.displayLib.lua.api.WidgetAPI;
import padej.displayLib.ui.widgets.Widget;
import org.bukkit.entity.Player;
import org.luaj.vm2.Globals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Общая часть контекста выполнения Lua скрипта экрана.
 *
 * <p>Содержит всё, что одинаково для приватных ({@link LuaContext}) и публичных
 * ({@link GlobalLuaContext}) экранов: окружение Lua, общие API, данные экрана и
 * учёт загруженных скриптов. Благодаря общему базовому типу {@link LuaEngine}
 * работает с любым экраном одним и тем же кодом.</p>
 */
public abstract class BaseLuaContext {
    private final Globals globals;
    private final Player player;
    private final DisplayLib plugin;

    private final PlayerAPI playerAPI;
    private final StorageAPI storageAPI;
    private final TimerAPI timerAPI;
    private final LogAPI logAPI;

    // Persistent data - живет пока экран открыт
    private final Map<String, Object> persistentData = new HashMap<>();

    // Отслеживание загруженных скриптов
    private final Set<String> loadedScripts = new HashSet<>();

    // Lua-обёртки виджетов: одна на виджет на всё время жизни экрана
    private final Map<Widget, WidgetAPI> widgetApis = new IdentityHashMap<>();

    /**
     * @param player владелец экрана; null для публичных экранов
     */
    protected BaseLuaContext(Globals globals, Player player, DisplayLib plugin) {
        this.globals = globals;
        this.player = player;
        this.plugin = plugin;

        this.playerAPI = player != null ? new PlayerAPI(player) : null;
        this.storageAPI = new StorageAPI(player, plugin);
        this.timerAPI = new TimerAPI(plugin);
        this.logAPI = new LogAPI(plugin);
    }

    public Globals getGlobals() { return globals; }
    public Player getPlayer() { return player; }
    public DisplayLib getPlugin() { return plugin; }

    /** API игрока; null, если у контекста нет постоянного игрока (публичный экран). */
    public PlayerAPI getPlayerAPI() { return playerAPI; }
    public StorageAPI getStorageAPI() { return storageAPI; }
    public TimerAPI getTimerAPI() { return timerAPI; }
    public LogAPI getLogAPI() { return logAPI; }

    /**
     * Lua-объект виджета. Создаётся при первом обращении и затем переиспользуется:
     * раньше каждый {@code screen.widget(id)} и каждый клик строили новую таблицу с замыканиями.
     */
    public WidgetAPI widgetApi(Widget widget) {
        return widgetApis.computeIfAbsent(widget, WidgetAPI::new);
    }

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
     * Очистка при закрытии экрана. Безопасно вызывать повторно.
     */
    public void cleanup() {
        timerAPI.cancelAllTimers();
        persistentData.clear();
        loadedScripts.clear();
        widgetApis.clear();
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

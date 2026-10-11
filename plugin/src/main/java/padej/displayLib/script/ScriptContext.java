package padej.displayLib.script;

import org.bukkit.entity.Player;
import padej.displayLib.DisplayLib;
import padej.displayLib.script.api.LogAPI;
import padej.displayLib.script.api.PlayerAPI;
import padej.displayLib.script.api.ScreenAPI;
import padej.displayLib.script.api.StorageAPI;
import padej.displayLib.script.api.TimerAPI;
import padej.displayLib.script.api.WidgetAPI;
import padej.displayLib.ui.widgets.Widget;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Скриптовое окружение одного экземпляра экрана: API-объекты, которые скрипт видит
 * как глобалы, и сам скрипт.
 *
 * <p>Глобалы: {@code player} (владелец приватного экрана; {@code null} у публичного),
 * {@code screen}, {@code storage}, {@code timer}, {@code log}. Обработчик клика получает
 * аргументами виджет и кликнувшего игрока: {@code void onBuy(dyn widget, dyn player)}.</p>
 *
 * <p>Контекст создаётся вместе с экраном и очищается в {@code remove()} экрана при любом
 * способе его удаления: закрытие, переключение, выход, смерть или смена мира игрока,
 * выключение плагина. После очистки таймеры отменены и новые не создаются.</p>
 */
public final class ScriptContext {
    public static final String HOOK_OPEN = "onOpen";
    public static final String HOOK_CLOSE = "onClose";

    private final Player owner;
    private final PlayerAPI ownerApi;
    private final ScreenAPI screenApi;
    private final StorageAPI storageApi;
    private final TimerAPI timerApi;
    private final LogAPI logApi;

    /** Обёртки игроков публичного экрана (у приватного - только владелец). */
    private final Map<UUID, PlayerAPI> playerApis = new HashMap<>();

    private ScreenScript script;
    private boolean closed;

    /**
     * @param owner      владелец экрана; {@code null} для публичного
     * @param scriptFile путь скрипта относительно {@code scripts/}; {@code null}, если у экрана нет скрипта
     */
    public ScriptContext(DisplayLib plugin, JumperEngine engine, ScreenAPI.Host host, Player owner, String scriptFile) {
        this.owner = owner;
        this.ownerApi = owner != null ? new PlayerAPI(owner) : null;
        if (ownerApi != null) playerApis.put(owner.getUniqueId(), ownerApi);

        this.screenApi = new ScreenAPI(host);
        this.storageApi = new StorageAPI(owner != null ? owner.getUniqueId() : null);
        this.timerApi = new TimerAPI(plugin, this::guarded);
        this.logApi = new LogAPI(plugin.getLogger(), scriptFile != null ? scriptFile : host.getScreenId());

        if (scriptFile != null && engine != null) {
            Map<String, Object> globals = new LinkedHashMap<>();
            globals.put("player", ownerApi);
            globals.put("screen", screenApi);
            globals.put("storage", storageApi);
            globals.put("timer", timerApi);
            globals.put("log", logApi);
            this.script = engine.load(scriptFile, globals);
        }
    }

    public Player getOwner() {
        return owner;
    }

    public ScreenAPI getScreenApi() {
        return screenApi;
    }

    public TimerAPI getTimerApi() {
        return timerApi;
    }

    /** Загруженный скрипт или {@code null}, если у экрана нет скрипта либо он не загрузился. */
    public ScreenScript getScript() {
        return script;
    }

    public boolean hasScript() {
        return script != null;
    }

    /** Обёртка игрока: одна на игрока на всё время жизни экрана. */
    public PlayerAPI playerApi(Player player) {
        if (player == null) return null;
        return playerApis.computeIfAbsent(player.getUniqueId(), id -> new PlayerAPI(player));
    }

    /** Обёртка виджета: одна на виджет на всё время жизни экрана. */
    public WidgetAPI widgetApi(String id, Widget widget) {
        return screenApi.widgetApi(id, widget);
    }

    /** Забыть обёртку игрока (ушёл с публичного экрана). */
    public void forgetPlayer(UUID playerId) {
        if (owner == null || !owner.getUniqueId().equals(playerId)) {
            playerApis.remove(playerId);
        }
    }

    /** Вызвать необязательную функцию жизненного цикла ({@code onOpen} / {@code onClose}). */
    public void callHook(String name) {
        if (script == null || closed) return;
        script.invoke(name, false);
    }

    /**
     * Вызвать обработчик клика: {@code name(widget, player)}.
     *
     * @return true, если функция найдена и выполнена без ошибок
     */
    /**
     * Будет ли клик по кнопке с {@code onClick: "name"} что-то делать: скрипт загружен, функция
     * объявлена и её тело не пустое. Иначе виджет можно не обсчитывать (см. Widget#isInteractive).
     */
    public boolean clickDoesSomething(String name) {
        ScreenScript script = getScript();
        return script != null && name != null && script.hasFunction(name) && !script.isEmptyFunction(name);
    }

    public boolean callClick(String name, String widgetId, Widget widget, Player player) {
        if (script == null || closed) return false;
        WidgetAPI w = widget != null ? widgetApi(widgetId, widget) : null;
        return script.invoke(name, true, w, playerApi(player));
    }

    /** Вызвать произвольную функцию скрипта с аргументами (для Java API других плагинов). */
    public boolean call(String name, Object... args) {
        if (script == null || closed) return false;
        return script.invoke(name, true, args);
    }

    private void guarded(Runnable callback) {
        if (closed) return;
        if (script != null) {
            script.guarded(callback);
        } else {
            callback.run();
        }
    }

    /** Очистка при удалении экрана. Безопасно вызывать повторно. */
    public void cleanup() {
        closed = true;
        timerApi.shutdown();
        screenApi.cleanup();
        playerApis.clear();
        script = null;
    }
}

package padej.displayLib.script;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Ввод из чата для скриптов экранов ({@code player.input(...)}): следующее сообщение игрока перехватывается
 * и отдаётся функции скрипта.
 *
 * <p>У игрока одно ожидание: новое заменяет прежнее. Чат приходит в асинхронном потоке, поэтому таблица
 * ожиданий - {@link ConcurrentHashMap}, а сообщение забирается атомарно ({@code remove}): одно сообщение -
 * одному ожиданию, таймаут и сообщение не могут сработать оба. Функция скрипта, таймеры и всё остальное -
 * в главном потоке.</p>
 *
 * <p>Ожидание снимается: при закрытии экрана, который его поставил ({@link #cancelAll(Object)} из
 * {@link ScriptContext#cleanup()}), при выходе игрока, по таймауту (функция не вызывается).</p>
 */
public final class ChatInput implements Listener {
    private static volatile ChatInput instance;

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /**
     * Одно ожидание.
     *
     * @param owner    кто поставил (контекст экрана); снимается вместе с ним
     * @param deliver  вызвать функцию скрипта с текстом (главный поток, под сторожем скрипта)
     */
    private record Pending(Object owner, Consumer<String> deliver, BukkitTask[] timeout) {
        void cancelTimeout() {
            BukkitTask t = timeout[0];
            if (t != null) t.cancel();
        }
    }

    private ChatInput(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Включение плагина. */
    public static void install(Plugin plugin) {
        shutdown();
        ChatInput input = new ChatInput(plugin);
        Bukkit.getPluginManager().registerEvents(input, plugin);
        instance = input;
    }

    /** Выключение плагина: все ожидания снимаются без вызова функций. */
    public static void shutdown() {
        ChatInput input = instance;
        instance = null;
        if (input == null) return;
        HandlerList.unregisterAll(input);
        for (Pending p : input.pending.values()) p.cancelTimeout();
        input.pending.clear();
    }

    /** Сервис или {@code null}, если плагин выключен. */
    public static ChatInput get() {
        return instance;
    }

    /**
     * Ждать следующее сообщение игрока. Главный поток.
     *
     * @param owner        контекст экрана, который ждёт (снимается вместе с ним)
     * @param deliver      получит текст сообщения в главном потоке
     * @param timeoutTicks {@code <= 0} - без таймаута; по таймауту {@code deliver} не вызывается
     */
    public void request(Player player, Object owner, Consumer<String> deliver, long timeoutTicks) {
        UUID id = player.getUniqueId();
        Pending p = new Pending(owner, deliver, new BukkitTask[1]);
        Pending old = pending.put(id, p);
        if (old != null) old.cancelTimeout();
        if (timeoutTicks > 0) {
            p.timeout[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> pending.remove(id, p), timeoutTicks);
        }
    }

    /** Снять ожидание игрока. */
    public void cancel(UUID player) {
        Pending p = pending.remove(player);
        if (p != null) p.cancelTimeout();
    }

    /** Снять ожидание игрока, если его поставил {@code owner}. */
    public void cancel(UUID player, Object owner) {
        Pending p = pending.get(player);
        if (p != null && p.owner == owner && pending.remove(player, p)) p.cancelTimeout();
    }

    /** Ждёт ли игрок ввода (от любого экрана). */
    public boolean awaiting(UUID player) {
        return pending.containsKey(player);
    }

    /** Ждёт ли игрок ввода, поставленного {@code owner}. */
    public boolean awaiting(UUID player, Object owner) {
        Pending p = pending.get(player);
        return p != null && p.owner == owner;
    }

    /** Снять все ожидания, поставленные {@code owner} (экран закрыт). */
    public void cancelAll(Object owner) {
        for (Map.Entry<UUID, Pending> e : pending.entrySet()) {
            Pending p = e.getValue();
            if (p.owner == owner && pending.remove(e.getKey(), p)) p.cancelTimeout();
        }
    }

    /**
     * Самый ранний приоритет: сообщение для ввода не доходит до чата и до обработчиков других плагинов
     * с {@code ignoreCancelled = true} (обычная практика). Уже отменённое кем-то сообщение тоже забирается -
     * игрок отвечал на вопрос экрана.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending p = pending.remove(event.getPlayer().getUniqueId());
        if (p == null) return;
        event.setCancelled(true);
        String text = event.signedMessage().message(); // как набрал игрок, без форматирования
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.cancelTimeout();
                p.deliver.accept(text);
            });
        } catch (RuntimeException ex) {
            // плагин выключается - отдавать некому
            plugin.getLogger().log(Level.FINE, "chat input dropped: " + ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }
}

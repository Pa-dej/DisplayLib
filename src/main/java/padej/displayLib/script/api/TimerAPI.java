package padej.displayLib.script.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Таймеры экрана: глобал {@code timer}. Время - в тиках (20 тиков = 1 секунда).
 *
 * <p>Функции возвращают ID таймера. При любом удалении экрана все его таймеры
 * отменяются, а новые не создаются (возвращается {@code -1}).</p>
 *
 * <pre>
 * timer.after(40, () -&gt; player.message("прошло 2 секунды"));
 * int id = timer.every(20, () -&gt; label.text("" + millis()));
 * timer.times(10, 5, i -&gt; player.sound("block.note_block.pling", 1, 1 + i * 0.1));
 * timer.cancel(id);
 * </pre>
 *
 * <p>Колбэк - любая функция Jumper; она выполняется под той же политикой доступа
 * и тем же сторожевым таймером, что и остальной скрипт.</p>
 */
public final class TimerAPI {
    private final Plugin plugin;
    private final Consumer<Runnable> guard;

    /** Активные таймеры по ID задачи; завершившиеся удаляются сразу. */
    private final Map<Integer, BukkitTask> activeTasks = new HashMap<>();

    /** Экран закрыт: новые таймеры не создаются. */
    private boolean closed = false;

    /**
     * @param guard обёртка вызова скриптового колбэка (перехват ошибок, сторожевой таймер)
     */
    public TimerAPI(Plugin plugin, Consumer<Runnable> guard) {
        this.plugin = plugin;
        this.guard = guard;
    }

    /** Один раз через {@code ticks} тиков. */
    public int after(double ticks, Runnable fn) {
        if (closed || fn == null) return -1;
        long delay = Math.max(0L, Math.round(ticks));
        int[] id = new int[1];
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeTasks.remove(id[0]);
            guard.accept(fn);
        }, delay);
        id[0] = task.getTaskId();
        activeTasks.put(id[0], task);
        return id[0];
    }

    /** Повторять каждые {@code ticks} тиков; первый вызов - на следующем тике. */
    public int every(double ticks, Runnable fn) {
        if (closed || fn == null) return -1;
        long period = Math.max(1L, Math.round(ticks));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> guard.accept(fn), 0L, period);
        activeTasks.put(task.getTaskId(), task);
        return task.getTaskId();
    }

    /** Вызвать {@code fn(i)} {@code count} раз с периодом {@code period}; {@code i} от 1. */
    public int times(double period, double count, IntConsumer fn) {
        if (closed || fn == null) return -1;
        long periodTicks = Math.max(1L, Math.round(period));
        int maxCount = (int) Math.round(count);
        int[] counter = {0};
        BukkitTask[] ref = new BukkitTask[1];
        ref[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int current = ++counter[0];
            if (current > maxCount) {
                ref[0].cancel();
                activeTasks.remove(ref[0].getTaskId());
                return;
            }
            guard.accept(() -> fn.accept(current));
        }, 0L, periodTicks);
        activeTasks.put(ref[0].getTaskId(), ref[0]);
        return ref[0].getTaskId();
    }

    /** Отменить таймер по ID. */
    public void cancel(double id) {
        BukkitTask task = activeTasks.remove((int) Math.round(id));
        if (task != null) task.cancel();
    }

    /** Отменить все таймеры. */
    public void cancelAll() {
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    /** Число активных таймеров. */
    public int active() {
        return activeTasks.size();
    }

    /** Закрытие экрана: отменить всё и запретить новые таймеры. */
    public void shutdown() {
        closed = true;
        cancelAll();
    }
}

package padej.displayLib.script;

import me.padej.jumper.interp.Interpreter;
import org.bukkit.plugin.Plugin;

/**
 * Сторожевой таймер скриптов.
 *
 * <p>Все скрипты выполняются в основном потоке сервера, поэтому в каждый момент
 * работает не более одного; отдельный демон-поток следит за ним и, если вызов
 * длится дольше лимита, вызывает {@link Interpreter#cancel()}. Jumper останавливает
 * скрипт на ближайшей итерации цикла или вызове функции ошибкой {@code Cancelled},
 * которую скрипт перехватить не может. Блокирующие вызовы Java (sleep, ожидание
 * блокировки) так не прерываются - политика доступа их просто не открывает.</p>
 */
final class Watchdog {
    private final Plugin plugin;
    private final long timeoutMillis;
    private volatile Interpreter running;
    private volatile long deadline;
    private volatile boolean fired;
    private Thread thread;

    Watchdog(Plugin plugin, long timeoutMillis) {
        this.plugin = plugin;
        this.timeoutMillis = timeoutMillis;
    }

    boolean enabled() {
        return timeoutMillis > 0;
    }

    void start() {
        if (!enabled()) return;
        thread = new Thread(this::loop, "DisplayLib-ScriptWatchdog");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
    }

    /** Начать следить за вызовом; возвращает предыдущее состояние для вложенных вызовов. */
    Interpreter enter(Interpreter interpreter) {
        if (!enabled()) return null;
        Interpreter previous = running;
        if (previous == null) {
            fired = false;
            deadline = System.currentTimeMillis() + timeoutMillis;
            running = interpreter;
        }
        return previous;
    }

    /**
     * Вызов закончился.
     *
     * @return true, если сторожок прервал этот вызов
     */
    boolean exit(Interpreter interpreter, Interpreter previous) {
        if (!enabled()) return false;
        if (previous != null) return false; // вложенный вызов, следит внешний
        running = null;
        boolean wasFired = fired;
        fired = false;
        if (wasFired) {
            // Отмена остаётся в силе до clearCancel(): экран должен работать дальше
            interpreter.clearCancel();
        }
        return wasFired;
    }

    long timeoutMillis() {
        return timeoutMillis;
    }

    private void loop() {
        while (thread != null && plugin.isEnabled()) {
            try {
                Thread.sleep(Math.max(10L, Math.min(50L, timeoutMillis / 4)));
            } catch (InterruptedException e) {
                return;
            }
            Interpreter current = running;
            if (current != null && !fired && System.currentTimeMillis() > deadline) {
                fired = true;
                current.cancel();
            }
        }
    }
}

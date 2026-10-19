package padej.displayLib.particles;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Поток частиц: ведёт запуски, которым не нужен мир, - частицы-пакеты без коллизий ({@link ParticleEmission#async}).
 * Их физика, кадры вида и все пакеты считаются здесь; основной поток только создаёт частицы (сущность вне мира
 * и её вид) и считает зрителей (игроки и миры доступны только ему).
 *
 * <p>Тик потока запускает основной поток в конце своего тика частиц - поток идёт с сервером шаг в шаг, анимация
 * у клиента та же. Если прошлый тик потока ещё не закончился (перегрузка), этот пропускается: частицы
 * «стареют» медленнее, как и при лаге сервера.</p>
 */
final class ParticleWorker {
    private final ParticleManager manager;
    private final PacketBackend backend;
    private final Logger log;
    private final ExecutorService exec;
    /** Запуски потока; трогает только поток частиц (или основной, когда поток стоит) */
    private final List<ParticleEmission> owned = new ArrayList<>();
    private final ConcurrentLinkedQueue<ParticleEmission> added = new ConcurrentLinkedQueue<>();
    /** Запуски, которые поток ещё не отпустил */
    private final AtomicInteger count = new AtomicInteger();
    private Future<?> running;
    private long lagWarned;
    /** Поток остановлен (или завис при остановке) - новых тиков нет */
    private boolean stopped;
    /** Тик потока не закончился за отведённое время: трогать его запуски из основного потока нельзя */
    private boolean stuck;

    ParticleWorker(ParticleManager manager, PacketBackend backend, Logger log) {
        this.manager = manager;
        this.backend = backend;
        this.log = log;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(() -> {
                ThreadLocalRandom.current(); // своё зерно потока (wander берёт генератор основного потока)
                r.run();
            }, "DisplayLib Particles");
            t.setDaemon(true);
            return t;
        });
    }

    /** Основной поток: запуск переходит потоку частиц. */
    void add(ParticleEmission emission) {
        count.incrementAndGet();
        added.offer(emission);
    }

    /** Нет ни одного запуска и неотправленных пакетов (можно остановить задачу частиц). */
    boolean isEmpty() {
        return count.get() == 0 && (running == null || running.isDone()) && !backend.hasWorkerPackets();
    }

    /** Основной поток, конец тика частиц: тик потока частиц. */
    void tick(int now) {
        if (stopped) return;
        if (running != null && !running.isDone()) {
            long t = System.currentTimeMillis();
            if (t - lagWarned > 60_000) {
                lagWarned = t;
                log.warning("Particles: the particle thread is behind the server (a tick took longer than 50 ms); particles are slowed down");
            }
            return;
        }
        int stamp = backend.forgetStamp();
        running = exec.submit(() -> run(now, stamp));
    }

    private void run(int now, int stamp) {
        for (ParticleEmission e; (e = added.poll()) != null; ) owned.add(e);
        for (Iterator<ParticleEmission> it = owned.iterator(); it.hasNext(); ) {
            ParticleEmission e = it.next();
            try {
                manager.tickAsync(e, now, backend);
            } catch (Throwable ex) {
                manager.asyncFailed(e, ex);
            }
            if (e.closed || (e.cancelled && e.particles.isEmpty() && e.incoming.isEmpty())) {
                try {
                    backend.workerFinish(e);
                } catch (Throwable ignored) {
                    // убрать не удалось - клиенты забудут частицы при перезаходе
                }
                it.remove();
                count.decrementAndGet();
            }
        }
        try {
            backend.workerFlush(owned, stamp);
        } catch (Throwable ex) {
            manager.asyncFailed(null, ex);
        }
    }

    /** Основной поток: отправить пакеты, подготовленные прошлым тиком потока. */
    void sendReady() {
        backend.sendWorkerPackets();
    }

    /** Дождаться текущего тика потока; не дождались - {@link #stuck}. */
    private void await() {
        Future<?> f = running;
        if (f == null) return;
        try {
            f.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException ex) {
            stuck = true;
            log.warning("Particles: the particle thread did not finish its tick in 5 s; its particles are left as they are");
        } catch (Exception ignored) {
            // тик упал - поток свободен
        }
    }

    /**
     * Остановить поток. {@code finalTick}: ещё один тик прямо в основном потоке - отменённые запуски убирают
     * свои частицы у клиентов (выключение плагина).
     */
    void stop(int now, boolean finalTick) {
        stopped = true;
        await();
        if (finalTick && !stuck) {
            try {
                run(now, backend.forgetStamp());
                backend.sendWorkerPackets();
            } catch (Throwable ignored) {
                // выключение - не мешаем остальному
            }
        }
        exec.shutdownNow();
    }

    /** Основной поток, поток стоит: все запуски потока (для отката на сущности). */
    List<ParticleEmission> drainAll() {
        stopped = true;
        await();
        if (stuck) return List.of();
        for (ParticleEmission e; (e = added.poll()) != null; ) owned.add(e);
        List<ParticleEmission> out = new ArrayList<>(owned);
        owned.clear();
        count.set(0);
        return out;
    }
}

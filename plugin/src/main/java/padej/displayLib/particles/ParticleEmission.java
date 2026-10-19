package padej.displayLib.particles;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Один запуск эффекта: всплеск или испускание в течение {@code emitter.duration} тиков.
 * Возвращается из {@link Particles#play}; все методы - из основного потока.
 */
public final class ParticleEmission {
    final ParticleEffect effect;
    final Location center;
    /** Для viewers = OWNER; null - видят все */
    final UUID owner;
    /** Сколько тиков ещё испускать (0 - всплеск уже выпущен или испускание закончено) */
    int ticksLeft;
    /** Накопленная дробная часть rate */
    double carry;
    boolean stopped;
    /**
     * Живые частицы. У запуска в потоке частиц ({@link #async}) список принадлежит потоку частиц: основной поток
     * кладёт новые частицы в {@link #incoming}, а живых считает по {@link #asyncAlive}.
     */
    final List<LiveParticle> particles = new ArrayList<>();

    /** Частицы этого запуска движутся в потоке частиц (пакеты, без коллизий); при откате на сущности - false */
    boolean async;
    /** Созданные основным потоком, ещё не взятые потоком частиц */
    final ConcurrentLinkedQueue<LiveParticle> incoming = new ConcurrentLinkedQueue<>();
    /** Живые частицы async-запуска: + при создании (основной поток), - при смерти (поток частиц) */
    final AtomicInteger asyncAlive = new AtomicInteger();
    /** Отменён: поток частиц убирает все его частицы */
    volatile boolean cancelled;
    /** Основной поток больше не ведёт запуск: поток частиц может его забыть */
    volatile boolean closed;
    /** Зрители запуска у {@link PacketBackend} (пишет основной поток) и те, что видел поток частиц */
    volatile Object audience;
    Object workerAudience;

    ParticleEmission(ParticleEffect effect, Location center, UUID owner) {
        this(effect, center, owner, false);
    }

    ParticleEmission(ParticleEffect effect, Location center, UUID owner, boolean async) {
        this.async = async;
        this.effect = effect;
        this.center = center.clone();
        this.owner = owner;
        int d = effect.emitter().duration();
        this.ticksLeft = d == ParticleEffect.Emitter.FOREVER ? Integer.MAX_VALUE : d;
    }

    public ParticleEffect getEffect() {
        return effect;
    }

    public Location getCenter() {
        return center.clone();
    }

    /** Перенести точку испускания (для эффекта, который следует за чем-то). Уже выпущенные частицы не двигаются. */
    public void setCenter(Location location) {
        center.setWorld(location.getWorld());
        center.setX(location.getX());
        center.setY(location.getY());
        center.setZ(location.getZ());
    }

    /** Больше не испускать; уже выпущенные частицы доживают своё. */
    public void stop() {
        stopped = true;
        ticksLeft = 0;
    }

    /** Остановить и убрать все частицы этого запуска (частицы потока частиц - на его ближайшем тике). */
    public void cancel() {
        stop();
        if (async) cancelled = true; // частицы уберёт поток частиц на своём тике
        else for (LiveParticle p : particles) p.kill();
    }

    /** Сколько частиц этого запуска ещё живо. */
    public int alive() {
        if (async) return asyncAlive.get();
        int n = 0;
        for (LiveParticle p : particles) if (!p.dead) n++;
        return n;
    }

    /** Испускание закончено и все частицы исчезли. */
    public boolean isDone() {
        return ticksLeft <= 0 && alive() == 0;
    }
}

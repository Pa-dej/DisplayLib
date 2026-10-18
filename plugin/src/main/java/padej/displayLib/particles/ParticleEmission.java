package padej.displayLib.particles;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    final List<LiveParticle> particles = new ArrayList<>();

    ParticleEmission(ParticleEffect effect, Location center, UUID owner) {
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

    /** Остановить и сразу убрать все частицы этого запуска. */
    public void cancel() {
        stop();
        for (LiveParticle p : particles) p.kill();
    }

    /** Сколько частиц этого запуска ещё живо. */
    public int alive() {
        int n = 0;
        for (LiveParticle p : particles) if (!p.dead) n++;
        return n;
    }

    /** Испускание закончено и все частицы исчезли. */
    public boolean isDone() {
        return ticksLeft <= 0 && alive() == 0;
    }
}

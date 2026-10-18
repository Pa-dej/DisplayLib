package padej.displayLib.particles;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Частицы - настоящие Display-сущности в мире: сервер хранит их в чанках и рассылает своим трекером.
 * Запасной способ, если пакеты недоступны (другая версия сервера) или выключены в config.jmc.
 */
final class EntityBackend implements ParticleBackend {
    private final Plugin plugin;

    EntityBackend(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "entities";
    }

    @Override
    public <T extends Display> ParticleView spawn(ParticleEmission emission, Player owner, Location at, Class<T> type, Consumer<T> setup) {
        T entity = at.getWorld().spawn(at, type, d -> {
            d.setPersistent(false);
            if (owner != null) d.setVisibleByDefault(false);
            setup.accept(d);
        });
        if (owner != null) owner.showEntity(plugin, entity);
        return new View(entity);
    }

    @Override
    public void rejoin(Player player, ParticleEmission emission) {
        // новый объект игрока не видит скрытые от остальных сущности
        for (LiveParticle p : emission.particles) {
            if (!p.dead && p.view instanceof View v && v.entity.isValid()) player.showEntity(plugin, v.entity);
        }
    }

    private record View(Display entity) implements ParticleView {
        @Override
        public Display display() {
            return entity;
        }

        @Override
        public void moveTo(double x, double y, double z, float yaw) {
            entity.teleport(new Location(entity.getWorld(), x, y, z, yaw, 0));
        }

        @Override
        public void flush() {
            // сущность в мире - изменения рассылает трекер сервера
        }

        @Override
        public boolean valid() {
            return entity.isValid();
        }

        @Override
        public void remove() {
            try {
                entity.remove();
            } catch (RuntimeException ignored) {
                // мир уже выгружен - сущности и так нет
            }
        }
    }
}

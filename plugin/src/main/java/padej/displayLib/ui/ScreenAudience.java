package padej.displayLib.ui;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Кому показывать сущности экрана, когда они идут пакетами ({@link ScreenDisplays}).
 *
 * <ul>
 * <li>{@link #player} - приватный экран: только владелец (и только пока он в мире экрана). Другие игроки
 * сущностей не видят вообще.</li>
 * <li>{@link #area} - публичный экран: все игроки мира в пределах {@code radius} от экрана; ушедшие дальше
 * {@code radius + 16} перестают его видеть (запас, чтобы на границе не мигало).</li>
 * </ul>
 *
 * <p>Только основной поток.</p>
 */
public final class ScreenAudience {
    /** Как часто пересчитывать зрителей, тики */
    static final int REFRESH_TICKS = 10;
    private static final double LEAVE_MARGIN = 16;

    private final UUID owner;
    private final World world;
    private final double cx, cy, cz;
    private final double radius;

    /** Текущие зрители */
    final Set<ServerPlayer> viewers = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Растёт при каждой смене зрителей - сущности сверяют своих зрителей только тогда */
    int version;
    private int refreshedAt;
    /** Пересчитать на ближайшем тике, не дожидаясь {@link #REFRESH_TICKS} */
    private boolean stale = true;

    private ScreenAudience(UUID owner, World world, double cx, double cy, double cz, double radius) {
        this.owner = owner;
        this.world = world;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
    }

    /** Приватный экран в мире {@code world}: видит только {@code owner}. */
    public static ScreenAudience player(Player owner, World world) {
        return new ScreenAudience(owner.getUniqueId(), world, 0, 0, 0, 0);
    }

    /** Публичный экран: игроки в радиусе {@code radius} блоков от {@code center}. */
    public static ScreenAudience area(Location center, double radius) {
        return new ScreenAudience(null, center.getWorld(), center.getX(), center.getY(), center.getZ(), radius);
    }

    /** Публичный экран с радиусом видимости из конфига. */
    public static ScreenAudience area(Location center) {
        return area(center, ScreenDisplays.viewDistance());
    }

    public boolean isPrivate() {
        return owner != null;
    }

    /** Пересчитать зрителей (раз в {@link #REFRESH_TICKS} тиков или сразу при {@code force}). */
    void refresh(int tick, boolean force) {
        if (!stale && (refreshedAt == tick || (!force && tick - refreshedAt < REFRESH_TICKS))) return;
        stale = false;
        refreshedAt = tick;
        List<ServerPlayer> want = new ArrayList<>(4);
        if (owner != null) {
            Player p = Bukkit.getPlayer(owner);
            if (p != null && p.getWorld() == world) want.add(((CraftPlayer) p).getHandle());
        } else {
            double in = radius * radius;
            double out = (radius + LEAVE_MARGIN) * (radius + LEAVE_MARGIN);
            for (Player p : world.getPlayers()) {
                ServerPlayer sp = ((CraftPlayer) p).getHandle();
                double dx = sp.getX() - cx, dy = sp.getY() - cy, dz = sp.getZ() - cz;
                double d = dx * dx + dy * dy + dz * dz;
                if (d <= (viewers.contains(sp) ? out : in)) want.add(sp);
            }
        }
        if (want.size() == viewers.size() && viewers.containsAll(want)) return;
        viewers.clear();
        viewers.addAll(want);
        version++;
    }

    /** Игрок перезашёл в мир / вышел: при следующем тике зрители пересчитываются заново. */
    void forget(ServerPlayer p) {
        if (viewers.remove(p)) version++;
        stale = true;
    }
}

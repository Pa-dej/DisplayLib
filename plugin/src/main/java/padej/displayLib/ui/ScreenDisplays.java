package padej.displayLib.ui;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import padej.displayLib.utils.EasedAnimation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Сущности экранов пакетами.
 *
 * <p>Виджет создаёт сущность вне мира ({@link World#createEntity}) и работает с ней как раньше - сеттеры Bukkit,
 * анимации наведения, morph; на сервере её нет (ни в чанке, ни в трекере). В конце каждого тика
 * ({@link ServerTickEndEvent}) изменения её метаданных и позиции уходят пакетами зрителям её
 * {@link ScreenAudience}: приватный экран видит только владелец, публичный - игроки рядом.</p>
 *
 * <p><b>Наведение на публичном экране.</b> У каждого зрителя может быть свой «слой» ({@link Overlay}) - копия
 * сущности, тоже вне мира. Анимация наведения пишет в слой; клиент этого игрока получает метаданные слоя под
 * id общей сущности, остальные видят общую. Поля, которые слой менял (текст, трансформация, свечение...), у
 * игрока берутся из слоя, остальные изменения общей сущности (скрипт сменил текст, кадры) доходят как обычно.
 * Когда игрок увёл взгляд и обратная анимация доиграла, слой снимается и игрок снова получает общие значения.</p>
 *
 * <p>Если пакеты выключены ({@code screens.packets: false}) или сервер другой версии, сущности экранов -
 * обычные сущности мира, как раньше; тогда наведение на публичных экранах не показывается.</p>
 *
 * <p>Только основной поток.</p>
 */
public final class ScreenDisplays implements Listener {
    private static final int BUNDLE_MAX = 4000;
    static final double DEFAULT_VIEW_DISTANCE = 64;
    /** Слой снимается через столько тиков тишины после ухода взгляда */
    private static final int RELEASE_QUIET_TICKS = 2;

    private static ScreenDisplays instance;
    private static double viewDistance = DEFAULT_VIEW_DISTANCE;

    private final Plugin plugin;
    /** Сущности по id */
    private final Map<Integer, Tracked> tracked = new HashMap<>();
    private final List<Tracked> order = new ArrayList<>();
    private final Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> out = new IdentityHashMap<>();
    private final Map<ServerPlayer, IntArrayList> removed = new IdentityHashMap<>();
    private int tick;

    private ScreenDisplays(Plugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Включение
    // -------------------------------------------------------------------------

    /**
     * Включение плагина.
     *
     * @param packets      сущности экранов пакетами; {@code false} - обычные сущности мира
     * @param viewDistance с какого расстояния видны публичные экраны (только для пакетов)
     */
    public static void install(Plugin plugin, boolean packets, double viewDistance) {
        shutdown();
        ScreenDisplays.viewDistance = viewDistance > 0 ? viewDistance : DEFAULT_VIEW_DISTANCE;
        if (!packets) return;
        try {
            probe();
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Screens: packets are not supported on this server version ("
                    + e + "), screens use regular entities", e);
            return;
        }
        ScreenDisplays s = new ScreenDisplays(plugin);
        Bukkit.getPluginManager().registerEvents(s, plugin);
        instance = s;
    }

    /** Выключение: все сущности убираются у клиентов. */
    public static void shutdown() {
        ScreenDisplays s = instance;
        instance = null;
        if (s == null) return;
        HandlerList.unregisterAll(s);
        for (Tracked t : s.order) {
            for (ServerPlayer p : t.shown) s.queueRemove(p, t.handle.getId());
            t.dropOverlays();
        }
        s.tracked.clear();
        s.order.clear();
        s.send();
    }

    /** Сущности экранов идут пакетами (иначе - обычные сущности мира). */
    public static boolean packets() {
        return instance != null;
    }

    /** Свой вид наведения у каждого зрителя публичного экрана (только с пакетами). */
    public static boolean perViewerHover() {
        return instance != null;
    }

    /** Радиус видимости публичных экранов. */
    public static double viewDistance() {
        return viewDistance;
    }

    /** Собрать все виды пакетов (без отправки): несовместимость версии всплывёт при включении. */
    private static void probe() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) return;
        TextDisplay d = world.createEntity(new Location(world, 0, 0, 0), TextDisplay.class);
        net.minecraft.world.entity.Display h = handle(d);
        addPacket(h);
        h.getEntityData().getNonDefaultValues();
        new ClientboundSetEntityDataPacket(h.getId(), List.of());
        syncPacket(h);
        new ClientboundRemoveEntitiesPacket(new IntArrayList(new int[]{h.getId()}));
        new ClientboundBundlePacket(List.of());
        h.getEntityData().packDirty();
        h.getEntityData().packAll();
        h.getEntityData().assignValues(List.of());
    }

    // -------------------------------------------------------------------------
    // API виджетов
    // -------------------------------------------------------------------------

    /**
     * Создать сущность экрана. С пакетами - сущность вне мира, которую увидят зрители {@code audience}
     * (первый раз - в конце тика, уже со всеми настройками, сделанными сразу после создания); без пакетов -
     * обычная сущность мира.
     */
    public static <T extends Display> T spawn(Location at, Class<T> type, ScreenAudience audience) {
        ScreenDisplays s = instance;
        if (s == null || audience == null) return at.getWorld().spawn(at, type);
        T display = at.getWorld().createEntity(at, type);
        net.minecraft.world.entity.Display h = handle(display);
        Tracked t = new Tracked(h, display, audience);
        s.tracked.put(h.getId(), t);
        s.order.add(t);
        return display;
    }

    /** Сущность жива: не удалена (для сущностей вне мира {@code isValid()} всегда {@code false}). */
    public static boolean alive(org.bukkit.entity.Entity e) {
        return e != null && !e.isDead();
    }

    /** Переместить сущность экрана (клиенты получат новую позицию с её teleportDuration). */
    public static void teleport(Display display, Location to) {
        if (display == null) return;
        Tracked t = find(display);
        if (t == null) {
            display.teleport(to);
            return;
        }
        t.handle.setPos(to.getX(), to.getY(), to.getZ());
        display.setRotation(to.getYaw(), to.getPitch());
    }

    /**
     * Слой наведения игрока {@code player} над общей сущностью {@code base} (см. описание класса).
     * {@code create = false} - только существующий. {@code null} - сущность не пакетная или игрок её не видит.
     */
    public static Display overlay(Display base, Player player, boolean create) {
        Tracked t = find(base);
        if (t == null || player == null) return null;
        ServerPlayer sp = ((CraftPlayer) player).getHandle();
        Overlay o = t.overlays.get(sp);
        if (o == null) {
            if (!create || !t.shown.contains(sp)) return null;
            o = t.newOverlay();
            t.overlays.put(sp, o);
        }
        o.released = false;
        o.quiet = 0;
        return o.bukkit;
    }

    /** Существующий слой игрока с этим UUID или {@code null}. */
    public static Display overlayOf(Display base, UUID player) {
        Tracked t = find(base);
        if (t == null || t.overlays.isEmpty()) return null;
        for (Map.Entry<ServerPlayer, Overlay> e : t.overlays.entrySet()) {
            if (e.getKey().getUUID().equals(player)) return e.getValue().bukkit;
        }
        return null;
    }

    /** Игрок увёл взгляд: слой снимется, когда его анимация доиграет. */
    public static void release(Display base, Player player) {
        Tracked t = find(base);
        if (t == null || player == null) return;
        Overlay o = t.overlays.get(((CraftPlayer) player).getHandle());
        if (o != null) o.released = true;
    }

    private static Tracked find(Display display) {
        ScreenDisplays s = instance;
        if (s == null || display == null) return null;
        Tracked t = s.tracked.get(display.getEntityId());
        return t != null && t.bukkit == display ? t : null;
    }

    private static net.minecraft.world.entity.Display handle(Display d) {
        return (net.minecraft.world.entity.Display) ((CraftEntity) d).getHandle();
    }

    // -------------------------------------------------------------------------
    // Тик
    // -------------------------------------------------------------------------

    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        tick++;
        if (order.isEmpty()) return;
        try {
            for (Iterator<Tracked> it = order.iterator(); it.hasNext(); ) {
                Tracked t = it.next();
                if (t.handle.isRemoved()) {
                    for (ServerPlayer p : t.shown) queueRemove(p, t.handle.getId());
                    t.dropOverlays();
                    t.shown.clear();
                    tracked.remove(t.handle.getId());
                    it.remove();
                    continue;
                }
                t.audience.refresh(tick, false);
                sync(t);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Screens: packet sync failed", e);
        }
        send();
    }

    private void sync(Tracked t) {
        net.minecraft.world.entity.Display h = t.handle;
        int id = h.getId();
        List<SynchedEntityData.DataValue<?>> dirty = h.getEntityData().packDirty();
        boolean moved = h.getX() != t.x || h.getY() != t.y || h.getZ() != t.z || h.getYRot() != t.yaw || h.getXRot() != t.pitch;
        Packet<? super ClientGamePacketListener> move = null;
        if (moved) {
            t.x = h.getX(); t.y = h.getY(); t.z = h.getZ(); t.yaw = h.getYRot(); t.pitch = h.getXRot();
            if (!t.shown.isEmpty()) move = syncPacket(h);
        }

        if (!t.shown.isEmpty()) {
            ClientboundSetEntityDataPacket shared = dirty != null ? new ClientboundSetEntityDataPacket(id, dirty) : null;
            for (ServerPlayer p : t.shown) {
                Overlay o = t.overlays.isEmpty() ? null : t.overlays.get(p);
                if (o == null) {
                    if (shared != null) queue(p, shared);
                } else {
                    syncOverlay(t, p, o, dirty);
                }
                if (move != null) queue(p, move);
            }
        }

        // зрители сменились: ушедшим - удаление, новым - появление с полным видом
        ScreenAudience a = t.audience;
        if (t.seen != a.version) {
            t.seen = a.version;
            for (Iterator<ServerPlayer> it = t.shown.iterator(); it.hasNext(); ) {
                ServerPlayer p = it.next();
                if (a.viewers.contains(p)) continue;
                it.remove();
                queueRemove(p, id);
                Overlay o = t.overlays.remove(p);
                if (o != null) o.drop();
            }
            for (ServerPlayer p : a.viewers) {
                if (!t.shown.add(p)) continue;
                queue(p, addPacket(h));
                List<SynchedEntityData.DataValue<?>> all = h.getEntityData().getNonDefaultValues();
                if (all != null && !all.isEmpty()) queue(p, new ClientboundSetEntityDataPacket(id, all));
            }
        }
    }

    /**
     * Зритель со слоем: общие изменения - кроме полей слоя (они же переносятся в слой, чтобы он оставался
     * копией), затем изменения самого слоя. Слой, у которого доиграла обратная анимация, снимается.
     */
    private void syncOverlay(Tracked t, ServerPlayer p, Overlay o, List<SynchedEntityData.DataValue<?>> dirty) {
        int id = t.handle.getId();
        if (dirty != null) {
            List<SynchedEntityData.DataValue<?>> pass = new ArrayList<>(dirty.size());
            for (SynchedEntityData.DataValue<?> v : dirty) if (!o.owns(v.id())) pass.add(v);
            if (!pass.isEmpty()) {
                o.handle.getEntityData().assignValues(pass);
                queue(p, new ClientboundSetEntityDataPacket(id, pass));
            }
        }
        List<SynchedEntityData.DataValue<?>> own = o.handle.getEntityData().packDirty();
        if (own != null) {
            for (SynchedEntityData.DataValue<?> v : own) o.own(v.id());
            queue(p, new ClientboundSetEntityDataPacket(id, own));
            o.quiet = 0;
        } else if (o.released && !EasedAnimation.isAnimating(o.bukkit) && ++o.quiet >= RELEASE_QUIET_TICKS) {
            // вернуть игроку общие значения полей, которые менял слой
            List<SynchedEntityData.DataValue<?>> back = new ArrayList<>();
            for (SynchedEntityData.DataValue<?> v : t.handle.getEntityData().packAll()) if (o.owns(v.id())) back.add(v);
            if (!back.isEmpty()) queue(p, new ClientboundSetEntityDataPacket(id, back));
            t.overlays.remove(p);
            o.drop();
        }
    }

    // -------------------------------------------------------------------------
    // Пакеты
    // -------------------------------------------------------------------------

    private static ClientboundAddEntityPacket addPacket(net.minecraft.world.entity.Display h) {
        // точные координаты (конструктор с BlockPos округлил бы их до блока)
        return new ClientboundAddEntityPacket(h.getId(), h.getUUID(), h.getX(), h.getY(), h.getZ(),
                h.getXRot(), h.getYRot(), h.getType(), 0, Vec3.ZERO, h.getYRot());
    }

    private static ClientboundEntityPositionSyncPacket syncPacket(net.minecraft.world.entity.Display h) {
        return new ClientboundEntityPositionSyncPacket(h.getId(),
                new PositionMoveRotation(new Vec3(h.getX(), h.getY(), h.getZ()), Vec3.ZERO, h.getYRot(), h.getXRot()), false);
    }

    private void queue(ServerPlayer p, Packet<? super ClientGamePacketListener> packet) {
        out.computeIfAbsent(p, k -> new ArrayList<>()).add(packet);
    }

    private void queueRemove(ServerPlayer p, int id) {
        removed.computeIfAbsent(p, k -> new IntArrayList()).add(id);
    }

    /** Каждому игроку - одна связка за тик; удаления - первыми одним пакетом. */
    private void send() {
        for (Map.Entry<ServerPlayer, IntArrayList> e : removed.entrySet()) {
            out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(0, new ClientboundRemoveEntitiesPacket(e.getValue()));
        }
        removed.clear();
        if (out.isEmpty()) return;
        for (Map.Entry<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> e : out.entrySet()) {
            ServerPlayer p = e.getKey();
            List<Packet<? super ClientGamePacketListener>> list = e.getValue();
            if (p.hasDisconnected() || list.isEmpty()) continue;
            if (list.size() == 1) {
                p.connection.send(list.get(0));
                continue;
            }
            for (int i = 0; i < list.size(); i += BUNDLE_MAX) {
                p.connection.send(new ClientboundBundlePacket(new ArrayList<>(list.subList(i, Math.min(list.size(), i + BUNDLE_MAX)))));
            }
        }
        out.clear();
    }

    // -------------------------------------------------------------------------
    // Игроки: после смены мира и возрождения клиент забыл все сущности - покажем заново
    // -------------------------------------------------------------------------

    private void forget(Player player) {
        ServerPlayer sp = ((CraftPlayer) player).getHandle();
        out.remove(sp);
        removed.remove(sp);
        for (Tracked t : order) {
            if (t.shown.remove(sp)) t.seen = -1;
            Overlay o = t.overlays.remove(sp);
            if (o != null) o.drop();
            t.audience.forget(sp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        forget(event.getPlayer());
    }

    // -------------------------------------------------------------------------

    /** Сущность экрана и кто её видит. */
    private static final class Tracked {
        final net.minecraft.world.entity.Display handle;
        final Display bukkit;
        final ScreenAudience audience;
        final Set<ServerPlayer> shown = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<ServerPlayer, Overlay> overlays = new IdentityHashMap<>(2);
        /** Последняя отправленная позиция */
        double x, y, z;
        float yaw, pitch;
        /** Версия зрителей {@link ScreenAudience#version}, с которой сверены {@link #shown} */
        int seen = -1;

        Tracked(net.minecraft.world.entity.Display handle, Display bukkit, ScreenAudience audience) {
            this.handle = handle;
            this.bukkit = bukkit;
            this.audience = audience;
            this.x = handle.getX();
            this.y = handle.getY();
            this.z = handle.getZ();
            this.yaw = handle.getYRot();
            this.pitch = handle.getXRot();
        }

        /** Копия сущности вне мира с теми же метаданными. */
        Overlay newOverlay() {
            Location at = bukkit.getLocation();
            Display copy = at.getWorld().createEntity(at, displayType(bukkit));
            net.minecraft.world.entity.Display h = handle(copy);
            h.getEntityData().packDirty();
            List<SynchedEntityData.DataValue<?>> values = handle.getEntityData().packAll();
            h.getEntityData().assignValues(values);
            return new Overlay(h, copy);
        }

        void dropOverlays() {
            for (Overlay o : overlays.values()) o.drop();
            overlays.clear();
        }
    }

    private static Class<? extends Display> displayType(Display d) {
        if (d instanceof TextDisplay) return TextDisplay.class;
        if (d instanceof org.bukkit.entity.ItemDisplay) return org.bukkit.entity.ItemDisplay.class;
        if (d instanceof org.bukkit.entity.BlockDisplay) return org.bukkit.entity.BlockDisplay.class;
        return TextDisplay.class;
    }

    /** Слой наведения одного зрителя. */
    private static final class Overlay {
        final net.minecraft.world.entity.Display handle;
        final Display bukkit;
        /** Поля метаданных (id), которые менял слой: биты 0..63 */
        long owned;
        /** Взгляд ушёл - снять, когда анимация доиграет */
        boolean released;
        int quiet;

        Overlay(net.minecraft.world.entity.Display handle, Display bukkit) {
            this.handle = handle;
            this.bukkit = bukkit;
        }

        boolean owns(int id) {
            return id < 64 && (owned & (1L << id)) != 0;
        }

        void own(int id) {
            if (id < 64) owned |= 1L << id;
        }

        void drop() {
            EasedAnimation.cancelAll(bukkit);
            handle.discard();
        }
    }
}

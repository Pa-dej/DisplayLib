package padej.displayLib.particles;

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
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Частицы пакетами: на сервере нет сущностей - ни в чанках, ни в трекере, ни в тике мира. Сущность создаётся
 * вне мира ({@link World#createEntity}) только как хранилище вида: сеттеры Bukkit меняют её метаданные, а
 * изменения ({@code packDirty}) уходят зрителям пакетом. Позиция - пакет синхронизации позиции, клиент ведёт
 * Display-сущность к ней за {@code teleportDuration}, как и при телепорте настоящей.
 *
 * <p>Пакеты тика копятся по игрокам и в {@link #flush()} уходят одной связкой (bundle) на игрока: одна отправка
 * в сеть вместо тысяч, клиент применяет связку целиком в одном кадре.</p>
 *
 * <p>Зрители - на каждый запуск: владелец ({@code viewers: "owner"}) или игроки мира не дальше
 * {@code viewDistance} от точки запуска. Список обновляется раз в {@value #REFRESH_TICKS} тиков: новым зрителям
 * приходят все живые частицы запуска в текущем виде, ушедшим - удаление. Пока зрителей нет, новые частицы не
 * появляются (их некому показывать) - как частицы-сущности в незагруженных чанках.</p>
 *
 * <p>Единственный класс плагина, который зависит от внутренних классов сервера (mojang-имена Paper 1.21.11).
 * Если на другой версии их нет, {@link ParticleManager} переходит на {@link EntityBackend}.</p>
 */
final class PacketBackend implements ParticleBackend, Listener {
    static final double DEFAULT_VIEW_DISTANCE = 96;
    /** Как часто пересчитывать зрителей запуска */
    static final int REFRESH_TICKS = 10;
    /** Запас на выход из зоны видимости: на границе частицы не мигают */
    private static final double LEAVE_MARGIN = 16;
    /** Клиент принимает в одной связке не больше 4096 пакетов */
    private static final int BUNDLE_MAX = 4000;

    private final double enter2, leave2;
    private final Map<ParticleEmission, Audience> audiences = new IdentityHashMap<>();
    /** Пакеты этого тика по игрокам - уходят связкой в flush() */
    private final Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> outbox = new IdentityHashMap<>();
    private boolean probed;

    PacketBackend(Plugin plugin, double viewDistance) {
        double d = viewDistance > 0 ? viewDistance : DEFAULT_VIEW_DISTANCE;
        this.enter2 = d * d;
        this.leave2 = (d + LEAVE_MARGIN) * (d + LEAVE_MARGIN);
        try {
            // поля и методы отправки - есть ли они в этой версии сервера
            ServerPlayer.class.getField("connection");
            net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getMethod("send", Packet.class);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex.toString(), ex);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String name() {
        return "packets";
    }

    @Override
    public void shutdown() {
        HandlerList.unregisterAll(this);
        audiences.clear();
        outbox.clear();
    }

    // -------------------------------------------------------------------------
    // Частицы
    // -------------------------------------------------------------------------

    @Override
    public boolean visible(ParticleEmission emission) {
        if (!emission.center.isWorldLoaded()) return false;
        return !audience(emission, emission.center.getWorld()).viewers.isEmpty();
    }

    @Override
    public <T extends Display> ParticleView spawn(ParticleEmission emission, Player owner, Location at, Class<T> type, Consumer<T> setup) {
        T display = at.getWorld().createEntity(at, type);
        setup.accept(display);
        net.minecraft.world.entity.Display handle = (net.minecraft.world.entity.Display) ((CraftEntity) display).getHandle();
        if (!probed) {
            probe(handle);
            probed = true;
        }
        Audience audience = audience(emission, at.getWorld());
        View view = new View(audience, display, handle);
        for (ServerPlayer p : audience.viewers) queueSpawn(p, handle);
        handle.getEntityData().packDirty(); // всё уже в пакетах появления
        return view;
    }

    /** Первая частица: собрать все виды пакетов (без отправки) - несовместимость версии всплывёт здесь, а не в тике. */
    private static void probe(net.minecraft.world.entity.Display handle) {
        addPacket(handle);
        new ClientboundSetEntityDataPacket(handle.getId(), List.of());
        ClientboundEntityPositionSyncPacket.of(handle);
        new ClientboundRemoveEntitiesPacket(new IntArrayList(new int[]{handle.getId()}));
        new ClientboundBundlePacket(List.of());
        handle.snapTo(handle.getX(), handle.getY(), handle.getZ(), handle.getYRot(), 0);
        handle.getEntityData().getNonDefaultValues();
        handle.getEntityData().packDirty();
    }

    private static ClientboundAddEntityPacket addPacket(net.minecraft.world.entity.Display handle) {
        // точные координаты (конструктор с BlockPos округлил бы их до блока)
        return new ClientboundAddEntityPacket(handle.getId(), handle.getUUID(), handle.getX(), handle.getY(), handle.getZ(),
                handle.getXRot(), handle.getYRot(), handle.getType(), 0, Vec3.ZERO, handle.getYHeadRot());
    }

    /** Появление и полный вид - подряд в одной связке тика: клиент не покажет кадр без метаданных. */
    private void queueSpawn(ServerPlayer p, net.minecraft.world.entity.Display handle) {
        queue(p, addPacket(handle));
        List<SynchedEntityData.DataValue<?>> data = handle.getEntityData().getNonDefaultValues();
        if (data != null && !data.isEmpty()) queue(p, new ClientboundSetEntityDataPacket(handle.getId(), data));
    }

    private void queue(ServerPlayer p, Packet<? super ClientGamePacketListener> packet) {
        outbox.computeIfAbsent(p, k -> new ArrayList<>()).add(packet);
    }

    private final class View implements ParticleView {
        final Audience audience;
        final Display display;
        final net.minecraft.world.entity.Display handle;
        boolean removed;

        View(Audience audience, Display display, net.minecraft.world.entity.Display handle) {
            this.audience = audience;
            this.display = display;
            this.handle = handle;
            audience.views.add(this);
        }

        @Override
        public Display display() {
            return display;
        }

        @Override
        public void moveTo(double x, double y, double z, float yaw) {
            handle.snapTo(x, y, z, yaw, 0);
            if (audience.viewers.isEmpty()) return;
            Packet<? super ClientGamePacketListener> packet = ClientboundEntityPositionSyncPacket.of(handle);
            for (ServerPlayer p : audience.viewers) queue(p, packet);
        }

        @Override
        public void flush() {
            List<SynchedEntityData.DataValue<?>> dirty = handle.getEntityData().packDirty();
            if (dirty == null || audience.viewers.isEmpty()) return;
            Packet<? super ClientGamePacketListener> packet = new ClientboundSetEntityDataPacket(handle.getId(), dirty);
            for (ServerPlayer p : audience.viewers) queue(p, packet);
        }

        @Override
        public boolean valid() {
            return !removed;
        }

        @Override
        public void remove() {
            if (removed) return;
            removed = true;
            audience.views.remove(this);
            if (!audience.viewers.isEmpty()) audience.removed.add(handle.getId());
        }
    }

    // -------------------------------------------------------------------------
    // Зрители
    // -------------------------------------------------------------------------

    /** Зрители одного запуска в одном мире и его частицы. */
    private static final class Audience {
        final World world;
        List<ServerPlayer> viewers = new ArrayList<>();
        final Set<View> views = Collections.newSetFromMap(new IdentityHashMap<>());
        /** Удалённые за этот тик (уйдут одним пакетом) */
        final IntArrayList removed = new IntArrayList();
        /** Точка, от которой меряется видимость: центр запуска, пока он в этом мире */
        double ax, ay, az;
        int nextRefresh;

        Audience(World world, Location center) {
            this.world = world;
            this.ax = center.getX();
            this.ay = center.getY();
            this.az = center.getZ();
        }
    }

    /**
     * Зрители запуска для частиц в мире {@code world}. Запуск, перенесённый в другой мир ({@code setCenter}),
     * начинает новых зрителей, а частицы старого мира у клиентов убираются.
     */
    private Audience audience(ParticleEmission emission, World world) {
        Audience a = audiences.get(emission);
        if (a != null && a.world != world) {
            retire(a);
            a = null;
        }
        if (a == null) {
            a = new Audience(world, emission.center);
            audiences.put(emission, a);
            refresh(emission, a, Bukkit.getCurrentTick());
        }
        return a;
    }

    /** Убрать частицы запуска у всех зрителей; сами частицы доживают невидимыми. */
    private void retire(Audience a) {
        for (View v : a.views) if (!v.removed) a.removed.add(v.handle.getId());
        sendRemoved(a);
        a.viewers = new ArrayList<>();
    }

    @Override
    public void tick(ParticleEmission emission, int now) {
        Audience audience = audiences.get(emission);
        if (audience == null) return; // ещё ни одной частицы
        if (now >= audience.nextRefresh) refresh(emission, audience, now);
    }

    @Override
    public void flush() {
        for (Audience a : audiences.values()) sendRemoved(a);
        if (outbox.isEmpty()) return;
        for (Map.Entry<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> entry : outbox.entrySet()) {
            ServerPlayer p = entry.getKey();
            List<Packet<? super ClientGamePacketListener>> packets = entry.getValue();
            if (p.hasDisconnected() || packets.isEmpty()) continue;
            if (packets.size() == 1) {
                p.connection.send(packets.get(0));
                continue;
            }
            for (int i = 0; i < packets.size(); i += BUNDLE_MAX) {
                p.connection.send(new ClientboundBundlePacket(new ArrayList<>(packets.subList(i, Math.min(packets.size(), i + BUNDLE_MAX)))));
            }
        }
        outbox.clear();
    }

    @Override
    public void finish(ParticleEmission emission) {
        Audience a = audiences.remove(emission);
        if (a == null) return;
        for (View v : a.views) if (!v.removed) a.removed.add(v.handle.getId());
        a.views.clear();
        sendRemoved(a);
    }

    @Override
    public void rejoin(Player player, ParticleEmission emission) {
        Audience a = audiences.get(emission);
        if (a != null) a.nextRefresh = 0; // покажется на ближайшем тике
    }

    private void sendRemoved(Audience a) {
        if (a.removed.isEmpty()) return;
        if (!a.viewers.isEmpty()) {
            Packet<? super ClientGamePacketListener> packet = new ClientboundRemoveEntitiesPacket(new IntArrayList(a.removed));
            for (ServerPlayer p : a.viewers) queue(p, packet);
        }
        a.removed.clear();
    }

    /** Пересчитать зрителей: новым - все живые частицы, ушедшим - их удаление. */
    private void refresh(ParticleEmission emission, Audience audience, int now) {
        audience.nextRefresh = now + REFRESH_TICKS;
        List<ServerPlayer> want = wanted(emission, audience);
        List<ServerPlayer> old = audience.viewers;
        if (want.equals(old)) return;
        sendRemoved(audience); // удаления этого тика - ещё старым зрителям
        if (!audience.views.isEmpty()) {
            IntArrayList ids = null;
            for (ServerPlayer p : old) {
                if (want.contains(p)) continue;
                if (ids == null) {
                    ids = new IntArrayList(audience.views.size());
                    for (View v : audience.views) ids.add(v.handle.getId());
                }
                queue(p, new ClientboundRemoveEntitiesPacket(ids));
            }
            for (ServerPlayer p : want) {
                if (old.contains(p)) continue;
                for (View v : audience.views) queueSpawn(p, v.handle);
            }
        }
        audience.viewers = want;
    }

    private List<ServerPlayer> wanted(ParticleEmission emission, Audience audience) {
        List<ServerPlayer> out = new ArrayList<>(2);
        World world = audience.world;
        if (Bukkit.getWorld(world.getUID()) != world) return out; // мир выгружен
        if (emission.owner != null && emission.effect.viewers() == ParticleEffect.Viewers.OWNER) {
            Player p = Bukkit.getPlayer(emission.owner);
            if (p != null && p.getWorld() == world) out.add(((CraftPlayer) p).getHandle());
            return out;
        }
        if (emission.center.isWorldLoaded() && emission.center.getWorld() == world) {
            audience.ax = emission.center.getX();
            audience.ay = emission.center.getY();
            audience.az = emission.center.getZ();
        }
        for (Player p : world.getPlayers()) {
            ServerPlayer sp = ((CraftPlayer) p).getHandle();
            double dx = sp.getX() - audience.ax, dy = sp.getY() - audience.ay, dz = sp.getZ() - audience.az;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= enter2 || (d2 <= leave2 && audience.viewers.contains(sp))) out.add(sp);
        }
        return out;
    }

    /** Игрок больше не видит частицы: при выходе, смене мира и возрождении клиент сам забывает сущности. */
    private void forget(Player player) {
        ServerPlayer sp = ((CraftPlayer) player).getHandle();
        outbox.remove(sp);
        for (Audience a : audiences.values()) {
            if (a.viewers.remove(sp)) a.nextRefresh = 0;
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
}

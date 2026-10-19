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
import net.minecraft.world.entity.PositionMoveRotation;
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
import java.util.concurrent.ConcurrentLinkedQueue;
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
    /** Зрители запусков; только основной поток */
    private final Map<ParticleEmission, Audience> audiences = new IdentityHashMap<>();
    /** Пакеты тика: обычных запусков (основной поток) и запусков потока частиц */
    private final Outbox mainOut = new Outbox(), workerOut = new Outbox();
    /**
     * Готовые пакеты потока частиц - отправит основной поток в начале следующего тика частиц. Отправленные
     * из другого потока пакеты сервер сбрасывает в сеть вместе со своими, то в этом тике, то в следующем: у
     * клиента кадры шли рывками (0 / 100 мс вместо 50). Из основного потока - ровно раз в тик.
     */
    private final List<WorkerBatch> workerReady = new ArrayList<>();
    private final Object workerReadyLock = new Object();
    /**
     * Основной поток: когда игрок последний раз «забыл» сущности (выход, смена мира, возрождение) - номер по
     * {@link #forgetSeq}. Пакеты потока частиц, собранные до этого, ему уже не отправляются: иначе частица
     * появилась бы у клиента в новом мире и никогда не была бы убрана.
     */
    private final Map<ServerPlayer, Integer> forgotAt = new java.util.WeakHashMap<>();
    private int forgetSeq;

    /** Пакеты одного тика потока частиц и номер «забываний», при котором этот тик начался. */
    private record WorkerBatch(int stamp, Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> packets) {}
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
        mainOut.packets.clear();
    }

    // -------------------------------------------------------------------------
    // Частицы
    // -------------------------------------------------------------------------

    @Override
    public boolean visible(ParticleEmission emission) {
        if (!emission.center.isWorldLoaded()) return false;
        Audience a = audience(emission, emission.center.getWorld());
        return a.async ? a.hasViewers : !a.viewers.isEmpty();
    }

    /**
     * Частица запуска в потоке частиц ({@link ParticleEmission#async}) создаётся здесь, в основном потоке, но
     * показывается зрителям уже в потоке частиц ({@link #attach}): все пакеты такого запуска шлёт один поток,
     * и порядок «появление → движение → удаление» у каждого зрителя не нарушается.
     */
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
        if (!audience.async) attach(view);
        return view;
    }

    /** Показать частицу зрителям её запуска - в потоке, который ведёт этот запуск. */
    void attach(ParticleView particle) {
        View view = (View) particle;
        Audience a = view.audience;
        a.views.add(view);
        for (ServerPlayer p : a.viewers) queueSpawn(p, view);
        view.handle.getEntityData().packDirty(); // всё уже в пакетах появления
    }

    /** Первая частица: собрать все виды пакетов (без отправки) - несовместимость версии всплывёт здесь, а не в тике. */
    private static void probe(net.minecraft.world.entity.Display handle) {
        addPacket(handle, handle.getX(), handle.getY(), handle.getZ(), handle.getYRot());
        new ClientboundSetEntityDataPacket(handle.getId(), List.of());
        syncPacket(handle.getId(), 0, 0, 0, 0);
        new ClientboundRemoveEntitiesPacket(new IntArrayList(new int[]{handle.getId()}));
        new ClientboundBundlePacket(List.of());
        handle.getEntityData().getNonDefaultValues();
        handle.getEntityData().packDirty();
    }

    private static ClientboundAddEntityPacket addPacket(net.minecraft.world.entity.Display handle, double x, double y, double z, float yaw) {
        // точные координаты (конструктор с BlockPos округлил бы их до блока)
        return new ClientboundAddEntityPacket(handle.getId(), handle.getUUID(), x, y, z, 0, yaw, handle.getType(), 0, Vec3.ZERO, yaw);
    }

    /** Позиция у клиента: сущность вне мира не двигаем (snapTo пересчитывает чанк, рамку и т.п. - лишняя работа). */
    private static ClientboundEntityPositionSyncPacket syncPacket(int id, double x, double y, double z, float yaw) {
        return new ClientboundEntityPositionSyncPacket(id, new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, yaw, 0), false);
    }

    /** Появление и полный вид - подряд в одной связке тика: клиент не покажет кадр без метаданных. */
    private static void queueSpawn(ServerPlayer p, View v) {
        net.minecraft.world.entity.Display handle = v.handle;
        Outbox out = v.audience.out;
        out.queue(p, addPacket(handle, v.x, v.y, v.z, v.yaw));
        List<SynchedEntityData.DataValue<?>> data = handle.getEntityData().getNonDefaultValues();
        if (data != null && !data.isEmpty()) out.queue(p, new ClientboundSetEntityDataPacket(handle.getId(), data));
    }

    /** Пакеты тика по игрокам; у каждого потока свой. Отправляет всегда основной поток. */
    private static final class Outbox {
        Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> packets = new IdentityHashMap<>();

        void queue(ServerPlayer p, Packet<? super ClientGamePacketListener> packet) {
            packets.computeIfAbsent(p, k -> new ArrayList<>()).add(packet);
        }

        /** Забрать накопленное (поток частиц отдаёт его основному). */
        Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> take() {
            Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> out = packets;
            packets = new IdentityHashMap<>();
            return out;
        }

        void flush() {
            send(take());
        }

        /** Каждому игроку - одна связка. */
        static void send(Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> packets) {
            if (packets == null || packets.isEmpty()) return;
            for (Map.Entry<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> entry : packets.entrySet()) {
                ServerPlayer p = entry.getKey();
                List<Packet<? super ClientGamePacketListener>> list = entry.getValue();
                if (p.hasDisconnected() || list.isEmpty()) continue;
                if (list.size() == 1) {
                    p.connection.send(list.get(0));
                    continue;
                }
                for (int i = 0; i < list.size(); i += BUNDLE_MAX) {
                    p.connection.send(new ClientboundBundlePacket(new ArrayList<>(list.subList(i, Math.min(list.size(), i + BUNDLE_MAX)))));
                }
            }
        }
    }

    private static final class View implements ParticleView {
        final Audience audience;
        final Display display;
        final net.minecraft.world.entity.Display handle;
        /** Где частица у клиента (последняя отправленная цель) */
        double x, y, z;
        float yaw;
        boolean removed;

        View(Audience audience, Display display, net.minecraft.world.entity.Display handle) {
            this.audience = audience;
            this.display = display;
            this.handle = handle;
            this.x = handle.getX();
            this.y = handle.getY();
            this.z = handle.getZ();
            this.yaw = handle.getYRot();
        }

        @Override
        public Display display() {
            return display;
        }

        @Override
        public void moveTo(double x, double y, double z, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            if (audience.viewers.isEmpty()) return;
            Packet<? super ClientGamePacketListener> packet = syncPacket(handle.getId(), x, y, z, yaw);
            for (ServerPlayer p : audience.viewers) audience.out.queue(p, packet);
        }

        @Override
        public void flush() {
            List<SynchedEntityData.DataValue<?>> dirty = handle.getEntityData().packDirty();
            if (dirty == null || audience.viewers.isEmpty()) return;
            Packet<? super ClientGamePacketListener> packet = new ClientboundSetEntityDataPacket(handle.getId(), dirty);
            for (ServerPlayer p : audience.viewers) audience.out.queue(p, packet);
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

    /**
     * Зрители одного запуска в одном мире и его частицы. Поля {@code viewers}, {@code views}, {@code removed} -
     * у потока, который ведёт запуск (основной или поток частиц). Кого показывать, всегда считает основной
     * поток (игроки и миры - только там) и для запуска в потоке частиц отдаёт список через {@code want}.
     */
    private static final class Audience {
        final World world;
        final boolean async;
        final Outbox out;
        List<ServerPlayer> viewers = new ArrayList<>();
        final Set<View> views = Collections.newSetFromMap(new IdentityHashMap<>());
        /** Удалённые за этот тик (уйдут одним пакетом) */
        final IntArrayList removed = new IntArrayList();
        /** Основной поток: последний подсчитанный список (для запаса на выход из зоны) */
        List<ServerPlayer> lastWant = new ArrayList<>();
        /** Для потока частиц: кого показывать; есть ли зрители */
        volatile List<ServerPlayer> want = List.of();
        volatile boolean hasViewers;
        /** Для потока частиц: игроки, которые сами забыли сущности (выход, смена мира, возрождение) */
        final ConcurrentLinkedQueue<ServerPlayer> forgotten = new ConcurrentLinkedQueue<>();
        /** Точка, от которой меряется видимость: центр запуска, пока он в этом мире */
        double ax, ay, az;
        int nextRefresh;

        Audience(World world, Location center, boolean async, Outbox out) {
            this.world = world;
            this.async = async;
            this.out = out;
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
            if (!a.async) retire(a); // запуск в потоке частиц уберёт старые сам (workerBegin)
            a = null;
        }
        if (a == null) {
            a = new Audience(world, emission.center, emission.async, emission.async ? workerOut : mainOut);
            audiences.put(emission, a);
            emission.audience = a;
            refresh(emission, a, Bukkit.getCurrentTick());
        }
        return a;
    }

    /** Убрать частицы у всех зрителей; сами частицы доживают невидимыми. */
    private static void retire(Audience a) {
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
        for (Audience a : audiences.values()) if (!a.async) sendRemoved(a);
        mainOut.flush();
    }

    /** Поток частиц подготовил пакеты, которые ещё не отправлены. */
    boolean hasWorkerPackets() {
        synchronized (workerReadyLock) {
            return !workerReady.isEmpty();
        }
    }

    /** Основной поток: номер «забываний» для тика потока частиц, который сейчас начнётся. */
    int forgetStamp() {
        return forgetSeq;
    }

    /** Основной поток: отправить пакеты, которые поток частиц подготовил на прошлом тике. */
    void sendWorkerPackets() {
        List<WorkerBatch> batches;
        synchronized (workerReadyLock) {
            if (workerReady.isEmpty()) return;
            batches = new ArrayList<>(workerReady);
            workerReady.clear();
        }
        for (WorkerBatch batch : batches) {
            if (!forgotAt.isEmpty()) {
                batch.packets().keySet().removeIf(p -> {
                    Integer at = forgotAt.get(p);
                    return at != null && at > batch.stamp();
                });
            }
            Outbox.send(batch.packets());
        }
    }

    @Override
    public void finish(ParticleEmission emission) {
        Audience a = audiences.remove(emission);
        if (a == null || a.async) return; // запуск потока частиц заканчивает сам поток (workerFinish)
        for (View v : a.views) if (!v.removed) a.removed.add(v.handle.getId());
        a.views.clear();
        sendRemoved(a);
    }

    @Override
    public void rejoin(Player player, ParticleEmission emission) {
        Audience a = audiences.get(emission);
        if (a != null) a.nextRefresh = 0; // покажется на ближайшем тике
    }

    private static void sendRemoved(Audience a) {
        if (a.removed.isEmpty()) return;
        if (!a.viewers.isEmpty()) {
            Packet<? super ClientGamePacketListener> packet = new ClientboundRemoveEntitiesPacket(new IntArrayList(a.removed));
            for (ServerPlayer p : a.viewers) a.out.queue(p, packet);
        }
        a.removed.clear();
    }

    /** Основной поток: кого показывать. Обычный запуск применяет сразу, запуск потока частиц - в {@link #workerBegin}. */
    private void refresh(ParticleEmission emission, Audience audience, int now) {
        audience.nextRefresh = now + REFRESH_TICKS;
        List<ServerPlayer> want = wanted(emission, audience);
        audience.lastWant = want;
        if (audience.async) {
            audience.want = List.copyOf(want);
            audience.hasViewers = !want.isEmpty();
            return;
        }
        applyViewers(audience, want);
    }

    /** Новым зрителям - все живые частицы, ушедшим - их удаление (в потоке, который ведёт запуск). */
    private static void applyViewers(Audience audience, List<ServerPlayer> want) {
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
                audience.out.queue(p, new ClientboundRemoveEntitiesPacket(ids));
            }
            for (ServerPlayer p : want) {
                if (old.contains(p)) continue;
                for (View v : audience.views) queueSpawn(p, v);
            }
        }
        audience.viewers = new ArrayList<>(want);
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
            if (d2 <= enter2 || (d2 <= leave2 && audience.lastWant.contains(sp))) out.add(sp);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Поток частиц (ParticleWorker): запуски без коллизий
    // -------------------------------------------------------------------------

    /**
     * Поток частиц, начало запуска в тике: старый мир запуска убрать, забывших игроков вычеркнуть,
     * применить зрителей, которых насчитал основной поток.
     */
    void workerBegin(ParticleEmission emission) {
        Audience a = (Audience) emission.audience;
        Audience prev = (Audience) emission.workerAudience;
        if (prev != null && prev != a) retire(prev);
        emission.workerAudience = a;
        if (a == null) return;
        for (ServerPlayer p; (p = a.forgotten.poll()) != null; ) {
            if (a.viewers.contains(p)) {
                List<ServerPlayer> copy = new ArrayList<>(a.viewers);
                copy.remove(p);
                a.viewers = copy;
            }
        }
        applyViewers(a, a.want);
    }

    /** Поток частиц, конец тика: удаления запусков и все пакеты потока. */
    void workerFlush(List<ParticleEmission> emissions, int stamp) {
        for (ParticleEmission e : emissions) {
            if (e.workerAudience instanceof Audience a) sendRemoved(a);
        }
        Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> ready = workerOut.take();
        if (ready.isEmpty()) return;
        synchronized (workerReadyLock) {
            workerReady.add(new WorkerBatch(stamp, ready));
        }
    }

    /** Поток частиц: запуск закончен - убрать у зрителей всё, что ещё показано. */
    void workerFinish(ParticleEmission emission) {
        if (!(emission.workerAudience instanceof Audience a)) return;
        for (View v : a.views) if (!v.removed) a.removed.add(v.handle.getId());
        a.views.clear();
        sendRemoved(a);
    }

    /** Игрок больше не видит частицы: при выходе, смене мира и возрождении клиент сам забывает сущности. */
    private void forget(Player player) {
        ServerPlayer sp = ((CraftPlayer) player).getHandle();
        mainOut.packets.remove(sp);
        forgotAt.put(sp, ++forgetSeq);
        for (Audience a : audiences.values()) {
            a.lastWant.remove(sp);
            a.nextRefresh = 0;
            if (a.async) a.forgotten.offer(sp);
            else a.viewers.remove(sp);
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

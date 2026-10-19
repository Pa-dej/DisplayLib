package padej.displayLib.particles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Живые частицы и запуски эффектов. Одна задача планировщика на все частицы, работает,
 * только пока есть что обновлять. Все методы - из основного потока.
 */
public final class ParticleManager implements Listener {
    /** Пиксель текста в блоках (клиент рисует шрифт с масштабом 1/40). */
    private static final float TEXT_PIXEL = 1f / 40f;

    private final Plugin plugin;
    private final Logger log;
    private final int maxAlive;
    /** Как частицы показываются: пакетами или сущностями в мире */
    private ParticleBackend backend;
    /** Частицы без коллизий - в отдельном потоке (только пакетами) */
    private final boolean asyncEnabled;
    /** Поток частиц; создаётся при первом таком запуске */
    private ParticleWorker worker;

    private final List<ParticleEmission> emissions = new ArrayList<>();
    private int alive;
    private BukkitTask task;
    private boolean ticking;

    /** Предупреждения, которые уже были (не засорять консоль каждый запуск); пишут оба потока */
    private final Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, BlockData> blockCache = new HashMap<>();
    private final Map<String, Material> materialCache = new HashMap<>();

    /**
     * @param maxAlive     предел одновременно живых частиц на сервере; новые сверх него не появляются
     * @param packets      показывать частицы пакетами, без сущностей на сервере (если версия сервера позволяет)
     * @param viewDistance с какого расстояния от точки запуска игроки видят частицы (только для пакетов)
     * @param async        частицы без коллизий считать в отдельном потоке (только для пакетов)
     */
    public ParticleManager(Plugin plugin, int maxAlive, boolean packets, double viewDistance, boolean async) {
        this.plugin = plugin;
        this.asyncEnabled = async;
        this.log = plugin.getLogger();
        this.maxAlive = Math.max(1, maxAlive);
        this.backend = packets ? packetBackend(plugin, viewDistance) : new EntityBackend(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public ParticleManager(Plugin plugin, int maxAlive, boolean packets, double viewDistance) {
        this(plugin, maxAlive, packets, viewDistance, true);
    }

    public ParticleManager(Plugin plugin, int maxAlive) {
        this(plugin, maxAlive, true, PacketBackend.DEFAULT_VIEW_DISTANCE, true);
    }

    /** Новый запуск: в потоке частиц, если показ пакетами и эффекту не нужен мир (без коллизий). */
    private ParticleEmission newEmission(ParticleEffect effect, Location center, Player owner) {
        boolean async = asyncEnabled && backend instanceof PacketBackend && !effect.motion().collide();
        return new ParticleEmission(effect, center, owner != null ? owner.getUniqueId() : null, async);
    }

    /** Запуск добавлен в список: async - ещё и потоку частиц. */
    private void register(ParticleEmission emission) {
        emissions.add(emission);
        if (emission.async) {
            if (worker == null) worker = new ParticleWorker(this, (PacketBackend) backend, log);
            worker.add(emission);
        }
        ensureTask();
    }

    /** Новая частица запуска: обычная - в список, async - потоку частиц. */
    private void adopt(ParticleEmission e, LiveParticle p) {
        if (e.async) {
            e.asyncAlive.incrementAndGet();
            e.incoming.offer(p);
        } else {
            e.particles.add(p);
        }
        alive++;
    }

    /** Пакеты зависят от внутренних классов сервера: на другой версии - сущности. */
    private static ParticleBackend packetBackend(Plugin plugin, double viewDistance) {
        try {
            return new PacketBackend(plugin, viewDistance);
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("Particles: packets are not available on this server version (" + ex
                    + "), falling back to entities");
            return new EntityBackend(plugin);
        }
    }

    /** Как показываются частицы: {@code packets} или {@code entities}. */
    public String backendName() {
        return backend.name();
    }

    /** Сколько частиц сейчас живо. */
    public int aliveCount() {
        return alive;
    }

    /** Активные запуски (испускают или ещё имеют живые частицы). */
    public int emissionCount() {
        return emissions.size();
    }

    /**
     * Запустить эффект.
     *
     * @param owner игрок, для которого запущен эффект: при {@code viewers = "owner"} частицы видит
     *              только он; {@code null} - видят все
     */
    public ParticleEmission play(ParticleEffect effect, Location center, Player owner) {
        if (center.getWorld() == null) throw new IllegalArgumentException("location has no world");
        ParticleEmission emission = newEmission(effect, center, owner);
        emitSafely(emission, effect.emitter().count());
        if (!emission.isDone()) {
            register(emission);
        } else {
            backend.finish(emission);
        }
        backend.flush();
        return emission;
    }

    /**
     * Запустить эффект с явно заданными частицами (без испускания по {@code emitter}): каждая появляется в
     * {@code center + смещение куска} со своим текстом/размером/поворотом, а дальше живёт по движению и анимации
     * эффекта. Так сделан {@link TextShatter}.
     */
    public ParticleEmission playPieces(ParticleEffect effect, Location center, Player owner, java.util.List<ParticlePiece> pieces) {
        if (center.getWorld() == null) throw new IllegalArgumentException("location has no world");
        ParticleEmission emission = newEmission(effect, center, owner);
        emission.ticksLeft = 0; // только эти куски
        try {
            emitPieces(emission, pieces);
        } catch (RuntimeException ex) {
            warnOnce("spawn:" + effect.id(), "Particle effect '" + effect.id() + "' failed to spawn: " + ex);
            emission.cancel();
        }
        if (!emission.isDone()) {
            register(emission);
        } else {
            backend.finish(emission);
        }
        backend.flush();
        return emission;
    }

    private void emitPieces(ParticleEmission e, java.util.List<ParticlePiece> pieces) {
        World world = e.center.getWorld();
        if (world == null) return;
        Player owner = e.owner != null && e.effect.viewers() == ParticleEffect.Viewers.OWNER ? Bukkit.getPlayer(e.owner) : null;
        if (e.owner != null && e.effect.viewers() == ParticleEffect.Viewers.OWNER && owner == null) return;
        if (!backend.visible(e)) return; // некому показывать
        RandomGenerator r = ThreadLocalRandom.current();
        int now = Bukkit.getCurrentTick();
        for (ParticlePiece piece : pieces) {
            if (alive >= maxAlive) {
                warnOnce("max", "Particle limit reached (" + maxAlive + " alive, particles.maxAlive in config.jmc); extra particles are skipped");
                return;
            }
            LiveParticle p = spawn(e, world, owner, r, now, piece);
            if (p == null) continue;
            adopt(e, p);
        }
    }

    /** Убрать все частицы и остановить все запуски. */
    public void stopAll() {
        for (ParticleEmission e : emissions) {
            e.cancel(); // async: частицы уберёт поток частиц
            if (!ticking && !e.async) e.particles.clear(); // во время обхода списки чистит сам tick()
            backend.finish(e);
        }
        emissions.clear();
        backend.flush();
        alive = 0;
        // поток частиц ещё должен убрать свои частицы у клиентов - задача остановится сама
        if (task != null && (worker == null || worker.isEmpty())) {
            task.cancel();
            task = null;
        }
    }

    // -------------------------------------------------------------------------

    private void ensureTask() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Выключение плагина: убрать всё и отписаться от событий. */
    public void shutdown() {
        stopAll();
        if (worker != null) {
            worker.stop(Bukkit.getCurrentTick(), true);
            worker = null;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        backend.shutdown();
        HandlerList.unregisterAll(this);
    }

    private void tick() {
        ticking = true;
        try {
            tickAll();
        } catch (LinkageError ex) {
            if (backend instanceof EntityBackend) throw ex;
            fallBack(ex);
        } finally {
            ticking = false;
        }
    }

    /**
     * Пакеты несовместимы с версией сервера (внутренний класс или метод изменился): дальше - сущности.
     * Уже выпущенные частицы-пакеты убираются, запуски продолжают испускать новые сущностями.
     */
    private void fallBack(LinkageError ex) {
        log.warning("Particles: packets are not available on this server version (" + ex + "), falling back to entities");
        ParticleBackend old = backend;
        backend = new EntityBackend(plugin);
        if (worker != null) {
            // поток частиц больше не нужен: его запуски гасим здесь (поток стоит)
            for (ParticleEmission e : worker.drainAll()) {
                for (LiveParticle p; (p = e.incoming.poll()) != null; ) e.particles.add(p);
                for (LiveParticle p : e.particles) p.kill();
                e.particles.clear();
                e.asyncAlive.set(0);
                e.stop();
            }
            worker.stop(0, false);
            worker = null;
        }
        try {
            for (ParticleEmission e : emissions) {
                if (!e.async) for (LiveParticle p : e.particles) p.kill();
                old.finish(e);
            }
            old.flush();
        } catch (LinkageError | RuntimeException ignored) {
            // не удалось даже убрать - клиенты забудут их при перезаходе
        }
        old.shutdown();
    }

    private void tickAll() {
        int now = Bukkit.getCurrentTick();
        // пакеты потока частиц с прошлого тика - из основного потока, ровно раз в тик
        if (worker != null) worker.sendReady();
        // Обход по снимку: спавн и удаление сущностей вызывают события, и чужой обработчик
        // может запустить эффект или вызвать stopAll прямо во время обхода
        for (ParticleEmission e : new ArrayList<>(emissions)) {
            try {
                tickEmission(e, now);
            } catch (RuntimeException ex) {
                warnOnce("tick:" + e.effect.id(), "Particle effect '" + e.effect.id() + "' failed and was stopped: " + ex);
                e.cancel();
            }
            if (e.isDone()) {
                if (!e.async) e.particles.clear();
                e.closed = true; // поток частиц отпустит запуск
                emissions.remove(e);
                backend.finish(e);
            }
        }
        backend.flush();
        // поток частиц - после основного: частицы, созданные в этом тике, появятся у клиентов в этом же тике
        if (worker != null) worker.tick(now);
        // счётчик - заново: события и отмены во время обхода не дают ему разойтись с реальностью
        int count = 0;
        for (ParticleEmission e : emissions) count += e.async ? e.asyncAlive.get() : e.particles.size();
        alive = count;
        if (emissions.isEmpty() && (worker == null || worker.isEmpty()) && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickEmission(ParticleEmission e, int now) {
        if (!e.center.isWorldLoaded()) {
            e.cancel(); // мир выгружен
            return;
        }
        backend.tick(e, now);
        if (!e.async) tickParticles(e, now);

        // испускание в течение duration - после обхода: новые частицы получают первый кадр на следующем тике
        if (e.ticksLeft > 0 && !e.stopped) {
            if (e.ticksLeft != Integer.MAX_VALUE) e.ticksLeft--; // duration: -1 - пока не остановят
            e.carry += e.effect.emitter().rate();
            int n = (int) e.carry;
            e.carry -= n;
            emit(e, n);
        }
    }

    /**
     * Поток частиц: тик async-запуска. Новые частицы показываются зрителям сейчас, первый кадр - на следующем
     * тике (как и у обычных). Мир не трогается: коллизий у таких эффектов нет.
     */
    void tickAsync(ParticleEmission e, int now, PacketBackend packets) {
        packets.workerBegin(e);
        for (LiveParticle p; (p = e.incoming.poll()) != null; ) {
            p.spawnTick = Math.max(p.spawnTick, now);
            packets.attach(p.view);
            e.particles.add(p);
        }
        if (e.cancelled) for (LiveParticle p : e.particles) p.kill();
        tickParticles(e, now);
    }

    /** Поток частиц: запуск упал - убрать его частицы. */
    void asyncFailed(ParticleEmission e, Throwable ex) {
        String id = e != null ? e.effect.id() : "?";
        warnOnce("async:" + id, "Particle effect '" + id + "' failed in the particle thread and was stopped: " + ex);
        if (e == null) return;
        e.cancelled = true;
        for (LiveParticle p; (p = e.incoming.poll()) != null; ) e.particles.add(p);
        for (Iterator<LiveParticle> it = e.particles.iterator(); it.hasNext(); ) {
            LiveParticle p = it.next();
            try {
                p.kill();
            } catch (Throwable ignored) {
                // уже не показать и не убрать
            }
            it.remove();
            e.asyncAlive.decrementAndGet();
        }
    }

    /** Возраст, смерть и кадры частиц запуска (основной поток или, для async, поток частиц). */
    private void tickParticles(ParticleEmission e, int now) {
        int keyframeTicks = e.effect.keyframeTicks();
        boolean collide = e.effect.motion().collide() && !e.async;
        ParticleSim.Solid solid = null;
        World solidWorld = null;
        for (Iterator<LiveParticle> pi = e.particles.iterator(); pi.hasNext(); ) {
            LiveParticle p = pi.next();
            if (!p.dead && now > p.spawnTick) {
                p.age++;
                if (!p.view.valid()) {
                    p.kill(); // чанк выгрузился, сущность убрали
                } else if (p.age > p.life) { // клиент отстаёт на тик: даём дойти до конца жизни
                    p.kill();
                } else {
                    if (collide && solidWorld != p.world) { solidWorld = p.world; solid = solidIn(p.world); }
                    try {
                        p.update(keyframeTicks, collide ? solid : null);
                    } catch (RuntimeException ex) {
                        warnOnce("keyframe:" + e.effect.id(), "Particle effect '" + e.effect.id() + "' failed: " + ex);
                        p.kill();
                    }
                }
            }
            if (p.dead) {
                pi.remove();
                if (e.async) e.asyncAlive.decrementAndGet();
            }
        }
    }

    /** Твёрдые блоки мира; незагруженный чанк считается пустым (иначе проверка грузила бы его). */
    private static ParticleSim.Solid solidIn(World world) {
        return (x, y, z) -> world.isChunkLoaded(x >> 4, z >> 4) && world.getBlockAt(x, y, z).getType().isSolid();
    }

    private void emitSafely(ParticleEmission e, int count) {
        try {
            emit(e, count);
        } catch (RuntimeException ex) {
            warnOnce("spawn:" + e.effect.id(), "Particle effect '" + e.effect.id() + "' failed to spawn: " + ex);
            e.cancel();
        }
    }

    private void emit(ParticleEmission e, int count) {
        if (count <= 0) return;
        World world = e.center.getWorld();
        if (world == null) return;
        Player owner = e.owner != null && e.effect.viewers() == ParticleEffect.Viewers.OWNER ? Bukkit.getPlayer(e.owner) : null;
        if (e.owner != null && e.effect.viewers() == ParticleEffect.Viewers.OWNER && owner == null) return; // владелец не в сети
        if (!backend.visible(e)) return; // некому показывать (пакеты: рядом нет игроков)
        RandomGenerator r = ThreadLocalRandom.current();
        int now = Bukkit.getCurrentTick();
        for (int i = 0; i < count; i++) {
            if (alive >= maxAlive) {
                warnOnce("max", "Particle limit reached (" + maxAlive + " alive, particles.maxAlive in config.jmc); extra particles are skipped");
                return;
            }
            LiveParticle p = spawn(e, world, owner, r, now, null);
            if (p == null) return;
            adopt(e, p);
        }
    }

    // -------------------------------------------------------------------------
    // События
    // -------------------------------------------------------------------------

    /** Владелец перезашёл: новый объект игрока не видит скрытые от остальных частицы - показать заново. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (ParticleEmission e : emissions) {
            if (player.getUniqueId().equals(e.owner) && e.effect.viewers() == ParticleEffect.Viewers.OWNER) {
                backend.rejoin(player, e);
            }
        }
    }

    /** Мир выгружается: его частицы убираются сразу. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        for (ParticleEmission e : emissions) {
            if (e.center.isWorldLoaded() && e.center.getWorld() == world) e.cancel();
            else if (!e.async) for (LiveParticle p : e.particles) if (p.world == world) p.kill();
        }
    }

    private LiveParticle spawn(ParticleEmission e, World world, Player owner, RandomGenerator r, int now, ParticlePiece piece) {
        ParticleEffect effect = e.effect;
        double[] off = piece != null ? new double[]{piece.dx(), piece.dy(), piece.dz()} : ParticleSim.spawnOffset(effect.emitter(), r);
        double[] vel = ParticleSim.spawnVelocity(effect.motion(), off, r);
        ParticleSim.Body body = new ParticleSim.Body(e.center.getX() + off[0], e.center.getY() + off[1], e.center.getZ() + off[2],
                vel[0], vel[1], vel[2]);
        if (effect.motion().collide() && solidIn(world).at((int) Math.floor(body.x), (int) Math.floor(body.y), (int) Math.floor(body.z))) {
            body.ghost = true;
        }
        body.cx = e.center.getX(); body.cy = e.center.getY(); body.cz = e.center.getZ();
        if (effect.motion().wander() > 0) {
            double[] w = ParticleSim.randomUnit(r);
            body.wx = w[0]; body.wy = w[1]; body.wz = w[2];
            body.random = r;
        }

        int life = Math.max(1, (int) Math.round(effect.motion().lifetime().sample(r)));
        // первый отрезок движения - до появления: его длина нужна сущности как teleportDuration
        ParticleSim.Segment first = ParticleSim.planSegment(body, effect.motion(),
                effect.motion().collide() ? solidIn(world) : null, effect.keyframeTicks());
        double scale = piece != null && piece.scale() != null ? piece.scale() : effect.animation().scale().sample(r);
        double rotation = effect.animation().rotation().sample(r);
        double spin = effect.animation().spin().sample(r);

        ParticleEffect.Render render = effect.render();
        ParticleEffect.Animation anim = effect.animation();
        float yaw = piece != null ? piece.yaw() : 0;
        Location at = new Location(world, body.x, body.y, body.z, yaw, 0);
        float[] center = {0, 0, 0};
        int startColor = render.colors().isEmpty() ? 0xffffffff : pick(render.colors(), r);
        int color0 = ParticleSim.colorAt(anim, startColor, 0, life);
        Component baseText = null;
        int frameStart = 0;
        ParticleEffect.TextVariant textVariant = null;
        boolean textRecolor = false;
        Consumer<Display> common = d -> {
            d.setBillboard(render.billboard());
            if (render.brightness() != null) d.setBrightness(new Display.Brightness(render.brightness(), render.brightness()));
            d.setTeleportDuration(first.length());
            d.setInterpolationDuration(0);
        };

        ParticleView entity;
        switch (render.type()) {
            case TEXT -> {
                textVariant = piece != null && piece.text() != null ? ParticleEffect.TextVariant.of(piece.text()) : pick(render.texts(), r);
                if (textVariant.animated()) {
                    frameStart = textVariant.randomStart() ? r.nextInt(textVariant.frames().size()) : 0;
                    baseText = textVariant.frames().get(ParticleSim.frameAt(frameStart, textVariant.frames().size(), textVariant.frameInterval(), textVariant.overLife(), 0, life));
                } else {
                    baseText = textVariant.first();
                }
                Component text = baseText;
                // цвет: случайный из render.colors или кривая animation.color; без них - как в самом тексте
                boolean recolor = !render.colors().isEmpty() || anim.color() != null;
                if (recolor) text = text.color(TextColor.color(color0 & 0xffffff));
                else baseText = null;
                textRecolor = recolor;
                boolean objectOnly = TextDisplayButtonWidget.isObjectOnly(text);
                // якорь TextDisplay - низ текста по центру; центр картинки: 1 px правее и на полстроки выше
                center[0] = TEXT_PIXEL;
                center[1] = (5 - (objectOnly ? 0 : 1)) * TEXT_PIXEL;
                Component finalText = text;
                entity = spawnView(e, owner, at, TextDisplay.class, d -> {
                    common.accept(d);
                    d.text(finalText);
                    d.setBackgroundColor(Color.fromARGB(render.backgroundArgb()));
                    d.setShadowed(render.shadow());
                    if (ParticleColors.alpha(color0) < 255) d.setTextOpacity(LiveParticle.textOpacity(color0));
                    d.setTransformation(initial(effect, scale, rotation, center));
                });
            }
            case PIXEL -> {
                entity = spawnView(e, owner, at, TextDisplay.class, d -> {
                    common.accept(d);
                    d.text(Component.text(" "));
                    d.setBackgroundColor(Color.fromARGB(color0));
                    d.setTransformation(LiveParticle.pixelTransformation(ParticleSim.scaleAt(anim, scale, 0, 1), rotation));
                });
            }
            case ITEM -> {
                String name = pick(render.items(), r);
                Material m = materialCache.computeIfAbsent(name, ParticleEffect::item);
                if (m == null) {
                    warnOnce("item:" + name, "Particle effect '" + effect.id() + "': unknown item '" + name + "'");
                    return null;
                }
                entity = spawnView(e, owner, at, ItemDisplay.class, d -> {
                    common.accept(d);
                    d.setItemStack(new ItemStack(m));
                    d.setGlowing(render.glow());
                    if (render.glow() && render.glowColor() != null) d.setGlowColorOverride(Color.fromRGB(render.glowColor()));
                    d.setTransformation(initial(effect, scale, rotation, center));
                });
            }
            case BLOCK -> {
                BlockData data = blockData(effect, pick(render.blocks(), r));
                if (data == null) return null;
                center[0] = center[1] = center[2] = 0.5f; // блок рисуется от угла
                entity = spawnView(e, owner, at, BlockDisplay.class, d -> {
                    common.accept(d);
                    d.setBlock(data);
                    d.setGlowing(render.glow());
                    if (render.glow() && render.glowColor() != null) d.setGlowColorOverride(Color.fromRGB(render.glowColor()));
                    d.setTransformation(initial(effect, scale, rotation, center));
                });
            }
            default -> {
                return null;
            }
        }
        LiveParticle particle = new LiveParticle(e, entity, world, body, life, scale, rotation, spin, center, now,
                render.type() == ParticleEffect.RenderType.PIXEL, baseText, startColor,
                textVariant != null && textVariant.animated() ? textVariant : null, frameStart, textRecolor);
        particle.planned = first;
        particle.yaw = yaw;
        return particle;
    }

    /** Показ новой частицы; если пакеты несовместимы с версией сервера - переход на сущности. */
    private <T extends Display> ParticleView spawnView(ParticleEmission e, Player owner, Location at, Class<T> type, Consumer<T> setup) {
        try {
            return backend.spawn(e, owner, at, type, setup);
        } catch (LinkageError ex) {
            if (backend instanceof EntityBackend) throw ex;
            fallBack(ex);
            return backend.spawn(e, owner, at, type, setup);
        }
    }

    /** Вид в момент появления (с fadeIn - нулевой размер). */
    private static org.bukkit.util.Transformation initial(ParticleEffect effect, double scale, double rotation, float[] center) {
        return LiveParticle.transformation(center, ParticleSim.scaleAt(effect.animation(), scale, 0, 1), rotation);
    }

    private BlockData blockData(ParticleEffect effect, String spec) {
        BlockData cached = blockCache.get(spec);
        if (cached != null) return cached;
        try {
            BlockData data = Bukkit.createBlockData(spec);
            blockCache.put(spec, data);
            return data;
        } catch (IllegalArgumentException ex) {
            warnOnce("block:" + spec, "Particle effect '" + effect.id() + "': unknown block '" + spec + "'");
            return null;
        }
    }

    private static <T> T pick(List<T> list, RandomGenerator r) {
        return list.size() == 1 ? list.get(0) : list.get(r.nextInt(list.size()));
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) log.warning(message);
    }

    /** Для отладки и /dlib particle list. */
    public Map<String, Integer> aliveByEffect() {
        Map<String, Integer> out = new HashMap<>();
        for (ParticleEmission e : emissions) out.merge(e.effect.id(), e.alive(), Integer::sum);
        return out;
    }
}

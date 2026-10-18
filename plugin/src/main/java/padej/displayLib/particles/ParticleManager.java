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

    private final List<ParticleEmission> emissions = new ArrayList<>();
    private int alive;
    private BukkitTask task;
    private boolean ticking;

    /** Предупреждения, которые уже были (не засорять консоль каждый запуск) */
    private final Set<String> warned = new HashSet<>();
    private final Map<String, BlockData> blockCache = new HashMap<>();
    private final Map<String, Material> materialCache = new HashMap<>();

    /**
     * @param maxAlive предел одновременно живых частиц на сервере; новые сверх него не появляются
     */
    public ParticleManager(Plugin plugin, int maxAlive) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.maxAlive = Math.max(1, maxAlive);
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
        ParticleEmission emission = new ParticleEmission(effect, center, owner != null ? owner.getUniqueId() : null);
        emitSafely(emission, effect.emitter().count());
        if (!emission.isDone()) {
            emissions.add(emission);
            ensureTask();
        }
        return emission;
    }

    /** Убрать все частицы и остановить все запуски. */
    public void stopAll() {
        for (ParticleEmission e : emissions) {
            e.cancel();
            if (!ticking) e.particles.clear(); // во время обхода списки чистит сам tick()
        }
        emissions.clear();
        alive = 0;
        if (task != null) {
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
        HandlerList.unregisterAll(this);
    }

    private void tick() {
        ticking = true;
        try {
            tickAll();
        } finally {
            ticking = false;
        }
    }

    private void tickAll() {
        int now = Bukkit.getCurrentTick();
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
                e.particles.clear();
                emissions.remove(e);
            }
        }
        // счётчик - заново: события и отмены во время обхода не дают ему разойтись с реальностью
        int count = 0;
        for (ParticleEmission e : emissions) count += e.particles.size();
        alive = count;
        if (emissions.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickEmission(ParticleEmission e, int now) {
        if (!e.center.isWorldLoaded()) {
            e.cancel(); // мир выгружен
            return;
        }
        int keyframeTicks = e.effect.keyframeTicks();
        boolean collide = e.effect.motion().collide();
        ParticleSim.Solid solid = null;
        World solidWorld = null;
        for (Iterator<LiveParticle> pi = e.particles.iterator(); pi.hasNext(); ) {
            LiveParticle p = pi.next();
            if (!p.dead && now > p.spawnTick) {
                p.age++;
                if (!p.entity.isValid()) {
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
            if (p.dead) pi.remove();
        }

        // испускание в течение duration - после обхода: новые частицы получают первый кадр на следующем тике
        if (e.ticksLeft > 0 && !e.stopped) {
            if (e.ticksLeft != Integer.MAX_VALUE) e.ticksLeft--; // duration: -1 - пока не остановят
            e.carry += e.effect.emitter().rate();
            int n = (int) e.carry;
            e.carry -= n;
            emit(e, n);
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
        RandomGenerator r = ThreadLocalRandom.current();
        int now = Bukkit.getCurrentTick();
        for (int i = 0; i < count; i++) {
            if (alive >= maxAlive) {
                warnOnce("max", "Particle limit reached (" + maxAlive + " alive, particles.maxAlive in config.jmc); extra particles are skipped");
                return;
            }
            LiveParticle p = spawn(e, world, owner, r, now);
            if (p == null) return;
            e.particles.add(p);
            alive++;
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
                for (LiveParticle p : e.particles) p.showTo(player, plugin);
            }
        }
    }

    /** Мир выгружается: его частицы убираются сразу. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        for (ParticleEmission e : emissions) {
            if (e.center.isWorldLoaded() && e.center.getWorld() == world) e.cancel();
            else for (LiveParticle p : e.particles) if (p.world == world) p.kill();
        }
    }

    private LiveParticle spawn(ParticleEmission e, World world, Player owner, RandomGenerator r, int now) {
        ParticleEffect effect = e.effect;
        double[] off = ParticleSim.spawnOffset(effect.emitter(), r);
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
        double scale = effect.animation().scale().sample(r);
        double rotation = effect.animation().rotation().sample(r);
        double spin = effect.animation().spin().sample(r);

        ParticleEffect.Render render = effect.render();
        ParticleEffect.Animation anim = effect.animation();
        Location at = new Location(world, body.x, body.y, body.z, 0, 0);
        float[] center = {0, 0, 0};
        int startColor = render.colors().isEmpty() ? 0xffffffff : pick(render.colors(), r);
        int color0 = ParticleSim.colorAt(anim, startColor, 0, life);
        Component baseText = null;
        Consumer<Display> common = d -> {
            d.setPersistent(false);
            d.setBillboard(render.billboard());
            if (render.brightness() != null) d.setBrightness(new Display.Brightness(render.brightness(), render.brightness()));
            d.setTeleportDuration(first.length());
            d.setInterpolationDuration(0);
            if (owner != null) d.setVisibleByDefault(false);
        };

        Display entity;
        switch (render.type()) {
            case TEXT -> {
                baseText = pick(render.texts(), r);
                Component text = baseText;
                // цвет: случайный из render.colors или кривая animation.color; без них - как в самом тексте
                boolean recolor = !render.colors().isEmpty() || anim.color() != null;
                if (recolor) text = text.color(TextColor.color(color0 & 0xffffff));
                else baseText = null;
                boolean objectOnly = TextDisplayButtonWidget.isObjectOnly(text);
                // якорь TextDisplay - низ текста по центру; центр картинки: 1 px правее и на полстроки выше
                center[0] = TEXT_PIXEL;
                center[1] = (5 - (objectOnly ? 0 : 1)) * TEXT_PIXEL;
                Component finalText = text;
                entity = world.spawn(at, TextDisplay.class, d -> {
                    common.accept(d);
                    d.text(finalText);
                    d.setBackgroundColor(Color.fromARGB(render.backgroundArgb()));
                    d.setShadowed(render.shadow());
                    if (ParticleColors.alpha(color0) < 255) d.setTextOpacity(LiveParticle.textOpacity(color0));
                    d.setTransformation(initial(effect, scale, rotation, center));
                });
            }
            case PIXEL -> {
                entity = world.spawn(at, TextDisplay.class, d -> {
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
                entity = world.spawn(at, ItemDisplay.class, d -> {
                    common.accept(d);
                    d.setItemStack(new ItemStack(m));
                    d.setGlowing(render.glow());
                    d.setTransformation(initial(effect, scale, rotation, center));
                });
            }
            case BLOCK -> {
                BlockData data = blockData(effect, pick(render.blocks(), r));
                if (data == null) return null;
                center[0] = center[1] = center[2] = 0.5f; // блок рисуется от угла
                entity = world.spawn(at, BlockDisplay.class, d -> {
                    common.accept(d);
                    d.setBlock(data);
                    d.setGlowing(render.glow());
                    d.setTransformation(initial(effect, scale, rotation, center));
                });
            }
            default -> {
                return null;
            }
        }
        if (owner != null) owner.showEntity(plugin, entity);
        LiveParticle particle = new LiveParticle(e, entity, world, body, life, scale, rotation, spin, center, now,
                render.type() == ParticleEffect.RenderType.PIXEL, baseText, startColor);
        particle.planned = first;
        return particle;
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

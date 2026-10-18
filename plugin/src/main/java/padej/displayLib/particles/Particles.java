package padej.displayLib.particles;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

/**
 * Частицы из Display-сущностей: Java API для других плагинов.
 *
 * <pre>
 * ParticleEmission e = Particles.play("confetti", player.getLocation().add(0, 2, 0));
 * Particles.play("sparkle", location, player);   // viewers = "owner" - увидит только этот игрок
 * e.stop();                                       // перестать испускать (для эффектов с duration)
 * e.cancel();                                     // убрать всё сразу
 * </pre>
 *
 * <p>Эффекты описываются файлами {@code plugins/DisplayLib/particles/<id>.jmc} (см. {@link ParticleEffect}).
 * Все методы вызываются из основного потока сервера.</p>
 */
public final class Particles {
    private static ParticleRegistry registry;
    private static ParticleManager manager;

    private Particles() {}

    /** Включение и выключение плагина. */
    public static void install(ParticleRegistry registry, ParticleManager manager) {
        if (Particles.manager != null && Particles.manager != manager) Particles.manager.shutdown();
        Particles.registry = registry;
        Particles.manager = manager;
    }

    /** Запустить эффект по id; {@code null}, если такого нет. Частицы видят все. */
    public static ParticleEmission play(String effectId, Location location) {
        return play(effectId, location, null);
    }

    /**
     * Запустить эффект по id.
     *
     * @param owner для {@code viewers = "owner"} - единственный, кто видит частицы; {@code null} - видят все
     * @return запуск или {@code null}, если эффекта с таким id нет (или плагин выключен)
     */
    public static ParticleEmission play(String effectId, Location location, Player owner) {
        ParticleEffect effect = effect(effectId);
        return effect != null ? play(effect, location, owner) : null;
    }

    /** Запустить эффект-объект (например, изменённый через {@link ParticleEffect#withCount(int)}). */
    public static ParticleEmission play(ParticleEffect effect, Location location, Player owner) {
        if (manager == null) return null;
        return manager.play(effect, location, owner);
    }

    /** Эффект по id или {@code null}. */
    public static ParticleEffect effect(String id) {
        return registry != null ? registry.get(id) : null;
    }

    /** id всех загруженных эффектов. */
    public static Set<String> ids() {
        return registry != null ? registry.all().keySet() : Set.of();
    }

    /** Убрать все частицы. */
    public static void stopAll() {
        if (manager != null) manager.stopAll();
    }

    /** Сколько частиц сейчас живо. */
    public static int alive() {
        return manager != null ? manager.aliveCount() : 0;
    }

    /** Живые частицы по эффектам. */
    public static Map<String, Integer> aliveByEffect() {
        return manager != null ? manager.aliveByEffect() : Map.of();
    }

    public static ParticleRegistry registry() {
        return registry;
    }
}

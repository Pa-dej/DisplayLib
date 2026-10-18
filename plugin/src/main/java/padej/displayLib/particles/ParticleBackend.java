package padej.displayLib.particles;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Как частицы показываются игрокам: настоящими сущностями в мире ({@link EntityBackend}) или пакетами
 * без серверных сущностей ({@code nms.PacketBackend}). Все методы - из основного потока.
 */
interface ParticleBackend {
    /**
     * Показать новую частицу.
     *
     * @param owner для {@code viewers: "owner"} - единственный зритель, иначе {@code null}
     * @param setup настройка сущности до первого показа (тип, текст, трансформация…)
     */
    <T extends Display> ParticleView spawn(ParticleEmission emission, Player owner, Location at, Class<T> type, Consumer<T> setup);

    /** Есть ли кому показывать новые частицы запуска (у пакетов - зрители рядом); иначе они не появляются. */
    default boolean visible(ParticleEmission emission) {
        return true;
    }

    /** Начало обхода запуска в тике: обновить зрителей. */
    default void tick(ParticleEmission emission, int now) {}

    /** Конец тика: отправить накопленное (удаления частиц). */
    default void flush() {}

    /** Запуск закончен и убран из списка. */
    default void finish(ParticleEmission emission) {}

    /** Владелец перезашёл: показать ему его частицы. */
    default void rejoin(Player player, ParticleEmission emission) {}

    /** Выключение: отписаться от событий. */
    default void shutdown() {}

    /** Название для логов и {@code /dlib particle list}. */
    String name();
}

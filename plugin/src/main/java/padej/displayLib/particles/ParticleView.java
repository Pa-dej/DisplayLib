package padej.displayLib.particles;

import org.bukkit.entity.Display;

/**
 * Показ одной частицы. Вид меняется сеттерами Bukkit на {@link #display()} (для пакетов это сущность вне мира),
 * изменения уходят клиентам в {@link #flush()}; движение - сразу в {@link #moveTo}.
 */
interface ParticleView {
    /** Сущность, у которой меняются трансформация, текст, цвет, длительности интерполяции. */
    Display display();

    /** Переместить частицу (клиент ведёт её туда за {@code teleportDuration} тиков). */
    void moveTo(double x, double y, double z, float yaw);

    /** Отправить изменения вида, сделанные после прошлого flush. */
    void flush();

    /** Частица ещё показывается (сущность не убрал мир, например при выгрузке чанка). */
    boolean valid();

    void remove();
}

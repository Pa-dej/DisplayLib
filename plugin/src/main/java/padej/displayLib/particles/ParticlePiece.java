package padej.displayLib.particles;

import net.kyori.adventure.text.Component;

/**
 * Частица, заданная явно: где появляется и что показывает. Нужна для эффектов «из готовой картинки» -
 * например, слово рассыпается на буквы ({@link TextShatter}): каждая буква появляется ровно там, где
 * стояла в слове, а дальше летит по физике эффекта.
 *
 * @param text   текст частицы (для {@code TEXT}); {@code null} - как в эффекте
 * @param dx     смещение от центра запуска, блоков (по нему же считается направление {@code OUTWARD})
 * @param dy     то же по Y
 * @param dz     то же по Z
 * @param scale  начальный размер; {@code null} - случайный из {@code animation.scale}
 * @param yaw    поворот сущности (для {@code billboard: "FIXED"} - куда смотрит текст), градусы Minecraft
 */
public record ParticlePiece(Component text, double dx, double dy, double dz, Double scale, float yaw) {
    public ParticlePiece(Component text, double dx, double dy, double dz) {
        this(text, dx, dy, dz, null, 0);
    }
}

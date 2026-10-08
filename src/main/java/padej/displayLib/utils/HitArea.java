package padej.displayLib.utils;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Зона наведения виджета: прямоугольник в плоскости самой Display-сущности.
 *
 * <p>Строится из того же, из чего клиент рисует виджет: позиции сущности, её поворота
 * (yaw/pitch) и смещения {@code translation} из трансформации. Проверка наведения -
 * пересечение луча взгляда с плоскостью виджета и сравнение точки попадания с
 * полуразмерами прямоугольника в локальных осях виджета. Поэтому зона совпадает с тем,
 * что видит игрок, под любым углом и при любом наклоне экрана.</p>
 *
 * <p>Локальные оси сущности (как в клиенте, поворот {@code Ry(-yaw) * Rx(pitch)}):
 * Z - направление, в которое сущность "смотрит", Y - вверх, X - влево от неё.</p>
 *
 * <p>Изменяемый объект: базис вычисляется один раз при создании виджета,
 * в цикле обновления остаётся только арифметика без тригонометрии и аллокаций.</p>
 */
public final class HitArea {

    /** Луч почти параллелен плоскости виджета - попадания нет */
    private static final double PARALLEL_EPSILON = 1.0e-6;

    private World world;

    // Центр зоны в мире (позиция сущности + translation)
    private double cx, cy, cz;

    // Локальные оси сущности в мировых координатах
    private double xx, xy, xz;
    private double yx, yy, yz;
    private double zx, zy, zz;

    private double halfWidth, halfHeight;
    private boolean frontOnly;

    /**
     * Задать зону по состоянию сущности.
     *
     * @param entityLocation позиция и поворот (yaw/pitch) Display-сущности
     * @param tx,ty,tz       translation из трансформации сущности (в её локальных осях)
     * @param halfWidth      полуширина зоны вдоль локальной оси X
     * @param halfHeight     полувысота зоны вдоль локальной оси Y
     * @param frontOnly      true - наводиться можно только с лицевой стороны (куда сущность "смотрит")
     */
    public void set(Location entityLocation, float tx, float ty, float tz,
                    double halfWidth, double halfHeight, boolean frontOnly) {
        this.world = entityLocation.getWorld();
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.frontOnly = frontOnly;

        double yaw = Math.toRadians(entityLocation.getYaw());
        double pitch = Math.toRadians(entityLocation.getPitch());
        double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);

        xx = cosYaw;
        xy = 0.0;
        xz = sinYaw;

        yx = -sinPitch * sinYaw;
        yy = cosPitch;
        yz = sinPitch * cosYaw;

        zx = -cosPitch * sinYaw;
        zy = -sinPitch;
        zz = cosPitch * cosYaw;

        cx = entityLocation.getX() + xx * tx + yx * ty + zx * tz;
        cy = entityLocation.getY() + xy * tx + yy * ty + zy * tz;
        cz = entityLocation.getZ() + xz * tx + yz * ty + zz * tz;
    }

    /**
     * Пересечь луч взгляда с зоной.
     *
     * @return расстояние от глаз до точки попадания вдоль луча или -1, если попадания нет
     */
    public double intersect(ViewRay ray) {
        if (halfWidth <= 0.0 || halfHeight <= 0.0) return -1.0;
        if (world == null || ray.getWorld() != world) return -1.0;

        double dx = ray.getDirectionX();
        double dy = ray.getDirectionY();
        double dz = ray.getDirectionZ();

        // Проекция взгляда на нормаль плоскости: с лицевой стороны она отрицательна
        double facing = dx * zx + dy * zy + dz * zz;
        if (frontOnly ? facing > -PARALLEL_EPSILON : Math.abs(facing) < PARALLEL_EPSILON) return -1.0;

        // Вектор от глаз к центру зоны
        double ex = cx - ray.getOriginX();
        double ey = cy - ray.getOriginY();
        double ez = cz - ray.getOriginZ();

        double distance = (ex * zx + ey * zy + ez * zz) / facing;
        if (distance <= 0.0) return -1.0; // плоскость позади игрока

        // Точка попадания относительно центра зоны, в локальных осях виджета
        double px = dx * distance - ex;
        double py = dy * distance - ey;
        double pz = dz * distance - ez;

        if (Math.abs(px * xx + py * xy + pz * xz) >= halfWidth) return -1.0;
        if (Math.abs(px * yx + py * yy + pz * yz) >= halfHeight) return -1.0;

        return distance;
    }
}

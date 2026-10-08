package padej.displayLib.utils;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class PointDetection {
    public static boolean lookingAtPoint(@NotNull Vector eye, @NotNull Vector direction, Vector point, double tolerance) {
        return lookingAtPoint(eye, direction, point, tolerance, tolerance);
    }

    public static boolean lookingAtPoint(@NotNull Vector eye, @NotNull Vector direction, Vector point, double horizontalTolerance, double verticalTolerance) {
        return lookingAtPoint(eye.getX(), eye.getY(), eye.getZ(),
                direction.getX(), direction.getY(), direction.getZ(),
                point.getX(), point.getY(), point.getZ(),
                horizontalTolerance, verticalTolerance);
    }

    /**
     * Вариант без объектов: используется в горячем цикле обновления экранов,
     * чтобы не создавать Vector на каждый виджет и каждый тик.
     */
    public static boolean lookingAtPoint(double eyeX, double eyeY, double eyeZ,
                                         double dirX, double dirY, double dirZ,
                                         double pointX, double pointY, double pointZ,
                                         double horizontalTolerance, double verticalTolerance) {
        double dx = pointX - eyeX;
        double dy = pointY - eyeY;
        double dz = pointZ - eyeZ;

        // Быстрая проверка: точка за спиной?
        double dotProduct = dx * dirX + dy * dirY + dz * dirZ;
        if (dotProduct < 0) {
            return false;
        }

        double pointDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Точка на луче взгляда на том же расстоянии, что и цель
        double hx = eyeX + dirX * pointDistance - pointX;
        double hy = eyeY + dirY * pointDistance - pointY;
        double hz = eyeZ + dirZ * pointDistance - pointZ;

        double horizontalDist = Math.sqrt(hx * hx + hz * hz);
        double verticalDist = Math.abs(hy);

        return horizontalDist < horizontalTolerance && verticalDist < verticalTolerance;
    }
}

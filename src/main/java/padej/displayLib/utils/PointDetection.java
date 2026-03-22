package padej.displayLib.utils;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class PointDetection {
    public static boolean lookingAtPoint(@NotNull Vector eye, @NotNull Vector direction, Vector point, double tolerance) {
        return lookingAtPoint(eye, direction, point, tolerance, tolerance);
    }

    public static boolean lookingAtPoint(@NotNull Vector eye, @NotNull Vector direction, Vector point, double horizontalTolerance, double verticalTolerance) {
        // Быстрая проверка: точка за спиной?
        double dx = point.getX() - eye.getX();
        double dy = point.getY() - eye.getY();
        double dz = point.getZ() - eye.getZ();
        
        double dotProduct = dx * direction.getX() + dy * direction.getY() + dz * direction.getZ();
        if (dotProduct < 0) {
            return false; // Точка за спиной
        }
        
        double pointDistance = Math.sqrt(dx*dx + dy*dy + dz*dz);
        Vector lookingAtPoint = eye.clone().add(direction.clone().multiply(pointDistance));

        double horizontalDist = Math.sqrt(Math.pow(lookingAtPoint.getX() - point.getX(), 2) + Math.pow(lookingAtPoint.getZ() - point.getZ(), 2));
        double verticalDist = Math.abs(lookingAtPoint.getY() - point.getY());
        
        return horizontalDist < horizontalTolerance && verticalDist < verticalTolerance;
    }
}
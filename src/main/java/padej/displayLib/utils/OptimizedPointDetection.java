package padej.displayLib.utils;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Оптимизированная версия определения пересечения взгляда с точкой
 */
public class OptimizedPointDetection {
    
    // Кэш для избежания создания новых Vector объектов
    private static final ThreadLocal<Vector> TEMP_VECTOR = ThreadLocal.withInitial(Vector::new);
    
    /**
     * Быстрая проверка пересечения взгляда с точкой
     * Оптимизации:
     * 1. Избегаем создания новых Vector объектов
     * 2. Ранний выход при очевидных промахах
     * 3. Используем квадраты расстояний где возможно
     */
    public static boolean lookingAtPoint(@NotNull Vector eye, @NotNull Vector direction, 
                                       Vector point, double horizontalTolerance, double verticalTolerance) {
        
        // Быстрая проверка: слишком далеко по Y?
        double deltaY = Math.abs(point.getY() - eye.getY());
        if (deltaY > verticalTolerance + 50) { // +50 для учета направления луча
            return false;
        }
        
        // Вычисляем расстояние до точки
        double dx = point.getX() - eye.getX();
        double dy = point.getY() - eye.getY();
        double dz = point.getZ() - eye.getZ();
        double pointDistance = Math.sqrt(dx*dx + dy*dy + dz*dz);
        
        // Быстрая проверка: точка за спиной?
        double dotProduct = dx * direction.getX() + dy * direction.getY() + dz * direction.getZ();
        if (dotProduct < 0.5 * pointDistance) {
            return false;
        }
        
        // Вычисляем точку пересечения луча
        Vector temp = TEMP_VECTOR.get();
        temp.setX(eye.getX() + direction.getX() * pointDistance);
        temp.setY(eye.getY() + direction.getY() * pointDistance);
        temp.setZ(eye.getZ() + direction.getZ() * pointDistance);
        
        // Проверяем попадание в tolerance зону
        double horizontalDistSq = Math.pow(temp.getX() - point.getX(), 2) + Math.pow(temp.getZ() - point.getZ(), 2);
        double verticalDist = Math.abs(temp.getY() - point.getY());
        
        return horizontalDistSq < horizontalTolerance * horizontalTolerance && verticalDist < verticalTolerance;
    }
    
    /**
     * Версия с учетом блоков (гибридный подход)
     * Использует быстрый математический рэйкаст + опциональную проверку блоков
     */
    public static boolean lookingAtPointWithBlockCheck(@NotNull Vector eye, @NotNull Vector direction, 
                                                     Vector point, double horizontalTolerance, double verticalTolerance,
                                                     org.bukkit.World world, boolean checkBlocks) {
        
        // Сначала быстрая математическая проверка
        if (!lookingAtPoint(eye, direction, point, horizontalTolerance, verticalTolerance)) {
            return false;
        }
        
        // Если нужно, проверяем блоки (только если математическая проверка прошла)
        if (checkBlocks && world != null) {
            double distance = eye.distance(point);
            var result = world.rayTraceBlocks(eye.toLocation(world), direction, distance);
            return result == null; // null = нет препятствий
        }
        
        return true;
    }
}
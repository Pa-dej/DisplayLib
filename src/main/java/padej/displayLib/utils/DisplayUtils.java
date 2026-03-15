package padej.displayLib.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class DisplayUtils {

    public static void lookAtPos(Entity entity, Location location) {
        // Получаем разницу координат
        double dx = location.getX() - entity.getLocation().getX();
        double dy = location.getY() - entity.getLocation().getY();
        double dz = location.getZ() - entity.getLocation().getZ();
        
        // Вычисляем углы поворота
        double yaw = Math.atan2(dz, dx);
        double pitch = Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        
        // Конвертируем радианы в градусы и устанавливаем поворот
        float yawDegrees = (float) Math.toDegrees(yaw) - 90;
        float pitchDegrees = (float) Math.toDegrees(-pitch);
        
        entity.setRotation(yawDegrees, pitchDegrees);
    }
}
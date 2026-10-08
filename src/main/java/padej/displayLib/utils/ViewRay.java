package padej.displayLib.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Луч взгляда игрока: точка глаз и нормализованное направление.
 *
 * <p>Изменяемый объект, который заполняется один раз на игрока за обновление экрана
 * и затем переиспользуется для проверки всех виджетов ({@link HitArea#intersect}).</p>
 *
 * <p>Не потокобезопасен: использовать только из основного потока сервера.</p>
 */
public final class ViewRay {

    private final Location scratch = new Location(null, 0, 0, 0);

    private World world;
    private double ox, oy, oz;
    private double dx, dy, dz;

    /**
     * Заполнить луч по текущему положению и взгляду игрока.
     *
     * @return этот же объект
     */
    public ViewRay set(Player player) {
        player.getLocation(scratch);
        world = scratch.getWorld();
        ox = scratch.getX();
        oy = scratch.getY() + player.getEyeHeight();
        oz = scratch.getZ();

        // То же, что Location#getDirection(), но без создания Vector
        double yaw = Math.toRadians(scratch.getYaw());
        double pitch = Math.toRadians(scratch.getPitch());
        double xz = Math.cos(pitch);
        dx = -xz * Math.sin(yaw);
        dy = -Math.sin(pitch);
        dz = xz * Math.cos(yaw);

        // Не удерживаем ссылку на мир в переиспользуемом Location
        scratch.setWorld(null);
        return this;
    }

    public World getWorld() {
        return world;
    }

    public double getOriginX() { return ox; }
    public double getOriginY() { return oy; }
    public double getOriginZ() { return oz; }

    public double getDirectionX() { return dx; }
    public double getDirectionY() { return dy; }
    public double getDirectionZ() { return dz; }

    /** Квадрат расстояния от глаз до точки. */
    public double distanceSquared(double px, double py, double pz) {
        double x = px - ox;
        double y = py - oy;
        double z = pz - oz;
        return x * x + y * y + z * z;
    }
}

package padej.displayLib.render;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

public class RenderEntity<T extends Entity> {

    private final Class<T> clazz;
    private final Location location;
    private final Consumer<T> init;
    private final Consumer<T> preUpdate;
    private final Consumer<T> update;

    public RenderEntity(Class<T> clazz, Location location, Consumer<T> init, Consumer<T> preUpdate, Consumer<T> update) {
        this.clazz = clazz;
        this.location = location;
        this.init = init;
        this.preUpdate = preUpdate;
        this.update = update;
    }

    public RenderEntity(Class<T> clazz, Location location) {
        this(clazz, location, t -> {}, t -> {}, t -> {});
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public Location getLocation() {
        return location;
    }

    public Consumer<T> getInit() {
        return init;
    }

    public Consumer<T> getPreUpdate() {
        return preUpdate;
    }

    public Consumer<T> getUpdate() {
        return update;
    }
}
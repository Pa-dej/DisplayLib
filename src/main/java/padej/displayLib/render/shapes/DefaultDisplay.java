package padej.displayLib.render.shapes;

import padej.displayLib.utils.AlignmentType;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public abstract class DefaultDisplay {
    protected Display display;
    public Transformation emptyTransformation = new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(), new AxisAngle4f());

    public DefaultDisplay() {}

    public Display getDisplay() {
        return this.display;
    }

    public Transformation getTransformation() {
        return this.display != null ? this.display.getTransformation() : new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(), new AxisAngle4f());
    }

    public Location getLocation() {
        return this.display != null ? this.display.getLocation() : null;
    }

    public void removeEntity() {
        if (this.display != null && !this.display.isDead()) {
            this.display.remove();
            this.display = null;
        }
    }

    public void moveTo(Location location) {
        if (this.display != null && location != null) {
            this.display.teleport(location);
        }
    }

    public static Vector3f getOffset(AlignmentType type, float scale) {
        switch (type) {
            case CENTER:
            default:
                return new Vector3f(-scale / 2.0F, -scale / 2.0F, -scale / 2.0F);
            case TOP:
                return new Vector3f(-scale / 2.0F, -scale, -scale / 2.0F);
            case BOTTOM:
                return new Vector3f(-scale / 2.0F, 0.0F, -scale / 2.0F);
            case NONE:
                return new Vector3f(0.0F, 0.0F, 0.0F);
        }
    }
}
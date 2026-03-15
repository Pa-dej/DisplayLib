package padej.displayLib.render.shapes;

import padej.displayLib.utils.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;

public abstract class DefaultSquare extends DefaultDisplay {
    private float scale;
    private Color backgroundColor;
    private int backgroundAlpha;
    private Display.Billboard billboard;
    private boolean ignoreCull;

    private TextDisplay textDisplay;

    public DefaultSquare(float scale, Color backgroundColor, int backgroundAlpha, Display.Billboard billboard, boolean ignoreCull) {
        this.scale = scale;
        this.backgroundColor = backgroundColor;
        this.backgroundAlpha = backgroundAlpha;
        this.billboard = billboard;
        this.ignoreCull = ignoreCull;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int getBackgroundAlpha() {
        return backgroundAlpha;
    }

    public void setBackgroundAlpha(int backgroundAlpha) {
        this.backgroundAlpha = backgroundAlpha;
    }

    public Display.Billboard getBillboard() {
        return billboard;
    }

    public void setBillboard(Display.Billboard billboard) {
        this.billboard = billboard;
    }

    public boolean isIgnoreCull() {
        return ignoreCull;
    }

    public void setIgnoreCull(boolean ignoreCull) {
        this.ignoreCull = ignoreCull;
    }

    public TextDisplay getTextDisplay() {
        return textDisplay;
    }

    public Transformation getTransformation() {
        return textDisplay != null ? textDisplay.getTransformation() : emptyTransformation;
    }

    public Location getLocation() {
        return textDisplay != null ? textDisplay.getLocation() : null;
    }

    public TextDisplay spawn(Location spawnLocation) {
        if (textDisplay != null && !textDisplay.isDead()) {
            textDisplay.remove();
            textDisplay = null;
            return null;
        }
        textDisplay = (TextDisplay) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.TEXT_DISPLAY);
        textDisplay.setRotation(0, 0);
        textDisplay.setBackgroundColor(ColorUtil.formARGBColor(backgroundAlpha, backgroundColor));
        textDisplay.text(Component.text(" "));
        textDisplay.setBillboard(billboard);
        textDisplay.setSeeThrough(ignoreCull);
        textDisplay.setTransformation(new Transformation(
                new Vector3f(-scale / 80f, -scale / 16f, 0), // 16 and 80 is const's. Idk. Mojang, why?
                new AxisAngle4f(),
                new Vector3f(getScale(), getScale() / 2, 1),
                new AxisAngle4f()
        ));
        textDisplay.setInterpolationDuration(1);
        textDisplay.setTeleportDuration(1);

        return textDisplay;
    }

    @Override
    public void removeEntity() {
        if (this.textDisplay != null && !this.textDisplay.isDead()) {
            this.textDisplay.remove();
            this.textDisplay = null;
        }
    }
}
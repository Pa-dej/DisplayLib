package padej.displayLib.render.shapes;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;

public abstract class StringRectangle extends DefaultSquare {
    private String text;

    public StringRectangle(float scale, Color backgroundColor, int backgroundAlpha, Display.Billboard billboard, boolean ignoreCull, String text) {
        super(scale, backgroundColor, backgroundAlpha, billboard, ignoreCull);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        if (getTextDisplay() != null) {
            getTextDisplay().text(Component.text(text));
        }
    }

    @Override
    public TextDisplay spawn(Location spawnLocation) {
        TextDisplay display = super.spawn(spawnLocation);
        if (display != null) {
            display.text(Component.text(text));
            display.setTransformation(new Transformation(
                    new Vector3f(-getScale() / 80f, -getScale() / 16f, 0), // 16 and 80 is const's. Idk. Mojang, why?
                    new AxisAngle4f(),
                    new Vector3f(getScale(), getScale(), 1),
                    new AxisAngle4f()
            ));
        }
        return display;
    }
}
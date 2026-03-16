package padej.displayLib.render.shapes;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public abstract class ComponentRectangle extends DefaultSquare {
    private Component text;

    public ComponentRectangle(float scale, Color backgroundColor, int backgroundAlpha, Display.Billboard billboard, boolean ignoreCull, Component text) {
        super(scale, backgroundColor, backgroundAlpha, billboard, ignoreCull);
        this.text = text;
    }

    public Component getText() {
        return text;
    }

    public void setText(Component text) {
        this.text = text;
        if (getTextDisplay() != null) {
            getTextDisplay().text(text);
        }
    }

    @Override
    public TextDisplay spawn(Location spawnLocation) {
        TextDisplay display = super.spawn(spawnLocation);
        if (display != null) {
            display.text(text);
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),  // Убираем автоматическое смещение
                    new AxisAngle4f(),
                    new Vector3f(getScale(), getScale(), 1),
                    new AxisAngle4f()
            ));
        }
        return display;
    }
}
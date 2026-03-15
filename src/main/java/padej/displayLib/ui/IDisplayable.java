package padej.displayLib.ui;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

public interface IDisplayable {
    TextDisplay getTextDisplay();
    void softRemoveWithAnimation();
    void updateDisplayPosition(Location location, float yaw, float pitch);
    void setBackgroundColor(Color color);
}
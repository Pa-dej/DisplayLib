package padej.displayLib.ui.widgets;

import org.bukkit.Location;

public interface Widget {
    
    boolean isHovered();
    
    void handleClick();
    
    void remove();
    
    void update();
    
    Location getLocation();
    
    default boolean isValid() {
        return true;
    }
}
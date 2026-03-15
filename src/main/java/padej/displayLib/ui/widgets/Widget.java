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
    
    // Методы для Lua API
    boolean isVisible();
    void setVisible(boolean visible);
    
    boolean isEnabled();
    void setEnabled(boolean enabled);
    
    String getTooltip();
    void setTooltip(String tooltip);
}
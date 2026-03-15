package padej.displayLib.ui.base;

import padej.displayLib.ui.widgets.Widget;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public abstract class AbstractWidget implements Widget {
    protected Location location;
    protected Player viewer;
    protected boolean isHovered;
    protected double tolerance = 0.06;
    protected Component tooltip;
    protected int tooltipDelay;
    
    @Override
    public void update() {
        updateHoverState();
        updateTooltip();
    }
    
    protected abstract void updateHoverState();
    protected abstract void updateTooltip();
}
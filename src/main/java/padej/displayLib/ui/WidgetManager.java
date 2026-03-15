package padej.displayLib.ui;

import padej.displayLib.api.events.DisplayClickEvent;
import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;
import padej.displayLib.ui.widgets.Widget;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public abstract class WidgetManager {
    public final List<Widget> children = new ArrayList<>();
    public Player viewer;
    protected Location location;
    
    public WidgetManager(Player viewer, Location location) {
        this.viewer = viewer;
        this.location = location;
    }
    
    public <T extends Widget> T addDrawableChild(T child) {
        children.add(child);
        return child;
    }
    
    public void update() {
        // Удаляем недействительные виджеты перед обновлением
        children.removeIf(widget -> !widget.isValid());
        
        // Обновляем оставшиеся виджеты
        for (Widget widget : children) {
            widget.update();
        }
    }
    
    public void handleClick() {
        Widget nearestWidget = getNearestHoveredWidget();
        if (nearestWidget != null) {
            nearestWidget.handleClick();
        }
    }
    
    public void remove() {
        // Создаем копию списка для безопасной итерации
        List<Widget> toRemove = new ArrayList<>(children);
        for (Widget widget : toRemove) {
            widget.remove();
        }
        children.clear();
    }

    public boolean isLookingAtWidget() {
        return getNearestHoveredWidget() != null;
    }

    private Widget getNearestHoveredWidget() {
        Widget nearestWidget = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Widget widget : children) {
            if (widget.isHovered()) {
                Location widgetLoc = null;
                if (widget instanceof ItemDisplayButtonWidget) {
                    widgetLoc = ((ItemDisplayButtonWidget) widget).getDisplay().getLocation();
                } else if (widget instanceof TextDisplayButtonWidget) {
                    widgetLoc = ((TextDisplayButtonWidget) widget).getDisplay().getLocation();
                }

                if (widgetLoc != null) {
                    double distance = viewer.getEyeLocation().distance(widgetLoc);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestWidget = widget;
                    }
                }
            }
        }
        return nearestWidget;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
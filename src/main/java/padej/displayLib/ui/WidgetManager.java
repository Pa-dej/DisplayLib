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
    
    // Dirty flag для оптимизации cleanup
    private boolean isDirty = false;
    
    // Throttling для проверки расстояния
    private int rangeCheckTimer = 0;
    
    public WidgetManager(Player viewer, Location location) {
        this.viewer = viewer;
        this.location = location;
    }
    
    public <T extends Widget> T addDrawableChild(T child) {
        children.add(child);
        return child;
    }
    
    public void markDirty() {
        isDirty = true;
    }
    
    // Абстрактный метод для проверки расстояния (реализуется в Screen)
    protected abstract boolean isPlayerInRange();
    
    // Абстрактный метод для закрытия экрана (реализуется в Screen)
    protected abstract void tryClose();
    
    public void update() {
        // Проверка расстояния каждые 10 тиков (2 раза в секунду)
        if (++rangeCheckTimer >= 10) {
            rangeCheckTimer = 0;
            if (!isPlayerInRange()) {
                tryClose();
                return;
            }
        }
        
        // Удаляем недействительные виджеты только если есть изменения
        if (isDirty) {
            children.removeIf(widget -> !widget.isValid());
            isDirty = false;
        }
        
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
        List<Widget> toRemove = new ArrayList<>(children);
        for (Widget widget : toRemove) {
            widget.remove();
        }
        children.clear();

        markDirty();
    }

    public boolean isLookingAtWidget() {
        return getNearestHoveredWidget() != null;
    }

    private Widget getNearestHoveredWidget() {
        Widget nearestWidget = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Widget widget : children) {
            if (widget.isHovered()) {
                Location widgetLoc = widget.getLocation();

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
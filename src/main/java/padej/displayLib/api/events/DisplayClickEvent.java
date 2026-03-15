package padej.displayLib.api.events;

import padej.displayLib.ui.widgets.Widget;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DisplayClickEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Player player;
    private final Widget widget;
    
    public DisplayClickEvent(Player player, Widget widget) {
        this.player = player;
        this.widget = widget;
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public Widget getWidget() {
        return widget;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
    
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
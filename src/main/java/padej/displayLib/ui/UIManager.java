package padej.displayLib.ui;

import padej.displayLib.DisplayLib;
import padej.displayLib.api.events.DisplayClickEvent;
import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;
import padej.displayLib.ui.widgets.Widget;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UIManager implements Listener {
    private static UIManager instance;
    private final Map<Player, WidgetManager> activeScreens = new ConcurrentHashMap<>();
    private BukkitTask updateTask;
    private boolean isUpdateTaskRunning = false;

    private UIManager() {
        Bukkit.getPluginManager().registerEvents(this, DisplayLib.getInstance());
    }

    public static UIManager getInstance() {
        if (instance == null) {
            instance = new UIManager();
        }
        return instance;
    }

    public WidgetManager getActiveScreen(Player player) {
        return activeScreens.get(player);
    }

    public void registerScreen(Player player, WidgetManager manager) {
        activeScreens.put(player, manager);
        startUpdateTaskIfNeeded();
    }

    public void unregisterScreen(Player player) {
        activeScreens.remove(player);
        if (activeScreens.isEmpty()) {
            stopUpdateTask();
        }
    }
    private Widget getNearestHoveredWidget(WidgetManager manager) {
        Widget nearestWidget = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Widget widget : manager.children) {
            if (widget.isHovered()) {
                Location widgetLoc = widget.getLocation();

                if (widgetLoc != null) {
                    double distance = manager.viewer.getEyeLocation().distance(widgetLoc);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestWidget = widget;
                    }
                }
            }
        }
        return nearestWidget;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        WidgetManager manager = activeScreens.get(player);

        if (manager != null) {
            if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                Widget nearestWidget = getNearestHoveredWidget(manager);
                if (nearestWidget != null) {
                    event.setCancelled(true);
                    DisplayClickEvent clickEvent = new DisplayClickEvent(player, nearestWidget);
                    Bukkit.getPluginManager().callEvent(clickEvent);
                    if (!clickEvent.isCancelled()) {
                        nearestWidget.handleClick();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        WidgetManager manager = activeScreens.get(player);

        if (manager != null) {
            Widget nearestWidget = getNearestHoveredWidget(manager);
            if (nearestWidget != null) {
                event.setCancelled(true);
                DisplayClickEvent clickEvent = new DisplayClickEvent(player, nearestWidget);
                Bukkit.getPluginManager().callEvent(clickEvent);
                if (!clickEvent.isCancelled()) {
                    nearestWidget.handleClick();
                }
            }
        }
    }
    private void startUpdateTaskIfNeeded() {
        if (!isUpdateTaskRunning) {
            updateTask = Bukkit.getScheduler().runTaskTimer(DisplayLib.getInstance(), () -> {
                for (Map.Entry<Player, WidgetManager> entry : activeScreens.entrySet()) {
                    WidgetManager manager = entry.getValue();
                    if (manager != null) {
                        manager.update();
                    }
                }
            }, 0L, 5L);
            isUpdateTaskRunning = true;
        }
    }

    private void stopUpdateTask() {
        if (isUpdateTaskRunning && updateTask != null) {
            updateTask.cancel();
            isUpdateTaskRunning = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        WidgetManager manager = activeScreens.get(player);
        if (manager != null) {
            manager.remove();
            unregisterScreen(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        WidgetManager manager = activeScreens.get(player);
        if (manager != null) {
            manager.remove();
            unregisterScreen(player);
        }
    }

    public void cleanup() {
        Map<Player, WidgetManager> screensToRemove = new HashMap<>(activeScreens);

        for (Map.Entry<Player, WidgetManager> entry : screensToRemove.entrySet()) {
            WidgetManager manager = entry.getValue();
            if (manager != null) {
                manager.remove();
                unregisterScreen(entry.getKey());
            }
        }

        stopUpdateTask();
    }

    public boolean hasActiveScreens() {
        return !activeScreens.isEmpty();
    }
}
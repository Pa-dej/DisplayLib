package padej.displayLib.ui;

import padej.displayLib.DisplayLib;
import padej.displayLib.api.events.DisplayClickEvent;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.ScreenRegistry;
import padej.displayLib.lua.LuaEngine;
import padej.displayLib.lua.api.StorageAPI;
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
    private final Map<Player, ScreenInstance> activeScreens = new ConcurrentHashMap<>();
    private BukkitTask updateTask;
    private boolean isUpdateTaskRunning = false;
    private ScreenRegistry screenRegistry;
    private LuaEngine luaEngine;

    private UIManager() {
        Bukkit.getPluginManager().registerEvents(this, DisplayLib.getInstance());
    }

    private static class Holder {
        static final UIManager INSTANCE = new UIManager();
    }
    
    public static UIManager getInstance() {
        return Holder.INSTANCE;
    }

    public void initialize(ScreenRegistry screenRegistry, LuaEngine luaEngine) {
        this.screenRegistry = screenRegistry;
        this.luaEngine = luaEngine;
    }

    public ScreenInstance getActiveScreen(Player player) {
        return activeScreens.get(player);
    }

    // -------------------------------------------------------------------------
    // Screen open / close
    // -------------------------------------------------------------------------

    /**
     * Открыть экран перед игроком (позиция вычисляется автоматически).
     */
    public boolean openScreen(Player player, String screenId) {
        return openScreen(player, screenId, defaultLocationFor(player));
    }

    /**
     * Переключить экран, сохранив позицию и ориентацию текущего.
     * Используется при SWITCH_SCREEN из ScreenInstance.
     */
    public boolean switchScreen(Player player, String screenId) {
        Location existingLocation = null;
        float[] existingOrientation = null;
        ScreenInstance current = activeScreens.get(player);
        if (current != null) {
            existingLocation = current.getLocation();
            existingOrientation = current.getScreenOrientation(); // Сохраняем ориентацию
        }
        
        if (existingLocation != null && existingOrientation != null) {
            return openScreen(player, screenId, existingLocation, existingOrientation[0], existingOrientation[1]);
        } else {
            return openScreen(player, screenId, defaultLocationFor(player));
        }
    }

    /**
     * Открыть экран в конкретной позиции.
     */
    public boolean openScreen(Player player, String screenId, Location location) {
        return openScreen(player, screenId, location, null, null);
    }
    
    /**
     * Открыть экран в конкретной позиции с заданной ориентацией.
     */
    public boolean openScreen(Player player, String screenId, Location location, Float yaw, Float pitch) {
        if (screenRegistry == null) {
            DisplayLib.getInstance().getLogger().warning("ScreenRegistry not initialized!");
            return false;
        }

        ScreenDefinition definition = screenRegistry.getScreen(screenId);
        if (definition == null) {
            DisplayLib.getInstance().getLogger().warning("Screen not found: " + screenId);
            player.sendMessage("§cЭкран не найден: " + screenId);
            return false;
        }

        // Закрываем старый экран БЕЗ потери позиции (она уже снята выше)
        forceCloseScreen(player);

        ScreenInstance instance;
        if (yaw != null && pitch != null) {
            // Создаем с заданной ориентацией (для переключения экранов)
            instance = new ScreenInstance(screenId, definition, player, location, yaw, pitch, luaEngine);
        } else {
            // Создаем с автоматической ориентацией (для новых экранов)
            instance = new ScreenInstance(screenId, definition, player, location, luaEngine);
        }
        
        registerScreen(player, instance);

        DisplayLib.getInstance().getLogger().info(
                "Opened screen '" + screenId + "' for " + player.getName());
        return true;
    }

    /**
     * Закрыть экран игрока (вызывается из кнопки / Lua).
     */
    public void closeScreen(Player player) {
        ScreenInstance screen = activeScreens.get(player);
        if (screen != null) {
            // Вызываем tryClose для правильного порядка cleanup
            screen.tryClose();
        }
    }

    /**
     * Внутреннее закрытие — всегда удаляет entity.
     * Используется только из tryClose() и для принудительного закрытия.
     */
    public void forceCloseScreen(Player player) {
        ScreenInstance screen = activeScreens.get(player);
        if (screen != null) {
            screen.remove();
            unregisterScreen(player);
        }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public void registerScreen(Player player, ScreenInstance screenInstance) {
        activeScreens.put(player, screenInstance);
        startUpdateTaskIfNeeded();
    }

    public void unregisterScreen(Player player) {
        activeScreens.remove(player);
        if (activeScreens.isEmpty()) {
            stopUpdateTask();
        }
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ScreenInstance screen = activeScreens.get(player);
        if (screen == null) return;

        if (event.getAction() == Action.LEFT_CLICK_AIR
                || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Widget nearest = getNearestHovered(screen);
            if (nearest != null) {
                event.setCancelled(true);
                fireClickEvent(player, nearest);
            }
        }
    }

    @EventHandler
    public void onEntityAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ScreenInstance screen = activeScreens.get(player);
        if (screen == null) return;

        Widget nearest = getNearestHovered(screen);
        if (nearest != null) {
            event.setCancelled(true);
            fireClickEvent(player, nearest);
        }
    }

    private void fireClickEvent(Player player, Widget widget) {
        DisplayClickEvent clickEvent = new DisplayClickEvent(player, widget);
        Bukkit.getPluginManager().callEvent(clickEvent);
        if (!clickEvent.isCancelled()) {
            widget.handleClick();
        }
    }

    private Widget getNearestHovered(ScreenInstance screen) {
        Widget nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Widget widget : screen.children) {
            if (!widget.isHovered()) continue;
            Location loc = widget.getLocation();
            if (loc == null) continue;
            double dist = screen.viewer.getEyeLocation().distance(loc);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = widget;
            }
        }
        return nearest;
    }

    // -------------------------------------------------------------------------
    // Update loop
    // -------------------------------------------------------------------------

    private void startUpdateTaskIfNeeded() {
        if (isUpdateTaskRunning) return;
        updateTask = Bukkit.getScheduler().runTaskTimer(DisplayLib.getInstance(), () -> {
            for (ScreenInstance screen : activeScreens.values()) {
                if (screen != null) screen.update();
            }
        }, 0L, 5L);
        isUpdateTaskRunning = true;
    }

    private void stopUpdateTask() {
        if (isUpdateTaskRunning && updateTask != null) {
            updateTask.cancel();
            isUpdateTaskRunning = false;
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle events
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Очищаем storage данные игрока
        StorageAPI.clearPlayerData(event.getPlayer().getUniqueId());
        forceCloseScreen(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        forceCloseScreen(event.getEntity());
    }

    public void cleanup() {
        new HashMap<>(activeScreens).forEach((player, screen) -> {
            if (screen != null) screen.remove();
            unregisterScreen(player);
        });
        stopUpdateTask();
    }

    public boolean hasActiveScreens() {
        return !activeScreens.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Location defaultLocationFor(Player player) {
        return player.getLocation()
                .add(0, player.getHeight() / 2.0, 0)
                .add(player.getLocation().getDirection().multiply(2));
    }
}
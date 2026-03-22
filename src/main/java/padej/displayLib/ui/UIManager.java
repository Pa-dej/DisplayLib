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
import java.util.List;
import java.util.ArrayList;

public class UIManager implements Listener {
    // PRIVATE screens (existing behavior)
    private final Map<Player, ScreenInstance> privateScreens = new ConcurrentHashMap<>();
    private final Map<Player, BukkitTask> privateUpdateTasks = new ConcurrentHashMap<>();
    
    // PUBLIC screens (new)
    private final List<GlobalScreenInstance> publicScreens = new ArrayList<>();
    private final Map<GlobalScreenInstance, BukkitTask> publicUpdateTasks = new ConcurrentHashMap<>();
    
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
        return privateScreens.get(player);
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
        ScreenInstance current = privateScreens.get(player);
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

        // Проверяем тип экрана - только PERSONAL экраны можно открывать для игрока
        if (definition.getScreenType() != ScreenDefinition.ScreenType.PRIVATE) {
            player.sendMessage("§cЭтот экран не может быть открыт для игрока (тип: " + definition.getScreenType() + ")");
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
        ScreenInstance screen = privateScreens.get(player);
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
        ScreenInstance screen = privateScreens.get(player);
        if (screen != null) {
            screen.remove();
            unregisterScreen(player);
        }
    }

    // -------------------------------------------------------------------------
    // PUBLIC screen management
    // -------------------------------------------------------------------------

    /**
     * Открыть глобальный экран в указанной позиции
     */
    public boolean openPublicScreen(String screenId, Location location) {
        return openPublicScreen(screenId, location, location.getYaw(), location.getPitch());
    }

    /**
     * Открыть глобальный экран в указанной позиции с заданной ориентацией
     */
    public boolean openPublicScreen(String screenId, Location location, float yaw, float pitch) {
        if (screenRegistry == null) {
            DisplayLib.getInstance().getLogger().warning("ScreenRegistry not initialized!");
            return false;
        }

        ScreenDefinition definition = screenRegistry.getScreen(screenId);
        if (definition == null) {
            DisplayLib.getInstance().getLogger().warning("Screen not found: " + screenId);
            return false;
        }

        // Проверяем тип экрана - только PUBLIC экраны можно открывать публично
        if (definition.getScreenType() != ScreenDefinition.ScreenType.PUBLIC) {
            DisplayLib.getInstance().getLogger().warning("Screen " + screenId + " is not a PUBLIC screen (type: " + definition.getScreenType() + ")");
            return false;
        }

        // Проверяем, есть ли уже экран с таким ID - если да, закрываем его
        GlobalScreenInstance existingScreen = findPublicScreenById(screenId);
        if (existingScreen != null) {
            DisplayLib.getInstance().getLogger().info(
                    "Closing existing public screen '" + screenId + "' to recreate it");
            closeGlobalScreen(existingScreen);
        }

        GlobalScreenInstance instance = new GlobalScreenInstance(screenId, definition, location, yaw, pitch, luaEngine);
        registerPublicScreen(instance);

        DisplayLib.getInstance().getLogger().info(
                "Opened public screen '" + screenId + "' at " + location);
        return true;
    }

    /**
     * Закрыть публичный экран
     */
    public void closeGlobalScreen(GlobalScreenInstance screen) {
        if (screen != null) {
            screen.remove();
            unregisterPublicScreen(screen);
        }
    }

    /**
     * Принудительно закрыть публичный экран
     */
    public void forceCloseGlobalScreen(GlobalScreenInstance screen) {
        closeGlobalScreen(screen);
    }

    /**
     * Получить все публичные экраны
     */
    public List<GlobalScreenInstance> getPublicScreens() {
        return new ArrayList<>(publicScreens);
    }

    /**
     * Найти публичный экран по ID
     */
    public GlobalScreenInstance findPublicScreenById(String screenId) {
        for (GlobalScreenInstance screen : publicScreens) {
            if (screen.getScreenId().equals(screenId)) {
                return screen;
            }
        }
        return null;
    }

    /**
     * Закрыть публичный экран по ID
     */
    public boolean closePublicScreenById(String screenId) {
        GlobalScreenInstance screen = findPublicScreenById(screenId);
        if (screen != null) {
            closeGlobalScreen(screen);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public void registerScreen(Player player, ScreenInstance screenInstance) {
        privateScreens.put(player, screenInstance);
        startUpdateTaskForScreen(player, screenInstance);
    }

    public void unregisterScreen(Player player) {
        privateScreens.remove(player);
        stopUpdateTaskForScreen(player);
    }

    public void registerPublicScreen(GlobalScreenInstance screenInstance) {
        publicScreens.add(screenInstance);
        startUpdateTaskForPublicScreen(screenInstance);
    }

    public void unregisterPublicScreen(GlobalScreenInstance screenInstance) {
        publicScreens.remove(screenInstance);
        stopUpdateTaskForPublicScreen(screenInstance);
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Step 1: existing private screen logic (unchanged)
        ScreenInstance personal = privateScreens.get(player);
        if (personal != null) {
            if (event.getAction() == Action.LEFT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                Widget nearest = getNearestHovered(personal);
                if (nearest != null) {
                    event.setCancelled(true);
                    fireClickEvent(player, nearest);
                    return;
                }
            }
        }

        // Step 2: public screens
        if (event.getAction() == Action.LEFT_CLICK_AIR
                || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            for (GlobalScreenInstance global : publicScreens) {
                if (!global.getNearbyPlayers().contains(player)) continue;
                
                Widget hovered = global.getHoveredWidgetFor(player);
                if (hovered != null) {
                    event.setCancelled(true);
                    global.handleClickBy(player);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onEntityAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        
        // Step 1: existing private screen logic (unchanged)
        ScreenInstance personal = privateScreens.get(player);
        if (personal != null) {
            Widget nearest = getNearestHovered(personal);
            if (nearest != null) {
                event.setCancelled(true);
                fireClickEvent(player, nearest);
                return;
            }
        }

        // Step 2: public screens
        for (GlobalScreenInstance global : publicScreens) {
            if (!global.getNearbyPlayers().contains(player)) continue;
            
            Widget hovered = global.getHoveredWidgetFor(player);
            if (hovered != null) {
                event.setCancelled(true);
                global.handleClickBy(player);
                return;
            }
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

    private void startUpdateTaskForScreen(Player player, ScreenInstance screenInstance) {
        // Останавливаем предыдущую задачу если есть
        stopUpdateTaskForScreen(player);
        
        // Получаем tick_rate из определения экрана
        int tickRate = screenInstance.getDefinition().getTickRate();
        
        // Создаем новую задачу обновления для этого экрана
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(DisplayLib.getInstance(), () -> {
            ScreenInstance screen = privateScreens.get(player);
            if (screen != null) {
                screen.update();
            }
        }, 0L, tickRate);
        
        privateUpdateTasks.put(player, task);
    }

    private void stopUpdateTaskForScreen(Player player) {
        BukkitTask task = privateUpdateTasks.remove(player);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void startUpdateTaskForPublicScreen(GlobalScreenInstance screenInstance) {
        // Получаем tick_rate из определения экрана
        int tickRate = screenInstance.getDefinition().getTickRate();
        
        // Создаем новую задачу обновления для этого глобального экрана
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(DisplayLib.getInstance(), () -> {
            screenInstance.update();
        }, 0L, tickRate);
        
        publicUpdateTasks.put(screenInstance, task);
    }

    private void stopUpdateTaskForPublicScreen(GlobalScreenInstance screenInstance) {
        BukkitTask task = publicUpdateTasks.remove(screenInstance);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle events
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Очищаем storage данные игрока
        StorageAPI.clearPlayerData(player.getUniqueId());
        
        // Закрываем личный экран
        forceCloseScreen(player);
        
        // Убираем игрока из публичных экранов
        for (GlobalScreenInstance global : publicScreens) {
            global.getNearbyPlayers().remove(player);
            // Очищаем hover состояния для этого игрока
            Widget hoveredWidget = global.getHoveredWidgetFor(player);
            if (hoveredWidget != null) {
                player.clearTitle();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Закрываем личный экран
        forceCloseScreen(player);
        
        // Убираем игрока из публичных экранов
        for (GlobalScreenInstance global : publicScreens) {
            global.getNearbyPlayers().remove(player);
            Widget hoveredWidget = global.getHoveredWidgetFor(player);
            if (hoveredWidget != null) {
                player.clearTitle();
            }
        }
    }

    public void cleanup() {
        // Cleanup private screens
        new HashMap<>(privateScreens).forEach((player, screen) -> {
            if (screen != null) screen.remove();
            unregisterScreen(player);
        });
        
        // Cleanup public screens
        new ArrayList<>(publicScreens).forEach(this::closeGlobalScreen);
        
        // Останавливаем все оставшиеся задачи обновления
        privateUpdateTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        });
        privateUpdateTasks.clear();
        
        publicUpdateTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        });
        publicUpdateTasks.clear();
    }

    public boolean hasActiveScreens() {
        return !privateScreens.isEmpty() || !publicScreens.isEmpty();
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
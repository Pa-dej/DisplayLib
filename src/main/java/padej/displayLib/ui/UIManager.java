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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Реестр открытых экранов и обработчик ввода.
 *
 * <p>Всё состояние менеджера используется только из основного потока сервера
 * (события Bukkit, задачи планировщика, команды), поэтому синхронизация не нужна.</p>
 */
public class UIManager implements Listener {
    // PRIVATE screens: ключ - UUID игрока, чтобы не удерживать объект Player
    // и не зависеть от того, тот же ли это экземпляр после перезахода
    private final Map<UUID, ScreenInstance> privateScreens = new HashMap<>();
    private final Map<UUID, ScreenTicker> privateUpdateTasks = new HashMap<>();
    
    // PUBLIC screens. Список обходится в обработчиках событий, а скрипт по клику может
    // открыть или закрыть экран, поэтому используется copy-on-write: обход всегда
    // идёт по снимку и не ломается при изменении списка.
    private final List<GlobalScreenInstance> publicScreens = new CopyOnWriteArrayList<>();
    private final Map<GlobalScreenInstance, ScreenTicker> publicUpdateTasks = new IdentityHashMap<>();
    
    // Одна задача планировщика на все экраны вместо отдельной задачи на каждый экран
    private final List<ScreenTicker> tickers = new ArrayList<>();
    private BukkitTask tickTask;
    private boolean tickersDirty = false;

    /** Обновление одного экрана со своим периодом (tick_rate). */
    private static final class ScreenTicker {
        final Runnable update;
        final int period;
        int countdown = 1; // первое обновление - на ближайшем тике
        boolean cancelled = false;

        ScreenTicker(Runnable update, int period) {
            this.update = update;
            this.period = period;
        }
    }
    
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
        return privateScreens.get(player.getUniqueId());
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
        ScreenInstance current = getActiveScreen(player);
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
        ScreenInstance screen = getActiveScreen(player);
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
        ScreenInstance screen = getActiveScreen(player);
        if (screen != null) {
            // Сначала снимаем регистрацию: если удаление сущностей бросит исключение,
            // в реестре не останется "мёртвый" экран с работающей задачей обновления
            unregisterScreen(player);
            screen.remove();
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
            unregisterPublicScreen(screen);
            screen.remove();
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
        privateScreens.put(player.getUniqueId(), screenInstance);
        startUpdateTaskForScreen(player.getUniqueId(), screenInstance);
    }

    public void unregisterScreen(Player player) {
        UUID playerId = player.getUniqueId();
        privateScreens.remove(playerId);
        stopUpdateTaskForScreen(playerId);
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
        if (event.getAction() != Action.LEFT_CLICK_AIR
                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        
        if (handleLeftClick(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        
        if (handleLeftClick(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Общая обработка левого клика (по воздуху, блоку или сущности).
     *
     * @return true, если клик пришёлся на виджет и исходное событие нужно отменить
     */
    private boolean handleLeftClick(Player player) {
        // Быстрый выход: без открытых экранов событие нас не касается
        if (privateScreens.isEmpty() && publicScreens.isEmpty()) return false;
        
        // Step 1: PRIVATE screen (с учётом interaction_radius - одинаково для всех видов клика)
        ScreenInstance personal = privateScreens.get(player.getUniqueId());
        if (personal != null && personal.checkPlayerInInteractionRange()) {
            Widget nearest = personal.getNearestHoveredWidget();
            if (nearest != null) {
                fireClickEvent(player, nearest);
                return true;
            }
        }

        // Step 2: public screens
        for (GlobalScreenInstance global : publicScreens) {
            if (!global.isNearby(player)) continue;
            
            if (global.getHoveredWidgetFor(player) != null) {
                global.handleClickBy(player);
                return true;
            }
        }
        return false;
    }

    private void fireClickEvent(Player player, Widget widget) {
        DisplayClickEvent clickEvent = new DisplayClickEvent(player, widget);
        Bukkit.getPluginManager().callEvent(clickEvent);
        if (!clickEvent.isCancelled()) {
            widget.handleClick();
        }
    }

    // -------------------------------------------------------------------------
    // Update loop
    // -------------------------------------------------------------------------

    private void startUpdateTaskForScreen(UUID playerId, ScreenInstance screenInstance) {
        // Останавливаем предыдущее обновление если есть
        stopUpdateTaskForScreen(playerId);
        
        privateUpdateTasks.put(playerId, startTicker(screenInstance.getDefinition().getTickRate(), () -> {
            // Обновляем только если этот экран всё ещё активен у игрока
            if (privateScreens.get(playerId) == screenInstance) {
                screenInstance.update();
            }
        }));
    }

    private void stopUpdateTaskForScreen(UUID playerId) {
        stopTicker(privateUpdateTasks.remove(playerId));
    }

    private void startUpdateTaskForPublicScreen(GlobalScreenInstance screenInstance) {
        publicUpdateTasks.put(screenInstance,
                startTicker(screenInstance.getDefinition().getTickRate(), screenInstance::update));
    }

    private void stopUpdateTaskForPublicScreen(GlobalScreenInstance screenInstance) {
        stopTicker(publicUpdateTasks.remove(screenInstance));
    }

    private ScreenTicker startTicker(int tickRate, Runnable update) {
        ScreenTicker ticker = new ScreenTicker(update, Math.max(1, tickRate));
        tickers.add(ticker);
        
        // Общая задача работает, только пока есть хотя бы один экран
        if (tickTask == null) {
            tickTask = Bukkit.getScheduler().runTaskTimer(DisplayLib.getInstance(), this::tickScreens, 0L, 1L);
        }
        return ticker;
    }

    private void stopTicker(ScreenTicker ticker) {
        if (ticker != null) {
            // Из списка убирается после обхода: остановка может прийти изнутри обновления экрана
            ticker.cancelled = true;
            tickersDirty = true;
        }
    }

    private void tickScreens() {
        // Обход по индексу: во время обновления экраны могут открываться (добавляются в конец) и закрываться
        for (int i = 0; i < tickers.size(); i++) {
            ScreenTicker ticker = tickers.get(i);
            if (ticker.cancelled || --ticker.countdown > 0) continue;
            
            ticker.countdown = ticker.period;
            try {
                ticker.update.run();
            } catch (Exception e) {
                // Ошибка одного экрана не должна останавливать обновление остальных
                DisplayLib.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Error updating screen", e);
            }
        }
        
        if (tickersDirty) {
            tickersDirty = false;
            for (int i = tickers.size() - 1; i >= 0; i--) {
                if (tickers.get(i).cancelled) {
                    tickers.remove(i);
                }
            }
        }
        
        if (tickers.isEmpty() && tickTask != null) {
            tickTask.cancel();
            tickTask = null;
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
        
        releasePlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        releasePlayer(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // Экран остался в прежнем мире - игроку он больше недоступен
        releasePlayer(event.getPlayer());
    }

    /**
     * Закрыть личный экран игрока и убрать игрока из состояния публичных экранов.
     */
    private void releasePlayer(Player player) {
        forceCloseScreen(player);
        
        for (GlobalScreenInstance global : publicScreens) {
            global.removePlayer(player);
        }
    }

    public void cleanup() {
        // Cleanup private screens
        for (ScreenInstance screen : new ArrayList<>(privateScreens.values())) {
            try {
                screen.remove();
            } catch (Exception e) {
                DisplayLib.getInstance().getLogger().warning("Failed to remove screen '" + screen.getScreenId() + "': " + e.getMessage());
            }
        }
        privateScreens.clear();
        
        // Cleanup public screens
        for (GlobalScreenInstance screen : publicScreens) {
            try {
                screen.remove();
            } catch (Exception e) {
                DisplayLib.getInstance().getLogger().warning("Failed to remove public screen '" + screen.getScreenId() + "': " + e.getMessage());
            }
        }
        publicScreens.clear();
        
        // Останавливаем обновление экранов
        privateUpdateTasks.clear();
        publicUpdateTasks.clear();
        tickers.forEach(ticker -> ticker.cancelled = true);
        tickers.clear();
        tickersDirty = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
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
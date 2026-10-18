package padej.displayLib.ui;

import padej.displayLib.DisplayLib;
import padej.displayLib.api.events.DisplayClickEvent;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.ScreenRegistry;
import padej.displayLib.data.PublicScreenStore;
import padej.displayLib.script.JumperEngine;
import padej.displayLib.script.api.StorageAPI;
import padej.displayLib.ui.widgets.Widget;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        /** Каждый тик: кадровые анимации текста (может быть null) */
        final Runnable everyTick;
        final int period;
        int countdown = 1; // первое обновление - на ближайшем тике
        boolean cancelled = false;

        ScreenTicker(Runnable update, Runnable everyTick, int period) {
            this.update = update;
            this.everyTick = everyTick;
            this.period = period;
        }
    }
    
    private ScreenRegistry screenRegistry;
    private JumperEngine scriptEngine;

    /** Расстановка публичных экранов на диске (null, пока плагин не включён) */
    private PublicScreenStore publicStore;
    /** Сохранённые публичные экраны, чей мир ещё не загружен: ставятся по WorldLoadEvent */
    private final List<PublicScreenStore.Placement> pendingPublic = new ArrayList<>();

    // Защита от открытия экранов из onClose там, где экран тут же потерялся бы:
    /** игроки, которые сейчас выходят с сервера (onClose("quit")) */
    private final Set<UUID> quitting = new HashSet<>();
    /** id публичных экранов, которые сейчас заменяются (onClose("replaced")) */
    private final Set<String> replacingPublic = new HashSet<>();
    /** плагин выключается (onClose("shutdown")) */
    private boolean shuttingDown = false;

    private UIManager() {
        Bukkit.getPluginManager().registerEvents(this, DisplayLib.getInstance());
    }

    private static class Holder {
        static final UIManager INSTANCE = new UIManager();
    }
    
    public static UIManager getInstance() {
        return Holder.INSTANCE;
    }

    public void initialize(ScreenRegistry screenRegistry, JumperEngine scriptEngine) {
        initialize(screenRegistry, scriptEngine, null);
    }

    /**
     * @param publicStore файл расстановки публичных экранов; {@code null} - экраны не сохраняются
     */
    public void initialize(ScreenRegistry screenRegistry, JumperEngine scriptEngine, PublicScreenStore publicStore) {
        this.screenRegistry = screenRegistry;
        this.scriptEngine = scriptEngine;
        this.publicStore = publicStore;
        this.shuttingDown = false;
        // Читаем сразу, при включении: если другой плагин поставит публичный экран раньше первого тика,
        // запись в файл не затрёт остальные сохранённые экраны
        if (publicStore != null) publicStore.load();
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

        if (shuttingDown || quitting.contains(player.getUniqueId()) || !player.isOnline()) {
            // из onClose("quit"/"shutdown"): экран некому показывать, он остался бы висеть
            DisplayLib.getInstance().getLogger().warning("Screen '" + screenId + "' is not opened for "
                    + player.getName() + ": the player is leaving or the plugin is shutting down");
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

        // onClose("switch") текущего экрана - до создания нового: его onOpen идёт после.
        // Скрипт в onClose может сам закрыть экран, поэтому текущий берётся заново.
        ScreenInstance previous = getActiveScreen(player);
        if (previous != null) previous.fireClose(CloseReason.SWITCH);
        ScreenInstance current = getActiveScreen(player);
        ScreenInstance instance;
        if (current != null && definition.getMorph().isEnabled()
                && current.getLocation().getWorld() == location.getWorld()) {
            // Morph: новый экран забирает сущности старого и анимирует их к своему состоянию.
            // Старый экран снимается с регистрации сразу, его скрипт и подсказки - в remove().
            unregisterScreen(player);
            current.handOffMorph();
            instance = new ScreenInstance(screenId, definition, player, location, yaw, pitch, scriptEngine, current);
            current.remove();
        } else {
            // Закрываем старый экран (позиция уже снята в switchScreen)
            if (current != null) closeScreen(current, CloseReason.SWITCH);
            if (yaw != null && pitch != null) {
                // Создаем с заданной ориентацией (для переключения экранов)
                instance = new ScreenInstance(screenId, definition, player, location, yaw, pitch, scriptEngine);
            } else {
                // Создаем с автоматической ориентацией (для новых экранов)
                instance = new ScreenInstance(screenId, definition, player, location, scriptEngine);
            }
        }
        
        registerScreen(player, instance);

        DisplayLib.getInstance().getLogger().info(
                "Opened screen '" + screenId + "' for " + player.getName());
        return true;
    }

    /**
     * Закрыть экран игрока (кнопка, скрипт, команда, Java API); onClose получает {@code "close"}.
     */
    public void closeScreen(Player player) {
        closeScreen(player, CloseReason.CLOSE);
    }

    /** Закрыть экран игрока с указанной причиной (её получает onClose). */
    public void closeScreen(Player player, CloseReason reason) {
        ScreenInstance screen = getActiveScreen(player);
        if (screen != null) {
            closeScreen(screen, reason);
        }
    }

    /**
     * Закрыть конкретный экран: {@code onClose(reason)} (ровно один раз), снять с регистрации,
     * удалить сущности (с анимацией, если у экрана включён morph).
     *
     * <p>Работает с экземпляром, а не с «текущим экраном игрока»: если скрипт в onClose
     * открыл игроку другой экран, тот не будет закрыт по ошибке.</p>
     */
    public void closeScreen(ScreenInstance screen, CloseReason reason) {
        Player player = screen.getViewer();
        try {
            screen.fireClose(reason);
        } finally {
            // Регистрация снимается и при исключении: в реестре не остаётся "мёртвый" экран с задачей обновления
            if (privateScreens.get(player.getUniqueId()) == screen) {
                unregisterScreen(player);
            }
            screen.removeAnimated();
        }
    }

    /**
     * То же, что {@link #closeScreen(Player)}. Раньше закрывало без onClose; теперь onClose
     * вызывается при любом закрытии.
     */
    public void forceCloseScreen(Player player) {
        closeScreen(player, CloseReason.CLOSE);
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
     * Открыть глобальный экран в указанной позиции с заданной ориентацией - до выключения сервера,
     * как и раньше. Чтобы экран вставал на место после перезапуска, -
     * {@link #openPublicScreen(String, Location, float, float, boolean)} с {@code persistent = true}
     * (так делает {@code /displaylib openpublic}).
     */
    public boolean openPublicScreen(String screenId, Location location, float yaw, float pitch) {
        return openPublicScreen(screenId, location, yaw, pitch, false);
    }

    /**
     * Открыть глобальный экран.
     *
     * @param persistent {@code true} - запомнить в {@code data/public_screens.jmc} и ставить заново
     *                   при каждом запуске сервера, пока экран не закроют явно; {@code false} - только
     *                   до выключения (для плагинов, которые сами расставляют свои экраны при включении)
     */
    public boolean openPublicScreen(String screenId, Location location, float yaw, float pitch, boolean persistent) {
        if (shuttingDown || replacingPublic.contains(screenId) || location.getWorld() == null) {
            DisplayLib.getInstance().getLogger().warning("Public screen '" + screenId + "' is not opened"
                    + (location.getWorld() == null ? ": location has no world" : ": called from onClose of the same screen or during shutdown"));
            return false;
        }
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
            unregisterPublicScreen(existingScreen);
            replacingPublic.add(screenId);
            try {
                existingScreen.remove(CloseReason.REPLACED);
            } finally {
                replacingPublic.remove(screenId);
            }
        }

        GlobalScreenInstance instance = new GlobalScreenInstance(screenId, definition, location, yaw, pitch, scriptEngine, persistent);
        registerPublicScreen(instance);
        pendingPublic.removeIf(p -> p.id().equals(screenId));
        if (publicStore != null) {
            if (persistent) {
                publicStore.put(new PublicScreenStore.Placement(screenId, location.getWorld().getName(),
                        location.getX(), location.getY(), location.getZ(), yaw, pitch));
            } else {
                publicStore.remove(screenId); // заменил сохранённый экран несохраняемым
            }
        }

        DisplayLib.getInstance().getLogger().info(
                "Opened public screen '" + screenId + "' at " + location);
        return true;
    }

    /**
     * Закрыть публичный экран (onClose получает {@code "close"}). Сохранённый экран
     * забывается: после перезапуска он не появится.
     */
    public void closeGlobalScreen(GlobalScreenInstance screen) {
        if (screen != null) {
            unregisterPublicScreen(screen);
            if (publicStore != null) publicStore.remove(screen.getScreenId());
            screen.remove(CloseReason.CLOSE);
        }
    }

    /**
     * Поставить сохранённые публичные экраны (включение плагина). Экраны из ещё не загруженных
     * миров ставятся, когда мир загрузится; экраны, файла которых нет, пропускаются, но
     * из сохранения не удаляются - на случай, если файл временно убран или не разобрался.
     */
    public void restorePublicScreens() {
        if (publicStore == null) return;
        pendingPublic.clear();
        int restored = 0;
        for (PublicScreenStore.Placement p : publicStore.all()) {
            if (findPublicScreenById(p.id()) != null) continue; // уже поставлен (другим плагином при включении)
            World world = Bukkit.getWorld(p.world());
            if (world == null) {
                pendingPublic.add(p);
                continue;
            }
            if (restore(p, world)) restored++;
        }
        if (restored > 0 || !pendingPublic.isEmpty()) {
            DisplayLib.getInstance().getLogger().info("Restored " + restored + " public screen(s)"
                    + (pendingPublic.isEmpty() ? "" : ", " + pendingPublic.size() + " waiting for their world to load"));
        }
    }

    private boolean restore(PublicScreenStore.Placement p, World world) {
        if (screenRegistry == null || screenRegistry.getScreen(p.id()) == null) {
            DisplayLib.getInstance().getLogger().warning("Saved public screen '" + p.id()
                    + "' is not restored: no such screen (kept in data/public_screens.jmc)");
            return false;
        }
        Location location = new Location(world, p.x(), p.y(), p.z());
        // При запуске чанк экрана обычно не загружен: загружаем, чтобы сущности встали.
        // Потом при выгрузке чанка они исчезают и возвращаются, когда рядом появятся игроки (ensureSpawned).
        world.getChunkAt(location);
        return openPublicScreen(p.id(), location, p.yaw(), p.pitch(), true);
    }

    /**
     * Мир выгружается (Multiverse и т.п.): его публичные экраны снимаются (onClose("unload")),
     * сохранённые ждут, пока мир загрузится снова.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        for (GlobalScreenInstance screen : publicScreens) {
            Location loc = screen.getLocation();
            if (loc.getWorld() != world) continue;
            unregisterPublicScreen(screen);
            screen.remove(CloseReason.WORLD_UNLOAD);
            PublicScreenStore.Placement saved = publicStore != null ? publicStore.get(screen.getScreenId()) : null;
            if (screen.isPersistent() && saved != null) pendingPublic.add(saved);
        }
    }

    /** Мир загрузился позже плагина (Multiverse и т.п.) или снова: поставить ждущие его экраны. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (pendingPublic.isEmpty()) return;
        World world = event.getWorld();
        List<PublicScreenStore.Placement> ready = new ArrayList<>();
        pendingPublic.removeIf(p -> p.world().equals(world.getName()) && ready.add(p));
        for (PublicScreenStore.Placement p : ready) {
            if (findPublicScreenById(p.id()) == null) restore(p, world);
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
        // сохранённый экран, чей мир ещё не загружен: убрать из ожидающих и из сохранённых
        boolean pending = pendingPublic.removeIf(p -> p.id().equals(screenId));
        if (publicStore != null && publicStore.get(screenId) != null) {
            publicStore.remove(screenId);
            return true;
        }
        return pending;
    }

    /** Сохранённые публичные экраны, которые ждут загрузки своего мира. */
    public List<PublicScreenStore.Placement> getPendingPublicScreens() {
        return new ArrayList<>(pendingPublic);
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
                fireClickEvent(personal, player, nearest);
                return true;
            }
        }

        // Step 2: public screens
        for (GlobalScreenInstance global : publicScreens) {
            if (!global.isNearby(player)) continue;
            
            if (global.getHoveredWidgetFor(player) != null) {
                Widget widget = global.widgetUnder(player);
                if (widget != null) {
                    DisplayClickEvent clickEvent = new DisplayClickEvent(player, widget,
                            global.getScreenId(), global.idOf(widget), true);
                    Bukkit.getPluginManager().callEvent(clickEvent);
                    if (!clickEvent.isCancelled()) {
                        global.handleClickOn(player, widget);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void fireClickEvent(ScreenInstance screen, Player player, Widget widget) {
        DisplayClickEvent clickEvent = new DisplayClickEvent(player, widget, screen.getScreenId(), screen.idOf(widget), false);
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
        }, screenInstance::tickAnimations));
    }

    private void stopUpdateTaskForScreen(UUID playerId) {
        stopTicker(privateUpdateTasks.remove(playerId));
    }

    private void startUpdateTaskForPublicScreen(GlobalScreenInstance screenInstance) {
        publicUpdateTasks.put(screenInstance,
                startTicker(screenInstance.getDefinition().getTickRate(), screenInstance::update, screenInstance::tickAnimations));
    }

    private void stopUpdateTaskForPublicScreen(GlobalScreenInstance screenInstance) {
        stopTicker(publicUpdateTasks.remove(screenInstance));
    }

    private ScreenTicker startTicker(int tickRate, Runnable update, Runnable everyTick) {
        ScreenTicker ticker = new ScreenTicker(update, everyTick, Math.max(1, tickRate));
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
            if (ticker.cancelled) continue;
            if (ticker.everyTick != null) {
                try {
                    ticker.everyTick.run();
                } catch (Exception e) {
                    DisplayLib.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Error ticking screen animations", e);
                }
            }
            if (--ticker.countdown > 0) continue;
            
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

        // Сначала onClose("quit") - он ещё может писать в storage, - потом сохранение и выгрузка данных.
        // Пока идёт onClose, новые экраны этому игроку не открываются (они остались бы висеть).
        quitting.add(player.getUniqueId());
        try {
            releasePlayer(player, CloseReason.QUIT);
        } finally {
            quitting.remove(player.getUniqueId());
        }
        StorageAPI.clearPlayerData(player.getUniqueId());
    }

    private padej.displayLib.config.PluginConfig.CloseOn closeOn() {
        padej.displayLib.config.PluginConfig c = DisplayLib.getInstance() instanceof DisplayLib d ? d.getPluginConfig() : null;
        return c != null ? c.closeOn() : padej.displayLib.config.PluginConfig.CloseOn.DEFAULTS;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (closeOn().death()) releasePlayer(event.getEntity(), CloseReason.DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // Экран остался в прежнем мире - игроку он больше недоступен
        if (closeOn().worldChange()) releasePlayer(event.getPlayer(), CloseReason.WORLD_CHANGE);
        else closeScreen(event.getPlayer(), CloseReason.WORLD_CHANGE);
    }

    /** Телепорт дальше closeOn.teleport блоков: экран остался на старом месте, закрываем. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        double limit = closeOn().teleport();
        if (limit <= 0 || !privateScreens.containsKey(event.getPlayer().getUniqueId())) return;
        org.bukkit.Location to = event.getTo();
        if (to == null) return;
        if (!to.getWorld().equals(event.getFrom().getWorld()) || to.distanceSquared(event.getFrom()) > limit * limit) {
            closeScreen(event.getPlayer(), CloseReason.TELEPORT);
        }
    }

    /** Открытие инвентаря (сундук, верстак, GUI другого плагина) - если включено в closeOn. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!closeOn().inventory() || !(event.getPlayer() instanceof Player p)) return;
        if (privateScreens.containsKey(p.getUniqueId())) closeScreen(p, CloseReason.INVENTORY);
    }

    /**
     * Закрыть личный экран игрока и убрать игрока из состояния публичных экранов.
     */
    private void releasePlayer(Player player, CloseReason reason) {
        closeScreen(player, reason);
        
        for (GlobalScreenInstance global : publicScreens) {
            global.removePlayer(player);
        }
    }

    public void cleanup() {
        shuttingDown = true; // onClose("shutdown") не может открыть новые экраны
        // Cleanup private screens: onClose("shutdown"), затем удаление
        for (ScreenInstance screen : new ArrayList<>(privateScreens.values())) {
            try {
                screen.fireClose(CloseReason.SHUTDOWN);
            } catch (Exception e) {
                DisplayLib.getInstance().getLogger().warning("onClose of screen '" + screen.getScreenId() + "' failed: " + e.getMessage());
            }
            try {
                screen.remove();
            } catch (Exception e) {
                DisplayLib.getInstance().getLogger().warning("Failed to remove screen '" + screen.getScreenId() + "': " + e.getMessage());
            }
        }
        privateScreens.clear();
        
        // Cleanup public screens
        // Публичные экраны остаются в data/public_screens.jmc и встанут обратно при запуске
        for (GlobalScreenInstance screen : publicScreens) {
            try {
                screen.remove(CloseReason.SHUTDOWN);
            } catch (Exception e) {
                DisplayLib.getInstance().getLogger().warning("Failed to remove public screen '" + screen.getScreenId() + "': " + e.getMessage());
            }
        }
        publicScreens.clear();
        pendingPublic.clear();
        MorphTransition.cancelAll();
        
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
package padej.displayLib.config;

import padej.displayLib.DisplayLib;
import padej.displayLib.pack.ContentPack;
import padej.displayLib.pack.ContentPacks;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Реестр экранов с поддержкой hot reload.
 *
 * <p>Поток наблюдения за файлами только замечает изменения; сам разбор {@code .jmc} и
 * обновление реестра выполняются в основном потоке сервера, так что игровой код видит
 * реестр только в согласованном состоянии.</p>
 */
public class ScreenRegistry {
    /** Пауза для накопления событий: редакторы сохраняют файл несколькими операциями подряд */
    private static final long DEBOUNCE_MILLIS = 150;

    private final DisplayLib plugin;
    private final ScreenLoader screenLoader;
    private final boolean hotReload;
    private final Map<String, ScreenDefinition> screens = new ConcurrentHashMap<>();
    
    /** Набор -> (имя файла без расширения -> id экрана из этого файла); только основной поток */
    private final Map<ContentPack, Map<String, String>> screenIdByFile = new HashMap<>();
    /** Папки screens/ наборов под наблюдением hot reload */
    private final Map<WatchKey, ContentPack> watched = new ConcurrentHashMap<>();
    
    private volatile WatchService watchService;
    private Thread watchThread;
    private volatile boolean watching = false;
    
    /**
     * @param hotReload следить ли за папкой screens/ (config.jmc: hotReload)
     */
    public ScreenRegistry(DisplayLib plugin, boolean hotReload) {
        this.plugin = plugin;
        this.hotReload = hotReload;
        this.screenLoader = new ScreenLoader(plugin);
    }
    
    /**
     * Инициализация реестра - загрузка всех экранов
     */
    public void initialize() {
        screens.putAll(loadAllPacks());
        analyze();
        
        plugin.getLogger().info("Loaded " + screens.size() + " screen(s)");
        
        // Запускаем hot reload, если он не отключен в config.jmc
        if (hotReload) {
            startHotReload();
        }
    }
    
    /**
     * Получить экран по ID
     */
    public ScreenDefinition getScreen(String screenId) {
        return screens.get(screenId);
    }
    
    /**
     * Проверить существование экрана
     */
    public boolean hasScreen(String screenId) {
        return screens.containsKey(screenId);
    }
    
    /**
     * Получить все экраны (неизменяемое представление, без копирования)
     */
    public Map<String, ScreenDefinition> getAllScreens() {
        return Collections.unmodifiableMap(screens);
    }
    
    /**
     * Перезагрузить все экраны
     */
 /**
     * Экраны всех наборов. id общие: при совпадении остаётся экран набора, зарегистрированного раньше
     * (DisplayLib - первым).
     */
    private Map<String, ScreenDefinition> loadAllPacks() {
        Map<String, ScreenDefinition> all = new java.util.LinkedHashMap<>();
        for (ContentPack pack : ContentPacks.all()) {
            for (ScreenDefinition screen : screenLoader.loadAllScreens(pack).values()) {
                ScreenDefinition previous = all.putIfAbsent(screen.getId(), screen);
                if (previous != null) warnDuplicate(screen, previous);
            }
        }
        return all;
    }

    private void warnDuplicate(ScreenDefinition skipped, ScreenDefinition kept) {
        plugin.getLogger().warning("Screen id '" + skipped.getId() + "' of content pack " + packName(skipped)
                + " is already used by " + packName(kept) + "; skipped");
    }

    private static String packName(ScreenDefinition s) {
        return s.getPack() != null ? s.getPack().name() : "DisplayLib";
    }

    /** Набор добавлен: загрузить его экраны и следить за его screens/. */
    public void addPack(ContentPack pack) {
        int added = 0;
        for (ScreenDefinition screen : screenLoader.loadAllScreens(pack).values()) {
            ScreenDefinition previous = screens.get(screen.getId());
            if (previous != null && previous.getPack() != pack) { warnDuplicate(screen, previous); continue; }
            screens.put(screen.getId(), screen);
            added++;
        }
        analyze();
        plugin.getLogger().info("Loaded " + added + " screen(s) of content pack " + pack.name());
        if (hotReload) watch(pack);
    }

    /** Набор снят: убрать его экраны (открытые доживают до закрытия). */
    public void removePack(ContentPack pack) {
        screens.values().removeIf(s -> s.getPack() == pack);
        screenIdByFile.remove(pack);
        watched.entrySet().removeIf(e -> { if (e.getValue() == pack) { e.getKey().cancel(); return true; } return false; });
        analyze();
    }

    public void reloadAll() {
        Map<String, ScreenDefinition> loadedScreens = loadAllPacks();
        
        // Без промежуточной очистки: в реестре не возникает момента, когда экранов нет вовсе
        screens.putAll(loadedScreens);
        screens.keySet().retainAll(loadedScreens.keySet());
        screenIdByFile.clear();
        analyze();
        
        plugin.getLogger().info("Reloaded " + screens.size() + " screen(s)");
    }

    /**
     * Статический разбор всех экранов: список не тикаемых виджетов ({@link ScreenAnalyzer}).
     * Пересчитывается целиком при любой загрузке: цели {@code switchTo} зависят от других экранов.
     */
    /** Отчёт о проблемах экранов для {@code /displaylib validate}. */
    public java.util.List<String> validate() {
        return ScreenAnalyzer.report(screens, this::scriptsOf, this::accessOf, plugin.getLogger());
    }

    private Path scriptsOf(ScreenDefinition screen) {
        ContentPack pack = screen.getPack() != null ? screen.getPack() : ContentPacks.main();
        return pack != null ? pack.scripts() : plugin.getDataFolder().toPath().resolve("scripts").toAbsolutePath().normalize();
    }

    private me.padej.jumper.runtime.Access accessOf(ScreenDefinition screen) {
        return plugin.getScriptEngine() != null ? plugin.getScriptEngine().access(screen.getPack()) : null;
    }

    public void analyze() {
        try {
            ScreenAnalyzer.analyze(screens, this::scriptsOf, this::accessOf, plugin.getLogger());
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Screen analysis failed", e);
        }
    }
    
    /**
     * Перезагрузить экран из файла {@code <fileId>.jmc}.
     * Вызывать из основного потока.
     *
     * @param fileId имя файла экрана без расширения
     */
    public void reloadScreen(String fileId) {
        reloadScreen(ContentPacks.main(), fileId);
    }

    /** Перезагрузить экран из файла {@code <fileId>.jmc} набора {@code pack}. Основной поток. */
    public void reloadScreen(ContentPack pack, String fileId) {
        if (pack == null) return;
        ScreenDefinition screen = screenLoader.loadScreen(pack, fileId);
        Map<String, String> byFile = screenIdByFile.computeIfAbsent(pack, k -> new HashMap<>());
        String previousId = byFile.get(fileId);
        
        if (screen != null) {
            // Экран регистрируется под id из файла (как при полной загрузке), а не под именем файла
            String screenId = screen.getId() != null ? screen.getId() : fileId;
            ScreenDefinition other = screens.get(screenId);
            if (other != null && other.getPack() != null && other.getPack() != pack) {
                warnDuplicate(screen, other);
                return;
            }
            if (previousId != null && !previousId.equals(screenId)) {
                screens.remove(previousId);
            }
            screens.put(screenId, screen);
            byFile.put(fileId, screenId);
            analyze();
            plugin.getLogger().info("Reloaded screen: " + screenId);
            return;
        }
        
        Path directory = pack.screens();
        boolean fileExists = Files.exists(directory.resolve(fileId + ScreenLoader.EXTENSION));
        
        if (fileExists) {
            // Файл есть, но не читается (например, сохранён на середине правки) -
            // оставляем прежнюю версию экрана
            plugin.getLogger().warning("Screen file '" + fileId + "' could not be loaded, keeping previous version");
            return;
        }
        
        String removedId = previousId != null ? previousId : fileId;
        byFile.remove(fileId);
        ScreenDefinition current = screens.get(removedId);
        if (current != null && (current.getPack() == null || current.getPack() == pack) && screens.remove(removedId) != null) {
            analyze(); // переходы на удалённый экран стали «в никуда»
            plugin.getLogger().info("Removed screen: " + removedId);
        }
    }
    
    /**
     * Запуск hot reload мониторинга
     */
    private void startHotReload() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            for (ContentPack pack : ContentPacks.all()) watch(pack);
            
            watching = true;
            watchThread = new Thread(this::watchForChanges, "ScreenRegistry-HotReload");
            watchThread.setDaemon(true);
            watchThread.start();
            
            plugin.getLogger().info("Hot reload enabled for screens directory");
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to start hot reload", e);
        }
    }
    
    /** Следить за screens/ набора. */
    private void watch(ContentPack pack) {
        WatchService service = watchService;
        if (service == null) return;
        try {
            WatchKey key = pack.screens().register(service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            watched.put(key, pack);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Cannot watch " + pack.screens(), e);
        }
    }

    /**
     * Мониторинг изменений файлов (отдельный поток)
     */
    private void watchForChanges() {
        WatchService service = watchService;
        
        while (watching) {
            try {
                Map<ContentPack, Set<String>> changedFiles = new HashMap<>();
                
                WatchKey key = service.take();
                
                // Собираем все события за короткий интервал: одно сохранение файла
                // обычно порождает несколько событий MODIFY подряд
                while (key != null) {
                    ContentPack pack = watched.get(key);
                    if (pack != null) collectChangedFiles(key, changedFiles.computeIfAbsent(pack, k -> new HashSet<>()));
                    else key.pollEvents();
                    if (!key.reset()) {
                        watched.remove(key); // папку удалили или набор снят
                    }
                    key = service.poll(DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
                }
                
                if (!changedFiles.isEmpty() && plugin.isEnabled()) {
                    // Разбор .jmc и изменение реестра - в основном потоке
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Map.Entry<ContentPack, Set<String>> e : changedFiles.entrySet()) {
                            if (!ContentPacks.all().contains(e.getKey())) continue; // набор уже снят
                            for (String fileId : e.getValue()) reloadScreen(e.getKey(), fileId);
                        }
                    });
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception e) {
                if (!watching || !plugin.isEnabled()) {
                    break; // плагин выключается
                }
                plugin.getLogger().log(Level.WARNING, "Error in hot reload watcher", e);
            }
        }
    }
    
    private void collectChangedFiles(WatchKey key, Set<String> changedFiles) {
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }
            
            String fileName = event.context().toString();
            
            // Обрабатываем только файлы экранов
            if (fileName.endsWith(ScreenLoader.EXTENSION)) {
                changedFiles.add(fileName.substring(0, fileName.length() - ScreenLoader.EXTENSION.length()));
            }
        }
    }
    
    /**
     * Остановка hot reload
     */
    public void shutdown() {
        watching = false;
        
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        
        WatchService service = watchService;
        watchService = null;
        if (service != null) {
            try {
                service.close();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close watch service", e);
            }
        }
    }
    
    /**
     * Получить загрузчик экранов (для команд)
     */
    public ScreenLoader getScreenLoader() {
        return screenLoader;
    }
}

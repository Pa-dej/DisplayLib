package padej.displayLib.config;

import padej.displayLib.DisplayLib;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Реестр экранов с поддержкой hot reload
 */
public class ScreenRegistry {
    private final DisplayLib plugin;
    private final ScreenLoader screenLoader;
    private final Map<String, ScreenDefinition> screens = new ConcurrentHashMap<>();
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watching = false;
    
    public ScreenRegistry(DisplayLib plugin) {
        this.plugin = plugin;
        this.screenLoader = new ScreenLoader(plugin);
    }
    
    /**
     * Инициализация реестра - загрузка всех экранов
     */
    public void initialize() {
        // Загружаем все экраны
        Map<String, ScreenDefinition> loadedScreens = screenLoader.loadAllScreens();
        screens.putAll(loadedScreens);
        
        plugin.getLogger().info("Loaded " + screens.size() + " screen(s)");
        
        // Запускаем hot reload если включен debug режим
        if (isDebugMode()) {
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
     * Получить все экраны
     */
    public Map<String, ScreenDefinition> getAllScreens() {
        return Map.copyOf(screens);
    }
    
    /**
     * Перезагрузить все экраны
     */
    public void reloadAll() {
        screens.clear();
        Map<String, ScreenDefinition> loadedScreens = screenLoader.loadAllScreens();
        screens.putAll(loadedScreens);
        
        plugin.getLogger().info("Reloaded " + screens.size() + " screen(s)");
    }
    
    /**
     * Перезагрузить конкретный экран
     */
    public void reloadScreen(String screenId) {
        ScreenDefinition screen = screenLoader.loadScreen(screenId);
        if (screen != null) {
            screens.put(screenId, screen);
            plugin.getLogger().info("Reloaded screen: " + screenId);
        } else {
            screens.remove(screenId);
            plugin.getLogger().info("Removed screen: " + screenId);
        }
    }
    
    /**
     * Запуск hot reload мониторинга
     */
    private void startHotReload() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path screensDir = screenLoader.getScreensDirectory();
            
            screensDir.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            
            watching = true;
            watchThread = new Thread(this::watchForChanges, "ScreenRegistry-HotReload");
            watchThread.setDaemon(true);
            watchThread.start();
            
            plugin.getLogger().info("Hot reload enabled for screens directory");
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to start hot reload", e);
        }
    }
    
    /**
     * Мониторинг изменений файлов
     */
    private void watchForChanges() {
        while (watching) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    String fileName = filename.toString();
                    
                    // Обрабатываем только YAML файлы
                    if (!fileName.endsWith(".yml") && !fileName.endsWith(".yaml")) {
                        continue;
                    }
                    
                    String screenId = fileName.replaceAll("\\.(yml|yaml)$", "");
                    
                    // Небольшая задержка для завершения записи файла
                    Thread.sleep(100);
                    
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        screens.remove(screenId);
                        plugin.getLogger().info("Hot reload: Removed screen " + screenId);
                    } else {
                        // CREATE или MODIFY
                        reloadScreen(screenId);
                        plugin.getLogger().info("Hot reload: Updated screen " + screenId);
                    }
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error in hot reload watcher", e);
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
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close watch service", e);
            }
        }
    }
    
    /**
     * Проверка debug режима
     */
    private boolean isDebugMode() {
        // Можно добавить в config.yml или plugin.yml
        // Пока что всегда включен для разработки
        return true;
    }
}
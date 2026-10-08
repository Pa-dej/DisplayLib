package padej.displayLib.config;

import padej.displayLib.DisplayLib;
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
 * <p>Поток наблюдения за файлами только замечает изменения; сам разбор YAML и
 * обновление реестра выполняются в основном потоке сервера. Так загрузчик
 * (экземпляр SnakeYAML не потокобезопасен) никогда не используется из двух потоков
 * одновременно, а игровой код видит реестр только в согласованном состоянии.</p>
 */
public class ScreenRegistry {
    /** Пауза для накопления событий: редакторы сохраняют файл несколькими операциями подряд */
    private static final long DEBOUNCE_MILLIS = 150;

    private final DisplayLib plugin;
    private final ScreenLoader screenLoader;
    private final Map<String, ScreenDefinition> screens = new ConcurrentHashMap<>();
    
    /** Имя файла (без расширения) -> id экрана из этого файла; только основной поток */
    private final Map<String, String> screenIdByFile = new HashMap<>();
    
    private volatile WatchService watchService;
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
        
        // Запускаем hot reload, если он не отключен в config.yml
        if (isHotReloadEnabled()) {
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
    public void reloadAll() {
        Map<String, ScreenDefinition> loadedScreens = screenLoader.loadAllScreens();
        
        // Без промежуточной очистки: в реестре не возникает момента, когда экранов нет вовсе
        screens.putAll(loadedScreens);
        screens.keySet().retainAll(loadedScreens.keySet());
        screenIdByFile.clear();
        
        plugin.getLogger().info("Reloaded " + screens.size() + " screen(s)");
    }
    
    /**
     * Перезагрузить экран из файла {@code <fileId>.yml} / {@code <fileId>.yaml}.
     * Вызывать из основного потока.
     *
     * @param fileId имя файла экрана без расширения
     */
    public void reloadScreen(String fileId) {
        ScreenDefinition screen = screenLoader.loadScreen(fileId);
        String previousId = screenIdByFile.get(fileId);
        
        if (screen != null) {
            // Экран регистрируется под id из YAML (как при полной загрузке), а не под именем файла
            String screenId = screen.getId() != null ? screen.getId() : fileId;
            if (previousId != null && !previousId.equals(screenId)) {
                screens.remove(previousId);
            }
            screens.put(screenId, screen);
            screenIdByFile.put(fileId, screenId);
            plugin.getLogger().info("Reloaded screen: " + screenId);
            return;
        }
        
        Path directory = screenLoader.getScreensDirectory();
        boolean fileExists = Files.exists(directory.resolve(fileId + ".yml"))
                || Files.exists(directory.resolve(fileId + ".yaml"));
        
        if (fileExists) {
            // Файл есть, но не читается (например, сохранён на середине правки) -
            // оставляем прежнюю версию экрана
            plugin.getLogger().warning("Screen file '" + fileId + "' could not be loaded, keeping previous version");
            return;
        }
        
        String removedId = previousId != null ? previousId : fileId;
        screenIdByFile.remove(fileId);
        if (screens.remove(removedId) != null) {
            plugin.getLogger().info("Removed screen: " + removedId);
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
     * Мониторинг изменений файлов (отдельный поток)
     */
    private void watchForChanges() {
        WatchService service = watchService;
        
        while (watching) {
            try {
                Set<String> changedFiles = new HashSet<>();
                
                WatchKey key = service.take();
                
                // Собираем все события за короткий интервал: одно сохранение файла
                // обычно порождает несколько событий MODIFY подряд
                while (key != null) {
                    collectChangedFiles(key, changedFiles);
                    if (!key.reset()) {
                        watching = false;
                        break;
                    }
                    key = service.poll(DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
                }
                
                if (!changedFiles.isEmpty() && plugin.isEnabled()) {
                    // Разбор YAML и изменение реестра - в основном потоке
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (String fileId : changedFiles) {
                            reloadScreen(fileId);
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
            
            // Обрабатываем только YAML файлы
            if (fileName.endsWith(".yml")) {
                changedFiles.add(fileName.substring(0, fileName.length() - ".yml".length()));
            } else if (fileName.endsWith(".yaml")) {
                changedFiles.add(fileName.substring(0, fileName.length() - ".yaml".length()));
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
     * Включён ли hot reload (config.yml, ключ {@code hot-reload}; по умолчанию включён)
     */
    private boolean isHotReloadEnabled() {
        return plugin.getConfig().getBoolean("hot-reload", true);
    }
    
    /**
     * Получить загрузчик экранов (для команд)
     */
    public ScreenLoader getScreenLoader() {
        return screenLoader;
    }
}

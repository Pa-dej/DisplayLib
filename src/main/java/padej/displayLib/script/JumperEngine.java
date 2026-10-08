package padej.displayLib.script;

import me.padej.jumper.interp.Interpreter;
import me.padej.jumper.runtime.Access;
import padej.displayLib.DisplayLib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Движок скриптов экранов на Jumper.
 *
 * <p>Три вида файлов, три расширения: {@code .jmp} - скрипты (в {@code scripts/}),
 * {@code .jmc} - конфиги (экраны и {@code config.jmc}), {@code .jma} - политика доступа
 * ({@code scripts.jma}).</p>
 *
 * <p>Каждый открытый экран получает собственный {@link Interpreter} ({@link ScreenScript}):
 * у Jumper глобалы ({@code player}, {@code screen}, ...) привязываются к скрипту при разборе,
 * а они у каждого экрана свои. Текст скрипта кэшируется и перечитывается, когда файл на
 * диске меняется (время изменения и размер), так что правка скрипта подхватывается при
 * следующем открытии экрана.</p>
 *
 * <p>Политика доступа одна на все скрипты и читается из {@code plugins/DisplayLib/scripts.jma};
 * если файла нет, он создаётся из ресурсов плагина. Без политики скрипт видел бы всю JVM.</p>
 *
 * <p>Сторожевой таймер ({@link Watchdog}) прерывает скрипт, который работает дольше
 * {@code scriptTimeoutMs} из {@code config.jmc}: бесконечный цикл в обработчике больше
 * не вешает сервер.</p>
 */
public final class JumperEngine {
    public static final String POLICY_FILE = "scripts.jma";

    private final DisplayLib plugin;
    private final Path scriptsDirectory;
    private final Path policyFile;
    private final Watchdog watchdog;
    private final Map<String, CachedSource> sources = new ConcurrentHashMap<>();
    private volatile Access access;

    private record CachedSource(long lastModified, long size, String text) {
    }

    public JumperEngine(DisplayLib plugin, long scriptTimeoutMillis) {
        this.plugin = plugin;
        Path data = plugin.getDataFolder().toPath();
        this.scriptsDirectory = data.resolve("scripts").toAbsolutePath().normalize();
        this.policyFile = data.resolve(POLICY_FILE);
        this.watchdog = new Watchdog(plugin, scriptTimeoutMillis);

        try {
            Files.createDirectories(scriptsDirectory);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create scripts directory", e);
        }
        this.access = loadPolicy();
        watchdog.start();
    }

    public Path getScriptsDirectory() {
        return scriptsDirectory;
    }

    public Path getPolicyFile() {
        return policyFile;
    }

    /** Текущая политика доступа. */
    public Access getAccess() {
        return access;
    }

    Watchdog watchdog() {
        return watchdog;
    }

    DisplayLib plugin() {
        return plugin;
    }

    /**
     * Создать скрипт для экрана: новый интерпретатор с политикой и глобалами, разбор
     * файла и выполнение его верхнего уровня (объявления функций и переменных).
     *
     * @param scriptFile путь относительно {@code scripts/}
     * @param globals    глобалы скрипта ({@code player}, {@code screen}, ...); значение может быть {@code null}
     * @return скрипт или {@code null}, если файл не найден или не разбирается (причина в логе)
     */
    public ScreenScript load(String scriptFile, Map<String, Object> globals) {
        Path path = resolve(scriptFile);
        if (path == null) return null;

        String source = read(scriptFile, path);
        if (source == null) return null;

        Interpreter interpreter = new Interpreter().access(access).cancellable(true);
        for (Map.Entry<String, Object> e : globals.entrySet()) {
            interpreter.define(e.getKey(), e.getValue());
        }
        return ScreenScript.create(this, interpreter, scriptFile, path, source);
    }

    /** Абсолютный путь скрипта внутри {@code scripts/}; {@code null}, если путь выходит за её пределы. */
    public Path resolve(String scriptFile) {
        Path path = scriptsDirectory.resolve(scriptFile).normalize();
        if (!path.startsWith(scriptsDirectory)) {
            plugin.getLogger().warning("Script path is outside of scripts directory: " + scriptFile);
            return null;
        }
        return path;
    }

    private String read(String scriptFile, Path path) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            sources.remove(scriptFile);
            plugin.getLogger().warning("Script file not found: " + scriptFile);
            return null;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Cannot read script " + scriptFile, e);
            return null;
        }

        long lastModified = attributes.lastModifiedTime().toMillis();
        long size = attributes.size();
        CachedSource cached = sources.get(scriptFile);
        if (cached != null && cached.lastModified() == lastModified && cached.size() == size) {
            return cached.text();
        }

        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            sources.put(scriptFile, new CachedSource(lastModified, size, text));
            return text;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Cannot read script " + scriptFile, e);
            return null;
        }
    }

    /**
     * Перечитать политику и сбросить кэш исходников ({@code /displaylib reload}).
     * Уже открытые экраны продолжают работать со старыми скриптами.
     */
    public void reload() {
        sources.clear();
        access = loadPolicy();
        plugin.getLogger().info("Script policy reloaded, source cache cleared");
    }

    /** Остановить сторожевой таймер (выключение плагина). */
    public void shutdown() {
        watchdog.stop();
    }

    private Access loadPolicy() {
        if (!Files.exists(policyFile)) {
            saveDefaultPolicy();
        }
        try {
            Access loaded = Access.load(policyFile);
            plugin.getLogger().info("Script access policy loaded from " + POLICY_FILE);
            return loaded;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, POLICY_FILE + " could not be read; scripts get an empty policy "
                    + "(no Java access at all) until it is fixed: " + e.getMessage());
            Access fallback = Access.none();
            fallback.allowPackage("padej.displayLib.script.api");
            return fallback;
        }
    }

    private void saveDefaultPolicy() {
        try (InputStream in = plugin.getResource(POLICY_FILE)) {
            if (in == null) {
                plugin.getLogger().warning("Default " + POLICY_FILE + " is missing from the plugin jar");
                return;
            }
            Files.createDirectories(policyFile.getParent());
            Files.copy(in, policyFile);
            plugin.getLogger().info("Created default " + POLICY_FILE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write default " + POLICY_FILE, e);
        }
    }
}

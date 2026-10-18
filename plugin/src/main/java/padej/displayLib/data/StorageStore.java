package padej.displayLib.data;

import me.padej.jumper.interp.Config;
import me.padej.jumper.runtime.JTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Данные глобала {@code storage}: по игроку и по публичному экрану.
 *
 * <p>Файлы - {@code .jmc} в {@code plugins/DisplayLib/data/storage/}:
 * {@code players/<uuid>.jmc} и {@code public/<screenId>.jmc}, внутри одна переменная
 * {@code dyn data = { "key": value, ... };}. Их можно читать и править руками при выключенном сервере.</p>
 *
 * <ul>
 * <li>Игрок: данные читаются с диска при первом обращении (открытие экрана), пишутся при выходе
 *     (после {@code onClose}) и выгружаются из памяти.</li>
 * <li>Публичный экран: данные общие для всех экземпляров экрана с этим id, живут до выключения.</li>
 * <li>Автосохранение - раз в {@code storage.autosaveSeconds} (config.jmc) только изменившихся данных;
 *     при выключении плагина сохраняется всё.</li>
 * </ul>
 *
 * <p>Снимок данных (текст файла) делается в основном потоке, сама запись - в отдельном потоке
 * по очереди, так что записи одного файла не обгоняют друг друга. Все методы, кроме записи,
 * вызываются из основного потока.</p>
 */
public final class StorageStore {
    static final String VAR = "data";
    private static final String HEADER = "// DisplayLib storage - значения глобала storage (не правьте при запущенном сервере)\n";

    /** Хранилище только в памяти: до установки настоящего (тесты, ранний доступ) или при storage.persist = false. */
    private static StorageStore active = new StorageStore(null, Logger.getLogger("DisplayLib"));

    private final Path root;
    private final Logger log;
    private final Map<String, Entry> entries = new HashMap<>();
    private final ExecutorService writer;
    /**
     * Записи в очереди: файл → текст. Если игрок перезашёл раньше, чем записались его данные,
     * они читаются отсюда, а не из устаревшего файла.
     */
    private final Map<Path, String> pending = new ConcurrentHashMap<>();

    private static final class Entry {
        final Path file;
        final Map<String, Object> data;
        /** Текст последней записи (или прочитанного файла): запись без изменений пропускается;
         *  null после неудачной записи - следующее сохранение повторит её */
        volatile String written;

        Entry(Path file, Map<String, Object> data) {
            this.file = file;
            this.data = data;
        }
    }

    /**
     * @param root папка {@code data/storage}; {@code null} - только память, на диск ничего не пишется
     */
    public StorageStore(Path root, Logger log) {
        this.root = root;
        this.log = log;
        this.writer = root == null ? null : Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DisplayLib-Storage");
            t.setDaemon(true);
            return t;
        });
    }

    public static StorageStore get() {
        return active;
    }

    /** Сделать хранилище текущим (включение плагина). */
    public static void install(StorageStore store) {
        active = store;
    }

    public boolean isPersistent() {
        return root != null;
    }

    /** Данные игрока (загружаются с диска при первом обращении). */
    public Map<String, Object> player(UUID playerId) {
        return entry("player:" + playerId, root != null ? root.resolve("players").resolve(playerId + ".jmc") : null).data;
    }

    /** Общие данные публичного экрана с этим id. */
    public Map<String, Object> publicScreen(String screenId) {
        return entry("public:" + screenId, root != null ? root.resolve("public").resolve(fileName(screenId) + ".jmc") : null).data;
    }

    /** Игрок вышел: сохранить его данные (если изменились) и выгрузить из памяти. */
    public void releasePlayer(UUID playerId) {
        Entry e = entries.remove("player:" + playerId);
        if (e != null) save(e, true);
    }

    /** Сохранить изменившиеся данные (автосохранение). */
    public void saveAll() {
        for (Entry e : entries.values()) save(e, true);
    }

    /** Выключение: дождаться очереди записи и синхронно сохранить всё оставшееся. */
    public void shutdown() {
        if (writer != null) {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warning("Storage writes did not finish in 10 s, saving synchronously");
                    writer.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        for (Entry e : entries.values()) save(e, false);
        entries.clear();
    }

    // -------------------------------------------------------------------------

    private Entry entry(String key, Path file) {
        Entry e = entries.get(key);
        if (e == null) {
            e = new Entry(file, load(file));
            e.written = file != null ? render(e) : null;
            entries.put(key, e);
        }
        return e;
    }

    private Map<String, Object> load(Path file) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (file == null) return data;
        String queued = pending.get(file);
        if (queued == null && !Files.exists(file)) return data;
        try {
            Object top = queued != null ? Config.parse(queued) : Config.load(file);
            Object v = top instanceof JTable t ? t.get(VAR) : null;
            if (v instanceof JTable table) {
                for (Map.Entry<Object, Object> e : table.asMap().entrySet()) {
                    data.put(String.valueOf(e.getKey()), e.getValue());
                }
            } else if (v != null) {
                throw new IOException("'" + VAR + "' is not a table");
            }
        } catch (IOException | RuntimeException ex) {
            // Файл испорчен: не затираем его пустыми данными, а откладываем в сторону
            Path broken = file.resolveSibling(file.getFileName() + ".broken");
            log.warning("Storage file " + file + " could not be read (" + ex.getMessage() + "), moved to " + broken.getFileName());
            try {
                Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                log.log(Level.WARNING, "Could not move broken storage file " + file, moveError);
            }
            data.clear();
        }
        return data;
    }

    private String render(Entry e) {
        return render(e, null);
    }

    private String render(Entry e, List<String> skipped) {
        return HEADER + "dyn " + VAR + " = " + JmcWriter.table(e.data, skipped) + ";\n";
    }

    private void save(Entry e, boolean async) {
        if (e.file == null) return;
        List<String> skipped = new ArrayList<>();
        String content = render(e, skipped);
        if (content.equals(e.written)) return;
        e.written = content;
        if (!skipped.isEmpty()) {
            log.warning("Storage " + e.file.getFileName() + ": not saved (only numbers, strings, booleans, null, arrays and tables can be): " + skipped);
        }
        Runnable write = () -> {
            try {
                JmcWriter.writeAtomically(e.file, content);
            } catch (IOException ex) {
                e.written = null; // повторить при следующем сохранении
                log.log(Level.WARNING, "Failed to save storage " + e.file, ex);
            } finally {
                pending.remove(e.file, content);
            }
        };
        pending.put(e.file, content);
        if (async && writer != null && !writer.isShutdown()) writer.execute(write);
        else write.run();
    }

    /** Имя файла из id экрана: только безопасные символы, иначе - с хэшем, чтобы разные id не совпали. */
    static String fileName(String id) {
        String safe = id.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.equals(id) && !safe.startsWith(".") ? safe : safe + "-" + Integer.toHexString(id.hashCode());
    }
}

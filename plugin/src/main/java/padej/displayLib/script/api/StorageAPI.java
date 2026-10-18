package padej.displayLib.script.api;

import padej.displayLib.data.StorageStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Хранилище «ключ - значение»: глобал {@code storage}.
 *
 * <p>У приватного экрана - данные игрока, у публичного - общие данные экрана (по его id).
 * Данные сохраняются на диск ({@code plugins/DisplayLib/data/storage/}) и переживают выход
 * игрока и перезапуск сервера (см. {@link StorageStore}; отключается {@code storage.persist} в config.jmc).
 * Сохраняются числа, строки, {@code boolean}, {@code null}, массивы и таблицы; объекты
 * (игрок, виджет, экземпляры классов скрипта) - нет.</p>
 *
 * <pre>
 * int visits = storage.get("visits", 0) + 1;
 * storage.set("visits", visits);
 * if (!storage.has("greeted")) { player.message("Добро пожаловать!"); storage.set("greeted", true); }
 * </pre>
 */
public final class StorageAPI {
    private final Map<String, Object> storage;

    private StorageAPI(Map<String, Object> storage) {
        this.storage = storage;
    }

    /** Хранилище игрока. */
    public static StorageAPI forPlayer(UUID playerId) {
        return new StorageAPI(StorageStore.get().player(playerId));
    }

    /** Общее хранилище публичного экрана. */
    public static StorageAPI forPublicScreen(String screenId) {
        return new StorageAPI(StorageStore.get().publicScreen(screenId));
    }

    /**
     * @param playerId владелец; {@code null} - отдельное несохраняемое хранилище
     * @deprecated {@link #forPlayer(UUID)} / {@link #forPublicScreen(String)}
     */
    @Deprecated
    public StorageAPI(UUID playerId) {
        this(playerId != null ? StorageStore.get().player(playerId) : new HashMap<>());
    }

    public Object get(String key) {
        return storage.get(key);
    }

    /** Значение или {@code defaultValue}, если ключа нет. */
    public Object get(String key, Object defaultValue) {
        Object value = storage.get(key);
        return value != null ? value : defaultValue;
    }

    /** Записать значение; {@code null} удаляет ключ. */
    public void set(String key, Object value) {
        if (value == null) {
            storage.remove(key);
        } else {
            storage.put(key, value);
        }
    }

    public boolean has(String key) {
        return storage.containsKey(key);
    }

    public void remove(String key) {
        storage.remove(key);
    }

    public void clear() {
        storage.clear();
    }

    public int size() {
        return storage.size();
    }

    /** Игрок вышел: сохранить его данные на диск и выгрузить из памяти. */
    public static void clearPlayerData(UUID playerId) {
        StorageStore.get().releasePlayer(playerId);
    }

    @Override
    public String toString() {
        return "storage" + storage;
    }
}

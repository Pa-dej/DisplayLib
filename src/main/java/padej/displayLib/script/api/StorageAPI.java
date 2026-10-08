package padej.displayLib.script.api;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище «ключ - значение» на игрока: глобал {@code storage}.
 *
 * <p>Данные живут в памяти сервера, переживают повторное открытие экрана и
 * очищаются при выходе игрока и при перезапуске сервера. У публичного экрана
 * хранилище своё и пустое на каждый экземпляр экрана.</p>
 *
 * <pre>
 * int visits = storage.get("visits", 0) + 1;
 * storage.set("visits", visits);
 * if (!storage.has("greeted")) { player.message("Добро пожаловать!"); storage.set("greeted", true); }
 * </pre>
 */
public final class StorageAPI {
    private static final Map<UUID, Map<String, Object>> PLAYER_STORAGE = new ConcurrentHashMap<>();

    private final Map<String, Object> storage;

    /** @param playerId владелец; {@code null} - отдельное хранилище публичного экрана */
    public StorageAPI(UUID playerId) {
        this.storage = playerId != null
                ? PLAYER_STORAGE.computeIfAbsent(playerId, k -> new HashMap<>())
                : new HashMap<>();
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

    /** Забыть данные игрока (выход с сервера). */
    public static void clearPlayerData(UUID playerId) {
        PLAYER_STORAGE.remove(playerId);
    }

    @Override
    public String toString() {
        return "storage" + storage;
    }
}

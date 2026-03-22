package padej.displayLib.lua.api;

import padej.displayLib.DisplayLib;
import org.bukkit.entity.Player;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua API для хранения данных между сессиями.
 * 
 * <p>Предоставляет постоянное хранилище данных для каждого игрока.
 * Данные сохраняются между открытиями экранов, но сбрасываются при выходе игрока с сервера.</p>
 * 
 * <h2>Доступные методы в Lua:</h2>
 * 
 * <p><b>Основные операции:</b></p>
 * <ul>
 * <li><b>storage.get(key)</b> - Получить значение по ключу</li>
 * <li><b>storage.get(key, default)</b> - Получить значение или значение по умолчанию</li>
 * <li><b>storage.set(key, value)</b> - Установить значение</li>
 * <li><b>storage.has(key)</b> - Проверить существование ключа</li>
 * <li><b>storage.remove(key)</b> - Удалить ключ</li>
 * <li><b>storage.clear()</b> - Очистить все данные</li>
 * </ul>
 * 
 * <h2>Поддерживаемые типы данных:</h2>
 * <ul>
 * <li>Строки (string)</li>
 * <li>Числа (number)</li>
 * <li>Логические значения (boolean)</li>
 * <li>nil (для удаления)</li>
 * </ul>
 * 
 * <h2>Примеры использования в Lua:</h2>
 * <pre>{@code
 * -- Сохранение настроек игрока
 * storage.set("language", "ru")
 * storage.set("music_volume", 0.8)
 * storage.set("show_hints", true)
 * 
 * -- Получение настроек
 * local lang = storage.get("language", "en")  -- "ru" или "en" по умолчанию
 * local volume = storage.get("music_volume")  -- 0.8 или nil
 * local hints = storage.get("show_hints", true)  -- true
 * 
 * -- Проверка существования
 * if storage.has("first_visit") then
 *     player.message("С возвращением!")
 * else
 *     storage.set("first_visit", true)
 *     player.message("Добро пожаловать!")
 * end
 * 
 * -- Счетчики
 * local visits = storage.get("visit_count", 0)
 * storage.set("visit_count", visits + 1)
 * 
 * -- Удаление данных
 * storage.remove("temporary_data")
 * 
 * -- Полная очистка (осторожно!)
 * storage.clear()
 * }</pre>
 * 
 * <p><b>Примечание:</b> Данные хранятся в памяти и очищаются при выходе игрока.
 * Для постоянного хранения используйте файлы или базы данных через внешние плагины.</p>
 * 
 * @author DisplayLib
 * @version 1.0
 */
public class StorageAPI extends LuaTable {
    // Глобальное хранилище для всех игроков
    private static final Map<UUID, Map<String, Object>> playerStorage = new ConcurrentHashMap<>();
    
    private final Player player;
    private final DisplayLib plugin;
    private final Map<String, Object> storage;
    
    public StorageAPI(Player player, DisplayLib plugin) {
        this.player = player;
        this.plugin = plugin;
        
        // Для публичных экранов player может быть null
        if (player != null) {
            this.storage = playerStorage.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        } else {
            // Для публичных экранов используем пустое хранилище
            this.storage = new HashMap<>();
        }
        
        // get(key) или get(key, default)
        set("get", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue key, LuaValue defaultValue) {
                String keyStr = key.checkjstring();
                Object value = storage.get(keyStr);
                
                if (value == null) {
                    return defaultValue.isnil() ? LuaValue.NIL : defaultValue;
                }
                
                return toLuaValue(value);
            }
        });
        
        // set(key, value)
        set("set", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue key, LuaValue value) {
                String keyStr = key.checkjstring();
                Object javaValue = toJavaValue(value);
                
                if (javaValue == null) {
                    storage.remove(keyStr);
                } else {
                    storage.put(keyStr, javaValue);
                }
                
                return LuaValue.NIL;
            }
        });
        
        // has(key)
        set("has", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue key) {
                String keyStr = key.checkjstring();
                return LuaValue.valueOf(storage.containsKey(keyStr));
            }
        });
        
        // remove(key)
        set("remove", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue key) {
                String keyStr = key.checkjstring();
                storage.remove(keyStr);
                return LuaValue.NIL;
            }
        });
        
        // clear()
        set("clear", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                storage.clear();
                return LuaValue.NIL;
            }
        });
    }
    
    /**
     * Очистить данные игрока при выходе
     */
    public static void clearPlayerData(UUID playerId) {
        playerStorage.remove(playerId);
    }
    
    /**
     * Конвертация Java объекта в LuaValue
     */
    private LuaValue toLuaValue(Object obj) {
        if (obj == null) return LuaValue.NIL;
        if (obj instanceof String) return LuaValue.valueOf((String) obj);
        if (obj instanceof Number) return LuaValue.valueOf(((Number) obj).doubleValue());
        if (obj instanceof Boolean) return LuaValue.valueOf((Boolean) obj);
        return LuaValue.NIL;
    }
    
    /**
     * Конвертация LuaValue в Java объект
     */
    private Object toJavaValue(LuaValue value) {
        if (value.isnil()) return null;
        if (value.isstring()) return value.tojstring();
        if (value.isnumber()) return value.todouble();
        if (value.isboolean()) return value.toboolean();
        return null;
    }
}
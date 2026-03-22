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
 * Lua API для хранения данных между сессиями
 * Данные живут между открытиями экрана, но сбрасываются при выходе игрока
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
        
        // Для глобальных экранов player может быть null
        if (player != null) {
            this.storage = playerStorage.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        } else {
            // Для глобальных экранов используем пустое хранилище
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
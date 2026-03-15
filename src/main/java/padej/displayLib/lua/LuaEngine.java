package padej.displayLib.lua;

import padej.displayLib.DisplayLib;
import padej.displayLib.ui.ScreenInstance;
import org.bukkit.entity.Player;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Движок для выполнения Lua скриптов экранов
 */
public class LuaEngine {
    private final DisplayLib plugin;
    private final Path scriptsDirectory;
    private final ConcurrentHashMap<String, LuaValue> compiledScripts = new ConcurrentHashMap<>();
    
    public LuaEngine(DisplayLib plugin) {
        this.plugin = plugin;
        this.scriptsDirectory = plugin.getDataFolder().toPath().resolve("scripts");
        
        try {
            Files.createDirectories(scriptsDirectory);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create scripts directory", e);
        }
    }
    
    /**
     * Создать изолированную Lua среду для экрана
     */
    public LuaContext createContext(ScreenInstance screen, Player player) {
        Globals globals = JsePlatform.standardGlobals();
        
        // Ограничиваем доступ к опасным функциям
        restrictGlobals(globals);
        
        // Создаем контекст с API
        LuaContext context = new LuaContext(globals, screen, player, plugin);
        
        // Инжектим API объекты
        globals.set("player", context.getPlayerAPI());
        globals.set("screen", context.getScreenAPI());
        globals.set("widget", LuaValue.NIL); // будет установлен при клике
        globals.set("storage", context.getStorageAPI());
        globals.set("timer", context.getTimerAPI());
        globals.set("log", context.getLogAPI());
        
        return context;
    }
    
    /**
     * Загрузить и скомпилировать скрипт
     */
    public LuaValue loadScript(String scriptPath) {
        return compiledScripts.computeIfAbsent(scriptPath, path -> {
            try {
                Path fullPath = scriptsDirectory.resolve(path);
                if (!Files.exists(fullPath)) {
                    plugin.getLogger().warning("Script file not found: " + path);
                    return LuaValue.NIL;
                }
                
                String content = Files.readString(fullPath);
                Globals globals = JsePlatform.standardGlobals();
                return globals.load(content, path);
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load script: " + path, e);
                return LuaValue.NIL;
            }
        });
    }
    
    /**
     * Выполнить функцию в контексте
     */
    public boolean callFunction(LuaContext context, String scriptPath, String functionName, LuaValue... args) {
        try {
            Path fullPath = scriptsDirectory.resolve(scriptPath);
            if (!Files.exists(fullPath)) {
                plugin.getLogger().warning("Script file not found: " + scriptPath);
                return false;
            }
            
            String content = Files.readString(fullPath);
            
            // Загружаем скрипт в контекст пользователя (с API)
            LuaValue script = context.getGlobals().load(content, scriptPath);
            if (script.isnil()) {
                plugin.getLogger().warning("Failed to load script: " + scriptPath);
                return false;
            }
            
            // Выполняем скрипт в контексте для загрузки функций
            script.call();
            
            // Вызываем функцию
            LuaValue function = context.getGlobals().get(functionName);
            if (function.isfunction()) {
                plugin.getLogger().info("Calling Lua function: " + functionName + " in " + scriptPath);
                function.invoke(args);
                return true;
            } else {
                plugin.getLogger().warning("Function not found: " + functionName + " in " + scriptPath);
                return false;
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, 
                "Error calling Lua function " + functionName + " in " + scriptPath, e);
            return false;
        }
    }
    
    /**
     * Ограничить доступ к опасным функциям
     */
    private void restrictGlobals(Globals globals) {
        // Удаляем опасные функции
        globals.set("io", LuaValue.NIL);
        globals.set("os", LuaValue.NIL);
        globals.set("package", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("dofile", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
        globals.set("load", LuaValue.NIL);
        
        // Оставляем только безопасные стандартные библиотеки
        // math, string, table, ipairs, pairs, type, tostring, tonumber, pcall, error остаются
    }
    
    /**
     * Очистить кэш скриптов (для hot reload)
     */
    public void clearCache() {
        compiledScripts.clear();
        plugin.getLogger().info("Lua script cache cleared");
    }
    
    /**
     * Очистить кэш конкретного скрипта
     */
    public void clearScript(String scriptPath) {
        compiledScripts.remove(scriptPath);
        plugin.getLogger().info("Cleared Lua script from cache: " + scriptPath);
    }
}
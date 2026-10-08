package padej.displayLib.lua;

import padej.displayLib.DisplayLib;
import padej.displayLib.ui.GlobalScreenInstance;
import padej.displayLib.ui.ScreenInstance;
import org.bukkit.entity.Player;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Движок для выполнения Lua скриптов экранов.
 *
 * <p>Скрипт компилируется в {@link Prototype} один раз и кэшируется; каждый экран
 * получает собственное замыкание над общим прототипом и своим окружением. Прототип
 * неизменяем, поэтому его безопасно разделять между экранами. Кэш сам замечает
 * изменение файла на диске (по времени изменения и размеру), так что правки скрипта
 * подхватываются при следующем открытии экрана без повторной компиляции в остальных
 * случаях.</p>
 */
public class LuaEngine {
    private final DisplayLib plugin;
    private final Path scriptsDirectory;
    private final ConcurrentHashMap<String, CachedScript> compiledScripts = new ConcurrentHashMap<>();

    /** Скомпилированный скрипт и состояние файла, из которого он получен. */
    private record CachedScript(long lastModified, long size, Prototype prototype) {
    }

    public LuaEngine(DisplayLib plugin) {
        this.plugin = plugin;
        this.scriptsDirectory = plugin.getDataFolder().toPath().resolve("scripts").toAbsolutePath().normalize();

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
        LuaContext context = new LuaContext(newSandbox(), screen, player, plugin);
        injectApi(context, context.getScreenAPI());
        return context;
    }

    /**
     * Создать изолированную Lua среду для публичного экрана
     */
    public GlobalLuaContext createGlobalContext(GlobalScreenInstance screen, Player player) {
        GlobalLuaContext context = new GlobalLuaContext(newSandbox(), screen, player, plugin);
        injectApi(context, context.getScreenAPI());
        return context;
    }

    private Globals newSandbox() {
        Globals globals = JsePlatform.standardGlobals();
        restrictGlobals(globals);
        return globals;
    }

    private void injectApi(BaseLuaContext context, LuaValue screenApi) {
        Globals globals = context.getGlobals();
        globals.set("player", context.getPlayerAPI() != null ? context.getPlayerAPI() : LuaValue.NIL);
        globals.set("screen", screenApi);
        globals.set("widget", LuaValue.NIL); // будет установлен при клике
        globals.set("storage", context.getStorageAPI());
        globals.set("timer", context.getTimerAPI());
        globals.set("log", context.getLogAPI());
    }

    /**
     * Выполнить функцию скрипта в контексте экрана.
     * Отсутствие функции считается ошибкой и попадает в лог.
     *
     * @return true, если функция найдена и выполнена без ошибок
     */
    public boolean callFunction(BaseLuaContext context, String scriptPath, String functionName, LuaValue... args) {
        return invoke(context, scriptPath, functionName, true, args);
    }

    /**
     * Выполнить необязательную функцию скрипта (например, on_open или on_close).
     * Если функции в скрипте нет, вызов молча пропускается.
     *
     * @return true, если функция найдена и выполнена без ошибок
     */
    public boolean callOptionalFunction(BaseLuaContext context, String scriptPath, String functionName, LuaValue... args) {
        return invoke(context, scriptPath, functionName, false, args);
    }

    private boolean invoke(BaseLuaContext context, String scriptPath, String functionName,
                           boolean required, LuaValue... args) {
        try {
            Globals globals = context.getGlobals();

            // Скрипт выполняется в контексте один раз - при первом обращении
            if (!context.isScriptLoaded(scriptPath)) {
                Prototype prototype = getPrototype(globals, scriptPath);
                if (prototype == null) {
                    return false;
                }

                // Выполняем тело скрипта в окружении экрана, чтобы объявить его функции
                new LuaClosure(prototype, globals).call();
                context.markScriptLoaded(scriptPath);
            }

            LuaValue function = globals.get(functionName);
            if (function.isfunction()) {
                function.invoke(args);
                return true;
            }

            if (function.isnil()) {
                if (required) {
                    plugin.getLogger().warning("Function '" + functionName + "' not found in " + scriptPath);
                }
            } else {
                plugin.getLogger().warning("'" + functionName + "' in " + scriptPath
                        + " is not a function (type: " + function.typename() + ")");
            }
            return false;

        } catch (LuaError e) {
            // Ошибка самого скрипта: сообщение Lua уже содержит файл и строку
            plugin.getLogger().warning("Lua error in " + functionName + " (" + scriptPath + "): " + e.getMessage());
            return false;
        } catch (StackOverflowError e) {
            // Бесконечная рекурсия в скрипте не должна ронять тик сервера
            plugin.getLogger().warning("Lua stack overflow in " + functionName + " (" + scriptPath + ")");
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "Error calling Lua function " + functionName + " in " + scriptPath, e);
            return false;
        }
    }

    /**
     * Получить скомпилированный скрипт из кэша или скомпилировать его.
     *
     * @return прототип или null, если файл не найден либо не компилируется
     */
    private Prototype getPrototype(Globals compilerGlobals, String scriptPath) throws IOException {
        Path fullPath = scriptsDirectory.resolve(scriptPath).normalize();
        if (!fullPath.startsWith(scriptsDirectory)) {
            plugin.getLogger().warning("Script path is outside of scripts directory: " + scriptPath);
            return null;
        }

        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(fullPath, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            compiledScripts.remove(scriptPath);
            plugin.getLogger().warning("Script file not found: " + scriptPath);
            return null;
        }

        long lastModified = attributes.lastModifiedTime().toMillis();
        long size = attributes.size();

        CachedScript cached = compiledScripts.get(scriptPath);
        if (cached != null && cached.lastModified() == lastModified && cached.size() == size) {
            return cached.prototype();
        }

        String content = Files.readString(fullPath, StandardCharsets.UTF_8);
        Prototype prototype = compilerGlobals.compilePrototype(new StringReader(content), scriptPath);
        compiledScripts.put(scriptPath, new CachedScript(lastModified, size, prototype));
        return prototype;
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
        // luajava даёт скрипту доступ к любым Java-классам (Runtime, файлы, Bukkit) -
        // без его удаления остальные ограничения не имеют смысла
        globals.set("luajava", LuaValue.NIL);
        globals.set("debug", LuaValue.NIL);

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

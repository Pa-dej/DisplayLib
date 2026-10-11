package padej.displayLib.config;

import me.padej.jumper.interp.Config;
import me.padej.jumper.runtime.JTable;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Настройки плагина из {@code plugins/DisplayLib/config.jmc}.
 *
 * <pre>
 * boolean hotReload = true;      // следить за screens/ и перечитывать изменённые экраны
 * int scriptTimeoutMs = 1000;    // сторожевой таймер скриптов; 0 - выключен
 * </pre>
 *
 * @param hotReload       следить за папкой экранов
 * @param scriptTimeoutMs предел времени одного вызова скрипта в миллисекундах; {@code <= 0} - без предела
 */
public record PluginConfig(boolean hotReload, long scriptTimeoutMs) {
    public static final String FILE = "config.jmc";
    public static final PluginConfig DEFAULTS = new PluginConfig(true, 1000);

    /** Прочитать конфиг; если файла нет, он создаётся из ресурсов плагина. */
    public static PluginConfig load(JavaPlugin plugin) {
        Path file = plugin.getDataFolder().toPath().resolve(FILE);
        if (!Files.exists(file)) {
            saveDefault(plugin, file);
        }
        if (!Files.exists(file)) {
            return DEFAULTS;
        }

        try {
            Object data = Config.load(file);
            if (!(data instanceof JTable table)) {
                plugin.getLogger().warning(FILE + ": expected top-level variables, using defaults");
                return DEFAULTS;
            }
            Boolean hotReload = JmcValues.bool(table, "hotReload", "hot-reload", "hot_reload");
            Double timeout = JmcValues.number(table, "scriptTimeoutMs", "script-timeout-ms", "script_timeout_ms");
            return new PluginConfig(
                    hotReload != null ? hotReload : DEFAULTS.hotReload(),
                    timeout != null ? timeout.longValue() : DEFAULTS.scriptTimeoutMs());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, FILE + " could not be read, using defaults: " + e.getMessage());
            return DEFAULTS;
        }
    }

    private static void saveDefault(JavaPlugin plugin, Path file) {
        try (InputStream in = plugin.getResource(FILE)) {
            if (in == null) return;
            Files.createDirectories(file.getParent());
            Files.copy(in, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write default " + FILE, e);
        }
    }
}

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
 * @param storage         сохранение глобала storage на диск
 */
public record PluginConfig(boolean hotReload, long scriptTimeoutMs, CloseOn closeOn, Storage storage, Particles particles) {
    public static final String FILE = "config.jmc";
    public static final PluginConfig DEFAULTS = new PluginConfig(true, 1000, CloseOn.DEFAULTS, Storage.DEFAULTS, Particles.DEFAULTS);

    /**
     * Частицы: {@code dyn particles = { maxAlive: 1500, packets: true, viewDistance: 96, async: true };}
     *
     * @param maxAlive     сколько частиц может жить одновременно на всём сервере; новые сверх предела не появляются
     * @param packets      показывать частицы пакетами, без сущностей на сервере; {@code false} - настоящими сущностями
     * @param viewDistance с какого расстояния от точки запуска игроки видят частицы (только для пакетов)
     * @param async        частицы без коллизий считать в отдельном потоке (только для пакетов)
     */
    public record Particles(int maxAlive, boolean packets, double viewDistance, boolean async) {
        public static final Particles DEFAULTS = new Particles(1500, true, 96, true);

        static Particles parse(Object v) {
            if (!(v instanceof JTable t)) return DEFAULTS;
            Integer max = JmcValues.integer(t, "maxAlive", "max_alive", "max");
            Boolean packets = JmcValues.bool(t, "packets");
            Double view = JmcValues.number(t, "viewDistance", "view_distance");
            Boolean async = JmcValues.bool(t, "async", "thread");
            return new Particles(max != null && max > 0 ? max : DEFAULTS.maxAlive(),
                    packets != null ? packets : DEFAULTS.packets(),
                    view != null && view > 0 ? view : DEFAULTS.viewDistance(),
                    async != null ? async : DEFAULTS.async());
        }
    }

    /**
     * Глобал {@code storage}: {@code dyn storage = { persist: true, autosaveSeconds: 300 };}
     *
     * @param persist         сохранять данные на диск ({@code data/storage/}); {@code false} - только в памяти,
     *                        данные игрока забываются при выходе, публичных экранов - при перезапуске
     * @param autosaveSeconds период автосохранения изменившихся данных; {@code <= 0} - только при выходе игрока и выключении
     */
    public record Storage(boolean persist, int autosaveSeconds) {
        public static final Storage DEFAULTS = new Storage(true, 300);

        static Storage parse(Object v) {
            if (!(v instanceof JTable t)) return DEFAULTS;
            Boolean persist = JmcValues.bool(t, "persist", "save");
            Double autosave = JmcValues.number(t, "autosaveSeconds", "autosave_seconds", "autosave");
            return new Storage(persist != null ? persist : DEFAULTS.persist(),
                    autosave != null ? autosave.intValue() : DEFAULTS.autosaveSeconds());
        }
    }

    /**
     * Когда закрывать приватный экран автоматически (выход с сервера - всегда).
     * {@code dyn closeOn = { death: true, worldChange: true, teleport: 8, inventory: false };}
     *
     * @param death       смерть игрока
     * @param worldChange смена мира
     * @param teleport    телепорт дальше этого расстояния в блоках; {@code <= 0} - не закрывать
     * @param inventory   открытие любого инвентаря (сундук, верстак, GUI другого плагина)
     */
    public record CloseOn(boolean death, boolean worldChange, double teleport, boolean inventory) {
        public static final CloseOn DEFAULTS = new CloseOn(true, true, 8, false);

        static CloseOn parse(Object v) {
            if (!(v instanceof JTable t)) return DEFAULTS;
            Boolean death = JmcValues.bool(t, "death");
            Boolean world = JmcValues.bool(t, "worldChange", "world_change", "world");
            Object tp = JmcValues.get(t, "teleport");
            double teleport = tp instanceof Boolean b ? (b ? DEFAULTS.teleport() : 0) : tp instanceof Number n ? n.doubleValue() : DEFAULTS.teleport();
            Boolean inv = JmcValues.bool(t, "inventory");
            return new CloseOn(death != null ? death : DEFAULTS.death(), world != null ? world : DEFAULTS.worldChange(), teleport, inv != null ? inv : DEFAULTS.inventory());
        }
    }

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
                    timeout != null ? timeout.longValue() : DEFAULTS.scriptTimeoutMs(),
                    CloseOn.parse(JmcValues.get(table, "closeOn", "close_on")),
                    Storage.parse(JmcValues.get(table, "storage")),
                    Particles.parse(JmcValues.get(table, "particles")));
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

package padej.displayLib.pack;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import padej.displayLib.DisplayLib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Наборы содержимого ({@link ContentPack}): папка DisplayLib и папки других плагинов.
 *
 * <p>Плагин, у которого свои экраны, в {@code onEnable} регистрирует свою папку:</p>
 * <pre>{@code
 * // plugin.yml: depend: [DisplayLib]
 * ContentPacks.register(this);   // plugins/WordBomb/{screens,scripts,particles,scripts.jma}
 * }</pre>
 * <p>Папки создаются, {@code scripts.jma} при отсутствии - копия политики по умолчанию. Экраны и частицы набора
 * загружаются сразу, {@code screens/} отслеживается hot reload, {@code /displaylib reload} перечитывает и их.
 * При выключении плагина-владельца набор снимается сам. Без кода - {@code dyn packs = ["WordBomb"];} в
 * {@code config.jmc} DisplayLib (папки относительно {@code plugins/}).</p>
 *
 * <p>Только основной поток.</p>
 */
public final class ContentPacks implements Listener {
    private static ContentPacks instance;

    /** Что делать при смене набора (экраны, частицы, скрипты) - задаёт DisplayLib */
    public interface Hooks {
        void added(ContentPack pack);

        void removed(ContentPack pack);
    }

    private final DisplayLib plugin;
    private final List<ContentPack> packs = new ArrayList<>();
    private Hooks hooks;

    private ContentPacks(DisplayLib plugin) {
        this.plugin = plugin;
    }

    /** Включение DisplayLib: основной набор - его собственная папка. */
    public static ContentPack install(DisplayLib plugin) {
        shutdown();
        ContentPacks c = new ContentPacks(plugin);
        ContentPack main = new ContentPack(plugin.getName(), plugin, plugin.getDataFolder().toPath());
        c.mkdirs(main);
        c.packs.add(main);
        Bukkit.getPluginManager().registerEvents(c, plugin);
        instance = c;
        return main;
    }

    /** После того как экраны, скрипты и частицы созданы: с этого момента новые наборы подхватываются сразу. */
    public static void hooks(Hooks hooks) {
        if (instance != null) instance.hooks = hooks;
    }

    public static void shutdown() {
        ContentPacks c = instance;
        instance = null;
        if (c != null) HandlerList.unregisterAll(c);
    }

    /** Все наборы в порядке регистрации (DisplayLib первым). */
    public static List<ContentPack> all() {
        ContentPacks c = instance;
        return c != null ? List.copyOf(c.packs) : List.of();
    }

    /** Набор DisplayLib. */
    public static ContentPack main() {
        ContentPacks c = instance;
        return c != null && !c.packs.isEmpty() ? c.packs.get(0) : null;
    }

    /** Набор по имени (без учёта регистра) или {@code null}. */
    public static ContentPack get(String name) {
        for (ContentPack p : all()) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Зарегистрировать папку плагина ({@code plugins/<Имя>/}) как набор. */
    public static ContentPack register(Plugin owner) {
        return register(owner, owner.getDataFolder().toPath());
    }

    /**
     * Зарегистрировать папку {@code root} как набор плагина {@code owner}. Повторная регистрация той же папки
     * возвращает уже зарегистрированный набор.
     */
    public static ContentPack register(Plugin owner, Path root) {
        ContentPacks c = instance;
        if (c == null) throw new IllegalStateException("DisplayLib is not enabled");
        Path abs = root.toAbsolutePath().normalize();
        for (ContentPack p : c.packs) {
            if (p.root().equals(abs)) return p;
        }
        // имя - плагина, если это его папка, иначе имя папки (наборы из config.jmc)
        String base = abs.equals(owner.getDataFolder().toPath().toAbsolutePath().normalize())
                ? owner.getName() : abs.getFileName().toString();
        String name = base;
        for (int i = 2; get(name) != null; i++) name = base + "#" + i;
        ContentPack pack = new ContentPack(name, owner, abs);
        c.mkdirs(pack);
        c.packs.add(pack);
        c.plugin.getLogger().info("Content pack '" + pack.name() + "' registered: " + abs);
        if (c.hooks != null) c.hooks.added(pack);
        return pack;
    }

    /** Снять все наборы плагина (кроме основного). */
    public static void unregister(Plugin owner) {
        ContentPacks c = instance;
        if (c == null) return;
        for (ContentPack p : new ArrayList<>(c.packs)) {
            if (p.owner() == owner && p != c.packs.get(0)) c.remove(p);
        }
    }

    private void remove(ContentPack pack) {
        packs.remove(pack);
        plugin.getLogger().info("Content pack '" + pack.name() + "' removed");
        if (hooks != null) hooks.removed(pack);
    }

    private void mkdirs(ContentPack pack) {
        try {
            Files.createDirectories(pack.screens());
            Files.createDirectories(pack.scripts());
            Files.createDirectories(pack.particles());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Cannot create folders of content pack " + pack.name(), e);
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) unregister(event.getPlugin());
    }
}

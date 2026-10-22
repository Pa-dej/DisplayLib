package padej.displayLib;

import padej.displayLib.commands.DisplayLibCommand;
import padej.displayLib.config.PluginConfig;
import padej.displayLib.config.ScreenRegistry;
import padej.displayLib.data.PublicScreenStore;
import padej.displayLib.data.StorageStore;
import padej.displayLib.particles.ParticleManager;
import padej.displayLib.script.ChatInput;
import padej.displayLib.particles.ParticleRegistry;
import padej.displayLib.particles.Particles;
import padej.displayLib.render.shapes.Highlight;
import padej.displayLib.script.JumperEngine;
import padej.displayLib.ui.UIManager;
import padej.displayLib.ui.ScreenDisplays;
import padej.displayLib.pack.ContentPack;
import padej.displayLib.pack.ContentPacks;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * Главный класс плагина DisplayLib.
 *
 * <p>DisplayLib рисует интерактивные 3D-экраны прямо в игровом мире на Display-сущностях.
 * Экран описывается конфигом Jumper ({@code .jmc}), логика пишется на Jumper ({@code .jmp}),
 * доступ скриптов к Java ограничивает политика ({@code .jma}).</p>
 *
 * <h2>Структура файлов:</h2>
 * <pre>
 * plugins/DisplayLib/
 * ├── config.jmc        # Настройки плагина (hotReload, scriptTimeoutMs)
 * ├── scripts.jma       # Политика доступа скриптов к Java
 * ├── data/             # Пишет плагин: storage скриптов, расстановка публичных экранов
 * │   ├── storage/players/&lt;uuid&gt;.jmc, storage/public/&lt;screen&gt;.jmc
 * │   └── public_screens.jmc
 * ├── particles/        # Эффекты частиц: /displaylib particle &lt;id&gt;, Particles.play(...)
 * │   └── confetti.jmc
 * ├── screens/          # Экраны
 * │   ├── main_menu.jmc
 * │   └── settings.jmc
 * └── scripts/          # Скрипты экранов
 *     ├── main_menu.jmp
 *     └── lib/util.jmp  # общие модули: import "lib/util.jmp";
 * </pre>
 *
 * <h2>Команды:</h2>
 * <ul>
 * <li><b>/displaylib open &lt;screen&gt; [player] [x y z] [yaw pitch]</b> - Открыть приватный экран</li>
 * <li><b>/displaylib close</b> - Закрыть свой экран</li>
 * <li><b>/displaylib list</b> - Список экранов</li>
 * <li><b>/displaylib openpublic &lt;screen&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; [yaw] [pitch]</b> - Поставить публичный экран</li>
 * <li><b>/displaylib closepublic &lt;screen&gt;</b>, <b>listpublic</b></li>
 * <li><b>/displaylib reload</b> - Перечитать экраны, политику и скрипты</li>
 * <li><b>/displaylib examples</b> - Выгрузить примеры в папку плагина</li>
 * </ul>
 *
 * @author Padej_
 * @version 3.0.0
 */
@SuppressWarnings("unused")
public final class DisplayLib extends JavaPlugin {

    /** Экземпляр плагина; кэшируется, так как getInstance() вызывается из горячих путей */
    private static DisplayLib instance;

    private PluginConfig config;
    private ScreenRegistry screenRegistry;
    private JumperEngine scriptEngine;
    private StorageStore storage;
    private org.bukkit.scheduler.BukkitTask autosaveTask;
    private ParticleRegistry particleRegistry;
    private ParticleManager particleManager;

    @Override
    public void onEnable() {
        instance = this;

        // config.jmc: hotReload, scriptTimeoutMs
        config = PluginConfig.load(this);

        // Наборы содержимого: папка DisplayLib и папки из config.jmc (packs); плагины добавляют свои через
        // ContentPacks.register(plugin) - у каждого свои screens/, scripts/, particles/ и scripts.jma
        ContentPacks.install(this);
        Path pluginsDir = getDataFolder().toPath().toAbsolutePath().getParent();
        for (String name : config.packs()) {
            Path root = pluginsDir.resolve(name).normalize();
            if (!root.startsWith(pluginsDir) || !java.nio.file.Files.isDirectory(root)) {
                getLogger().warning("config.jmc packs: folder plugins/" + name + " not found; skipped");
                continue;
            }
            ContentPacks.register(this, root);
        }

        // Скрипты (.jmp) под политикой доступа (scripts.jma) - раньше экранов:
        // статический разбор экранов читает скрипты под той же политикой
        scriptEngine = new JumperEngine(this, config.scriptTimeoutMs());

        // Сущности экранов: пакетами (по умолчанию) или настоящими сущностями; до загрузки экранов -
        // от режима зависит, какие виджеты публичных экранов тикать
        ScreenDisplays.install(this, config.screens().packets(), config.screens().viewDistance());
        getLogger().info("Screens: shown as " + (ScreenDisplays.packets() ? "packets" : "entities"));

        // Экраны (.jmc) и их hot reload
        screenRegistry = new ScreenRegistry(this, config.hotReload());
        screenRegistry.initialize();

        // storage скриптов: на диск (data/storage) или только в памяти (storage.persist = false)
        Path dataDir = getDataFolder().toPath().resolve("data");
        storage = new StorageStore(config.storage().persist() ? dataDir.resolve("storage") : null, getLogger());
        StorageStore.install(storage);
        ChatInput.install(this); // player.input(...) в скриптах экранов
        int autosave = config.storage().autosaveSeconds();
        if (storage.isPersistent() && autosave > 0) {
            long period = autosave * 20L;
            autosaveTask = getServer().getScheduler().runTaskTimer(this, storage::saveAll, period, period);
        }

        // Эффекты частиц (particles/*.jmc)
        particleRegistry = new ParticleRegistry(() -> ContentPacks.all().stream()
                .map(p -> new ParticleRegistry.Source(p == ContentPacks.main() ? "particles/" : p.name() + "/particles/", p.particles()))
                .toList(), getLogger());
        particleRegistry.loadAll();
        particleManager = new ParticleManager(this, config.particles().maxAlive(),
                config.particles().packets(), config.particles().viewDistance(), config.particles().async());
        getLogger().info("Particles: shown as " + particleManager.backendName());
        Particles.install(particleRegistry, particleManager);

        UIManager.getInstance().initialize(screenRegistry, scriptEngine,
                new PublicScreenStore(dataDir.resolve("public_screens.jmc"), getLogger()));
        // Сохранённые публичные экраны - на первом тике: миры загружены, другие плагины включены
        getServer().getScheduler().runTask(this, () -> UIManager.getInstance().restorePublicScreens());

        // наборы, зарегистрированные дальше (плагины в onEnable), подхватываются сразу
        ContentPacks.hooks(new ContentPacks.Hooks() {
            @Override
            public void added(ContentPack pack) {
                scriptEngine.loadPolicy(pack);
                screenRegistry.addPack(pack);
                particleRegistry.loadAll();
            }

            @Override
            public void removed(ContentPack pack) {
                screenRegistry.removePack(pack);
                particleRegistry.loadAll();
            }
        });

        DisplayLibCommand commandExecutor = new DisplayLibCommand(this);
        getCommand("displaylib").setExecutor(commandExecutor);
        getCommand("displaylib").setTabCompleter(commandExecutor);

        Highlight.removeAllSelections();
        Highlight.startColorUpdateTask();

        getLogger().info("DisplayLib enabled: screens are .jmc, scripts are .jmp (Jumper)");
    }

    @Override
    public void onDisable() {
        ChatInput.shutdown(); // ожидания ввода снимаются без вызова функций
        if (screenRegistry != null) {
            screenRegistry.shutdown();
        }

        if (particleManager != null) {
            particleManager.shutdown();
            Particles.install(null, null);
            particleManager = null;
        }

        UIManager manager = UIManager.getInstance();
        if (manager.hasActiveScreens()) {
            getLogger().info("Cleaning up active UI screens...");
            manager.cleanup();
        }
        ScreenDisplays.shutdown(); // сущности экранов исчезают у клиентов
        ContentPacks.shutdown();

        // После закрытия экранов: onClose("shutdown") ещё мог писать в storage
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (storage != null) {
            storage.shutdown();
            StorageStore.install(new StorageStore(null, getLogger()));
            storage = null;
        }

        if (scriptEngine != null) {
            scriptEngine.shutdown();
        }
        instance = null;
    }

    public static JavaPlugin getInstance() {
        DisplayLib plugin = instance;
        return plugin != null ? plugin : JavaPlugin.getPlugin(DisplayLib.class);
    }

    public PluginConfig getPluginConfig() {
        return config;
    }

    public ScreenRegistry getScreenRegistry() {
        return screenRegistry;
    }

    public JumperEngine getScriptEngine() {
        return scriptEngine;
    }

    public ParticleRegistry getParticleRegistry() {
        return particleRegistry;
    }

    /**
     * Выгрузить примеры ({@code examples/screens/*.jmc}, {@code examples/scripts/*.jmp}) из JAR
     * в папку плагина. Существующие файлы перезаписываются.
     *
     * @return число записанных файлов; -1 при ошибке
     */
    public int extractExamples() {
        File jar = getFile();
        Path dataFolder = getDataFolder().toPath();
        int count = 0;
        try (JarFile jarFile = new JarFile(jar)) {
            List<JarEntry> entries = new ArrayList<>();
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements(); ) {
                JarEntry entry = e.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith("examples/")) {
                    entries.add(entry);
                }
            }
            for (JarEntry entry : entries) {
                // examples/screens/x.jmc -> plugins/DisplayLib/screens/x.jmc
                String relative = entry.getName().substring("examples/".length());
                Path target = dataFolder.resolve(relative).normalize();
                if (!target.startsWith(dataFolder)) continue;
                Files.createDirectories(target.getParent());
                try (InputStream in = jarFile.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                count++;
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to extract examples", e);
            return -1;
        }
        getLogger().info("Extracted " + count + " example file(s) to " + dataFolder);
        return count;
    }
}

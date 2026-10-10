package padej.displayLib;

import padej.displayLib.commands.DisplayLibCommand;
import padej.displayLib.config.PluginConfig;
import padej.displayLib.config.ScreenRegistry;
import padej.displayLib.render.shapes.Highlight;
import padej.displayLib.script.JumperEngine;
import padej.displayLib.ui.UIManager;
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

    @Override
    public void onEnable() {
        instance = this;

        // config.jmc: hotReload, scriptTimeoutMs
        config = PluginConfig.load(this);

        // Глифы (glyphs.jmc + ресурспак DisplayLib-icons): широкие картинки в тексте
        padej.displayLib.config.GlyphRegistry.load(this);

        // Экраны (.jmc) и их hot reload
        screenRegistry = new ScreenRegistry(this, config.hotReload());
        screenRegistry.initialize();

        // Скрипты (.jmp) под политикой доступа (scripts.jma)
        scriptEngine = new JumperEngine(this, config.scriptTimeoutMs());

        UIManager.getInstance().initialize(screenRegistry, scriptEngine);

        DisplayLibCommand commandExecutor = new DisplayLibCommand(this);
        getCommand("displaylib").setExecutor(commandExecutor);
        getCommand("displaylib").setTabCompleter(commandExecutor);

        Highlight.removeAllSelections();
        Highlight.startColorUpdateTask();

        getLogger().info("DisplayLib enabled: screens are .jmc, scripts are .jmp (Jumper)");
    }

    @Override
    public void onDisable() {
        if (screenRegistry != null) {
            screenRegistry.shutdown();
        }

        UIManager manager = UIManager.getInstance();
        if (manager.hasActiveScreens()) {
            getLogger().info("Cleaning up active UI screens...");
            manager.cleanup();
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

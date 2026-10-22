package padej.displayLib.particles;

import me.padej.jumper.interp.Config;
import me.padej.jumper.parser.ParseError;
import me.padej.jumper.runtime.JTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Эффекты частиц из {@code plugins/DisplayLib/particles/**}{@code /*.jmc}. Перечитываются по
 * {@code /displaylib reload}. Файл с ошибкой пропускается с понятным сообщением в консоли.
 */
public final class ParticleRegistry {
    public static final String EXTENSION = ".jmc";

    /** Папка эффектов и имя для сообщений ({@code particles/x.jmc} или {@code WordBomb/particles/x.jmc}) */
    public record Source(String prefix, Path directory) {
    }

    private final java.util.logging.Logger log;
    private final java.util.function.Supplier<List<Source>> sources;
    private Map<String, ParticleEffect> effects = Map.of();
    /** Папка текущего разбираемого файла (для сообщений) */
    private Source current;

    /** Одна папка (тесты, инструменты). */
    public ParticleRegistry(Path directory, java.util.logging.Logger log) {
        this(() -> List.of(new Source("particles/", directory)), log);
    }

    /**
     * Несколько папок (наборы содержимого): перечитываются все при каждом {@link #loadAll()}; при одинаковом id
     * остаётся эффект из папки, что раньше в списке.
     */
    public ParticleRegistry(java.util.function.Supplier<List<Source>> sources, java.util.logging.Logger log) {
        this.sources = sources;
        this.log = log;
    }

    /** Прочитать все файлы заново; прежний набор заменяется целиком. */
    public void loadAll() {
        Map<String, ParticleEffect> loaded = new LinkedHashMap<>();
        Map<String, String> origin = new LinkedHashMap<>();
        for (Source source : sources.get()) {
            current = source;
            loadDir(source.directory(), loaded, origin);
        }
        current = null;
        effects = Collections.unmodifiableMap(loaded);
        log.info("Loaded " + effects.size() + " particle effect(s)");
    }

    private void loadDir(Path directory, Map<String, ParticleEffect> loaded, Map<String, String> origin) {
        if (Files.isDirectory(directory)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(directory)) {
                files = walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(EXTENSION)).sorted().toList();
            } catch (IOException e) {
                log.warning("Cannot list particle effects in " + directory + ": " + e.getMessage());
                files = List.of();
            }
            for (Path file : files) {
                ParticleEffect effect = load(file);
                if (effect == null) continue;
                if (org.bukkit.Bukkit.getServer() != null) {
                    for (String item : effect.render().items()) {
                        if (ParticleEffect.item(item) == null) log.warning(relative(file) + ": unknown item '" + item + "'");
                    }
                    for (String block : effect.render().blocks()) {
                        try {
                            org.bukkit.Bukkit.createBlockData(block);
                        } catch (IllegalArgumentException e) {
                            log.warning(relative(file) + ": unknown block '" + block + "'");
                        }
                    }
                }
                if (effect.id().equalsIgnoreCase("list") || effect.id().equalsIgnoreCase("stop")) {
                    log.warning(relative(file) + ": id '" + effect.id() + "' is reserved by /displaylib particle " + effect.id() + "; rename it");
                }
                String previous = origin.putIfAbsent(effect.id(), relative(file));
                if (previous != null) {
                    log.warning("Particle effect id '" + effect.id() + "' in " + relative(file) + " is already defined in " + previous + "; skipped");
                    continue;
                }
                loaded.put(effect.id(), effect);
            }
        }
    }

    /** Разобрать один файл; {@code null} и предупреждение в консоли, если он с ошибкой. */
    public ParticleEffect load(Path file) {
        String name = file.getFileName().toString();
        String fallbackId = name.substring(0, name.length() - EXTENSION.length());
        try {
            Object top = Config.load(file);
            if (!(top instanceof JTable table)) throw new IllegalArgumentException("expected top-level variables");
            return ParticleEffect.parse(table, fallbackId);
        } catch (ParseError e) {
            log.warning(relative(file) + " (" + e.line + ":" + e.col + "): " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            log.warning(relative(file) + ": " + e.getMessage());
        }
        return null;
    }

    public ParticleEffect get(String id) {
        return effects.get(id);
    }

    public Map<String, ParticleEffect> all() {
        return effects;
    }

    private String relative(Path file) {
        Source s = current;
        if (s == null || !file.startsWith(s.directory())) return file.getFileName().toString();
        return s.prefix() + s.directory().relativize(file).toString().replace('\\', '/');
    }
}

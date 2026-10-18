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

    private final Path directory;
    private final java.util.logging.Logger log;
    private Map<String, ParticleEffect> effects = Map.of();

    public ParticleRegistry(Path directory, java.util.logging.Logger log) {
        this.directory = directory;
        this.log = log;
    }

    public Path getDirectory() {
        return directory;
    }

    /** Прочитать все файлы заново; прежний набор заменяется целиком. */
    public void loadAll() {
        Map<String, ParticleEffect> loaded = new LinkedHashMap<>();
        Map<String, Path> origin = new LinkedHashMap<>();
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
                        if (ParticleEffect.item(item) == null) log.warning("particles/" + relative(file) + ": unknown item '" + item + "'");
                    }
                    for (String block : effect.render().blocks()) {
                        try {
                            org.bukkit.Bukkit.createBlockData(block);
                        } catch (IllegalArgumentException e) {
                            log.warning("particles/" + relative(file) + ": unknown block '" + block + "'");
                        }
                    }
                }
                if (effect.id().equalsIgnoreCase("list") || effect.id().equalsIgnoreCase("stop")) {
                    log.warning("particles/" + relative(file) + ": id '" + effect.id() + "' is reserved by /displaylib particle " + effect.id() + "; rename it");
                }
                Path previous = origin.putIfAbsent(effect.id(), file);
                if (previous != null) {
                    log.warning("Particle effect id '" + effect.id() + "' in " + relative(file) + " is already defined in " + relative(previous) + "; skipped");
                    continue;
                }
                loaded.put(effect.id(), effect);
            }
        }
        effects = Collections.unmodifiableMap(loaded);
        log.info("Loaded " + effects.size() + " particle effect(s)");
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
            log.warning("particles/" + relative(file) + " (" + e.line + ":" + e.col + "): " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            log.warning("particles/" + relative(file) + ": " + e.getMessage());
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
        return directory.relativize(file).toString().replace('\\', '/');
    }
}

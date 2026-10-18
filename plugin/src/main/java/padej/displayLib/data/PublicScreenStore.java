package padej.displayLib.data;

import me.padej.jumper.interp.Config;
import me.padej.jumper.runtime.JTable;
import padej.displayLib.config.JmcValues;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Расстановка публичных экранов: {@code plugins/DisplayLib/data/public_screens.jmc}.
 *
 * <pre>
 * dyn screens = [
 *     { id: "global_shop", world: "world", x: 10.5, y: 65.0, z: -3.5, yaw: 90.0, pitch: 0.0 },
 * ];
 * </pre>
 *
 * <p>Публичный экран попадает сюда при открытии ({@code /displaylib openpublic} или Java API
 * с {@code persistent = true}) и убирается при явном закрытии; при выключении сервера список
 * не меняется, и при следующем запуске экраны ставятся обратно. Один экран на id - как и в игре.
 * Файл можно править руками при выключенном сервере.</p>
 */
public final class PublicScreenStore {
    public static final String VAR = "screens";

    /** Где стоит публичный экран. */
    public record Placement(String id, String world, double x, double y, double z, float yaw, float pitch) {}

    private final Path file;
    private final Logger log;
    private final Map<String, Placement> placements = new LinkedHashMap<>();
    /** Файл прочитан не целиком (ошибка разбора, пропущенные записи): перед первой записью сохранить копию */
    private boolean needsBackup;

    public PublicScreenStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /**
     * Прочитать файл. Если он прочитан не целиком (ошибка разбора, пропущенные записи), перед первой
     * перезаписью исходный файл копируется в {@code public_screens.jmc.broken}, чтобы правку можно было вернуть.
     */
    public void load() {
        placements.clear();
        needsBackup = false;
        if (!Files.exists(file)) return;
        try {
            Object top = Config.load(file);
            Object list = top instanceof JTable t ? t.get(VAR) : null;
            if (!(list instanceof List<?> items)) {
                log.warning(file.getFileName() + ": expected 'dyn " + VAR + " = [ ... ];'");
                needsBackup = true;
                return;
            }
            for (Object item : items) {
                Placement p = item instanceof JTable t ? parse(t) : null;
                if (p == null) {
                    log.warning(file.getFileName() + ": skipped entry " + item + " (needs id, world, x, y, z)");
                    needsBackup = true;
                    continue;
                }
                placements.put(p.id(), p);
            }
        } catch (IOException | RuntimeException e) {
            log.warning(file.getFileName() + " could not be read: " + e.getMessage());
            needsBackup = true;
        }
    }

    private static Placement parse(JTable t) {
        String id = JmcValues.str(t, "id");
        String world = JmcValues.str(t, "world");
        Double x = JmcValues.number(t, "x"), y = JmcValues.number(t, "y"), z = JmcValues.number(t, "z");
        if (id == null || world == null || x == null || y == null || z == null) return null;
        Double yaw = JmcValues.number(t, "yaw"), pitch = JmcValues.number(t, "pitch");
        return new Placement(id, world, x, y, z,
                yaw != null ? yaw.floatValue() : 0f, pitch != null ? pitch.floatValue() : 0f);
    }

    public Collection<Placement> all() {
        return new ArrayList<>(placements.values());
    }

    public Placement get(String id) {
        return placements.get(id);
    }

    /** Запомнить (или переместить) экран и сохранить файл. */
    public void put(Placement placement) {
        if (placement.equals(placements.put(placement.id(), placement))) return;
        save();
    }

    /** Забыть экран и сохранить файл. */
    public void remove(String id) {
        if (placements.remove(id) != null) save();
    }

    private void save() {
        if (needsBackup) {
            needsBackup = false;
            Path backup = file.resolveSibling(file.getFileName() + ".broken");
            try {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                log.warning(file.getFileName() + " was not read completely; the original is kept as " + backup.getFileName());
            } catch (IOException e) {
                log.log(Level.WARNING, "Could not back up " + file, e);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("// DisplayLib: публичные экраны, которые ставятся заново при запуске сервера.\n");
        sb.append("// Пишется плагином при /displaylib openpublic и closepublic; править - при выключенном сервере.\n");
        sb.append("dyn ").append(VAR).append(" = [\n");
        for (Placement p : placements.values()) {
            sb.append("    { id: ");
            JmcWriter.quote(sb, p.id());
            sb.append(", world: ");
            JmcWriter.quote(sb, p.world());
            sb.append(", x: ").append(p.x()).append(", y: ").append(p.y()).append(", z: ").append(p.z())
              .append(", yaw: ").append((double) p.yaw()).append(", pitch: ").append((double) p.pitch()).append(" },\n");
        }
        sb.append("];\n");
        try {
            JmcWriter.writeAtomically(file, sb.toString());
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to save " + file, e);
        }
    }
}

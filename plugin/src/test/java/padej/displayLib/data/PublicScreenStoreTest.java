package padej.displayLib.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PublicScreenStoreTest {
    private static final Logger LOG = Logger.getLogger("test");

    @Test
    void savesAndRestoresPlacements(@TempDir Path dir) {
        Path file = dir.resolve("data").resolve("public_screens.jmc");
        PublicScreenStore a = new PublicScreenStore(file, LOG);
        a.load(); // файла ещё нет
        var shop = new PublicScreenStore.Placement("global_shop", "world", 10.5, 65, -3.25, 90f, -12.5f);
        var board = new PublicScreenStore.Placement("board \"main\"", "world_nether", 0, 70, 0, 0f, 0f);
        a.put(shop);
        a.put(board);

        PublicScreenStore b = new PublicScreenStore(file, LOG);
        b.load();
        assertEquals(List.of(shop, board), List.copyOf(b.all()));

        b.remove("global_shop");
        PublicScreenStore c = new PublicScreenStore(file, LOG);
        c.load();
        assertEquals(List.of(board), List.copyOf(c.all()));
    }

    @Test
    void movingScreenReplacesEntry(@TempDir Path dir) {
        Path file = dir.resolve("public_screens.jmc");
        PublicScreenStore a = new PublicScreenStore(file, LOG);
        a.put(new PublicScreenStore.Placement("s", "world", 0, 0, 0, 0f, 0f));
        a.put(new PublicScreenStore.Placement("s", "world", 1, 2, 3, 0f, 0f));
        PublicScreenStore b = new PublicScreenStore(file, LOG);
        b.load();
        assertEquals(1, b.all().size());
        assertEquals(1.0, b.get("s").x());
    }

    @Test
    void handEditedFileWithBadEntry(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("public_screens.jmc");
        Files.writeString(file, """
                dyn screens = [
                    { id: "ok", world: "world", x: 1, y: 2, z: 3 },
                    { id: "no_world", x: 1, y: 2, z: 3 },
                ];
                """);
        PublicScreenStore a = new PublicScreenStore(file, LOG);
        a.load();
        assertEquals(1, a.all().size());
        assertEquals(0f, a.get("ok").yaw());

        // первая перезапись не теряет правку: исходный файл остаётся рядом
        String original = Files.readString(file);
        a.put(new PublicScreenStore.Placement("new", "world", 0, 0, 0, 0f, 0f));
        assertEquals(original, Files.readString(file.resolveSibling("public_screens.jmc.broken")));
    }

    @Test
    void unparsableFileIsBackedUpBeforeOverwrite(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("public_screens.jmc");
        Files.writeString(file, "dyn screens = [ { id: \"a\", ");
        PublicScreenStore a = new PublicScreenStore(file, LOG);
        a.load();
        assertTrue(a.all().isEmpty());
        a.put(new PublicScreenStore.Placement("b", "world", 0, 0, 0, 0f, 0f));
        assertTrue(Files.exists(file.resolveSibling("public_screens.jmc.broken")));
    }
}

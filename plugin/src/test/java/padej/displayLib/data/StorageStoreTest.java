package padej.displayLib.data;

import me.padej.jumper.interp.Config;
import me.padej.jumper.runtime.JArray;
import me.padej.jumper.runtime.JTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class StorageStoreTest {
    private static final Logger LOG = Logger.getLogger("test");

    /** Значения в том виде, в каком их создаёт скрипт Jumper. */
    private static JTable jumper(String source) {
        return (JTable) Config.parse(source);
    }

    @Test
    void roundTripKeepsJumperTypes(@TempDir Path dir) {
        UUID id = UUID.randomUUID();
        JTable src = jumper("""
                dyn list = [1, 2.5, "z", true, null, [3L]];
                dyn obj = { x: 1, "key with \\"quotes\\"": { deep: [false] } };
                """);

        StorageStore a = new StorageStore(dir, LOG);
        Map<String, Object> data = a.player(id);
        data.put("int", 5);
        data.put("long", 3_000_000_000L);
        data.put("double", 2.0);
        data.put("minInt", Integer.MIN_VALUE);
        data.put("minLong", Long.MIN_VALUE);
        data.put("text", "строка \"в кавычках\"\n\tи \\ слэш \u0001 👋");
        data.put("bool", false);
        data.put("list", src.get("list"));
        data.put("obj", src.get("obj"));
        a.shutdown();

        Map<String, Object> back = new StorageStore(dir, LOG).player(id);
        assertEquals(Integer.valueOf(5), back.get("int"));
        assertEquals(Long.valueOf(3_000_000_000L), back.get("long"));
        assertEquals(Double.valueOf(2.0), back.get("double"));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), back.get("minInt"));
        assertEquals(Long.valueOf(Long.MIN_VALUE), back.get("minLong"));
        assertEquals(data.get("text"), back.get("text"));
        assertEquals(Boolean.FALSE, back.get("bool"));
        assertInstanceOf(JArray.class, back.get("list"));
        assertInstanceOf(JTable.class, back.get("obj"));
        assertEquals(JmcWriter.literal(data.get("list")), JmcWriter.literal(back.get("list")));
        assertEquals(JmcWriter.literal(data.get("obj")), JmcWriter.literal(back.get("obj")));
        assertEquals(List.copyOf(data.keySet()), List.copyOf(back.keySet()), "порядок ключей сохраняется");
    }

    @Test
    void unsupportedValuesAreSkipped(@TempDir Path dir) {
        UUID id = UUID.randomUUID();
        StorageStore a = new StorageStore(dir, LOG);
        Map<String, Object> data = a.player(id);
        data.put("ok", 1);
        data.put("nan", Double.NaN);
        data.put("object", new Object());
        data.put("nested", List.of(1, new Object()));
        a.shutdown();

        Map<String, Object> back = new StorageStore(dir, LOG).player(id);
        assertEquals(Map.of("ok", 1), back);
    }

    @Test
    void releaseSavesAndForgetsPlayer(@TempDir Path dir) {
        UUID id = UUID.randomUUID();
        StorageStore a = new StorageStore(dir, LOG);
        a.player(id).put("visits", 3);
        a.releasePlayer(id);
        a.shutdown(); // дождаться записи
        assertEquals(3, new StorageStore(dir, LOG).player(id).get("visits"));
    }

    @Test
    void fastRejoinSeesLatestData(@TempDir Path dir) {
        UUID id = UUID.randomUUID();
        StorageStore a = new StorageStore(dir, LOG);
        for (int i = 1; i <= 50; i++) {
            a.player(id).put("visits", i);   // перезаход: данные читаются заново (из очереди записи или файла)
            a.releasePlayer(id);              // выход: запись уходит в очередь
            assertEquals(i, a.player(id).get("visits"), "после перезахода " + i);
        }
        a.shutdown();
        assertEquals(50, new StorageStore(dir, LOG).player(id).get("visits"));
    }

    @Test
    void publicScreenDataSharedById(@TempDir Path dir) {
        StorageStore a = new StorageStore(dir, LOG);
        a.publicScreen("global shop").put("sold", 7);
        assertSame(a.publicScreen("global shop"), a.publicScreen("global shop"));
        a.shutdown();
        assertEquals(7, new StorageStore(dir, LOG).publicScreen("global shop").get("sold"));
        assertNotEquals(StorageStore.fileName("a/b"), StorageStore.fileName("a_b"));
    }

    @Test
    void unchangedDataIsNotRewritten(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        StorageStore a = new StorageStore(dir, LOG);
        a.player(id); // пустые данные - файл не создаётся
        a.shutdown();
        assertFalse(Files.exists(dir.resolve("players").resolve(id + ".jmc")));
    }

    @Test
    void brokenFileIsMovedAsideNotOverwritten(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = dir.resolve("players").resolve(id + ".jmc");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "dyn data = { broken");

        StorageStore a = new StorageStore(dir, LOG);
        assertTrue(a.player(id).isEmpty());
        assertTrue(Files.exists(file.resolveSibling(id + ".jmc.broken")));
        a.shutdown();
    }

    @Test
    void memoryOnlyWritesNothing(@TempDir Path dir) {
        StorageStore a = new StorageStore(null, LOG);
        assertFalse(a.isPersistent());
        a.player(UUID.randomUUID()).put("x", 1);
        a.shutdown();
        assertEquals(0, dir.toFile().list().length);
    }
}

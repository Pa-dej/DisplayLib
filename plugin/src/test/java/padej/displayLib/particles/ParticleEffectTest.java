package padej.displayLib.particles;

import me.padej.jumper.interp.Config;
import me.padej.jumper.runtime.JTable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ParticleEffectTest {
    private static ParticleEffect parse(String src) {
        return ParticleEffect.parse((JTable) Config.parse(src), "fallback");
    }

    @Test
    void examplesParse() throws IOException {
        Path dir = Path.of("src/main/resources/examples/particles");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".jmc")).toList();
        }
        assertFalse(files.isEmpty());
        ParticleRegistry registry = new ParticleRegistry(dir, Logger.getLogger("test"));
        registry.loadAll();
        assertEquals(files.size(), registry.all().size(), "все примеры разбираются: " + registry.all().keySet());
        ParticleEffect confetti = registry.get("confetti");
        assertTrue(confetti.motion().collide());
        assertEquals(ParticleEffect.RenderType.ITEM, registry.get("coin_fountain").render().type());
        assertEquals(ParticleEffect.Viewers.OWNER, registry.get("poison").viewers());
        assertNull(registry.get("sparkle").motion().direction(), "OUTWARD");
    }

    @Test
    void defaultsAndRanges() {
        ParticleEffect e = parse("dyn render = { text: \"*\" };");
        assertEquals("fallback", e.id());
        assertEquals(ParticleEffect.RenderType.TEXT, e.render().type());
        assertEquals(1, e.emitter().count());
        assertEquals(4, e.keyframeTicks());
        assertEquals(new Range(20, 30), e.motion().lifetime());

        ParticleEffect r = parse("String id = \"x\"; int keyframeTicks = 500; dyn render = { text: \"*\" }; dyn motion = { lifetime: [40, 10], speed: 0.1 };");
        assertEquals("x", r.id());
        assertEquals(59, r.keyframeTicks(), "не больше 59 - предел интерполяции клиента");
        assertEquals(new Range(10, 40), r.motion().lifetime(), "порядок в диапазоне не важен");
        assertEquals(Range.of(0.1), r.motion().speed());
    }

    @Test
    void errorsAreReadable() {
        assertThrows(IllegalArgumentException.class, () -> parse("int x = 1;"), "без render");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("dyn render = { type: \"SPARK\", text: \"*\" };"));
        assertTrue(e.getMessage().contains("render.type") && e.getMessage().contains("TEXT"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> parse("dyn render = { type: \"ITEM\" };"), "ITEM без item");
        assertThrows(IllegalArgumentException.class, () -> parse("dyn render = { text: \"*\" }; dyn emitter = { duration: 20 };"),
                "duration без rate");
        assertThrows(IllegalArgumentException.class, () -> parse("dyn render = { text: \"*\", colors: [\"nope\"] };"));
    }

    @Test
    void withCountKeepsTheRest() {
        ParticleEffect e = parse("dyn render = { text: \"*\" }; dyn emitter = { shape: \"SPHERE\", radius: 2, count: 3 };");
        ParticleEffect m = e.withCount(50);
        assertEquals(50, m.emitter().count());
        assertEquals(2, m.emitter().radius());
        assertEquals(3, e.emitter().count(), "исходный не меняется");
    }

    /** Кадры текста: по кругу, по жизни, со случайного; один кадр - обычный текст. */
    @Test
    void textFrames() {
        ParticleEffect e = ParticleEffect.parse((JTable) Config.parse("dyn render = { type: \"TEXT\", frames: [\"a\", \"b\", \"c\"], frameInterval: 2, frameStart: \"random\" };"), "t");
        assertTrue(e.render().animated());
        assertEquals(3, e.render().frames().size());
        assertEquals(2, e.render().frameInterval());
        assertTrue(e.render().frameRandomStart());
        assertEquals(0, ParticleSim.frameAt(0, 3, 2, false, 0, 30));
        assertEquals(0, ParticleSim.frameAt(0, 3, 2, false, 1, 30));
        assertEquals(1, ParticleSim.frameAt(0, 3, 2, false, 2, 30));
        assertEquals(0, ParticleSim.frameAt(0, 3, 2, false, 6, 30), "по кругу");
        assertEquals(2, ParticleSim.frameAt(1, 3, 2, false, 2, 30), "со сдвигом");
        assertEquals(0, ParticleSim.frameAt(0, 4, 1, true, 0, 40));
        assertEquals(1, ParticleSim.frameAt(0, 4, 1, true, 10, 40));
        assertEquals(3, ParticleSim.frameAt(0, 4, 1, true, 40, 40), "по жизни - последний к концу");
        ParticleEffect one = ParticleEffect.parse((JTable) Config.parse("dyn render = { type: \"TEXT\", frames: [\"a\"] };"), "t");
        assertFalse(one.render().animated());
        assertEquals(1, one.render().texts().size());
        assertThrows(IllegalArgumentException.class, () -> ParticleEffect.parse((JTable) Config.parse("dyn render = { type: \"TEXT\", frames: [\"a\", \"b\"], frameStart: \"middle\" };"), "t"));
    }
}

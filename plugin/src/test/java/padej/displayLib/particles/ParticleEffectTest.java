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

    /** Варианты текста: статичные и с кадрами (как frames у кнопок экранов); краткая запись frames у render. */
    @Test
    void textFrames() {
        ParticleEffect e = ParticleEffect.parse((JTable) Config.parse("dyn render = { type: \"TEXT\", variants: [\"★\", { frames: [\"a\", \"b\", \"c\"], frameInterval: 2, frameStart: \"random\" }, { frames: [\"x\", \"y\"], framesOverLife: true }] };"), "t");
        List<ParticleEffect.TextVariant> v = e.render().texts();
        assertEquals(3, v.size());
        assertFalse(v.get(0).animated());
        assertTrue(v.get(1).animated());
        assertEquals(3, v.get(1).frames().size());
        assertEquals(2, v.get(1).frameInterval());
        assertTrue(v.get(1).randomStart());
        assertTrue(v.get(2).overLife());
        assertEquals(4, v.get(2).frameInterval(), "по умолчанию 4");
        assertEquals(0, ParticleSim.frameAt(0, 3, 2, false, 0, 30));
        assertEquals(0, ParticleSim.frameAt(0, 3, 2, false, 1, 30));
        assertEquals(1, ParticleSim.frameAt(0, 3, 2, false, 2, 30));
        assertEquals(0, ParticleSim.frameAt(0, 3, 2, false, 6, 30), "по кругу");
        assertEquals(2, ParticleSim.frameAt(1, 3, 2, false, 2, 30), "со сдвигом");
        assertEquals(0, ParticleSim.frameAt(0, 4, 1, true, 0, 40));
        assertEquals(1, ParticleSim.frameAt(0, 4, 1, true, 10, 40));
        assertEquals(3, ParticleSim.frameAt(0, 4, 1, true, 40, 40), "по жизни - последний к концу");
        ParticleEffect text = ParticleEffect.parse((JTable) Config.parse("dyn render = { type: \"TEXT\", text: { frames: [\"a\", \"b\"] } };"), "t");
        assertTrue(text.render().texts().get(0).animated(), "text тоже может быть кадрами");
        ParticleEffect shorthand = ParticleEffect.parse((JTable) Config.parse("dyn render = { type: \"TEXT\", frames: [\"a\", \"b\"], frameInterval: 5 };"), "t");
        assertEquals(5, shorthand.render().texts().get(0).frameInterval());
        assertThrows(IllegalArgumentException.class, () -> ParticleEffect.parse((JTable) Config.parse("dyn render = { type: \"TEXT\", variants: [{ frames: [\"a\", \"b\"], frameStart: \"middle\" }] };"), "t"));
    }

    /**
     * Цвет частицы красит и спрайты без своего цвета (как в редакторе): спрайт-сегмент не должен получать явный
     * белый - иначе text.color(цвет частицы) на корне до него не доходит. Свой цвет сегмента сохраняется.
     */
    @Test
    void particleColorTintsSpritesWithoutOwnColor() {
        ParticleEffect e = ParticleEffect.parse((JTable) Config.parse("dyn render = { type: \"TEXT\", variants: [[{ atlas: \"minecraft:particles\", sprite: \"spark_0\" }], [{ atlas: \"minecraft:particles\", sprite: \"spark_1\", color: \"#00ff00\" }], [{ player: \"Steve\" }]] };"), "t");
        var plain = e.render().texts().get(0).first();
        var own = e.render().texts().get(1).first();
        var head = e.render().texts().get(2).first();
        net.kyori.adventure.text.format.TextColor red = net.kyori.adventure.text.format.TextColor.color(0xff5555);
        assertNull(plain.children().get(0).color(), "спрайт без цвета наследует");
        assertNull(head.children().get(0).color(), "голова без цвета наследует");
        assertEquals(red, plain.color(red).color(), "цвет частицы - на корне, спрайт его наследует");
        assertEquals(net.kyori.adventure.text.format.TextColor.color(0x00ff00), own.color(red).children().get(0).color(), "свой цвет сегмента остаётся");
    }

    /** Слово на буквы: позиции по ширинам шрифта, стиль сохраняется, пробел - отступ без частицы. */
    @Test
    void textShatterLayout() {
        List<ParticlePiece> p = TextShatter.pieces(net.kyori.adventure.text.Component.text("слово"), 1, 0);
        assertEquals(5, p.size());
        double[] xs = p.stream().mapToDouble(ParticlePiece::dx).toArray();
        assertArrayEquals(new double[]{-12 / 40.0, -6 / 40.0, 0, 6 / 40.0, 12 / 40.0}, xs, 1e-9, "по 6 px, центр слова - 0");
        assertEquals("с", ((net.kyori.adventure.text.TextComponent) p.get(0).text()).content());
        List<ParticlePiece> turned = TextShatter.pieces(net.kyori.adventure.text.Component.text("ab"), 2, 90);
        assertEquals(0, turned.get(0).dx(), 1e-9, "yaw 90 - вдоль Z");
        assertEquals(-6 * 2 / 40.0 / 2 * 1, turned.get(0).dz(), 1e-9);
        List<ParticlePiece> spaced = TextShatter.pieces(net.kyori.adventure.text.Component.text("a b"), 1, 0);
        assertEquals(2, spaced.size(), "пробел - без частицы");
        assertEquals(-5 / 40.0, spaced.get(0).dx(), 1e-9, "6+4+6=16: центр a на -5");
        var red = net.kyori.adventure.text.Component.text("Hi", net.kyori.adventure.text.format.NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD);
        List<ParticlePiece> styled = TextShatter.pieces(red, 1, 0);
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED, styled.get(1).text().color(), "цвет буквы сохраняется");
        assertEquals(-(7 + 3) / 2.0 / 40.0 + 7 / 2.0 / 40.0 - 0, styled.get(0).dx() + 0, 1e-9);
    }
}

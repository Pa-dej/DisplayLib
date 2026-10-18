package padej.displayLib.particles;

import me.padej.jumper.interp.Config;
import me.padej.jumper.runtime.JTable;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class ParticleColorsTest {
    private static ParticleEffect parse(String src) {
        return ParticleEffect.parse((JTable) Config.parse(src), "t");
    }

    private static Object jmc(String value) {
        return ((JTable) Config.parse("dyn v = " + value + ";")).get("v");
    }

    @Test
    void parsesColorsWithAlpha() {
        assertEquals(0xffff8800, ParticleColors.parse("#ff8800", "c"));
        assertEquals(0x80ff8800, ParticleColors.parse("#ff880080", "c"), "#rrggbbaa");
        assertEquals(0x00000000, ParticleColors.parse("#00000000", "c"));
        assertEquals(0xff0a141e, ParticleColors.parse(jmc("[10, 20, 30]"), "c"));
        assertEquals(0x400a141e, ParticleColors.parse(jmc("[10, 20, 30, 64]"), "c"));
        assertEquals(0xffff5555, ParticleColors.parse("red", "c"), "имя как у виджетов");
        assertThrows(IllegalArgumentException.class, () -> ParticleColors.parse("#zzz", "c"));
    }

    @Test
    void oklabRoundTripAndMidpoint() {
        for (int c : new int[]{0xffffffff, 0xff000000, 0xffff8800, 0xff699cc9, 0xff123456}) {
            double[] lab = ParticleColors.toOklab(c);
            assertEquals(c & 0xffffff, ParticleColors.fromOklab(lab[0], lab[1], lab[2]) & 0xffffff, Integer.toHexString(c));
        }
        // жёлтый → синий: в OkLab середина не уходит в грязно-серый, как при смешивании RGB
        int mid = ParticleColors.lerp(0xffffff00, 0xff0000ff, 0.5);
        int r = mid >> 16 & 0xff, g = mid >> 8 & 0xff, b = mid & 0xff;
        assertTrue(Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 40, Integer.toHexString(mid));
        assertEquals(0x80, ParticleColors.alpha(ParticleColors.lerp(0xff000000, 0x00000000, 0.5)), 1, "прозрачность - линейно");
    }

    @Test
    void gradientAndCurve() {
        ParticleColors.Gradient even = ParticleColors.Gradient.parse(jmc("[\"#ffffff\", \"#000000\"]"), "g");
        assertEquals(0xffffffff, even.sample(0));
        assertEquals(0xff000000, even.sample(1));
        ParticleColors.Gradient stops = ParticleColors.Gradient.parse(jmc("[[0, \"#ffffff\"], [0.2, \"#ffffff\"], [1, \"#00000000\"]]"), "g");
        assertEquals(0xffffffff, stops.sample(0.1), "до 0.2 - белый");
        assertEquals(0, ParticleColors.alpha(stops.sample(1)));
        assertThrows(IllegalArgumentException.class, () -> ParticleColors.Gradient.parse(jmc("[[0.5, \"#fff\"], [0.2, \"#000\"]]"), "g"));

        ParticleColors.Curve fade = ParticleColors.Curve.parse(jmc("[1, 0]"), "o");
        assertEquals(0.25, fade.sample(0.75), 1e-9);
        ParticleColors.Curve inOut = ParticleColors.Curve.parse(jmc("[[0, 0], [0.2, 1], [0.8, 1], [1, 0]]"), "o");
        assertEquals(0.5, inOut.sample(0.1), 1e-9);
        assertEquals(1, inOut.sample(0.5), 1e-9);
        assertTrue(ParticleColors.Curve.parse(null, "o").isConstantOne());
    }

    @Test
    void colorAtCombinesGradientAndOpacity() {
        ParticleEffect.Animation a = parse("dyn render = { type: \"PIXEL\" }; dyn animation = { opacity: [1, 0] };").animation();
        assertEquals(0xff112233, ParticleSim.colorAt(a, 0xff112233, 0, 10));
        assertEquals(0x80, ParticleColors.alpha(ParticleSim.colorAt(a, 0xff112233, 5, 10)), 1);
        assertEquals(0x112233, ParticleSim.colorAt(a, 0xff112233, 5, 10) & 0xffffff, "цвет тот же, меняется только альфа");
        assertTrue(a.animatesColor());
        ParticleEffect.Animation still = parse("dyn render = { type: \"PIXEL\" };").animation();
        assertFalse(still.animatesColor());
    }

    @Test
    void pixelDefaultsToWhite() {
        ParticleEffect e = parse("dyn render = { type: \"PIXEL\" };");
        assertEquals(java.util.List.of(0xffffffff), e.render().colors());
    }

    /** Фон пустого TextDisplay [-0.05, 0.075] × [0, 0.25] становится квадратом со стороной s с центром в точке сущности. */
    @Test
    void pixelTransformationMakesCenteredSquare() {
        for (double deg : new double[]{0, 30, 90}) {
            double s = 0.5;
            var t = LiveParticle.pixelTransformation(s, deg);
            Vector3f[] corners = {new Vector3f(-0.05f, 0, 0), new Vector3f(0.075f, 0, 0), new Vector3f(0.075f, 0.25f, 0), new Vector3f(-0.05f, 0.25f, 0)};
            Vector3f sum = new Vector3f();
            Vector3f[] out = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                out[i] = new Vector3f(corners[i]).mul(t.getScale()).rotate(t.getLeftRotation()).add(t.getTranslation());
                sum.add(out[i]);
            }
            assertEquals(0, sum.div(4).length(), 1e-5, "центр квадрата - в точке сущности, deg=" + deg);
            assertEquals(s, out[0].distance(out[1]), 1e-5, "сторона");
            assertEquals(s, out[1].distance(out[2]), 1e-5, "сторона");
        }
    }

    @Test
    void attractPullsTowardsTheAxis() {
        ParticleEffect.Motion m = parse("dyn render = { type: \"PIXEL\" }; dyn motion = { attract: 0.02, attractAxis: \"Y\", drag: 0.1 };").motion();
        ParticleSim.Body b = new ParticleSim.Body(1, 5, 0, 0, 0, 0);
        b.cx = 0; b.cy = 0; b.cz = 0;
        for (int i = 0; i < 60; i++) ParticleSim.step(b, m, null);
        assertTrue(Math.abs(b.x) < 0.3, "стянулась к оси: x=" + b.x);
        assertEquals(5, b.y, 1e-9, "к оси, а не к точке: высота не меняется");
    }

    @Test
    void wanderMovesButAttractKeepsNearby() {
        ParticleEffect.Motion m = parse("dyn render = { type: \"PIXEL\" }; dyn motion = { wander: 0.02, attract: 0.004, drag: 0.1 };").motion();
        ParticleSim.Body b = new ParticleSim.Body(0, 0, 0, 0, 0, 0);
        b.random = new SplittableRandom(1);
        double travelled = 0, px = 0, py = 0, pz = 0, maxDist = 0;
        for (int i = 0; i < 400; i++) {
            ParticleSim.step(b, m, null);
            travelled += Math.sqrt((b.x - px) * (b.x - px) + (b.y - py) * (b.y - py) + (b.z - pz) * (b.z - pz));
            px = b.x; py = b.y; pz = b.z;
            maxDist = Math.max(maxDist, Math.sqrt(b.x * b.x + b.y * b.y + b.z * b.z));
        }
        assertTrue(travelled > 2, "блуждает: путь " + travelled);
        assertTrue(maxDist < 4, "не улетает: " + maxDist);
    }

    @Test
    void foreverEmitter() {
        ParticleEffect e = parse("dyn render = { type: \"PIXEL\" }; dyn emitter = { duration: -1, rate: 1 };");
        assertEquals(ParticleEffect.Emitter.FOREVER, e.emitter().duration());
        assertEquals(0, e.emitter().count());
    }
}

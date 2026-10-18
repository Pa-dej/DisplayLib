package padej.displayLib.particles;

import me.padej.jumper.interp.Config;
import me.padej.jumper.runtime.JTable;
import org.joml.Vector3f;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class ParticleSimTest {
    private static final RandomGenerator R = RandomGenerator.of("L64X128MixRandom");

    private static ParticleEffect parse(String src) {
        return ParticleEffect.parse((JTable) Config.parse("dyn render = { text: \"*\" }; " + src), "t");
    }

    /** Пол: всё ниже y = 0 - камень. */
    private static final ParticleSim.Solid FLOOR = (x, y, z) -> y < 0;

    @Test
    void fallsAndRestsOnTheFloor() {
        ParticleEffect.Motion m = parse("dyn motion = { gravity: 0.04, collide: true, bounce: 0.3, friction: 0.5 };").motion();
        ParticleSim.Body b = new ParticleSim.Body(0.5, 2, 0.5, 0.05, 0, 0);
        for (int i = 0; i < 200 && !b.resting; i++) ParticleSim.step(b, m, FLOOR);
        assertTrue(b.resting, "легла");
        assertTrue(b.y >= 0 && b.y < 0.1, "на полу, а не в нём: y=" + b.y);
        double x = b.x;
        ParticleSim.step(b, m, FLOOR);
        assertEquals(x, b.x, "лежащая не двигается");
    }

    @Test
    void bouncesOffTheFloor() {
        ParticleEffect.Motion m = parse("dyn motion = { gravity: 0.04, collide: true, bounce: 0.8 };").motion();
        ParticleSim.Body b = new ParticleSim.Body(0.5, 0.3, 0.5, 0, -0.5, 0);
        assertTrue(ParticleSim.step(b, m, FLOOR), "удар");
        assertTrue(b.vy > 0, "отскочила вверх: vy=" + b.vy);
        assertTrue(b.y >= 0);
    }

    @Test
    void wallStopsHorizontalMotion() {
        ParticleEffect.Motion m = parse("dyn motion = { collide: true, bounce: 0 };").motion();
        ParticleSim.Solid wall = (x, y, z) -> x >= 3;
        ParticleSim.Body b = new ParticleSim.Body(0.5, 5, 0.5, 0.4, 0, 0);
        for (int i = 0; i < 20; i++) ParticleSim.step(b, m, wall);
        assertTrue(b.x < 3, "не прошла сквозь стену: x=" + b.x);
    }

    @Test
    void noCollisionWithoutCollideOrForGhosts() {
        ParticleEffect.Motion m = parse("dyn motion = { gravity: 0.1, collide: false };").motion();
        ParticleSim.Body b = new ParticleSim.Body(0, 1, 0, 0, 0, 0);
        for (int i = 0; i < 30; i++) ParticleSim.step(b, m, FLOOR);
        assertTrue(b.y < 0, "без collide падает сквозь пол");

        ParticleEffect.Motion c = parse("dyn motion = { gravity: 0.1, collide: true };").motion();
        ParticleSim.Body g = new ParticleSim.Body(0, -0.5, 0, 0, 0, 0);
        g.ghost = true; // родилась внутри блока
        for (int i = 0; i < 5; i++) ParticleSim.step(g, c, FLOOR);
        assertTrue(g.y < -0.5, "призрак не застревает");
    }

    @Test
    void hitSnapsToTheBlockFace() {
        ParticleEffect.Motion m = parse("dyn motion = { collide: true, bounce: 0.5 };").motion();
        ParticleSim.Body b = new ParticleSim.Body(0.5, 0.2, 0.5, 0, -0.5, 0);
        assertTrue(ParticleSim.step(b, m, FLOOR));
        assertEquals(0, b.y, 0.01, "прижата к полу, а не висит над ним");
        ParticleSim.Solid wall = (x, y, z) -> x >= 3;
        ParticleSim.Body w = new ParticleSim.Body(2.8, 5, 0.5, 0.5, 0, 0);
        assertTrue(ParticleSim.step(w, m, wall));
        assertEquals(3, w.x, 0.01, "прижата к стене");
        assertTrue(w.vx < 0, "отскочила");
    }

    /** Пример coin_fountain: монеты должны лечь, а не подпрыгивать на полу каждый тик. */
    @Test
    void coinFountainSettles() throws Exception {
        ParticleRegistry registry = new ParticleRegistry(java.nio.file.Path.of("src/main/resources/examples/particles"), java.util.logging.Logger.getLogger("t"));
        registry.loadAll();
        ParticleEffect e = registry.get("coin_fountain");
        RandomGenerator r = new SplittableRandom(7);
        int settled = 0, n = 500;
        for (int i = 0; i < n; i++) {
            double[] v = ParticleSim.spawnVelocity(e.motion(), new double[]{0, 0, 0}, r);
            ParticleSim.Body b = new ParticleSim.Body(0.5, 0.01, 0.5, v[0], v[1], v[2]);
            int life = (int) e.motion().lifetime().min();
            for (int t = 0; t < life && !b.resting; t++) ParticleSim.step(b, e.motion(), FLOOR);
            if (b.resting) settled++;
            assertTrue(b.y >= 0, "не провалилась");
        }
        assertTrue(settled > n * 0.95, "легли " + settled + " из " + n);
    }

    @RepeatedTest(5)
    void velocityStaysInsideTheCone() {
        ParticleEffect.Motion m = parse("dyn motion = { speed: [0.1, 0.2], direction: [1, 1, 0], spread: 30 };").motion();
        Vector3f axis = new Vector3f(1, 1, 0).normalize();
        RandomGenerator r = new SplittableRandom(R.nextLong());
        for (int i = 0; i < 500; i++) {
            double[] v = ParticleSim.spawnVelocity(m, new double[]{0, 0, 0}, r);
            Vector3f d = new Vector3f((float) v[0], (float) v[1], (float) v[2]);
            float speed = d.length();
            assertTrue(speed >= 0.0999 && speed <= 0.2001, "скорость " + speed);
            double angle = Math.toDegrees(axis.angle(d));
            assertTrue(angle <= 30.01, "угол " + angle);
        }
    }

    @Test
    void outwardGoesAwayFromCenter() {
        ParticleEffect e = parse("dyn emitter = { shape: \"SPHERE\", radius: 1, surface: true }; dyn motion = { speed: 0.1, direction: \"OUTWARD\" };");
        for (int i = 0; i < 200; i++) {
            double[] off = ParticleSim.spawnOffset(e.emitter(), R);
            assertEquals(1.0, Math.sqrt(off[0] * off[0] + off[1] * off[1] + off[2] * off[2]), 1e-9, "на поверхности");
            double[] v = ParticleSim.spawnVelocity(e.motion(), off, R);
            assertTrue(v[0] * off[0] + v[1] * off[1] + v[2] * off[2] > 0, "наружу");
        }
    }

    @Test
    void shapesStayInBounds() {
        ParticleEffect box = parse("dyn emitter = { shape: \"BOX\", size: [2, 1, 4] };");
        ParticleEffect circle = parse("dyn emitter = { shape: \"CIRCLE\", radius: 0.5 };");
        for (int i = 0; i < 500; i++) {
            double[] p = ParticleSim.spawnOffset(box.emitter(), R);
            assertTrue(Math.abs(p[0]) <= 1 && Math.abs(p[1]) <= 0.5 && Math.abs(p[2]) <= 2);
            double[] c = ParticleSim.spawnOffset(circle.emitter(), R);
            assertEquals(0, c[1]);
            assertTrue(c[0] * c[0] + c[2] * c[2] <= 0.25 + 1e-9);
        }
    }

    @Test
    void scaleCurve() {
        ParticleEffect.Animation a = parse("dyn animation = { scale: 1, endScale: 0, easing: \"LINEAR\", fadeIn: 4 };").animation();
        assertEquals(0, ParticleSim.scaleAt(a, 1, 0, 100), 1e-9, "fadeIn с нуля");
        assertEquals(0.5 * 0.98, ParticleSim.scaleAt(a, 1, 2, 100), 1e-9, "половина fadeIn");
        assertEquals(0.5, ParticleSim.scaleAt(a, 1, 50, 100), 1e-9);
        assertEquals(0, ParticleSim.scaleAt(a, 1, 100, 100), 1e-9);
        ParticleEffect.Animation keep = parse("dyn animation = { scale: 2 };").animation();
        assertEquals(2, ParticleSim.scaleAt(keep, 2, 70, 100), 1e-9, "endScale по умолчанию 1");
    }

    @Test
    void transformationKeepsCenterInPlace() {
        float[] c = {0.025f, 0.1f, 0};
        for (double deg : new double[]{0, 37, 90, 200}) {
            var t = LiveParticle.transformation(c, 2.5, deg);
            // точка-центр картинки после T + R·S·c должна оказаться в начале координат
            Vector3f p = new Vector3f(c[0], c[1], c[2]).mul(t.getScale()).rotate(t.getLeftRotation()).add(t.getTranslation());
            assertEquals(0, p.length(), 1e-5, "deg=" + deg);
        }
    }
}

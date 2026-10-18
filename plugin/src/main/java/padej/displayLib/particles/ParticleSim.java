package padej.displayLib.particles;

import padej.displayLib.config.HoverAnimation;
import padej.displayLib.utils.EasedAnimation;

import java.util.random.RandomGenerator;

/**
 * Математика частиц без Bukkit: стартовая точка и скорость, шаг движения с коллизиями,
 * размер и поворот во времени. Всё в блоках и тиках.
 */
public final class ParticleSim {
    private ParticleSim() {}

    /** Твёрдый ли блок с такими координатами (для коллизий). */
    @FunctionalInterface
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    /** Ниже этой скорости (блоков/тик) после удара частица считается лежащей. */
    static final double REST_SPEED = 0.008;

    /** Положение и скорость одной частицы. */
    public static final class Body {
        public double x, y, z;
        public double vx, vy, vz;
        /** Лежит на поверхности и больше не движется */
        public boolean resting;
        /** Появилась внутри блока: коллизии для неё выключены, иначе она застряла бы */
        public boolean ghost;

        public Body(double x, double y, double z, double vx, double vy, double vz) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
        }

        public double speedSq() {
            return vx * vx + vy * vy + vz * vz;
        }
    }

    // -------------------------------------------------------------------------
    // Рождение
    // -------------------------------------------------------------------------

    /** Смещение точки рождения от центра эмиттера по его форме. */
    public static double[] spawnOffset(ParticleEffect.Emitter e, RandomGenerator r) {
        return switch (e.shape()) {
            case POINT -> new double[]{0, 0, 0};
            case SPHERE -> {
                double[] d = randomUnit(r);
                // равномерно по объёму: радиус ~ кубический корень
                double rad = e.surface() ? e.radius() : e.radius() * Math.cbrt(r.nextDouble());
                yield new double[]{d[0] * rad, d[1] * rad, d[2] * rad};
            }
            case CIRCLE -> {
                // горизонтальный круг (плоскость XZ)
                double a = r.nextDouble() * Math.PI * 2;
                double rad = e.surface() ? e.radius() : e.radius() * Math.sqrt(r.nextDouble());
                yield new double[]{Math.cos(a) * rad, 0, Math.sin(a) * rad};
            }
            case BOX -> {
                double[] h = e.size();
                double[] p = {(r.nextDouble() * 2 - 1) * h[0], (r.nextDouble() * 2 - 1) * h[1], (r.nextDouble() * 2 - 1) * h[2]};
                if (e.surface()) {
                    // прижать к случайной грани
                    int axis = r.nextInt(3);
                    p[axis] = (r.nextBoolean() ? 1 : -1) * h[axis];
                }
                yield p;
            }
        };
    }

    /** Начальная скорость: конус {@code spread} вокруг оси или «от центра» (direction == null). */
    public static double[] spawnVelocity(ParticleEffect.Motion m, double[] offset, RandomGenerator r) {
        double speed = m.speed().sample(r);
        if (speed == 0) return new double[]{0, 0, 0};
        double[] axis = m.direction();
        if (axis == null) {
            double len = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]);
            axis = len < 1e-9 ? randomUnit(r) : new double[]{offset[0] / len, offset[1] / len, offset[2] / len};
        }
        double[] d = randomInCone(axis, Math.toRadians(m.spread()), r);
        return new double[]{d[0] * speed, d[1] * speed, d[2] * speed};
    }

    /** Случайный единичный вектор, равномерно по сфере. */
    static double[] randomUnit(RandomGenerator r) {
        double z = r.nextDouble() * 2 - 1;
        double a = r.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - z * z);
        return new double[]{s * Math.cos(a), z, s * Math.sin(a)};
    }

    /** Случайный единичный вектор в конусе с половинным углом {@code angle} вокруг единичной оси (равномерно по шапке сферы). */
    static double[] randomInCone(double[] axis, double angle, RandomGenerator r) {
        if (angle <= 0) return axis.clone();
        double cosT = 1 - r.nextDouble() * (1 - Math.cos(Math.min(angle, Math.PI)));
        double sinT = Math.sqrt(Math.max(0, 1 - cosT * cosT));
        double phi = r.nextDouble() * Math.PI * 2;
        // базис, перпендикулярный оси
        double[] helper = Math.abs(axis[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] u = normalize(cross(axis, helper));
        double[] v = cross(axis, u);
        double c = Math.cos(phi) * sinT, s = Math.sin(phi) * sinT;
        return new double[]{
                axis[0] * cosT + u[0] * c + v[0] * s,
                axis[1] * cosT + u[1] * c + v[1] * s,
                axis[2] * cosT + u[2] * c + v[2] * s};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] a) {
        double len = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new double[]{a[0] / len, a[1] / len, a[2] / len};
    }

    // -------------------------------------------------------------------------
    // Движение
    // -------------------------------------------------------------------------

    /**
     * Один тик: гравитация, сопротивление, перемещение. С коллизиями движение идёт по осям
     * (сначала Y, затем X и Z): ось, на которой частица вошла бы в блок, отражается с потерей
     * ({@code bounce}), остальные гасятся трением; медленная после удара частица ложится.
     *
     * @param solid {@code null} - без коллизий
     * @return true, если частица ударилась о блок на этом тике
     */
    public static boolean step(Body b, ParticleEffect.Motion m, Solid solid) {
        if (b.resting) return false;
        b.vy -= m.gravity();
        double keep = 1 - m.drag();
        b.vx *= keep; b.vy *= keep; b.vz *= keep;

        if (solid == null || b.ghost || !m.collide()) {
            b.x += b.vx; b.y += b.vy; b.z += b.vz;
            return false;
        }

        boolean hit = false;
        // Y
        double ny = b.y + b.vy;
        if (solid.at(floor(b.x), floor(ny), floor(b.z))) {
            hit = true;
            b.vy = -b.vy * m.bounce();
            b.vx *= m.friction(); b.vz *= m.friction();
        } else {
            b.y = ny;
        }
        // X
        double nx = b.x + b.vx;
        if (solid.at(floor(nx), floor(b.y), floor(b.z))) {
            hit = true;
            b.vx = -b.vx * m.bounce();
            b.vy *= m.friction(); b.vz *= m.friction();
        } else {
            b.x = nx;
        }
        // Z
        double nz = b.z + b.vz;
        if (solid.at(floor(b.x), floor(b.y), floor(nz))) {
            hit = true;
            b.vz = -b.vz * m.bounce();
            b.vx *= m.friction(); b.vy *= m.friction();
        } else {
            b.z = nz;
        }

        if (hit && b.speedSq() < REST_SPEED * REST_SPEED
                && (m.gravity() <= 0 || solid.at(floor(b.x), floor(b.y - 0.05), floor(b.z)))) {
            b.vx = b.vy = b.vz = 0;
            b.resting = true;
        }
        return hit;
    }

    /**
     * Продвинуть частицу до {@code maxSteps} тиков; останавливается сразу после удара, чтобы
     * ключевой кадр пришёлся на точку удара.
     *
     * @return сколько тиков прошло (от 1 до maxSteps)
     */
    public static int advance(Body b, ParticleEffect.Motion m, Solid solid, int maxSteps) {
        for (int i = 1; i <= maxSteps; i++) {
            if (step(b, m, solid) && i < maxSteps) return i;
        }
        return maxSteps;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    // -------------------------------------------------------------------------
    // Вид во времени
    // -------------------------------------------------------------------------

    /** Размер в возрасте {@code age}: от start к start·endScale по кривой, с вырастанием за fadeIn. */
    public static double scaleAt(ParticleEffect.Animation a, double start, int age, int life) {
        double t = life <= 0 ? 1 : Math.max(0, Math.min(1, (double) age / life));
        double k = EasedAnimation.getEasingFunction(a.easing() != null ? a.easing() : HoverAnimation.EasingType.LINEAR).applyAsDouble(t);
        double s = start * (1 + (a.endScale() - 1) * k);
        if (a.fadeIn() > 0 && age < a.fadeIn()) s *= (double) age / a.fadeIn();
        return Math.max(0, s);
    }

    /** Угол в плоскости частицы, градусы. */
    public static double rotationAt(double start, double spin, int age) {
        return start + spin * age;
    }
}

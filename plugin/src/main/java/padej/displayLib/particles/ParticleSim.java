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
        /** Точка притяжения ({@code motion.attract}) - центр эмиттера в момент рождения */
        public double cx, cy, cz;
        /** Направление блуждания ({@code motion.wander}), единичный вектор; поворачивается понемногу каждый тик */
        public double wx, wy = 1, wz;
        /** Случайность блуждания; {@code null} - блуждания нет */
        public RandomGenerator random;

        public Body(double x, double y, double z, double vx, double vy, double vz) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
        }

        public double speedSq() {
            return vx * vx + vy * vy + vz * vz;
        }

        /** Копия состояния (для просчёта вперёд). */
        public Body copy() {
            Body b = new Body(x, y, z, vx, vy, vz);
            b.resting = resting; b.ghost = ghost;
            b.cx = cx; b.cy = cy; b.cz = cz;
            b.wx = wx; b.wy = wy; b.wz = wz;
            b.random = random;
            return b;
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
     * Притяжение и блуждание. Притяжение - постоянной силы к точке (или оси) рождения, ослабевает
     * в последней четверти блока, чтобы частица не дрожала в центре. Блуждание - толчок вдоль
     * направления, которое каждый тик немного поворачивается: путь получается плавным, а не дёрганым.
     */
    static void applyForces(Body b, ParticleEffect.Motion m) {
        if (m.attract() != 0) {
            double dx = b.cx - b.x, dy = m.attractAxis() ? 0 : b.cy - b.y, dz = b.cz - b.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > 1e-6) {
                double k = m.attract() * Math.min(1, d / 0.25) / d;
                b.vx += dx * k; b.vy += dy * k; b.vz += dz * k;
            }
        }
        if (m.wander() > 0 && b.random != null) {
            double[] j = randomUnit(b.random);
            double wx = b.wx + j[0] * 0.6, wy = b.wy + j[1] * 0.6, wz = b.wz + j[2] * 0.6;
            double len = Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (len > 1e-9) { b.wx = wx / len; b.wy = wy / len; b.wz = wz / len; }
            double push = m.wander() * b.random.nextDouble();
            b.vx += b.wx * push; b.vy += b.wy * push; b.vz += b.wz * push;
        }
    }

    /** Отступ от грани блока, к которой прижимается частица при ударе. */
    static final double SKIN = 1e-3;

    /**
     * Один тик: гравитация, сопротивление, перемещение. С коллизиями движение идёт по осям
     * (сначала Y, затем X и Z): на оси, где частица вошла бы в блок, она прижимается к его грани,
     * скорость по оси отражается с потерей ({@code bounce}), остальные гасятся трением ({@code friction}).
     * На полу медленный удар (меньше двух шагов гравитации) не отскакивает: частица скользит
     * и ложится, когда почти остановилась.
     *
     * @param solid {@code null} - без коллизий
     * @return true, если частица ударилась о блок на этом тике
     */
    public static boolean step(Body b, ParticleEffect.Motion m, Solid solid) {
        if (b.resting) return false;
        b.vy -= m.gravity();
        applyForces(b, m);
        double keep = 1 - m.drag();
        b.vx *= keep; b.vy *= keep; b.vz *= keep;

        if (solid == null || b.ghost || !m.collide()) {
            b.x += b.vx; b.y += b.vy; b.z += b.vz;
            return false;
        }

        boolean hit = false, floorContact = false;
        // Y
        double ny = b.y + b.vy;
        if (b.vy != 0 && solid.at(floor(b.x), floor(ny), floor(b.z))) {
            hit = true;
            boolean falling = b.vy < 0;
            b.y = falling ? Math.floor(ny) + 1 + SKIN : Math.floor(ny) - SKIN;
            double impact = Math.abs(b.vy);
            if (falling && impact < 2 * Math.max(m.gravity(), 0) + REST_SPEED) {
                b.vy = 0; // мягкое касание: не прыгать на месте
            } else {
                b.vy = -b.vy * m.bounce();
            }
            floorContact = falling;
            b.vx *= m.friction(); b.vz *= m.friction();
        } else {
            b.y = ny;
        }
        // X
        double nx = b.x + b.vx;
        if (b.vx != 0 && solid.at(floor(nx), floor(b.y), floor(b.z))) {
            hit = true;
            b.x = b.vx < 0 ? Math.floor(nx) + 1 + SKIN : Math.floor(nx) - SKIN;
            b.vx = -b.vx * m.bounce();
            b.vy *= m.friction(); b.vz *= m.friction();
        } else {
            b.x = nx;
        }
        // Z
        double nz = b.z + b.vz;
        if (b.vz != 0 && solid.at(floor(b.x), floor(b.y), floor(nz))) {
            hit = true;
            b.z = b.vz < 0 ? Math.floor(nz) + 1 + SKIN : Math.floor(nz) - SKIN;
            b.vz = -b.vz * m.bounce();
            b.vx *= m.friction(); b.vy *= m.friction();
        } else {
            b.z = nz;
        }

        if (hit && b.speedSq() < REST_SPEED * REST_SPEED && (m.gravity() <= 0 || floorContact)) {
            b.vx = b.vy = b.vz = 0;
            b.resting = true;
        }
        return hit;
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

    // -------------------------------------------------------------------------
    // Ключевые кадры
    // -------------------------------------------------------------------------

    /** Допустимое отличие скорости внутри отрезка от средней скорости отрезка (доля). */
    static final double SEGMENT_SPEED_TOLERANCE = 0.12;
    /** То же в абсолютных единицах для медленных частиц, блоков/тик. */
    static final double SEGMENT_SPEED_FLOOR = 0.003;
    /** Допустимое отклонение прямой клиента от настоящей траектории, блоков. */
    static final double SEGMENT_PATH_TOLERANCE = 0.02;

    /** Отрезок движения: сколько тиков и состояние тела в его конце. */
    public record Segment(int length, Body end) {}

    /**
     * Самый длинный отрезок (не больше {@code maxLength}), который клиент может провести по прямой
     * с постоянной скоростью незаметно для глаза: скорость на каждом тике отличается от средней
     * не больше чем на {@link #SEGMENT_SPEED_TOLERANCE}, а путь отходит от прямой не дальше
     * {@link #SEGMENT_PATH_TOLERANCE}. Глаз замечает именно скачки скорости между отрезками, поэтому
     * там, где скорость меняется быстро (старт с сопротивлением, вершина дуги, отскок), отрезки
     * короче, а на ровном полёте - длинные.
     */
    public static Segment planSegment(Body start, ParticleEffect.Motion m, Solid solid, int maxLength) {
        maxLength = Math.max(1, maxLength);
        Body cur = start.copy();
        Body[] states = new Body[maxLength + 1];
        double[] xs = new double[maxLength + 1], ys = new double[maxLength + 1], zs = new double[maxLength + 1];
        xs[0] = start.x; ys[0] = start.y; zs[0] = start.z;
        for (int i = 1; i <= maxLength; i++) {
            step(cur, m, solid);
            states[i] = cur.copy();
            xs[i] = cur.x; ys[i] = cur.y; zs[i] = cur.z;
        }
        int best = 1;
        for (int length = 2; length <= maxLength; length++) {
            if (!smooth(xs, ys, zs, length)) break;
            best = length;
        }
        return new Segment(best, states[best]);
    }

    private static boolean smooth(double[] xs, double[] ys, double[] zs, int length) {
        double ax = (xs[length] - xs[0]) / length, ay = (ys[length] - ys[0]) / length, az = (zs[length] - zs[0]) / length;
        double allowed = Math.max(SEGMENT_SPEED_TOLERANCE * Math.sqrt(ax * ax + ay * ay + az * az), SEGMENT_SPEED_FLOOR);
        for (int j = 0; j < length; j++) {
            double dx = xs[j + 1] - xs[j] - ax, dy = ys[j + 1] - ys[j] - ay, dz = zs[j + 1] - zs[j] - az;
            if (dx * dx + dy * dy + dz * dz > allowed * allowed) return false;
            double ex = xs[0] + ax * (j + 1) - xs[j + 1], ey = ys[0] + ay * (j + 1) - ys[j + 1], ez = zs[0] + az * (j + 1) - zs[j + 1];
            if (ex * ex + ey * ey + ez * ez > SEGMENT_PATH_TOLERANCE * SEGMENT_PATH_TOLERANCE) return false;
        }
        return true;
    }

    /** Цвет (ARGB) в возрасте {@code age}: кривая {@code animation.color} (или стартовый цвет) с множителем {@code opacity}. */
    public static int colorAt(ParticleEffect.Animation a, int startColor, int age, int life) {
        double t = life <= 0 ? 1 : Math.max(0, Math.min(1, (double) age / life));
        int c = a.color() != null ? a.color().sample(t) : startColor;
        return ParticleColors.withOpacity(c, a.opacity().sample(t));
    }

    /** Допустимое отличие размера на прямой клиента от настоящего, доля размера. */
    static final double TRANSFORM_SCALE_TOLERANCE = 0.03;
    /** То же в абсолютных единицах (для маленьких частиц и около нуля). */
    static final double TRANSFORM_SCALE_FLOOR = 0.003;
    /** Допустимое отличие каждого канала ARGB цвета от настоящего (0..255). */
    static final int TRANSFORM_COLOR_TOLERANCE = 5;

    /**
     * Длина кадра вида (размер, цвет): самый длинный отрезок от возраста {@code from} (не больше
     * {@code maxSteps}), на котором линейная интерполяция клиента - размер по прямой, цвет поканально
     * в ARGB от {@code fromScale}/{@code fromColor} - отходит от настоящих значений не больше допусков.
     * Линейный размер без кривой цвета проходит всю жизнь одним кадром; нелинейный easing, fadeIn,
     * градиенты и кривые прозрачности дробят жизнь на столько кадров, сколько нужно глазу.
     *
     * @param colored цвет меняется у сущности (PIXEL, TEXT); иначе сравнивается только размер
     */
    public static int planTransform(ParticleEffect.Animation a, double startScale, int startColor, boolean colored,
                                    double fromScale, int fromColor, int from, int life, int maxSteps) {
        if (maxSteps <= 1) return Math.max(1, maxSteps);
        double[] scales = new double[maxSteps + 1];
        int[] colors = colored ? new int[maxSteps + 1] : null;
        for (int j = 1; j <= maxSteps; j++) {
            scales[j] = scaleAt(a, startScale, from + j, life);
            if (colored) colors[j] = colorAt(a, startColor, from + j, life);
        }
        int best = 1;
        for (int n = 2; n <= maxSteps; n++) {
            boolean ok = true;
            for (int j = 1; j < n && ok; j++) {
                double f = (double) j / n;
                double lerpScale = fromScale + (scales[n] - fromScale) * f;
                double tolerance = Math.max(TRANSFORM_SCALE_TOLERANCE * Math.abs(scales[j]), TRANSFORM_SCALE_FLOOR);
                if (Math.abs(lerpScale - scales[j]) > tolerance) ok = false;
                else if (colored && !closeArgb(lerpArgb(fromColor, colors[n], f), colors[j])) ok = false;
            }
            if (!ok) break;
            best = n;
        }
        return best;
    }

    /**
     * Длины всех кадров вида за жизнь частицы - так же, как их отправляет {@link LiveParticle}
     * (с ограничением поворота меньше 180° за кадр). Для оценки нагрузки и тестов.
     */
    public static int[] transformPlan(ParticleEffect.Animation a, double startScale, int startColor, boolean colored,
                                      double spin, int life) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        double sentScale = scaleAt(a, startScale, 0, life);
        int sentColor = colorAt(a, startColor, 0, life);
        int visual = 0;
        while (visual < life) {
            int steps = life - visual;
            if (spin != 0) steps = Math.min(steps, Math.max(1, (int) (170 / Math.abs(spin))));
            steps = planTransform(a, startScale, startColor, colored, sentScale, sentColor, visual, life, steps);
            out.add(steps);
            visual += steps;
            sentScale = scaleAt(a, startScale, visual, life);
            if (colored) sentColor = colorAt(a, startColor, visual, life);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Как клиент интерполирует цвет фона: каждый канал ARGB по прямой. */
    public static int lerpArgb(int a, int b, double f) {
        if (f <= 0 || a == b) return a;
        if (f >= 1) return b;
        int out = 0;
        for (int sh = 24; sh >= 0; sh -= 8) {
            int x = (a >>> sh) & 0xff, y = (b >>> sh) & 0xff;
            out |= ((int) Math.round(x + (y - x) * f) & 0xff) << sh;
        }
        return out;
    }

    private static boolean closeArgb(int a, int b) {
        for (int sh = 24; sh >= 0; sh -= 8) {
            if (Math.abs(((a >>> sh) & 0xff) - ((b >>> sh) & 0xff)) > TRANSFORM_COLOR_TOLERANCE) return false;
        }
        return true;
    }

    /** Угол в плоскости частицы, градусы. */
    public static double rotationAt(double start, double spin, int age) {
        return start + spin * age;
    }
}

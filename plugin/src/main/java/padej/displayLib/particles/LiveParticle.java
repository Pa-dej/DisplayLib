package padej.displayLib.particles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Частица в мире: Display-сущность и её физическое тело.
 *
 * <p>Сервер не шлёт пакет каждый тик. Позиция: раз в ровно {@code keyframeTicks} тиков тело
 * просчитывается на столько же вперёд и сущность телепортируется в эту точку с
 * {@code teleportDuration = keyframeTicks} - клиент ведёт её туда равномерно. Отрезок всегда
 * одной длины: длительность интерполяции позиции задана сущности заранее и не может меняться
 * от кадра к кадру без рассинхрона. Размер и поворот: трансформация с
 * {@code interpolationDuration} - её отрезки могут быть короче (быстрое вращение, конец жизни),
 * длительность приходит вместе с кадром.</p>
 */
final class LiveParticle {
    final ParticleEmission emission;
    final Display entity;
    final World world;
    final ParticleSim.Body body;
    final int life;
    final double startScale;
    final double startRotation;
    final double spin;
    /** Точка в локальных координатах сущности, вокруг которой она масштабируется и вращается (центр картинки) */
    final float centerX, centerY, centerZ;
    /** Квадрат-фон (PIXEL): своя трансформация, цвет - фон сущности */
    final boolean pixel;
    /** Текст без цвета (TEXT): цвет во времени накладывается на него; null у остальных типов */
    final Component baseText;
    /** Стартовый цвет ARGB (случайный из render.colors) */
    final int startColor;
    int sentColor;
    /** Серверный тик появления: первый кадр - не раньше следующего (интерполяция только что появившейся сущности не работает) */
    final int spawnTick;

    int age;
    /** Возраст, до которого просчитано тело (цель последнего кадра позиции) */
    int simAge;
    int nextPosition = 1;
    int nextTransform = 1;
    /** Возраст, к которому относится последняя отправленная трансформация */
    int sentAge;
    double sentScale = Double.NaN, sentRotation = Double.NaN;
    boolean dead;

    LiveParticle(ParticleEmission emission, Display entity, World world, ParticleSim.Body body, int life,
                 double startScale, double startRotation, double spin, float[] center, int spawnTick,
                 boolean pixel, Component baseText, int startColor) {
        this.pixel = pixel;
        this.baseText = baseText;
        this.startColor = startColor;
        this.sentColor = ParticleSim.colorAt(emission.effect.animation(), startColor, 0, life);
        this.emission = emission;
        this.entity = entity;
        this.world = world;
        this.body = body;
        this.life = life;
        this.startScale = startScale;
        this.startRotation = startRotation;
        this.spin = spin;
        this.centerX = center[0];
        this.centerY = center[1];
        this.centerZ = center[2];
        this.spawnTick = spawnTick;
        this.sentScale = ParticleSim.scaleAt(emission.effect.animation(), startScale, 0, life);
        this.sentRotation = startRotation;
    }

    /**
     * Трансформация с размером {@code s} и углом {@code deg} (вокруг локальной Z), масштаб и поворот -
     * вокруг центра картинки {@code center}: T = -R·(S·c), так что центр остаётся в точке сущности.
     */
    static Transformation transformation(boolean pixel, float[] center, double s, double deg) {
        if (pixel) return pixelTransformation(s, deg);
        return transformation(center, s, deg);
    }

    /**
     * Квадрат со стороной {@code s} с центром в точке сущности, повёрнутый на {@code deg}. Фон пустого
     * TextDisplay ({@code " "}) занимает по x [-0.05, 0.075], по y [0, 0.25] блока; растяжение (8, 4)
     * со сдвигом делает из него единичный квадрат (приём из text-display-experiments Heledron).
     */
    static Transformation pixelTransformation(double s, double deg) {
        float k = (float) s;
        float a = (float) Math.toRadians(deg);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        // M = R·S(k)·T(-0.1, -0.5)·S(8, 4): translation = R·(k·(-0.1, -0.5)), scale = (8k, 4k, k)
        float tx = -0.1f * k, ty = -0.5f * k;
        return new Transformation(new Vector3f(tx * cos - ty * sin, tx * sin + ty * cos, 0),
                new Quaternionf().rotateZ(a), new Vector3f(8 * k, 4 * k, k), new Quaternionf());
    }

    static Transformation transformation(float[] center, double s, double deg) {
        float scale = (float) s;
        float a = (float) Math.toRadians(deg);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        float cx = center[0] * scale, cy = center[1] * scale, cz = center[2] * scale;
        Vector3f translation = new Vector3f(-(cx * cos - cy * sin), -(cx * sin + cy * cos), -cz);
        return new Transformation(translation, new Quaternionf().rotateZ(a), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** Обработать текущий возраст: отправить кадры позиции и вида, если подошло их время. */
    void update(int keyframeTicks, ParticleSim.Solid solid) {
        if (age >= nextPosition) sendPosition(keyframeTicks, solid);
        if (age >= nextTransform) sendTransform(keyframeTicks);
    }

    private void sendPosition(int keyframeTicks, ParticleSim.Solid solid) {
        ParticleEffect.Motion motion = emission.effect.motion();
        // догнать текущий возраст (первый кадр идёт через тик после появления)
        while (simAge < age) { ParticleSim.step(body, motion, solid); simAge++; }
        boolean wasResting = body.resting;
        double px = body.x, py = body.y, pz = body.z;
        // всегда полный отрезок, даже за концом жизни: длительность интерполяции у клиента - keyframeTicks
        for (int i = 0; i < keyframeTicks; i++) ParticleSim.step(body, motion, solid);
        simAge = age + keyframeTicks;
        nextPosition = simAge;
        if (!wasResting && (body.x != px || body.y != py || body.z != pz)) {
            entity.teleport(new Location(world, body.x, body.y, body.z, 0, 0));
        }
    }

    private void sendTransform(int keyframeTicks) {
        ParticleEffect.Animation animation = emission.effect.animation();
        int steps = Math.min(keyframeTicks, life - age);
        if (spin != 0) {
            // поворот за кадр (от последней отправленной трансформации) - меньше 180°,
            // иначе клиент повернёт кратчайшим путём, в обратную сторону
            int maxSpan = Math.max(1, (int) (170 / Math.abs(spin)));
            steps = Math.min(steps, Math.max(1, maxSpan - (age - sentAge)));
        }
        if (steps <= 0) {
            nextTransform = Integer.MAX_VALUE;
            return;
        }
        int target = age + steps;
        nextTransform = target;
        double s = ParticleSim.scaleAt(animation, startScale, target, life);
        double r = ParticleSim.rotationAt(startRotation, spin, target);
        boolean transformChanged = s != sentScale || r != sentRotation;
        // цвет фона и прозрачность текста клиент интерполирует вместе с трансформацией
        int c = entity instanceof TextDisplay ? ParticleSim.colorAt(animation, startColor, target, life) : sentColor;
        boolean colorChanged = c != sentColor;
        if (transformChanged || colorChanged) {
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(target - age);
            if (transformChanged) {
                entity.setTransformation(transformation(pixel, new float[]{centerX, centerY, centerZ}, s, r));
                sentScale = s;
                sentRotation = r;
            }
            if (colorChanged) applyColor((TextDisplay) entity, c, sentColor);
            sentColor = c;
        }
        sentAge = target;
    }

    /** Цвет частицы: фон квадрата (PIXEL) или цвет и прозрачность текста (TEXT). */
    void applyColor(TextDisplay d, int argb, int previous) {
        if (pixel) {
            d.setBackgroundColor(Color.fromARGB(argb));
            return;
        }
        if (baseText != null && (argb & 0xffffff) != (previous & 0xffffff)) {
            d.text(baseText.color(TextColor.color(argb & 0xffffff))); // цвет текста меняется ступенькой
        }
        if (ParticleColors.alpha(argb) != ParticleColors.alpha(previous)) d.setTextOpacity(textOpacity(argb));
    }

    /** Непрозрачность текста как байт; значения ниже 26 клиент всё равно не рисует. */
    static byte textOpacity(int argb) {
        return (byte) Math.max(4, ParticleColors.alpha(argb));
    }

    /** Показать частицу игроку (владелец перезашёл). */
    void showTo(org.bukkit.entity.Player player, org.bukkit.plugin.Plugin plugin) {
        if (!dead && entity.isValid()) player.showEntity(plugin, entity);
    }

    void kill() {
        if (dead) return;
        dead = true;
        try {
            entity.remove();
        } catch (RuntimeException ignored) {
            // мир уже выгружен - сущности и так нет
        }
    }
}

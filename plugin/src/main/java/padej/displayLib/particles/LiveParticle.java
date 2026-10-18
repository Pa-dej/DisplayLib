package padej.displayLib.particles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Частица в мире: Display-сущность и её физическое тело.
 *
 * <p>Сервер не шлёт пакет каждый тик. Позиция: тело просчитывается вперёд отрезками
 * ({@link ParticleSim#planSegment}), и сущность телепортируется в конец отрезка с
 * {@code teleportDuration} = его длине - клиент ведёт её туда по прямой с постоянной скоростью.
 * Длина отрезка выбирается по траектории (короче там, где скорость быстро меняется, не длиннее
 * {@code keyframeTicks}) и отправляется на кадр раньше. Размер, поворот, цвет: трансформация и
 * цвет с {@code interpolationDuration} - короткие отрезки на старте, потом {@code keyframeTicks}.</p>
 *
 * <p>Клиент показывает частицу с задержкой ровно в один тик: первый кадр не может прийти в тик
 * появления (интерполяция только что появившейся сущности не работает), поэтому вся анимация
 * сдвинута на тик, а не «догоняет» его.</p>
 */
final class LiveParticle {
    final ParticleEmission emission;
    /** Показ частицы: сущность в мире или пакеты */
    final ParticleView view;
    /** Сущность, у которой меняется вид (у пакетов - вне мира) */
    final Display entity;
    final World world;
    /** Тело в момент последней цели клиента */
    ParticleSim.Body body;
    /** Следующий отрезок движения, его длина уже отправлена клиенту как teleportDuration */
    ParticleSim.Segment planned;
    final int life;
    final double startScale;
    final double startRotation;
    final double spin;
    /** Точка в локальных координатах сущности, вокруг которой она масштабируется и вращается (центр картинки) */
    final float centerX, centerY, centerZ;
    /** Квадрат-фон (PIXEL): своя трансформация, цвет - фон сущности */
    final boolean pixel;
    /** Текст без цвета (TEXT): цвет во времени накладывается на него; null у остальных типов */
    Component baseText;
    /** Перекрашивать ли текст (есть colors или градиент); иначе цвет - как в самом тексте */
    final boolean recolor;
    /** Анимированный вариант текста (TEXT с frames) или null; первый кадр и текущий */
    final ParticleEffect.TextVariant frames;
    final int frameStart;
    int frame;
    /** Стартовый цвет ARGB (случайный из render.colors) */
    final int startColor;
    int sentColor;
    /** Серверный тик появления: первый кадр - не раньше следующего (интерполяция только что появившейся сущности не работает) */
    final int spawnTick;

    int age;
    int nextPosition = 1;
    int nextTransform = 1;
    /** Номер отрезка вида (короткие отрезки на старте) */
    int transformSegment;
    /** Возраст, к которому относится последняя отправленная трансформация */
    int sentAge;
    double sentScale = Double.NaN, sentRotation = Double.NaN;
    boolean dead;
    /** Поворот сущности (FIXED-текст смотрит в эту сторону); сохраняется при телепортах */
    float yaw;

    LiveParticle(ParticleEmission emission, ParticleView view, World world, ParticleSim.Body body, int life,
                 double startScale, double startRotation, double spin, float[] center, int spawnTick,
                 boolean pixel, Component baseText, int startColor) {
        this(emission, view, world, body, life, startScale, startRotation, spin, center, spawnTick, pixel, baseText, startColor, null, 0, baseText != null);
    }

    LiveParticle(ParticleEmission emission, ParticleView view, World world, ParticleSim.Body body, int life,
                 double startScale, double startRotation, double spin, float[] center, int spawnTick,
                 boolean pixel, Component baseText, int startColor, ParticleEffect.TextVariant frames, int frameStart, boolean recolor) {
        this.frames = frames;
        this.frameStart = frameStart;
        this.recolor = recolor;
        this.frame = frames == null ? 0 : ParticleSim.frameAt(frameStart, frames.frames().size(), frames.frameInterval(), frames.overLife(), 0, life);
        this.pixel = pixel;
        this.baseText = baseText;
        this.startColor = startColor;
        this.sentColor = ParticleSim.colorAt(emission.effect.animation(), startColor, 0, life);
        this.emission = emission;
        this.view = view;
        this.entity = view.display();
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
    /**
     * Длина {@code index}-го отрезка: на старте короткие (1, 1, 2, 2 тика), дальше {@code keyframeTicks}.
     * В начале жизни скорость меняется сильнее всего (разлёт, сопротивление, гравитация), а внутри отрезка
     * клиент ведёт частицу с постоянной скоростью - длинные первые отрезки дают видимые рывки.
     */
    static int segmentLength(int index, int keyframeTicks) {
        int ramp = index < 2 ? 1 : index < 4 ? 2 : keyframeTicks;
        return Math.max(1, Math.min(keyframeTicks, ramp));
    }

    /**
     * Обработать текущий возраст. Клиент показывает частицу с задержкой ровно в один тик: на тике
     * {@code age} отправляется цель для момента {@code age - 1 + L}, куда клиент придёт за L тиков.
     * Без этого сдвига первый кадр (он не может прийти в тик появления) догонял бы лишний тик пути.
     */
    void update(int keyframeTicks, ParticleSim.Solid solid) {
        if (age >= nextPosition) sendPosition(keyframeTicks, solid);
        if (age >= nextTransform) sendTransform(keyframeTicks);
        if (frames != null) updateFrame();
        view.flush();
    }

    /** Кадр текста для видимого возраста (age - 1, как и остальное): смена - один пакет метаданных. */
    private void updateFrame() {
        int next = ParticleSim.frameAt(frameStart, frames.frames().size(), frames.frameInterval(), frames.overLife(), age - 1, life);
        if (next == frame) return;
        frame = next;
        Component text = frames.frames().get(next);
        if (entity instanceof TextDisplay d) {
            if (recolor) {
                baseText = text;
                d.text(text.color(TextColor.color(sentColor & 0xffffff)));
            } else {
                d.text(text);
            }
        }
    }

    /**
     * Позиция: отрезок, запланированный на прошлом кадре (его длина уже задана сущности как
     * {@code teleportDuration}), уходит клиенту, и сразу планируется следующий - его длительность
     * отправляется заранее: метаданные уходят после пакета движения и действуют со следующего телепорта.
     */
    private void sendPosition(int keyframeTicks, ParticleSim.Solid solid) {
        ParticleSim.Segment segment = planned != null ? planned : ParticleSim.planSegment(body, emission.effect.motion(), solid, keyframeTicks);
        boolean wasResting = body.resting;
        double px = body.x, py = body.y, pz = body.z;
        body = segment.end();
        nextPosition = age + segment.length();
        if (!wasResting && (body.x != px || body.y != py || body.z != pz)) {
            view.moveTo(body.x, body.y, body.z, yaw);
        }
        planned = ParticleSim.planSegment(body, emission.effect.motion(), solid, keyframeTicks);
        if (planned.length() != segment.length()) entity.setTeleportDuration(planned.length());
    }

    private void sendTransform(int keyframeTicks) {
        ParticleEffect.Animation animation = emission.effect.animation();
        int visual = age - 1;
        int steps = life - visual;
        if (spin != 0) {
            // поворот за кадр (от последней отправленной трансформации) - меньше 180°,
            // иначе клиент повернёт кратчайшим путём, в обратную сторону
            int maxSpan = Math.max(1, (int) (170 / Math.abs(spin)));
            steps = Math.min(steps, Math.max(1, maxSpan - (visual - sentAge)));
        }
        if (steps <= 0) {
            nextTransform = Integer.MAX_VALUE;
            return;
        }
        // кадр такой длины, чтобы прямая клиента не отходила от кривой размера/цвета (LINEAR - один кадр)
        boolean colored = entity instanceof TextDisplay;
        steps = ParticleSim.planTransform(animation, startScale, startColor, colored, sentScale, sentColor, visual, life, steps);
        transformSegment++;
        int target = visual + steps;
        nextTransform = age + steps;
        double s = ParticleSim.scaleAt(animation, startScale, target, life);
        double r = ParticleSim.rotationAt(startRotation, spin, target);
        boolean transformChanged = s != sentScale || r != sentRotation;
        // цвет фона и прозрачность текста клиент интерполирует вместе с трансформацией
        int c = entity instanceof TextDisplay ? ParticleSim.colorAt(animation, startColor, target, life) : sentColor;
        boolean colorChanged = c != sentColor;
        if (transformChanged || colorChanged) {
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(steps);
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

    void kill() {
        if (dead) return;
        dead = true;
        view.remove();
    }
}

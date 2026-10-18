package padej.displayLib.particles;

import me.padej.jumper.runtime.JTable;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import padej.displayLib.config.HoverAnimation;
import padej.displayLib.config.JmcValues;
import padej.displayLib.ui.ScreenSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Эффект частиц из {@code plugins/DisplayLib/particles/<id>.jmc}. Неизменяемый: один объект
 * на все запуски эффекта.
 *
 * <pre>
 * String id = "confetti";
 * String viewers = "all";        // "all" | "owner"
 * int keyframeTicks = 4;         // 1..59: как часто сервер шлёт кадр (между кадрами интерполирует клиент)
 * dyn render = { type: "TEXT", variants: ["█", "▲"], colors: ["#ff5555", "#55ff55"], billboard: "CENTER", brightness: 15 };
 * dyn emitter = { shape: "SPHERE", radius: 0.2, count: 30, duration: 0, rate: 0 };
 * dyn motion = { lifetime: [80, 140], speed: [0.15, 0.3], direction: [0, 1, 0], spread: 50,
 *                gravity: 0.02, drag: 0.03, collide: true, bounce: 0.3 };
 * dyn animation = { scale: [0.2, 0.35], endScale: 0, easing: "EASE_IN", fadeIn: 3, rotation: [0, 360], spin: [-15, 15] };
 * </pre>
 *
 * <p>Скорость - блоков за тик, гравитация - блоков за тик², углы - градусы, время - тики.</p>
 */
public final class ParticleEffect {
    public enum Viewers { ALL, OWNER }
    /** PIXEL - цветной квадрат (фон пустого TextDisplay): любой цвет и прозрачность, плавная смена цвета. */
    public enum RenderType { TEXT, PIXEL, ITEM, BLOCK }
    public enum Shape { POINT, SPHERE, BOX, CIRCLE }

    /**
     * Что рисует частица.
     *
     * @param colors    цвета ARGB: случайный на частицу (цвет текста у TEXT, цвет квадрата у PIXEL)
     * @param glow      контур свечения (клиент рисует его у ITEM и BLOCK; у текста контура нет)
     * @param glowColor цвет контура RGB или {@code null} - белый (цвет команды)
     * @param texts     варианты текста (TEXT): на частицу случайный; вариант может быть анимацией кадров
     */
    /**
     * Текст частицы (вариант TEXT): один кадр - статичный текст; несколько - анимация, как {@code frames}
     * у текстовых кнопок экранов: кадры сменяются каждые {@code frameInterval} тиков по кругу или (overLife)
     * проходят один раз за жизнь; {@code randomStart} - каждая частица со случайного кадра.
     */
    public record TextVariant(List<Component> frames, int frameInterval, boolean overLife, boolean randomStart) {
        public static TextVariant of(Component text) {
            return new TextVariant(List.of(text), 4, false, false);
        }

        public boolean animated() {
            return frames.size() > 1;
        }

        public Component first() {
            return frames.get(0);
        }
    }

    public record Render(RenderType type, List<TextVariant> texts, List<Integer> colors, List<String> items,
                         List<String> blocks, Display.Billboard billboard, Integer brightness, boolean shadow,
                         int backgroundArgb, boolean glow, Integer glowColor) {}

    /**
     * Откуда и сколько.
     *
     * @param size     полуразмеры коробки (BOX)
     * @param surface  только по поверхности (SPHERE) / контуру (CIRCLE, BOX)
     * @param duration 0 - один всплеск из {@code count} частиц; больше 0 - сколько тиков испускать
     *                 {@code rate} частиц за тик (дробное значение копится); {@link #FOREVER} - пока
     *                 запуск не остановят ({@code stop()}, {@code /displaylib particle stop})
     */
    public record Emitter(Shape shape, double radius, double[] size, boolean surface, int count, int duration, double rate) {
        public static final int FOREVER = -1;
    }

    /**
     * Движение.
     *
     * @param direction ось разлёта (единичный вектор) или {@code null} - от центра формы («OUTWARD»)
     * @param spread    половина угла конуса разлёта вокруг оси, градусы (180 - во все стороны)
     * @param drag      доля скорости, теряемая за тик (0..1)
     * @param bounce    доля скорости после удара о блок (0 - частица останавливается)
     * @param friction  доля скорости вдоль поверхности, остающаяся при ударе
     * @param attract   притяжение к точке рождения эмиттера, блоков/тик² (отрицательное - отталкивание)
     * @param attractAxis притягивать к вертикальной оси через центр (как пламя к фитилю), а не к точке
     * @param wander    случайное блуждание, блоков/тик²: направление толчка плавно поворачивается (светлячки)
     */
    public record Motion(Range lifetime, Range speed, double[] direction, double spread, double gravity, double drag,
                         boolean collide, double bounce, double friction, double attract, boolean attractAxis, double wander) {}

    /**
     * Вид во времени.
     *
     * @param scale    начальный размер (множитель)
     * @param endScale размер к концу жизни, доля начального (0 - исчезает, 1 - не меняется)
     * @param fadeIn   за сколько тиков частица вырастает из 0 до начального размера
     * @param rotation начальный угол в плоскости частицы
     * @param spin     скорость вращения, градусы за тик
     * @param color    цвет по доле жизни (вместо {@code render.colors}) или {@code null}
     * @param opacity  множитель непрозрачности по доле жизни (TEXT, PIXEL)
     */
    public record Animation(Range scale, double endScale, HoverAnimation.EasingType easing, int fadeIn,
                            Range rotation, Range spin, ParticleColors.Gradient color, ParticleColors.Curve opacity) {
        /** Меняется ли цвет или прозрачность во времени. */
        public boolean animatesColor() {
            return color != null && color.colors().length > 1 || !opacity.isConstantOne() && opacity.values().length > 1;
        }
    }

    private final String id;
    private final Viewers viewers;
    private final int keyframeTicks;
    private final Render render;
    private final Emitter emitter;
    private final Motion motion;
    private final Animation animation;

    public ParticleEffect(String id, Viewers viewers, int keyframeTicks, Render render, Emitter emitter, Motion motion, Animation animation) {
        this.id = id;
        this.viewers = viewers;
        this.keyframeTicks = Math.max(1, Math.min(59, keyframeTicks));
        this.render = render;
        this.emitter = emitter;
        this.motion = motion;
        this.animation = animation;
    }

    public String id() { return id; }
    public Viewers viewers() { return viewers; }
    public int keyframeTicks() { return keyframeTicks; }
    public Render render() { return render; }
    public Emitter emitter() { return emitter; }
    public Motion motion() { return motion; }
    public Animation animation() { return animation; }

    /** Копия с другим числом частиц во всплеске (команда, API). */
    public ParticleEffect withCount(int count) {
        Emitter e = emitter;
        return new ParticleEffect(id, viewers, keyframeTicks, render,
                new Emitter(e.shape(), e.radius(), e.size(), e.surface(), Math.max(0, count), e.duration(), e.rate()), motion, animation);
    }

    /** Копия с другой видимостью. */
    public ParticleEffect withViewers(Viewers viewers) {
        return new ParticleEffect(id, viewers, keyframeTicks, render, emitter, motion, animation);
    }

    // -------------------------------------------------------------------------
    // Разбор
    // -------------------------------------------------------------------------

    /**
     * Разобрать эффект из переменных верхнего уровня файла {@code .jmc}.
     *
     * @param fallbackId id, если в файле нет {@code String id} (имя файла)
     * @throws IllegalArgumentException с понятным описанием ошибки
     */
    public static ParticleEffect parse(JTable top, String fallbackId) {
        String id = JmcValues.str(top, "id");
        if (id == null || id.isBlank()) id = fallbackId;

        Viewers viewers = enumOf(Viewers.class, JmcValues.str(top, "viewers"), Viewers.ALL, "viewers");
        Integer keyframe = JmcValues.integer(top, "keyframeTicks", "keyframe_ticks");

        Render render = parseRender(JmcValues.table(top, "render"));
        Emitter emitter = parseEmitter(JmcValues.table(top, "emitter"));
        Motion motion = parseMotion(JmcValues.table(top, "motion"));
        Animation animation = parseAnimation(JmcValues.table(top, "animation"));
        return new ParticleEffect(id, viewers, keyframe != null ? keyframe : 4, render, emitter, motion, animation);
    }

    private static Render parseRender(JTable t) {
        if (t == null) throw new IllegalArgumentException("render: required, e.g. dyn render = { type: \"TEXT\", text: \"★\" };");
        RenderType type = enumOf(RenderType.class, JmcValues.str(t, "type"), RenderType.TEXT, "render.type");

        List<TextVariant> texts = new ArrayList<>();
        List<String> items = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        switch (type) {
            case TEXT -> {
                Object single = JmcValues.get(t, "text");
                if (single != null) texts.add(textVariant(single, "render.text"));
                List<?> variants = JmcValues.list(t, "variants");
                if (variants != null) for (Object v : variants) texts.add(textVariant(v, "render.variants"));
                // краткая запись одного анимированного варианта: frames рядом с type
                if (JmcValues.list(t, "frames") != null) texts.add(textVariant(t, "render"));
                if (texts.isEmpty()) throw new IllegalArgumentException("render: TEXT needs text: \"★\" or variants: [\"★\", { frames: [\"◐\", \"◓\"], frameInterval: 3 }]");
            }
            case PIXEL -> { /* только цвет: colors или animation.color */ }
            case ITEM -> {
                // имена проверяются на сервере: ParticleRegistry при загрузке, ParticleManager при запуске
                items.addAll(strings(t, "item", "items"));
                if (items.isEmpty()) throw new IllegalArgumentException("render: ITEM needs item: \"GOLD_NUGGET\" or items: [...]");
            }
            case BLOCK -> {
                // BlockData проверяется при первом запуске (нужен сервер): "minecraft:glowstone", "oak_log[axis=x]"
                blocks.addAll(strings(t, "block", "blocks"));
                if (blocks.isEmpty()) throw new IllegalArgumentException("render: BLOCK needs block: \"glowstone\" or blocks: [...]");
            }
        }

        List<Integer> colors = new ArrayList<>();
        List<?> colorList = JmcValues.list(t, "colors");
        Object single = JmcValues.get(t, "color");
        if (single != null) colors.add(ParticleColors.parse(single, "render.color"));
        if (colorList != null) {
            // [r, g, b(, a)] - один цвет; ["#..", [r, g, b]] - список
            if ((colorList.size() == 3 || colorList.size() == 4) && colorList.stream().allMatch(Number.class::isInstance)) {
                colors.add(ParticleColors.parse(colorList, "render.colors"));
            } else {
                for (Object c : colorList) colors.add(ParticleColors.parse(c, "render.colors"));
            }
        }
        if (type == RenderType.PIXEL && colors.isEmpty()) colors.add(0xffffffff);

        Display.Billboard billboard = enumOf(Display.Billboard.class, JmcValues.str(t, "billboard"),
                type == RenderType.BLOCK ? Display.Billboard.FIXED : Display.Billboard.CENTER, "render.billboard");
        Integer brightness = JmcValues.integer(t, "brightness");
        if (brightness != null) brightness = Math.max(0, Math.min(15, brightness));
        Boolean shadow = JmcValues.bool(t, "shadow");
        int[] bg = JmcValues.ints(t, 3, "background", "backgroundColor");
        Integer bgAlpha = JmcValues.integer(t, "backgroundAlpha", "bgAlpha");
        int alpha = bgAlpha != null ? Math.max(0, Math.min(255, bgAlpha)) : (bg != null ? 255 : 0);
        int argb = bg == null ? 0 : (alpha << 24) | (clamp255(bg[0]) << 16) | (clamp255(bg[1]) << 8) | clamp255(bg[2]);
        Boolean glow = JmcValues.bool(t, "glow");
        Object glowColorValue = JmcValues.get(t, "glowColor");
        Integer glowColor = glowColorValue == null ? null : ParticleColors.parse(glowColorValue, "render.glowColor") & 0xffffff;
        return new Render(type, List.copyOf(texts), List.copyOf(colors), List.copyOf(items), List.copyOf(blocks),
                billboard, brightness, shadow != null && shadow, argb, glow != null && glow, glowColor);
    }

    /**
     * Вариант текста: строка/сегменты - статичный; таблица {@code { frames: [...], frameInterval, framesOverLife,
     * frameStart }} - анимация кадрами (как frames у кнопок экранов, text = первый кадр).
     */
    private static TextVariant textVariant(Object v, String field) {
        if (v instanceof JTable table && JmcValues.get(table, "frames") != null) {
            List<?> list = JmcValues.list(table, "frames");
            if (list == null || list.isEmpty()) throw new IllegalArgumentException(field + ".frames: expected a list of texts");
            List<Component> frames = new ArrayList<>();
            for (Object f : list) frames.add(ScreenSupport.parseFormattedText(JmcValues.toJava(f)));
            Integer interval = JmcValues.integer(table, "frameInterval");
            Boolean overLife = JmcValues.bool(table, "framesOverLife");
            String start = JmcValues.str(table, "frameStart");
            if (start != null && !start.equalsIgnoreCase("first") && !start.equalsIgnoreCase("random")) {
                throw new IllegalArgumentException(field + ".frameStart: expected \"first\" or \"random\", got '" + start + "'");
            }
            return new TextVariant(List.copyOf(frames), interval != null ? Math.max(1, interval) : 4, overLife != null && overLife, "random".equalsIgnoreCase(start));
        }
        return TextVariant.of(ScreenSupport.parseFormattedText(JmcValues.toJava(v)));
    }

    private static Emitter parseEmitter(JTable t) {
        if (t == null) return new Emitter(Shape.POINT, 0, new double[]{0, 0, 0}, false, 1, 0, 0);
        Shape shape = enumOf(Shape.class, JmcValues.str(t, "shape"), Shape.POINT, "emitter.shape");
        Double radius = JmcValues.number(t, "radius");
        float[] size = JmcValues.floats(t, 3, "size");
        Boolean surface = JmcValues.bool(t, "surface");
        Integer count = JmcValues.integer(t, "count");
        Integer duration = JmcValues.integer(t, "duration");
        Double rate = JmcValues.number(t, "rate");
        int d = duration == null ? 0 : duration < 0 ? Emitter.FOREVER : duration;
        if (d != 0 && (rate == null || rate <= 0)) throw new IllegalArgumentException("emitter: duration needs rate > 0 (particles per tick)");
        return new Emitter(shape, radius != null ? Math.abs(radius) : 0.25,
                size != null ? new double[]{Math.abs(size[0]) / 2, Math.abs(size[1]) / 2, Math.abs(size[2]) / 2} : new double[]{0.25, 0.25, 0.25},
                surface != null && surface, count != null ? Math.max(0, count) : (d != 0 ? 0 : 1), d, rate != null ? rate : 0);
    }

    private static Motion parseMotion(JTable t) {
        Range lifetime = Range.parse(JmcValues.get(t, "lifetime", "life"), new Range(20, 30), "motion.lifetime");
        if (lifetime.min() < 1) throw new IllegalArgumentException("motion.lifetime: at least 1 tick");
        Range speed = Range.parse(JmcValues.get(t, "speed"), Range.ZERO, "motion.speed");
        Object dir = JmcValues.get(t, "direction");
        double[] direction;
        if (dir instanceof String s && s.equalsIgnoreCase("OUTWARD")) {
            direction = null;
        } else if (dir == null) {
            direction = new double[]{0, 1, 0};
        } else {
            float[] v = JmcValues.floats(t, 3, "direction");
            if (v == null) throw new IllegalArgumentException("motion.direction: expected [x, y, z] or \"OUTWARD\"");
            double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            direction = len < 1e-9 ? new double[]{0, 1, 0} : new double[]{v[0] / len, v[1] / len, v[2] / len};
        }
        return new Motion(lifetime, speed, direction,
                clamp(num(t, "spread", 0), 0, 180),
                num(t, "gravity", 0),
                clamp(num(t, "drag", 0), 0, 1),
                bool(t, "collide", false),
                clamp(num(t, "bounce", 0), 0, 1),
                clamp(num(t, "friction", 0.6), 0, 1),
                num(t, "attract", 0),
                "Y".equalsIgnoreCase(t != null ? JmcValues.str(t, "attractAxis") : null) || bool(t, "attractAxis", false),
                Math.max(0, num(t, "wander", 0)));
    }

    private static Animation parseAnimation(JTable t) {
        Range scale = Range.parse(JmcValues.get(t, "scale"), Range.of(0.3), "animation.scale");
        HoverAnimation.EasingType easing = enumOf(HoverAnimation.EasingType.class, JmcValues.str(t, "easing"),
                HoverAnimation.EasingType.LINEAR, "animation.easing");
        return new Animation(scale, Math.max(0, num(t, "endScale", 1)), easing,
                (int) Math.max(0, num(t, "fadeIn", 0)),
                Range.parse(JmcValues.get(t, "rotation"), Range.ZERO, "animation.rotation"),
                Range.parse(JmcValues.get(t, "spin"), Range.ZERO, "animation.spin"),
                ParticleColors.Gradient.parse(t != null ? JmcValues.get(t, "color", "colors") : null, "animation.color"),
                ParticleColors.Curve.parse(t != null ? JmcValues.get(t, "opacity") : null, "animation.opacity"));
    }

    /** Предмет по имени ("GOLD_NUGGET", "minecraft:emerald") или {@code null}, если такого нет. Нужен сервер. */
    public static Material item(String name) {
        Material m = Material.matchMaterial(name);
        return m != null && m.isItem() && !m.isAir() ? m : null;
    }

    // -------------------------------------------------------------------------

    private static double num(JTable t, String key, double fallback) {
        Double v = t != null ? JmcValues.number(t, key) : null;
        return v != null ? v : fallback;
    }

    private static boolean bool(JTable t, String key, boolean fallback) {
        Boolean v = t != null ? JmcValues.bool(t, key) : null;
        return v != null ? v : fallback;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static List<String> strings(JTable t, String one, String many) {
        List<String> out = new ArrayList<>();
        String single = JmcValues.str(t, one);
        if (single != null) out.add(single);
        List<?> list = JmcValues.list(t, many);
        if (list != null) for (Object o : list) if (o != null) out.add(o.toString());
        return out;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, E fallback, String field) {
        if (name == null) return fallback;
        E value = JmcValues.enumValue(type, name);
        if (value == null) {
            List<String> names = new ArrayList<>();
            for (E e : type.getEnumConstants()) names.add(e.name());
            throw new IllegalArgumentException(field + ": unknown value '" + name + "', expected one of " + names);
        }
        return value;
    }

    @Override
    public String toString() {
        return "ParticleEffect[" + id + ", " + render.type().name().toLowerCase(Locale.ROOT) + "]";
    }
}

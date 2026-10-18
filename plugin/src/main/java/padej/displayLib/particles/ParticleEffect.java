package padej.displayLib.particles;

import me.padej.jumper.runtime.JTable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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
    public enum RenderType { TEXT, ITEM, BLOCK }
    public enum Shape { POINT, SPHERE, BOX, CIRCLE }

    /** Что рисует частица. */
    public record Render(RenderType type, List<Component> texts, List<TextColor> colors, List<String> items,
                         List<String> blocks, Display.Billboard billboard, Integer brightness, boolean shadow,
                         int backgroundArgb, boolean glow) {}

    /**
     * Откуда и сколько.
     *
     * @param size     полуразмеры коробки (BOX)
     * @param surface  только по поверхности (SPHERE) / контуру (CIRCLE, BOX)
     * @param duration 0 - один всплеск из {@code count} частиц; больше 0 - сколько тиков испускать
     *                 {@code rate} частиц за тик (дробное значение копится)
     */
    public record Emitter(Shape shape, double radius, double[] size, boolean surface, int count, int duration, double rate) {}

    /**
     * Движение.
     *
     * @param direction ось разлёта (единичный вектор) или {@code null} - от центра формы («OUTWARD»)
     * @param spread    половина угла конуса разлёта вокруг оси, градусы (180 - во все стороны)
     * @param drag      доля скорости, теряемая за тик (0..1)
     * @param bounce    доля скорости после удара о блок (0 - частица останавливается)
     * @param friction  доля скорости вдоль поверхности, остающаяся при ударе
     */
    public record Motion(Range lifetime, Range speed, double[] direction, double spread, double gravity, double drag,
                         boolean collide, double bounce, double friction) {}

    /**
     * Вид во времени.
     *
     * @param scale    начальный размер (множитель)
     * @param endScale размер к концу жизни, доля начального (0 - исчезает, 1 - не меняется)
     * @param fadeIn   за сколько тиков частица вырастает из 0 до начального размера
     * @param rotation начальный угол в плоскости частицы
     * @param spin     скорость вращения, градусы за тик
     */
    public record Animation(Range scale, double endScale, HoverAnimation.EasingType easing, int fadeIn,
                            Range rotation, Range spin) {}

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

        List<Component> texts = new ArrayList<>();
        List<String> items = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        switch (type) {
            case TEXT -> {
                Object single = JmcValues.get(t, "text");
                if (single != null) texts.add(ScreenSupport.parseFormattedText(JmcValues.toJava(single)));
                List<?> variants = JmcValues.list(t, "variants");
                if (variants != null) for (Object v : variants) texts.add(ScreenSupport.parseFormattedText(JmcValues.toJava(v)));
                if (texts.isEmpty()) throw new IllegalArgumentException("render: TEXT needs text: \"★\" or variants: [\"★\", \"✦\"]");
            }
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

        List<TextColor> colors = new ArrayList<>();
        List<?> colorList = JmcValues.list(t, "colors");
        Object single = JmcValues.get(t, "color");
        if (single != null) colors.add(color(single));
        if (colorList != null) {
            // [r, g, b] - один цвет; ["#..", [r, g, b]] - список
            if (colorList.size() == 3 && colorList.stream().allMatch(Number.class::isInstance)) colors.add(color(colorList));
            else for (Object c : colorList) colors.add(color(c));
        }

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
        return new Render(type, List.copyOf(texts), List.copyOf(colors), List.copyOf(items), List.copyOf(blocks),
                billboard, brightness, shadow != null && shadow, argb, glow != null && glow);
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
        int d = duration != null ? Math.max(0, duration) : 0;
        if (d > 0 && (rate == null || rate <= 0)) throw new IllegalArgumentException("emitter: duration > 0 needs rate > 0 (particles per tick)");
        return new Emitter(shape, radius != null ? Math.abs(radius) : 0.25,
                size != null ? new double[]{Math.abs(size[0]) / 2, Math.abs(size[1]) / 2, Math.abs(size[2]) / 2} : new double[]{0.25, 0.25, 0.25},
                surface != null && surface, count != null ? Math.max(0, count) : (d > 0 ? 0 : 1), d, rate != null ? rate : 0);
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
                clamp(num(t, "friction", 0.6), 0, 1));
    }

    private static Animation parseAnimation(JTable t) {
        Range scale = Range.parse(JmcValues.get(t, "scale"), Range.of(0.3), "animation.scale");
        HoverAnimation.EasingType easing = enumOf(HoverAnimation.EasingType.class, JmcValues.str(t, "easing"),
                HoverAnimation.EasingType.LINEAR, "animation.easing");
        return new Animation(scale, Math.max(0, num(t, "endScale", 1)), easing,
                (int) Math.max(0, num(t, "fadeIn", 0)),
                Range.parse(JmcValues.get(t, "rotation"), Range.ZERO, "animation.rotation"),
                Range.parse(JmcValues.get(t, "spin"), Range.ZERO, "animation.spin"));
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

    private static TextColor color(Object v) {
        if (v instanceof List<?> rgb && rgb.size() >= 3 && rgb.get(0) instanceof Number r
                && rgb.get(1) instanceof Number g && rgb.get(2) instanceof Number b) {
            return TextColor.color(clamp255(r.intValue()), clamp255(g.intValue()), clamp255(b.intValue()));
        }
        TextColor c = v != null ? ScreenSupport.parseColorPublic(v.toString()) : null;
        if (c == null) throw new IllegalArgumentException("render.colors: unknown color " + v + " (\"#rrggbb\", name or [r, g, b])");
        return c;
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

package padej.displayLib.particles;

import net.kyori.adventure.text.format.TextColor;
import padej.displayLib.ui.ScreenSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * Цвета частиц: разбор с прозрачностью, смешивание в OkLab, кривые цвета и непрозрачности во времени.
 * Цвет - ARGB в {@code int}.
 */
public final class ParticleColors {
    private ParticleColors() {}

    /**
     * Цвет из значения Jumper: {@code "#rrggbb"}, {@code "#rrggbbaa"}, имя ({@code "red"}),
     * {@code [r, g, b]} или {@code [r, g, b, a]} (0..255).
     *
     * @throws IllegalArgumentException с понятным описанием
     */
    public static int parse(Object v, String field) {
        if (v instanceof List<?> l && (l.size() == 3 || l.size() == 4) && l.stream().allMatch(Number.class::isInstance)) {
            int r = c255(l.get(0)), g = c255(l.get(1)), b = c255(l.get(2));
            int a = l.size() == 4 ? c255(l.get(3)) : 255;
            return argb(a, r, g, b);
        }
        if (v instanceof String s) {
            String hex = s.startsWith("#") ? s.substring(1) : null;
            if (hex != null && hex.length() == 8 && hex.matches("[0-9a-fA-F]{8}")) {
                long x = Long.parseLong(hex, 16);
                return argb((int) (x & 0xff), (int) (x >> 24 & 0xff), (int) (x >> 16 & 0xff), (int) (x >> 8 & 0xff));
            }
            TextColor c = ScreenSupport.parseColorPublic(s);
            if (c != null) return 0xff000000 | c.value();
        }
        throw new IllegalArgumentException(field + ": unknown color " + v + " (\"#rrggbb\", \"#rrggbbaa\", name, [r, g, b] or [r, g, b, a])");
    }

    private static int c255(Object n) {
        return Math.max(0, Math.min(255, ((Number) n).intValue()));
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xff) << 24 | (r & 0xff) << 16 | (g & 0xff) << 8 | (b & 0xff);
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    /** Тот же цвет с другой непрозрачностью (множитель 0..1). */
    public static int withOpacity(int argb, double opacity) {
        int a = (int) Math.round(alpha(argb) * Math.max(0, Math.min(1, opacity)));
        return (argb & 0x00ffffff) | a << 24;
    }

    // -------------------------------------------------------------------------
    // OkLab (Björn Ottosson): смешивание без грязно-серой середины
    // -------------------------------------------------------------------------

    /** Смесь двух цветов: RGB - в OkLab, прозрачность - линейно. */
    public static int lerp(int from, int to, double t) {
        if (t <= 0) return from;
        if (t >= 1) return to;
        double[] a = toOklab(from), b = toOklab(to);
        double l = a[0] + (b[0] - a[0]) * t, m = a[1] + (b[1] - a[1]) * t, s = a[2] + (b[2] - a[2]) * t;
        int rgb = fromOklab(l, m, s);
        int alpha = (int) Math.round(alpha(from) + (alpha(to) - alpha(from)) * t);
        return (rgb & 0x00ffffff) | alpha << 24;
    }

    static double[] toOklab(int argb) {
        double r = linear((argb >> 16 & 0xff) / 255.0), g = linear((argb >> 8 & 0xff) / 255.0), b = linear((argb & 0xff) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    static int fromOklab(double L, double A, double B) {
        double l = L + 0.3963377774 * A + 0.2158037573 * B;
        double m = L - 0.1055613458 * A - 0.0638541728 * B;
        double s = L - 0.0894841775 * A - 1.2914855480 * B;
        l = l * l * l; m = m * m * m; s = s * s * s;
        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
        return argb(255, srgb(r), srgb(g), srgb(b));
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int srgb(double c) {
        c = Math.max(0, Math.min(1, c));
        double v = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    // -------------------------------------------------------------------------
    // Кривые во времени (доля жизни 0..1)
    // -------------------------------------------------------------------------

    /**
     * Цвет по доле жизни: {@code ["#fff", "#f80", "#0000"]} - равномерно, или
     * {@code [[0, "#fff"], [0.2, "#fcd303"], [1, "#00000000"]]} - с явными долями.
     */
    public record Gradient(double[] at, int[] colors) {
        public int sample(double t) {
            if (t <= at[0]) return colors[0];
            for (int i = 1; i < at.length; i++) {
                if (t <= at[i]) {
                    double span = at[i] - at[i - 1];
                    return lerp(colors[i - 1], colors[i], span <= 0 ? 1 : (t - at[i - 1]) / span);
                }
            }
            return colors[colors.length - 1];
        }

        public static Gradient parse(Object v, String field) {
            if (v == null) return null;
            if (!(v instanceof List<?> list) || list.isEmpty()) {
                return new Gradient(new double[]{0}, new int[]{ParticleColors.parse(v, field)});
            }
            // один цвет как [r, g, b(, a)]
            if (list.stream().allMatch(Number.class::isInstance)) {
                return new Gradient(new double[]{0}, new int[]{ParticleColors.parse(v, field)});
            }
            List<double[]> stops = stops(list, field);
            boolean explicit = stops != null;
            int n = list.size();
            double[] at = new double[n];
            int[] colors = new int[n];
            for (int i = 0; i < n; i++) {
                Object item = list.get(i);
                if (explicit) {
                    List<?> pair = (List<?>) item;
                    at[i] = ((Number) pair.get(0)).doubleValue();
                    colors[i] = ParticleColors.parse(pair.get(1), field);
                } else {
                    at[i] = n == 1 ? 0 : (double) i / (n - 1);
                    colors[i] = ParticleColors.parse(item, field);
                }
            }
            checkOrder(at, field);
            return new Gradient(at, colors);
        }
    }

    /**
     * Число по доле жизни: {@code 0.5} - постоянное, {@code [1, 0]} - от и до,
     * {@code [[0, 0], [0.2, 1], [0.8, 1], [1, 0]]} - с явными долями.
     */
    public record Curve(double[] at, double[] values) {
        public static final Curve ONE = new Curve(new double[]{0}, new double[]{1});

        public double sample(double t) {
            if (t <= at[0]) return values[0];
            for (int i = 1; i < at.length; i++) {
                if (t <= at[i]) {
                    double span = at[i] - at[i - 1];
                    double k = span <= 0 ? 1 : (t - at[i - 1]) / span;
                    return values[i - 1] + (values[i] - values[i - 1]) * k;
                }
            }
            return values[values.length - 1];
        }

        public boolean isConstantOne() {
            for (double v : values) if (v != 1) return false;
            return true;
        }

        public static Curve parse(Object v, String field) {
            if (v == null) return ONE;
            if (v instanceof Number n) return new Curve(new double[]{0}, new double[]{n.doubleValue()});
            if (v instanceof List<?> list && !list.isEmpty()) {
                if (list.stream().allMatch(Number.class::isInstance)) {
                    int n = list.size();
                    double[] at = new double[n], values = new double[n];
                    for (int i = 0; i < n; i++) {
                        at[i] = n == 1 ? 0 : (double) i / (n - 1);
                        values[i] = ((Number) list.get(i)).doubleValue();
                    }
                    return new Curve(at, values);
                }
                List<double[]> stops = stops(list, field);
                if (stops != null) {
                    double[] at = new double[stops.size()], values = new double[stops.size()];
                    for (int i = 0; i < stops.size(); i++) {
                        List<?> pair = (List<?>) list.get(i);
                        if (!(pair.get(1) instanceof Number n)) throw new IllegalArgumentException(field + ": expected [time, number] pairs");
                        at[i] = stops.get(i)[0];
                        values[i] = n.doubleValue();
                    }
                    checkOrder(at, field);
                    return new Curve(at, values);
                }
            }
            throw new IllegalArgumentException(field + ": expected a number, [from, to] or [[time, value], ...]");
        }
    }

    /** Пары [доля, значение]: список долей, если все элементы - такие пары, иначе null. */
    private static List<double[]> stops(List<?> list, String field) {
        List<double[]> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof List<?> pair) || pair.size() != 2 || !(pair.get(0) instanceof Number t)) return null;
            out.add(new double[]{t.doubleValue()});
        }
        return out;
    }

    private static void checkOrder(double[] at, String field) {
        for (int i = 0; i < at.length; i++) {
            if (at[i] < 0 || at[i] > 1) throw new IllegalArgumentException(field + ": times must be within 0..1, got " + at[i]);
            if (i > 0 && at[i] < at[i - 1]) throw new IllegalArgumentException(field + ": times must go in increasing order");
        }
    }
}

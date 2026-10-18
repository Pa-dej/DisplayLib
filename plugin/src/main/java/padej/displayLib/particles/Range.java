package padej.displayLib.particles;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Число или случайное значение из диапазона: в файле эффекта {@code 0.2} или {@code [0.1, 0.3]}.
 */
public record Range(double min, double max) {
    public static final Range ZERO = new Range(0, 0);

    public static Range of(double value) {
        return new Range(value, value);
    }

    /** Случайное значение в [min, max] (равномерно). */
    public double sample(RandomGenerator random) {
        return min == max ? min : min + (max - min) * random.nextDouble();
    }

    /**
     * Из значения Jumper: число → точное, массив из двух чисел → диапазон (порядок не важен),
     * {@code null} → {@code fallback}.
     *
     * @throws IllegalArgumentException если значение не число и не пара чисел
     */
    public static Range parse(Object value, Range fallback, String name) {
        if (value == null) return fallback;
        if (value instanceof Number n) return of(n.doubleValue());
        if (value instanceof List<?> list && list.size() == 2
                && list.get(0) instanceof Number a && list.get(1) instanceof Number b) {
            double x = a.doubleValue(), y = b.doubleValue();
            return new Range(Math.min(x, y), Math.max(x, y));
        }
        throw new IllegalArgumentException(name + ": expected a number or [min, max], got " + value);
    }
}

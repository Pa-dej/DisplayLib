package padej.displayLib.config;

import me.padej.jumper.runtime.JArray;
import me.padej.jumper.runtime.JTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Чтение значений из таблицы конфига Jumper ({@code .jmc}, {@link JTable}/{@link JArray}).
 *
 * <p>Каждый метод принимает несколько имён ключа: первое - основное (camelCase, как
 * в примерах), остальные - принятые синонимы (например, snake_case из прежних YAML-файлов),
 * чтобы экран переносился заменой расширения и минимальной правкой.</p>
 */
public final class JmcValues {
    private JmcValues() {
    }

    /** Значение по первому найденному ключу или {@code null}. */
    static Object get(JTable table, String... keys) {
        if (table == null) return null;
        for (String key : keys) {
            Object value = table.get(key);
            if (value != null) return value;
        }
        return null;
    }

    static boolean has(JTable table, String... keys) {
        return get(table, keys) != null;
    }

    static String str(JTable table, String... keys) {
        Object value = get(table, keys);
        return value != null ? String.valueOf(value) : null;
    }

    static String str(JTable table, String defaultValue, String[] keys) {
        String value = str(table, keys);
        return value != null ? value : defaultValue;
    }

    static Integer integer(JTable table, String... keys) {
        Object value = get(table, keys);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return (int) Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Double number(JTable table, String... keys) {
        Object value = get(table, keys);
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Boolean bool(JTable table, String... keys) {
        Object value = get(table, keys);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s.trim());
        return null;
    }

    static JTable table(JTable table, String... keys) {
        Object value = get(table, keys);
        return value instanceof JTable t ? t : null;
    }

    static List<?> list(JTable table, String... keys) {
        Object value = get(table, keys);
        return value instanceof List<?> l ? l : null;
    }

    /** Массив чисел фиксированной длины; недостающие элементы остаются нулями. */
    static float[] floats(JTable table, int length, String... keys) {
        List<?> list = list(table, keys);
        if (list == null) return null;
        float[] result = new float[length];
        for (int i = 0; i < Math.min(length, list.size()); i++) {
            if (list.get(i) instanceof Number n) result[i] = n.floatValue();
        }
        return result;
    }

    static int[] ints(JTable table, int length, String... keys) {
        List<?> list = list(table, keys);
        if (list == null) return null;
        int[] result = new int[length];
        for (int i = 0; i < Math.min(length, list.size()); i++) {
            if (list.get(i) instanceof Number n) result[i] = n.intValue();
        }
        return result;
    }

    /** Константа перечисления по имени без учёта регистра; {@code null}, если нет такой. */
    static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Значение Jumper в обычные объекты Java: таблица → {@link LinkedHashMap},
     * массив → {@link ArrayList}, остальное как есть. Для полей, которые дальше
     * разбираются общим кодом (сегменты форматированного текста, подсказки).
     */
    public static Object toJava(Object value) {
        if (value instanceof JTable t) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Object key : t.keys()) {
                map.put(String.valueOf(key), toJava(t.get(key)));
            }
            return map;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(toJava(item));
            }
            return result;
        }
        return value;
    }
}

package padej.displayLib.data;

import me.padej.jumper.runtime.JArray;
import me.padej.jumper.runtime.JTable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Запись значений Jumper литералами {@code .jmc}, чтобы файл данных читался обратно штатным
 * {@code Config.load} с теми же типами, с какими его записал скрипт: {@code int} → {@link Integer},
 * {@code long} → {@link Long} (суффикс {@code L}), {@code double} → {@link Double},
 * {@code String}, {@code boolean}, {@code null}, массивы ({@link JArray}) и таблицы ({@link JTable}).
 *
 * <p>Не записываются: экземпляры классов скрипта, функции, Java-объекты (игрок, виджет и т.п.),
 * {@code NaN} и бесконечности. Для них {@link #literal(Object)} возвращает {@code null}.</p>
 */
public final class JmcWriter {
    private JmcWriter() {}

    /** Литерал Jumper для значения или {@code null}, если значение записать нельзя. */
    public static String literal(Object value) {
        StringBuilder sb = new StringBuilder();
        return append(sb, value, 0) ? sb.toString() : null;
    }

    /**
     * Таблица {@code { "key": value, ... }} с переносами строк; записи, которые нельзя записать,
     * пропускаются, их ключи добавляются в {@code skipped} (если не null).
     */
    public static String table(Map<?, ?> map, List<String> skipped) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        for (Map.Entry<?, ?> e : map.entrySet()) {
            StringBuilder entry = new StringBuilder("    ");
            quote(entry, String.valueOf(e.getKey()));
            entry.append(": ");
            if (append(entry, e.getValue(), 1)) {
                sb.append(entry).append(",\n");
            } else if (skipped != null) {
                skipped.add(String.valueOf(e.getKey()));
            }
        }
        return sb.append('}').toString();
    }

    private static boolean append(StringBuilder sb, Object v, int depth) {
        if (depth > 64) return false; // защита от циклических ссылок
        if (v == null) { sb.append("null"); return true; }
        if (v instanceof Boolean b) { sb.append(b); return true; }
        if (v instanceof Integer || v instanceof Short || v instanceof Byte) {
            int i = ((Number) v).intValue();
            // -2147483648 как литерал не читается (2147483648 не помещается в int)
            sb.append(i == Integer.MIN_VALUE ? "(-2147483647 - 1)" : Integer.toString(i));
            return true;
        }
        if (v instanceof Long l) {
            sb.append(l == Long.MIN_VALUE ? "(-9223372036854775807L - 1L)" : l + "L");
            return true;
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return false;
            sb.append(Double.toString(d)); // всегда с точкой или экспонентой: читается как double
            return true;
        }
        if (v instanceof CharSequence || v instanceof Character) { quote(sb, v.toString()); return true; }
        if (v instanceof JArray || v instanceof List<?>) {
            List<?> list = (List<?>) v;
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                if (!append(sb, list.get(i), depth + 1)) return false;
            }
            sb.append(']');
            return true;
        }
        if (v instanceof JTable t) {
            if (t.cls() != null) return false; // экземпляр класса скрипта - не данные
            return appendMap(sb, t.asMap(), depth);
        }
        if (v instanceof Map<?, ?> m) return appendMap(sb, m, depth);
        return false;
    }

    private static boolean appendMap(StringBuilder sb, Map<?, ?> map, int depth) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            sb.append(first ? " " : ", ");
            first = false;
            quote(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            if (!append(sb, e.getValue(), depth + 1)) return false;
        }
        sb.append(first ? "}" : " }");
        return true;
    }

    static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** Записать файл целиком через временный файл (не оставляет полузаписанный файл при сбое). */
    public static void writeAtomically(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        // своё имя временного файла у каждой записи: две записи одного файла не смешиваются
        Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

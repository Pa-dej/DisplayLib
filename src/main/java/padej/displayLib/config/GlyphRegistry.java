package padej.displayLib.config;

import me.padej.jumper.interp.Config;
import me.padej.jumper.runtime.JTable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Глифы - прямоугольные картинки произвольного размера в тексте (кнопки, рамки, полоски).
 *
 * <p>Спрайт-объект клиента ({@code {atlas, sprite}}) всегда рисуется квадратом 8×8 px, поэтому
 * широкая текстура в нём сплющивается. Глиф - это символ шрифта {@code displaylib:icons} из
 * ресурспака DisplayLib-icons.zip: bitmap-провайдер с нужной картинкой, высотой и ascent,
 * так что ширина сохраняется пропорционально. Список глифов - {@code plugins/DisplayLib/glyphs.jmc}
 * (его пишет веб-редактор вместе с ресурспаком):</p>
 *
 * <pre>
 * dyn glyphs = {
 *     "widget/button": { height: 20, ascent: 12, width: 201 },   // width - advance в px при height
 * };
 * </pre>
 *
 * <p>Символы назначаются по порядку: U+E000, U+E001, … - в том же порядке редактор строит шрифт.
 * В тексте экрана: {@code { glyph: "widget/button" }}.</p>
 */
public final class GlyphRegistry {
    public static final String FILE = "glyphs.jmc";
    public static final Key FONT = Key.key("displaylib", "icons");
    public static final int FIRST_CHAR = 0xE000;

    public record Glyph(String name, int codePoint, int width, int height, int ascent) {
        public String text() { return new String(Character.toChars(codePoint)); }
    }

    private static volatile Map<String, Glyph> glyphs = Map.of();

    private GlyphRegistry() {}

    public static Glyph get(String name) {
        if (name == null) return null;
        String n = name.trim();
        if (n.startsWith("minecraft:")) n = n.substring("minecraft:".length());
        if (n.endsWith(".png")) n = n.substring(0, n.length() - 4);
        return glyphs.get(n);
    }

    public static Map<String, Glyph> all() { return glyphs; }

    /** Компонент глифа: символ шрифта displaylib:icons; неизвестный глиф - подпись [name]. */
    public static Component component(String name) {
        Glyph g = get(name);
        if (g == null) return Component.text("[" + name + "]");
        return Component.text(g.text()).font(FONT).color(NamedTextColor.WHITE);
    }

    /** Прочитать glyphs.jmc из папки плагина (отсутствие файла - пустой реестр). */
    public static void load(JavaPlugin plugin) {
        Path file = plugin.getDataFolder().toPath().resolve(FILE);
        Map<String, Glyph> map = new LinkedHashMap<>();
        if (Files.exists(file)) {
            try {
                Object data = Config.load(file);
                JTable root = data instanceof JTable t ? t : null;
                Object list = root != null ? root.get("glyphs") : null;
                if (list instanceof JTable table) {
                    int cp = FIRST_CHAR;
                    for (Object key : table.keys()) {
                        String name = String.valueOf(key);
                        Object v = table.get(key);
                        int height = 8, ascent = 6, width = 9;
                        if (v instanceof JTable spec) {
                            Double h = JmcValues.number(spec, "height"), a = JmcValues.number(spec, "ascent"), w = JmcValues.number(spec, "width");
                            if (h != null) height = h.intValue();
                            ascent = a != null ? a.intValue() : height / 2 + 2;
                            if (w != null) width = w.intValue();
                        }
                        map.put(name, new Glyph(name, cp++, width, height, ascent));
                    }
                } else if (root != null) {
                    plugin.getLogger().warning(FILE + ": expected 'glyphs' table");
                }
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, FILE + " could not be read: " + e.getMessage());
            }
        }
        glyphs = Map.copyOf(map);
        if (!map.isEmpty()) plugin.getLogger().info("Loaded " + map.size() + " glyph(s) from " + FILE);
    }
}

package padej.displayLib.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Звук в конфиге виджета: {@code clickSound} / {@code hoverSound}.
 * Записи: {@code "ui.button.click"} (ключ клиента) или {@code "UI_BUTTON_CLICK"} (имя константы), таблица
 * {@code { sound: "block.note_block.pling", volume: 0.5, pitch: 1.5 }}, {@code false} / {@code "none"} - без звука.
 */
public record SoundSpec(Sound sound, float volume, float pitch) {
    public static final SoundSpec NONE = new SoundSpec(null, 0, 1);

    public boolean enabled() { return sound != null; }

    public void play(Player p) {
        if (sound != null && p != null) p.playSound(p.getLocation(), sound, volume, pitch);
    }

    /** Найти звук по ключу ("ui.button.click", "minecraft:ui.button.click") или имени константы ("UI_BUTTON_CLICK"). */
    public static Sound lookup(String name) {
        if (name == null) return null;
        String n = name.trim();
        if (n.isEmpty() || n.equalsIgnoreCase("none") || n.equalsIgnoreCase("false")) return null;
        try {
            NamespacedKey key = n.contains(":") ? NamespacedKey.fromString(n.toLowerCase()) : NamespacedKey.minecraft(n.toLowerCase());
            if (key != null) { Sound s = Registry.SOUNDS.get(key); if (s != null) return s; }
        } catch (IllegalArgumentException ignored) { /* не ключ - пробуем как константу */ }
        try { return Sound.valueOf(n.toUpperCase().replace('.', '_')); } catch (IllegalArgumentException e) { return null; }
    }

    /** Разобрать значение из конфига; {@code null} - ключа нет (оставить дефолт); {@link #NONE} - явно выключен. */
    public static SoundSpec parse(Object v, SoundSpec defaults, java.util.function.Consumer<String> warn) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b ? defaults : NONE;
        if (v instanceof CharSequence s) {
            Sound snd = lookup(s.toString());
            if (snd == null) { if (!s.toString().equalsIgnoreCase("none") && !s.toString().isBlank()) warn.accept("неизвестный звук '" + s + "'"); return NONE; }
            return new SoundSpec(snd, defaults.volume, defaults.pitch);
        }
        if (v instanceof java.util.Map<?, ?> m) {
            Object name = m.get("sound") != null ? m.get("sound") : m.get("name");
            Sound snd = name != null ? lookup(name.toString()) : defaults.sound;
            if (snd == null) { if (name != null) warn.accept("неизвестный звук '" + name + "'"); return NONE; }
            float vol = m.get("volume") instanceof Number x ? x.floatValue() : defaults.volume;
            float pitch = m.get("pitch") instanceof Number x ? x.floatValue() : defaults.pitch;
            return new SoundSpec(snd, vol, pitch);
        }
        return null;
    }
}

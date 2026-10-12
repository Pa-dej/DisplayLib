package padej.displayLib.config;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Голова игрока для {@code ITEM_BUTTON} с {@code material: PLAYER_HEAD}:
 * <pre>
 * skull: "Notch"                       // имя игрока (профиль дотягивается с Mojang асинхронно)
 * skull: "069a79f4-24e9-4726-a5be-fca90e38aaf5"   // UUID
 * skull: "@viewer"                     // голова того, кто смотрит (только приватные экраны)
 * skull: "eyJ0ZXh0dXJlcyI6..."         // значение textures (base64) из head database
 * skull: "https://textures.minecraft.net/texture/&lt;hash&gt;"
 * skull: { owner: "Notch" } / { texture: "..." } / { url: "..." }
 * </pre>
 */
public record SkullSpec(String owner, String texture) {
    public static final String VIEWER = "@viewer";

    public boolean isViewer() { return VIEWER.equalsIgnoreCase(owner); }

    /** Профиль нужно дополнять по сети (имя/uuid без текстуры). */
    public boolean needsLookup() { return texture == null && owner != null && !isViewer(); }

    public static SkullSpec parse(Object v) {
        if (v == null) return null;
        if (v instanceof java.util.Map<?, ?> m) {
            Object owner = m.get("owner") != null ? m.get("owner") : m.get("player") != null ? m.get("player") : m.get("name");
            Object texture = m.get("texture") != null ? m.get("texture") : m.get("value");
            Object url = m.get("url");
            if (texture != null) return new SkullSpec(null, texture.toString().trim());
            if (url != null) return new SkullSpec(null, fromUrl(url.toString().trim()));
            return owner != null ? new SkullSpec(owner.toString().trim(), null) : null;
        }
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("http://") || s.startsWith("https://")) return new SkullSpec(null, fromUrl(s));
        if (s.length() > 60 && !s.contains(" ")) return new SkullSpec(null, s); // base64 textures
        return new SkullSpec(s, null);
    }

    /** base64 {"textures":{"SKIN":{"url":...}}} из URL текстуры. */
    public static String fromUrl(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Профиль без сетевых запросов: текстура ставится сразу, имя/uuid - пустой профиль (дополнить {@link #needsLookup()}). */
    public PlayerProfile profile(Player viewer) {
        if (isViewer()) return viewer != null ? viewer.getPlayerProfile() : Bukkit.createProfile(UUID.nameUUIDFromBytes("steve".getBytes(StandardCharsets.UTF_8)), "Steve");
        if (texture != null) {
            PlayerProfile p = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), null);
            p.setProperty(new ProfileProperty("textures", texture));
            return p;
        }
        UUID id = null;
        try { id = UUID.fromString(owner); } catch (IllegalArgumentException ignored) { /* это имя */ }
        return id != null ? Bukkit.createProfile(id, null) : Bukkit.createProfile(null, owner);
    }
}

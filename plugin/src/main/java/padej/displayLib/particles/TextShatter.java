package padej.displayLib.particles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Текст, который рассыпается на частицы-буквы: сначала висит обычным TextDisplay, через {@code hold} тиков
 * исчезает, и на месте каждой буквы появляется частица с этой буквой - дальше она летит по эффекту
 * (разлёт {@code OUTWARD} - от середины слова, гравитация, отскоки, прозрачность…).
 *
 * <pre>
 * TextShatter.of("слово")
 *     .at(location)                 // низ текста по центру, как у TextDisplay
 *     .facing(player)               // текст смотрит на игрока (или .yaw(...))
 *     .effect("letters")            // физика и анимация букв - эффект particles/letters.jmc
 *     .hold(20)                     // сколько тиков слово висит целым
 *     .scale(1.5)
 *     .play();
 *
 * TextShatter.of(Component.text("BOOM", NamedTextColor.RED, TextDecoration.BOLD)).at(loc).play();
 * </pre>
 *
 * <p>Буквы сохраняют стиль слова (цвет, жирность). Если у эффекта есть {@code colors} или {@code animation.color},
 * они красят буквы без своего цвета - тогда слово до распада лучше показывать тем же цветом. Ширины букв -
 * по шрифту Minecraft по умолчанию (латиница точно, остальное ≈ 6 px); для своего шрифта или ресурспака позиции
 * могут немного разойтись. Для бесшовного перехода у эффекта стоит {@code billboard: "FIXED"},
 * {@code animation.rotation: 0}, без {@code fadeIn}. Все методы - из основного потока.</p>
 */
public final class TextShatter {
    /** Пиксель текста TextDisplay, блоков. */
    private static final double PX = 1 / 40.0;

    private final Component text;
    private Location at;
    private Float yaw;
    private Location facing;
    private ParticleEffect effect;
    private String effectId = "letters";
    private int hold = 20;
    private double scale = 1;
    private Player owner;

    private TextShatter(Component text) {
        this.text = text;
    }

    public static TextShatter of(String text) {
        return new TextShatter(Component.text(text));
    }

    public static TextShatter of(Component text) {
        return new TextShatter(text);
    }

    /** Где стоит слово: низ текста по центру (как точка TextDisplay). */
    public TextShatter at(Location location) {
        this.at = location.clone();
        return this;
    }

    /** Куда смотрит текст (yaw Minecraft: 0 - лицом на юг, читается с юга). */
    public TextShatter yaw(float yaw) {
        this.yaw = yaw;
        return this;
    }

    /** Текст повёрнут к этой точке (например, глазам игрока). */
    public TextShatter facing(Location target) {
        this.facing = target.clone();
        return this;
    }

    /** Текст повёрнут к игроку. */
    public TextShatter facing(Player player) {
        return facing(player.getEyeLocation());
    }

    /** Эффект для букв по id ({@code particles/<id>.jmc}); по умолчанию {@code letters}. */
    public TextShatter effect(String id) {
        this.effectId = id;
        this.effect = null;
        return this;
    }

    public TextShatter effect(ParticleEffect effect) {
        this.effect = effect;
        return this;
    }

    /** Сколько тиков слово висит целым (0 - рассыпается сразу). */
    public TextShatter hold(int ticks) {
        this.hold = Math.max(0, ticks);
        return this;
    }

    /** Размер текста (1 - как обычный TextDisplay). */
    public TextShatter scale(double scale) {
        this.scale = scale;
        return this;
    }

    /** Для эффекта с {@code viewers: "owner"} - кто видит (и слово, и буквы); {@code null} - все. */
    public TextShatter owner(Player owner) {
        this.owner = owner;
        return this;
    }

    /** Показать слово и рассыпать его через {@code hold} тиков. */
    public Handle play() {
        if (at == null || at.getWorld() == null) throw new IllegalStateException("TextShatter: at(location) is required");
        ParticleEffect fx = effect != null ? effect : Particles.effect(effectId);
        if (fx == null) throw new IllegalArgumentException("TextShatter: no particle effect '" + effectId + "' (particles/" + effectId + ".jmc)");
        float y = yaw != null ? yaw : facing != null ? yawTowards(at, facing) : at.getYaw();
        boolean ownerOnly = owner != null && fx.viewers() == ParticleEffect.Viewers.OWNER;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("DisplayLib");
        Handle handle = new Handle();
        if (hold <= 0 || plugin == null) {
            handle.emission = shatter(fx, y);
            return handle;
        }
        Location loc = at.clone();
        loc.setYaw(y);
        loc.setPitch(0);
        ParticleEffect.Render r = fx.render();
        handle.word = at.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(text);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBackgroundColor(Color.fromARGB(0));
            d.setShadowed(r.shadow());
            if (r.brightness() != null) d.setBrightness(new Display.Brightness(r.brightness(), r.brightness()));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f((float) scale, (float) scale, (float) scale), new AxisAngle4f()));
            if (ownerOnly) d.setVisibleByDefault(false);
        });
        if (ownerOnly) owner.showEntity(plugin, handle.word);
        handle.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            handle.task = null;
            if (handle.word != null) handle.word.remove();
            handle.word = null;
            if (!handle.cancelled) handle.emission = shatter(fx, y);
        }, hold);
        return handle;
    }

    /** Буквы на местах букв слова. */
    private ParticleEmission shatter(ParticleEffect fx, float yaw) {
        // центр картинки буквы - на полстроки выше низа текста (как центр TEXT-частицы)
        Location center = at.clone().add(0, 4 * PX * scale, 0);
        return Particles.playPieces(fx, center, owner, pieces(text, scale, yaw));
    }

    /**
     * Буквы текста как частицы: смещение каждой от центра слова (TextDisplay центрирует строку) вдоль «вправо»
     * для читающего - текст с yaw смотрит по (-sin, 0, cos), игрок глядит навстречу, его право - (cos, 0, sin).
     * Пробелы дают отступ, но частиц не порождают.
     */
    static List<ParticlePiece> pieces(Component text, double scale, float yaw) {
        List<Glyph> glyphs = new ArrayList<>();
        flatten(text, Style.empty(), glyphs);
        int width = 0;
        for (Glyph g : glyphs) width += g.advance;
        double a = Math.toRadians(yaw);
        double rx = Math.cos(a), rz = Math.sin(a);
        List<ParticlePiece> pieces = new ArrayList<>();
        int x = 0;
        for (Glyph g : glyphs) {
            if (!g.blank) {
                double d = (x + g.advance / 2.0 - width / 2.0) * PX * scale;
                pieces.add(new ParticlePiece(g.component, rx * d, 0, rz * d, scale, yaw));
            }
            x += g.advance;
        }
        return pieces;
    }

    private static float yawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        // текст «смотрит» на точку: его лицевая сторона - по направлению yaw
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // -------------------------------------------------------------------------
    // Разбор текста на буквы со стилем
    // -------------------------------------------------------------------------

    private record Glyph(Component component, int advance, boolean blank) {}

    private static void flatten(Component c, Style parent, List<Glyph> out) {
        Style style = c.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (c instanceof TextComponent t) {
            String s = t.content();
            boolean bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                if (cp == '\n') continue;
                out.add(new Glyph(Component.text(ch).style(style), advance(cp) + (bold && cp != ' ' ? 1 : 0), Character.isWhitespace(cp)));
            }
        } else if (c instanceof ObjectComponent) {
            // спрайт или голова - квадрат 8×8, одной частицей
            out.add(new Glyph(c.children(List.of()).style(style), 8, false));
        } else {
            String plain = PlainTextComponentSerializer.plainText().serialize(c.children(List.of()));
            if (!plain.isEmpty()) flatten(Component.text(plain).style(style), Style.empty(), out);
        }
        for (Component child : c.children()) flatten(child, style, out);
    }

    /** Ширина символа в шрифте Minecraft по умолчанию, пикселей с промежутком (как Font.width). */
    static int advance(int cp) {
        return switch (cp) {
            case ' ' -> 4;
            case '!', '\'', ',', '.', ':', ';', '|', 'i' -> 2;
            case '`', 'l' -> 3;
            case '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    /** Запуск: можно отменить до распада (слово исчезнет) или после (уберутся буквы). */
    public static final class Handle {
        private Entity word;
        private BukkitTask task;
        private ParticleEmission emission;
        private boolean cancelled;

        /** Убрать слово и/или буквы. */
        public void cancel() {
            cancelled = true;
            if (task != null) task.cancel();
            task = null;
            if (word != null) word.remove();
            word = null;
            if (emission != null) emission.cancel();
        }

        /** Запуск букв (после распада) или {@code null}, пока слово висит. */
        public ParticleEmission emission() {
            return emission;
        }

        /** Слово уже рассыпалось. */
        public boolean shattered() {
            return emission != null;
        }
    }
}

package padej.displayLib.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import padej.displayLib.DisplayLib;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Morph-переход: анимация смены одного экрана другим и закрытия экрана.
 *
 * <p>Что делает клиент сам (нужно лишь выставить {@code interpolationDuration}/{@code teleportDuration}
 * и новое значение): положение сущности (телепорт), трансформацию (масштаб, translation) и
 * <b>цвет фона</b> TextDisplay - в 1.19.4+ фон интерполируется {@code ColorInterpolator} по ARGB.
 * Что клиент не интерполирует и что разбивается на шаги на сервере: содержимое и цвет текста,
 * предмет ItemDisplay. Прозрачность текста ({@code textOpacity}) не используется: клиент
 * интерполирует её как знаковый байт (255 = −1), поэтому плавного перехода не получается.</p>
 *
 * <p>Три вида участников:</p>
 * <ul>
 * <li><b>matched</b> - виджет с тем же id и тем же типом сущности есть на обоих экранах: сущность
 *     старого виджета передаётся новому и доводится до его состояния (позиция, масштаб, фон -
 *     клиентом; текст/предмет - шагами). Переход начинается с того, что сущность показывает
 *     сейчас, в том числе с наведённого состояния.</li>
 * <li><b>fade-out</b> - виджет только на старом экране: масштаб → 0, альфа фона → 0, затем удаление.</li>
 * <li><b>fade-in</b> - виджет только на новом экране: создаётся с масштабом 0 и прозрачным фоном,
 *     на следующем тике анимируется к целевым значениям.</li>
 * </ul>
 *
 * <p>Порядок пакетов: телепорт уходит клиенту раньше метаданных того же тика, поэтому
 * {@code teleportDuration} выставляется на тике 0, а сам телепорт - на тике 1.</p>
 */
public final class MorphTransition {

    /** Все идущие переходы - чтобы снять сущности при выключении плагина. */
    private static final Set<MorphTransition> ACTIVE = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private final int duration;
    private final int steps;

    private final List<Matched> matched = new ArrayList<>();
    private final List<Fade> fadeIn = new ArrayList<>();
    private final List<Display> fadeOut = new ArrayList<>();

    private BukkitTask task;
    private int tick = 0;
    private int lastStep = 0;
    private Runnable onFinish;

    /** Сущность, переходящая от старого виджета к новому. */
    private static final class Matched {
        final Display display;
        final Location targetLocation;
        final Transformation targetTransformation;
        // TextDisplay
        final Color targetBackground;
        final Component targetText;
        final String startPlain, targetPlain;
        final TextColor startColor, targetColor;
        final boolean textSteps;      // текст меняется посимвольно (нет спрайтов)
        final boolean textChanged;
        // ItemDisplay
        final ItemStack targetItem;
        final Runnable afterMove;

        Matched(TextDisplayButtonWidget w, TextDisplay d) {
            display = d;
            targetLocation = w.targetLocation();
            targetTransformation = w.targetTransformation();
            targetBackground = w.targetBackground();
            targetText = w.targetText();
            targetItem = null;
            afterMove = w::invalidateHitArea;

            Component startText = d.text();
            startPlain = plain(startText);
            targetPlain = plain(targetText);
            startColor = colorOf(startText);
            targetColor = colorOf(targetText);
            textChanged = !startText.equals(targetText);
            textSteps = textChanged && !hasObject(startText) && !hasObject(targetText);
        }

        Matched(ItemDisplayButtonWidget w, ItemDisplay d) {
            display = d;
            targetLocation = w.targetLocation();
            targetTransformation = w.targetTransformation();
            targetItem = w.targetItem();
            afterMove = w::invalidateHitArea;
            targetBackground = null; targetText = null;
            startPlain = targetPlain = "";
            startColor = targetColor = null;
            textSteps = false;
            textChanged = false;
        }

        /** Промежуточный текст на доле пути f ∈ (0, 1). */
        Component textAt(double f) {
            return Component.text(morphText(startPlain, targetPlain, f)).color(lerp(startColor, targetColor, f));
        }
    }

    private record Fade(Display display, Transformation target, Color background) {}

    public MorphTransition(ScreenDefinition.Morph settings) {
        this.duration = Math.max(1, Math.min(59, settings.getDuration()));
        this.steps = Math.max(1, Math.min(settings.getSteps(), duration));
    }

    // -------------------------------------------------------------------------
    // Сбор участников
    // -------------------------------------------------------------------------

    /** Новый текстовый виджет, получивший сущность старого. */
    public void match(TextDisplayButtonWidget widget, TextDisplay entity) {
        matched.add(new Matched(widget, entity));
    }

    public void match(ItemDisplayButtonWidget widget, ItemDisplay entity) {
        matched.add(new Matched(widget, entity));
    }

    /** Виджет только нового экрана: сразу после создания прячем, чтобы клиент не увидел целевое состояние. */
    public void fadeIn(TextDisplayButtonWidget widget) {
        TextDisplay d = widget.getDisplay();
        if (d == null) return;
        Transformation target = widget.targetTransformation();
        Color bg = widget.targetBackground();
        d.setTransformation(zeroScale(target));
        d.setBackgroundColor(transparent(bg));
        fadeIn.add(new Fade(d, target, bg));
    }

    public void fadeIn(ItemDisplayButtonWidget widget) {
        ItemDisplay d = widget.getDisplay();
        if (d == null) return;
        Transformation target = widget.targetTransformation();
        d.setTransformation(zeroScale(target));
        fadeIn.add(new Fade(d, target, null));
    }

    /** Сущность, которой на новом экране нет: исчезает и удаляется. */
    public void fadeOut(Display entity) {
        if (entity != null) fadeOut.add(entity);
    }

    // -------------------------------------------------------------------------
    // Выполнение
    // -------------------------------------------------------------------------

    public boolean isEmpty() {
        return matched.isEmpty() && fadeIn.isEmpty() && fadeOut.isEmpty();
    }

    public int getDuration() {
        return duration;
    }

    /**
     * Запустить переход. {@code onFinish} вызывается через {@code duration + 1} тиков
     * (а также при отмене), когда все сущности пришли в целевое состояние.
     */
    public void start(Runnable onFinish) {
        this.onFinish = onFinish;
        ACTIVE.add(this);

        // тик 0: цели для клиента
        for (Matched m : matched) {
            m.display.setTeleportDuration(duration);
            interpolate(m.display, m.targetTransformation, duration);
            if (m.display instanceof TextDisplay td) td.setBackgroundColor(m.targetBackground);
        }
        for (Display d : fadeOut) {
            Transformation t = d.getTransformation();
            interpolate(d, zeroScale(t), duration);
            if (d instanceof TextDisplay td) td.setBackgroundColor(transparent(td.getBackgroundColor()));
        }
        if (duration <= 1) { finish(); return; }

        task = Bukkit.getScheduler().runTaskTimer(DisplayLib.getInstance(), this::onTick, 1L, 1L);
    }

    private void onTick() {
        tick++;
        if (tick == 1) {
            // телепорт после того, как клиент получил teleportDuration
            for (Matched m : matched) {
                if (!m.display.isDead()) {
                    m.display.teleport(m.targetLocation);
                    m.afterMove.run();
                }
            }
            // появление новых: клиент уже видит их с нулевым масштабом
            for (Fade f : fadeIn) {
                interpolate(f.display, f.target, duration - 1);
                if (f.display instanceof TextDisplay td && f.background != null) td.setBackgroundColor(f.background);
            }
        }

        int step = (int) Math.floor((double) tick * steps / duration);
        if (step != lastStep && tick < duration) {
            lastStep = step;
            double f = (double) step / steps;
            applyStep(f);
        }

        if (tick >= duration) finish();
    }

    /** Шаг для того, что клиент не интерполирует. */
    private void applyStep(double f) {
        for (Matched m : matched) {
            if (m.display.isDead()) continue;
            if (m.display instanceof TextDisplay td && m.textChanged) {
                if (m.textSteps) td.text(m.textAt(f));
                else if (f >= 0.5) td.text(m.targetText);
            } else if (m.display instanceof ItemDisplay id && m.targetItem != null && f >= 0.5) {
                if (!m.targetItem.equals(id.getItemStack())) id.setItemStack(m.targetItem);
            }
        }
    }

    private void finish() {
        if (task != null) { task.cancel(); task = null; }
        ACTIVE.remove(this);

        for (Matched m : matched) {
            Display d = m.display;
            if (d.isDead()) continue;
            if (tick < 1) { d.teleport(m.targetLocation); m.afterMove.run(); }
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setTransformation(m.targetTransformation);
            if (d instanceof TextDisplay td) { td.text(m.targetText); td.setBackgroundColor(m.targetBackground); }
            else if (d instanceof ItemDisplay id && m.targetItem != null) id.setItemStack(m.targetItem);
        }
        for (Fade f : fadeIn) {
            if (f.display.isDead()) continue;
            f.display.setInterpolationDuration(1);
            f.display.setTransformation(f.target);
            if (f.display instanceof TextDisplay td && f.background != null) td.setBackgroundColor(f.background);
        }
        for (Display d : fadeOut) d.remove();
        fadeOut.clear();

        Runnable r = onFinish;
        onFinish = null;
        if (r != null) r.run();
    }

    /** Прервать переход: всё сразу в конечном состоянии. */
    public void cancel() {
        if (ACTIVE.contains(this)) finish();
    }

    /**
     * Сущности matched/fade-in передаются следующему переходу (новый экран открыт, пока шёл этот):
     * этот переход их больше не трогает и лишь дожидается исчезновения своих fade-out сущностей.
     */
    public void handOff() {
        for (Matched m : matched) {
            // текст - сразу целевой, чтобы следующий переход стартовал с него; остальное уже целевое
            if (m.display instanceof TextDisplay td && !m.display.isDead()) td.text(m.targetText);
            else if (m.display instanceof ItemDisplay id && m.targetItem != null && !m.display.isDead()) id.setItemStack(m.targetItem);
        }
        matched.clear();
        fadeIn.clear();
        onFinish = null;
        if (fadeOut.isEmpty()) cancel();
    }

    /** Снять все переходы (выключение плагина). */
    public static void cancelAll() {
        for (MorphTransition t : new ArrayList<>(ACTIVE)) t.cancel();
    }

    // -------------------------------------------------------------------------
    // Утилиты
    // -------------------------------------------------------------------------

    private static void interpolate(Display d, Transformation t, int duration) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(1, duration));
        d.setTransformation(t);
    }

    private static Transformation zeroScale(Transformation t) {
        return new Transformation(new Vector3f(t.getTranslation()), new AxisAngle4f(t.getLeftRotation()),
                new Vector3f(0, 0, 0), new AxisAngle4f(t.getRightRotation()));
    }

    private static Color transparent(Color c) {
        return c == null ? Color.fromARGB(0, 0, 0, 0) : Color.fromARGB(0, c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Промежуточная строка между {@code a} и {@code b} на доле пути {@code f}: общий префикс и суффикс
     * остаются, середина старой строки "съедается", середина новой - "вырастает" символ за символом
     * ("text" → "example text": "extext", "exatext", "examptext", …).
     */
    static String morphText(String a, String b, double f) {
        int p = 0, max = Math.min(a.length(), b.length());
        while (p < max && a.charAt(p) == b.charAt(p)) p++;
        int q = 0;
        while (q < max - p && a.charAt(a.length() - 1 - q) == b.charAt(b.length() - 1 - q)) q++;
        String prefix = a.substring(0, p), suffix = a.substring(a.length() - q);
        String aMid = a.substring(p, a.length() - q), bMid = b.substring(p, b.length() - q);
        int nb = (int) Math.round(bMid.length() * f);
        int na = aMid.length() - (int) Math.round(aMid.length() * f);
        return prefix + bMid.substring(0, nb) + aMid.substring(aMid.length() - na) + suffix;
    }

    static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Первый заданный цвет в дереве компонента; по умолчанию белый. */
    static TextColor colorOf(Component c) {
        if (c == null) return TextColor.color(0xFFFFFF);
        // цвет первого непустого фрагмента (корень форматированного текста - пустой белый "сброс")
        for (Component part : c.iterable(ComponentIteratorType.DEPTH_FIRST)) {
            if (part instanceof TextComponent t && !t.content().isEmpty() && t.color() != null) return t.color();
        }
        return c.color() != null ? c.color() : TextColor.color(0xFFFFFF);
    }

    static boolean hasObject(Component c) {
        if (c == null) return false;
        for (Component part : c.iterable(ComponentIteratorType.DEPTH_FIRST)) {
            if (part instanceof ObjectComponent) return true;
        }
        return false;
    }

    static TextColor lerp(TextColor a, TextColor b, double f) {
        return TextColor.color(
                (int) Math.round(a.red() + (b.red() - a.red()) * f),
                (int) Math.round(a.green() + (b.green() - a.green()) * f),
                (int) Math.round(a.blue() + (b.blue() - a.blue()) * f));
    }
}

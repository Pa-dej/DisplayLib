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
 *
 * <p>Цели (текст, фон, трансформация, предмет) берутся в {@link #start} - после {@code onOpen} нового экрана:
 * анимация ведёт к тому, что выставил скрипт, а не к значениям из файла. С {@link #match}/{@link #fadeIn}
 * и до конца перехода виджеты не пишут в свои сущности (их сеттеры только запоминают состояние), а в
 * конце перехода сущность получает текущее состояние виджета - с изменениями, сделанными во время него.</p>
 */
public final class MorphTransition {

    /** Все идущие переходы - чтобы снять сущности при выключении плагина. */
    private static final Set<MorphTransition> ACTIVE = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private final int duration;
    private final int steps;

    private final List<Matched> matched = new ArrayList<>();
    private final List<Fade> fadeIn = new ArrayList<>();
    /** Собранные до start: пара «виджет - сущность» и что сущность показывала до перехода */
    private final List<PendingMatch> pendingMatched = new ArrayList<>();
    private final List<Object> pendingFadeIn = new ArrayList<>();

    private record PendingMatch(Object widget, Display entity, Component startText) {}
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
        /** Виджет нового экрана: в конце перехода он сам выставляет сущности своё текущее состояние */
        final Object widget;

        Matched(TextDisplayButtonWidget w, TextDisplay d, Component startText) {
            widget = w;
            display = d;
            targetLocation = w.targetLocation();
            targetTransformation = w.targetTransformation();
            targetBackground = w.targetBackground();
            targetText = w.targetText();
            targetItem = null;
            afterMove = w::invalidateHitArea;

            startPlain = plain(startText);
            targetPlain = plain(targetText);
            startColor = colorOf(startText);
            targetColor = colorOf(targetText);
            textChanged = !startText.equals(targetText);
            textSteps = textChanged && !hasObject(startText) && !hasObject(targetText);
        }

        Matched(ItemDisplayButtonWidget w, ItemDisplay d) {
            widget = w;
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

    private record Fade(Object widget, Display display, Transformation target, Color background) {}

    public MorphTransition(ScreenDefinition.Morph settings) {
        this.duration = Math.max(1, Math.min(59, settings.getDuration()));
        this.steps = Math.max(1, Math.min(settings.getSteps(), duration));
    }

    // -------------------------------------------------------------------------
    // Сбор участников
    // -------------------------------------------------------------------------

    /**
     * Новый текстовый виджет, получивший сущность старого. Цели берутся в {@link #start}; до тех пор
     * (и до конца перехода) виджет не пишет в сущность.
     */
    public void match(TextDisplayButtonWidget widget, TextDisplay entity) {
        widget.beginMorph();
        pendingMatched.add(new PendingMatch(widget, entity, entity.text()));
    }

    public void match(ItemDisplayButtonWidget widget, ItemDisplay entity) {
        widget.beginMorph();
        pendingMatched.add(new PendingMatch(widget, entity, null));
    }

    /** Виджет только нового экрана: сразу после создания прячем, чтобы клиент не увидел ни файл, ни цель. */
    public void fadeIn(TextDisplayButtonWidget widget) {
        TextDisplay d = widget.getDisplay();
        if (d == null) return;
        widget.beginMorph();
        d.setTransformation(zeroScale(widget.targetTransformation()));
        d.setBackgroundColor(transparent(widget.targetBackground()));
        pendingFadeIn.add(widget);
    }

    public void fadeIn(ItemDisplayButtonWidget widget) {
        ItemDisplay d = widget.getDisplay();
        if (d == null) return;
        widget.beginMorph();
        d.setTransformation(zeroScale(widget.targetTransformation()));
        pendingFadeIn.add(widget);
    }

    /** Цели участников - по текущему состоянию виджетов (после onOpen). */
    private void collectTargets() {
        for (PendingMatch p : pendingMatched) {
            if (p.widget() instanceof TextDisplayButtonWidget w && p.entity() instanceof TextDisplay td) {
                matched.add(new Matched(w, td, p.startText()));
            } else if (p.widget() instanceof ItemDisplayButtonWidget w && p.entity() instanceof ItemDisplay id) {
                matched.add(new Matched(w, id));
            }
        }
        pendingMatched.clear();
        for (Object o : pendingFadeIn) {
            if (o instanceof TextDisplayButtonWidget w && ScreenDisplays.alive(w.getDisplay())) {
                TextDisplay d = w.getDisplay();
                Color bg = w.targetBackground();
                d.text(w.targetText()); // появляется сразу с текстом из onOpen (пока масштаб нулевой)
                d.setBackgroundColor(transparent(bg));
                fadeIn.add(new Fade(w, d, w.targetTransformation(), bg));
            } else if (o instanceof ItemDisplayButtonWidget w && ScreenDisplays.alive(w.getDisplay())) {
                ItemDisplay d = w.getDisplay();
                ItemStack item = w.targetItem();
                if (!item.equals(d.getItemStack())) d.setItemStack(item);
                fadeIn.add(new Fade(w, d, w.targetTransformation(), null));
            }
        }
        pendingFadeIn.clear();
    }

    /** Сущность, которой на новом экране нет: исчезает и удаляется. */
    public void fadeOut(Display entity) {
        if (entity != null) fadeOut.add(entity);
    }

    // -------------------------------------------------------------------------
    // Выполнение
    // -------------------------------------------------------------------------

    public boolean isEmpty() {
        return matched.isEmpty() && fadeIn.isEmpty() && fadeOut.isEmpty() && pendingMatched.isEmpty() && pendingFadeIn.isEmpty();
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
        collectTargets();
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
                    ScreenDisplays.teleport(m.display, m.targetLocation);
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
            if (tick < 1) { ScreenDisplays.teleport(d, m.targetLocation); m.afterMove.run(); }
            d.setTeleportDuration(1);
            endMorph(m.widget, true); // текущее состояние виджета: цель или то, что скрипт поменял во время перехода
        }
        for (Fade f : fadeIn) {
            if (f.display.isDead()) continue;
            endMorph(f.widget, true);
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
     * Переход так и не начался (экран закрыли прямо в onOpen): сущности, которые исчезали, убрать сразу,
     * виджетам вернуть запись в сущности.
     */
    public void abort() {
        for (PendingMatch p : pendingMatched) endMorph(p.widget(), true);
        for (Object w : pendingFadeIn) endMorph(w, true);
        pendingMatched.clear();
        pendingFadeIn.clear();
        for (Display d : fadeOut) d.remove();
        fadeOut.clear();
    }

    private static void endMorph(Object widget, boolean transform) {
        if (widget instanceof TextDisplayButtonWidget w) w.endMorph(transform);
        else if (widget instanceof ItemDisplayButtonWidget w) w.endMorph(transform);
    }

    /**
     * Сущности matched/fade-in передаются следующему переходу (новый экран открыт, пока шёл этот):
     * этот переход их больше не трогает и лишь дожидается исчезновения своих fade-out сущностей.
     */
    public void handOff() {
        for (Matched m : matched) {
            // текст/предмет и фон - текущие у виджета, чтобы следующий переход стартовал с них;
            // трансформацию клиент уже ведёт к цели - её не трогаем
            if (!m.display.isDead()) endMorph(m.widget, false);
        }
        for (Fade f : fadeIn) {
            if (!f.display.isDead()) endMorph(f.widget, false);
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

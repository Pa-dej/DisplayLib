package padej.displayLib.script.api;

import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;
import padej.displayLib.ui.widgets.Widget;

/**
 * Виджет экрана, с точки зрения скрипта.
 *
 * <p>Приходит первым аргументом обработчика клика
 * ({@code void onBuy(dyn widget, dyn player)}) и возвращается из
 * {@code screen.widget("id")}. Объект один на виджет на всё время жизни экрана:
 * {@code screen.widget("a") == screen.widget("a")}.</p>
 *
 * <pre>
 * dyn label = screen.widget("counter");
 * label.text("Счётчик: " + n);
 * label.bgColor(40, 80, 40);
 * label.bgAlpha(200);
 * label.visible(n &gt; 0);
 * widget.tooltip("Нажато " + n + " раз");
 * </pre>
 */
public final class WidgetAPI {
    private final String id;
    private final Widget widget;

    public WidgetAPI(String id, Widget widget) {
        this.id = id;
        this.widget = widget;
    }

    /** Идентификатор виджета из файла экрана (может быть {@code null}). */
    public String id() {
        return id;
    }

    /** {@code "TEXT_BUTTON"}, {@code "SPRITE_BUTTON"} или {@code "ITEM_BUTTON"}. */
    public String type() {
        return widget instanceof TextDisplayButtonWidget t ? (t.isSprite() ? "SPRITE_BUTTON" : "TEXT_BUTTON")
                : widget instanceof ItemDisplayButtonWidget ? "ITEM_BUTTON"
                : widget.getClass().getSimpleName();
    }

    /** Жива ли сущность виджета. */
    public boolean valid() {
        return widget.isValid();
    }

    // ---- текст (только TEXT_BUTTON) ----

    /** Текущий текст; {@code null} у предметных виджетов. */
    public String text() {
        return widget instanceof TextDisplayButtonWidget t && t.isValid() ? t.getText() : null;
    }

    /**
     * Установить текст. Строка - как есть; таблица/массив сегментов - как в файле экрана
     * ({@code [{text: "x3", color: "gray"}, {sprite: "item/iron_ingot"}]}). У предметных виджетов ничего не делает.
     */
    public void text(Object text) {
        if (widget instanceof TextDisplayButtonWidget t && t.isValid()) {
            t.setText(toComponent(text));
        }
    }

    /** Установить текст, показываемый при наведении (форматы как у {@link #text(Object)}). */
    public void hoveredText(Object text) {
        if (widget instanceof TextDisplayButtonWidget t && t.isValid()) {
            t.setHoveredText(toComponent(text));
        }
    }

    /** Показать спрайт атласа вместо текста: {@code sprite("item/diamond_sword")} (атлас по префиксу). */
    public void sprite(String sprite) {
        sprite(null, sprite);
    }

    /** Показать спрайт из указанного атласа: {@code sprite("minecraft:blocks", "block/bricks")}. */
    public void sprite(String atlas, String sprite) {
        if (widget instanceof TextDisplayButtonWidget t && t.isValid()) {
            t.setText(padej.displayLib.ui.ScreenSupport.spriteComponent(atlas, sprite));
        }
    }

    /** Спрайт при наведении (SPRITE_BUTTON). */
    public void hoveredSprite(String atlas, String sprite) {
        if (widget instanceof TextDisplayButtonWidget t && t.isValid()) {
            t.setHoveredText(padej.displayLib.ui.ScreenSupport.spriteComponent(atlas, sprite));
        }
    }

    private static net.kyori.adventure.text.Component toComponent(Object text) {
        Object v = padej.displayLib.config.JmcValues.toJava(text); // JTable/JArray -> Map/List
        if (v instanceof java.util.Map<?, ?> m) v = java.util.List.of(m);
        if (v instanceof java.util.List<?>) return padej.displayLib.ui.ScreenSupport.parseFormattedText(v);
        return net.kyori.adventure.text.Component.text(String.valueOf(v));
    }

    /** Цвет фона 0–255 (только TEXT_BUTTON). */
    public void bgColor(double r, double g, double b) {
        if (widget instanceof TextDisplayButtonWidget t && t.isValid()) {
            t.setBackgroundColor(clamp(r), clamp(g), clamp(b));
        }
    }

    /** Прозрачность фона 0–255 (только TEXT_BUTTON). */
    public void bgAlpha(double alpha) {
        if (widget instanceof TextDisplayButtonWidget t && t.isValid()) {
            t.setBackgroundAlpha(clamp(alpha));
        }
    }

    // ---- общие ----

    public boolean visible() {
        return widget.isValid() && widget.isVisible();
    }

    public void visible(boolean visible) {
        if (widget.isValid()) widget.setVisible(visible);
    }

    public boolean enabled() {
        return widget.isEnabled();
    }

    public void enabled(boolean enabled) {
        widget.setEnabled(enabled);
    }

    /** Текст подсказки или {@code null}. */
    public String tooltip() {
        return widget.getTooltip();
    }

    public void tooltip(Object text) {
        widget.setTooltip(text == null ? null : String.valueOf(text));
    }

    /** Внутреннее: сам виджет. */
    public Widget handle() {
        return widget;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    @Override
    public String toString() {
        return "widget " + (id != null ? id : type());
    }
}

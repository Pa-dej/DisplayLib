package padej.displayLib.api.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import padej.displayLib.ui.widgets.Widget;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Игрок кликнул по виджету экрана DisplayLib - приватного или публичного.
 *
 * <p>Вызывается в основном потоке до того, как экран выполнит действие клика
 * ({@code onClick} виджета: функция скрипта, переключение, закрытие). Отмена события
 * отменяет это действие; сам клик (удар по воздуху, блоку или сущности) отменяется
 * в любом случае, раз он пришёлся на виджет.</p>
 *
 * <pre>
 * &#64;EventHandler
 * public void onClick(DisplayClickEvent e) {
 *     if (e.isPublicScreen() &amp;&amp; "global_shop".equals(e.getScreenId()) &amp;&amp; "buy".equals(e.getWidgetId())
 *             &amp;&amp; !e.getPlayer().hasPermission("shop.buy")) {
 *         e.setCancelled(true);
 *     }
 * }
 * </pre>
 */
public class DisplayClickEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Player player;
    private final Widget widget;
    private final String screenId;
    private final String widgetId;
    private final boolean publicScreen;

    /**
     * @param screenId     id экрана
     * @param widgetId     id виджета из файла экрана или {@code null}
     * @param publicScreen клик по публичному экрану
     */
    public DisplayClickEvent(Player player, Widget widget, String screenId, String widgetId, boolean publicScreen) {
        this.player = player;
        this.widget = widget;
        this.screenId = screenId;
        this.widgetId = widgetId;
        this.publicScreen = publicScreen;
    }

    /** Клик по приватному экрану без сведений об экране (для совместимости). */
    public DisplayClickEvent(Player player, Widget widget) {
        this(player, widget, null, null, false);
    }

    /** Кликнувший игрок. */
    public Player getPlayer() {
        return player;
    }

    public Widget getWidget() {
        return widget;
    }

    /** id экрана (поле {@code id} в {@code .jmc}). */
    public @Nullable String getScreenId() {
        return screenId;
    }

    /** id виджета из файла экрана; {@code null}, если у виджета нет id. */
    public @Nullable String getWidgetId() {
        return widgetId;
    }

    /** Клик по публичному экрану (общему для всех игроков), а не по личному экрану игрока. */
    public boolean isPublicScreen() {
        return publicScreen;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

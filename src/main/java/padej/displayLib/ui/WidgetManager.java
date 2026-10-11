package padej.displayLib.ui;

import padej.displayLib.DisplayLib;
import padej.displayLib.api.events.DisplayClickEvent;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;
import padej.displayLib.ui.widgets.Widget;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import padej.displayLib.utils.ViewRay;

import java.util.ArrayList;
import java.util.List;

public abstract class WidgetManager {
    public final List<Widget> children = new ArrayList<>();

    /**
     * Тикаемые виджеты: подмножество {@link #children}, за взглядом на которые есть смысл следить
     * ({@link Widget#isInteractive()}). Статические надписи, кнопки без действия и без hover-реакции
     * (список не тикаемых виджетов экрана составляется при загрузке, см. ScreenAnalyzer) сюда не попадают
     * и на тиках не обсчитываются вовсе.
     */
    private final List<Widget> ticked = new ArrayList<>();
    private boolean tickListDirty = true;
    /** Виджеты с кадровой анимацией текста - тикаются каждый серверный тик (независимо от tickRate). */
    private final List<TextDisplayButtonWidget> animated = new ArrayList<>();
    public Player viewer;
    protected Location location;
    
    // Dirty flag для оптимизации cleanup
    private boolean isDirty = false;
    
    // Throttling для проверки расстояния
    private int rangeCheckTimer = 0;
    
    // Переиспользуемый луч взгляда зрителя (без аллокаций в цикле обновления)
    private final ViewRay viewRay = new ViewRay();
    
    public WidgetManager(Player viewer, Location location) {
        this.viewer = viewer;
        this.location = location;
    }
    
    public <T extends Widget> T addDrawableChild(T child) {
        children.add(child);
        tickListDirty = true;
        return child;
    }
    
    public void markDirty() {
        isDirty = true;
        tickListDirty = true;
    }

    /** Пересобрать список тикаемых виджетов (состав детей или их интерактивность изменились). */
    private void refreshTickList() {
        ticked.clear();
        animated.clear();
        for (int i = 0; i < children.size(); i++) {
            Widget w = children.get(i);
            if (w == null) continue;
            if (w.isInteractive()) ticked.add(w);
            if (w instanceof TextDisplayButtonWidget t && t.hasFrames()) animated.add(t);
        }
        tickListDirty = false;
    }

    /**
     * Тик кадровых анимаций текста. Вызывается каждый серверный тик (а не раз в tickRate):
     * у экрана без анимированных виджетов это одна проверка пустого списка.
     */
    public void tickAnimations() {
        if (tickListDirty) refreshTickList();
        for (int i = 0; i < animated.size(); i++) animated.get(i).tickFrames();
    }

    /** Кадры виджета изменились из скрипта - пересобрать списки. */
    public void invalidateTickList() {
        tickListDirty = true;
    }

    /** Сколько виджетов экрана обсчитывается на тиках (для /displaylib info и отладки). */
    public int tickedWidgetCount() {
        if (tickListDirty) refreshTickList();
        return ticked.size();
    }
    
    // Абстрактный метод для получения ScreenDefinition (если есть)
    protected abstract ScreenDefinition getScreenDefinition();
    
    // Абстрактный метод для проверки interaction_radius (для оптимизации hover detection)
    protected abstract boolean isPlayerInInteractionRange();
    
    // Абстрактный метод для проверки расстояния (реализуется в Screen)
    protected abstract boolean isPlayerInRange();
    
    // Абстрактный метод для закрытия экрана (реализуется в Screen)
    protected abstract void tryClose();
    
    public void update() {
        // Получаем настройки из ScreenDefinition если доступно
        ScreenDefinition definition = getScreenDefinition();
        int rangeCheckInterval = (definition != null) ? definition.getRangeCheckInterval() : 10;
        
        // Проверка расстояния с настраиваемым интервалом
        if (++rangeCheckTimer >= rangeCheckInterval) {
            rangeCheckTimer = 0;
            if (!isPlayerInRange()) {
                tryClose();
                return;
            }
        }
        
        // Удаляем недействительные виджеты только если есть изменения
        if (isDirty) {
            children.removeIf(widget -> !widget.isValid());
            isDirty = false;
            tickListDirty = true;
        }
        if (tickListDirty) refreshTickList();
        if (ticked.isEmpty()) return; // одни статические виджеты - следить не за чем
        
        // Оптимизация: проверяем interaction_radius перед обновлением виджетов
        // Если игрок далеко, не обновляем hover состояния (экономим CPU)
        boolean playerInInteractionRange = isPlayerInInteractionRange();
        
        if (playerInInteractionRange) {
            // Луч взгляда вычисляется один раз на всё обновление и передаётся виджетам
            viewRay.set(viewer);
            // Индексный обход: обработчики могут менять список виджетов во время обновления,
            // а итератор ArrayList в этом случае бросил бы ConcurrentModificationException
            for (int i = 0; i < ticked.size(); i++) {
                ticked.get(i).update(viewRay);
            }
        } else {
            // Игрок вне радиуса взаимодействия - сбрасываем hover.
            // clearHover() сам проверяет сохранённое состояние, поэтому повторный
            // расчёт наведения (isHovered) здесь не нужен.
            for (int i = 0; i < ticked.size(); i++) {
                Widget widget = ticked.get(i);
                if (widget.isValid()) {
                    widget.clearHover();
                }
            }
        }
    }
    
    public void handleClick() {
        // Check interaction_radius before processing click
        boolean inRange = isPlayerInInteractionRange();
        
        if (!inRange) {
            return; // Player too far for interaction
        }
        
        Widget nearestWidget = getNearestHoveredWidget();
        if (nearestWidget != null) {
            nearestWidget.handleClick();
        }
    }
    
    public void remove() {
        List<Widget> toRemove = new ArrayList<>(children);
        for (Widget widget : toRemove) {
            widget.remove();
        }
        children.clear();
        ticked.clear();
        animated.clear();

        markDirty();
    }

    public boolean isLookingAtWidget() {
        return getNearestHoveredWidget() != null;
    }

    /**
     * Ближайший к глазам зрителя виджет, на который он сейчас смотрит.
     * Наведение пересчитывается в момент вызова, поэтому клик точен при любом tick_rate.
     * Ближайший определяется по расстоянию до точки попадания взгляда в виджет.
     */
    public Widget getNearestHoveredWidget() {
        if (viewer == null) return null;

        viewRay.set(viewer);
        if (tickListDirty) refreshTickList();

        Widget nearestWidget = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < ticked.size(); i++) {
            Widget widget = ticked.get(i);
            if (widget == null) continue;

            double distance = widget.hitDistance(viewRay);
            if (distance >= 0.0 && distance < nearestDistance) {
                nearestDistance = distance;
                nearestWidget = widget;
            }
        }
        return nearestWidget;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
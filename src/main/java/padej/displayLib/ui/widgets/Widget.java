package padej.displayLib.ui.widgets;

import org.bukkit.Location;

/**
 * Интерфейс для всех виджетов DisplayLib.
 * 
 * <p>Виджеты - это интерактивные элементы экрана, которые могут отображать текст,
 * предметы и реагировать на действия игрока.</p>
 * 
 * <p>Поддерживаются следующие типы виджетов:</p>
 * <ul>
 * <li><b>TextDisplayButtonWidget</b> - текстовые кнопки с поддержкой форматирования</li>
 * <li><b>ItemDisplayButtonWidget</b> - кнопки с отображением предметов Minecraft</li>
 * </ul>
 */
public interface Widget {
    
    boolean isHovered();
    
    void handleClick();
    
    void remove();
    
    void update();

    /**
     * Обновление с заранее вычисленным лучом взгляда зрителя.
     *
     * <p>Менеджер экрана вычисляет луч один раз за обновление и передаёт его всем
     * виджетам, чтобы каждый виджет не запрашивал положение глаз игрока заново.
     * Реализация по умолчанию игнорирует луч и вызывает {@link #update()}.</p>
     *
     * @param ray луч взгляда зрителя экрана
     */
    default void update(padej.displayLib.utils.ViewRay ray) {
        update();
    }

    /**
     * Пересечение луча взгляда с зоной наведения виджета.
     *
     * <p>Единая проверка для приватных и публичных экранов: по ней определяется и само
     * наведение, и то, какой из нескольких виджетов под взглядом ближе к игроку.</p>
     *
     * @param ray луч взгляда игрока
     * @return расстояние от глаз до точки попадания или отрицательное число, если игрок не смотрит на виджет
     */
    default double hitDistance(padej.displayLib.utils.ViewRay ray) {
        // Реализация по умолчанию для виджетов без собственной зоны наведения
        if (!isHovered()) return -1.0;
        Location loc = getLocation();
        if (loc == null || loc.getWorld() != ray.getWorld()) return -1.0;
        return Math.sqrt(ray.distanceSquared(loc.getX(), loc.getY(), loc.getZ()));
    }
    
    Location getLocation();
    
    default boolean isValid() {
        return true;
    }

    /**
     * Пересоздать сущность виджета, если она исчезла из мира (выгрузка чанка).
     * По умолчанию ничего не делает.
     */
    default void ensureSpawned() {
    }
    
    // Методы для Lua API
    boolean isVisible();
    void setVisible(boolean visible);
    
    boolean isEnabled();
    void setEnabled(boolean enabled);
    
    /**
     * Получает текст tooltip виджета.
     * @return текст tooltip или null если не установлен
     */
    String getTooltip();
    
    /**
     * Устанавливает простой текстовый tooltip.
     * @param tooltip текст tooltip
     */
    void setTooltip(String tooltip);
    
    /**
     * Устанавливает форматированный tooltip через Adventure Component.
     * 
     * <p>Этот метод используется внутренне для поддержки форматированных tooltip
     * из YAML конфигурации с цветами и стилями.</p>
     * 
     * @param tooltip Adventure Component с форматированием
     */
    default void setTooltip(net.kyori.adventure.text.Component tooltip) {
        setTooltip(tooltip != null ? tooltip.toString() : null);
    }
    
    // Метод для принудительного сброса hover состояния
    default void clearHover() {
        // По умолчанию ничего не делаем, реализация в конкретных классах
    }
}
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
    
    Location getLocation();
    
    default boolean isValid() {
        return true;
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
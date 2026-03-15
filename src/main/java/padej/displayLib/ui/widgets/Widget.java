package padej.displayLib.ui.widgets;

// Базовый класс для всех виджетов
public interface Widget {
    
    boolean isHovered();
    
    void handleClick();
    
    void remove();
    
    void update();
    
    default boolean isValid() {
        return true;
    }
}
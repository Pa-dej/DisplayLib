package padej.displayLib.lua.api;

import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;
import padej.displayLib.ui.widgets.Widget;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Lua API для работы с виджетом.
 * 
 * <p>Предоставляет методы для изменения свойств виджета во время выполнения.
 * Доступен через screen.widget(id) или в контексте onClick функций.</p>
 * 
 * <h2>Доступные методы в Lua:</h2>
 * 
 * <p><b>Для TEXT_BUTTON виджетов:</b></p>
 * <ul>
 * <li><b>widget.text()</b> - Получить текущий текст</li>
 * <li><b>widget.text(newText)</b> - Установить новый текст</li>
 * <li><b>widget.hoveredText(text)</b> - Установить текст при наведении</li>
 * <li><b>widget.bgColor(r, g, b)</b> - Установить цвет фона (0-255)</li>
 * <li><b>widget.bgAlpha(alpha)</b> - Установить прозрачность фона (0-255)</li>
 * </ul>
 * 
 * <p><b>Общие методы:</b></p>
 * <ul>
 * <li><b>widget.visible()</b> - Проверить видимость виджета</li>
 * <li><b>widget.visible(true/false)</b> - Показать/скрыть виджет</li>
 * <li><b>widget.enabled()</b> - Проверить активность виджета</li>
 * <li><b>widget.enabled(true/false)</b> - Включить/отключить взаимодействие</li>
 * <li><b>widget.tooltip()</b> - Получить текст подсказки</li>
 * <li><b>widget.tooltip(text)</b> - Установить подсказку</li>
 * </ul>
 * 
 * <h2>Примеры использования в Lua:</h2>
 * <pre>{@code
 * -- В onClick функции widget доступен автоматически
 * function onButtonClick()
 *     -- Изменение текста
 *     widget.text("Нажато!")
 *     widget.hoveredText("Нажми еще раз")
 *     
 *     -- Изменение цвета фона на красный
 *     widget.bgColor(255, 0, 0)
 *     widget.bgAlpha(200)
 *     
 *     -- Управление видимостью
 *     widget.visible(false)  -- Скрыть виджет
 *     
 *     -- Отключение взаимодействия
 *     widget.enabled(false)
 *     
 *     -- Изменение подсказки
 *     widget.tooltip("Кнопка была нажата")
 * end
 * 
 * -- Получение виджета через screen API
 * function updateCounter()
 *     local counter = screen.widget("counter_display")
 *     if counter then
 *         local count = screen.data("count") or 0
 *         counter.text("Счетчик: " .. count)
 *         
 *         -- Цвет зависит от значения
 *         if count > 10 then
 *             counter.bgColor(255, 0, 0)  -- Красный
 *         else
 *             counter.bgColor(0, 255, 0)  -- Зеленый
 *         end
 *     end
 * end
 * }</pre>
 * 
 * @author DisplayLib
 * @version 1.0
 * @see ScreenAPI
 */
public class WidgetAPI extends LuaTable {
    private final Widget widget;
    
    public WidgetAPI(Widget widget) {
        this.widget = widget;
        
        // Текст (только для TEXT_BUTTON)
        set("text", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                if (!(widget instanceof TextDisplayButtonWidget textWidget)) {
                    return LuaValue.NIL;
                }
                
                // Проверяем что виджет еще валиден
                if (!textWidget.isValid()) {
                    return LuaValue.NIL;
                }
                
                if (arg.isnil()) {
                    // Геттер
                    return LuaValue.valueOf(textWidget.getText());
                } else {
                    // Сеттер
                    String text = arg.checkjstring();
                    textWidget.setText(text);
                    return LuaValue.NIL;
                }
            }
        });
        
        // Hovered текст (только для TEXT_BUTTON)
        set("hoveredText", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                if (!(widget instanceof TextDisplayButtonWidget textWidget)) {
                    return LuaValue.NIL;
                }
                
                // Проверяем что виджет еще валиден
                if (!textWidget.isValid()) {
                    return LuaValue.NIL;
                }
                
                String text = arg.checkjstring();
                textWidget.setHoveredText(text);
                return LuaValue.NIL;
            }
        });
        
        // Видимость
        set("visible", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                // Проверяем что виджет еще валиден
                if (!widget.isValid()) {
                    return LuaValue.NIL;
                }
                
                if (arg.isnil()) {
                    // Геттер
                    return LuaValue.valueOf(widget.isVisible());
                } else {
                    // Сеттер
                    boolean visible = arg.checkboolean();
                    widget.setVisible(visible);
                    return LuaValue.NIL;
                }
            }
        });
        
        // Активность (реагирует ли на hover/click)
        set("enabled", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                if (arg.isnil()) {
                    // Геттер
                    return LuaValue.valueOf(widget.isEnabled());
                } else {
                    // Сеттер
                    boolean enabled = arg.checkboolean();
                    widget.setEnabled(enabled);
                    return LuaValue.NIL;
                }
            }
        });
        
        // Tooltip
        set("tooltip", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                if (arg.isnil()) {
                    // Геттер
                    String tooltip = widget.getTooltip();
                    return tooltip != null ? LuaValue.valueOf(tooltip) : LuaValue.NIL;
                } else {
                    // Сеттер
                    String tooltip = arg.checkjstring();
                    widget.setTooltip(tooltip);
                    return LuaValue.NIL;
                }
            }
        });
        
        // Цвет фона (только для TEXT_BUTTON)
        set("bgColor", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue r, LuaValue g, LuaValue b) {
                if (!(widget instanceof TextDisplayButtonWidget textWidget)) {
                    return LuaValue.NIL;
                }
                
                // Проверяем что виджет еще валиден
                if (!textWidget.isValid()) {
                    return LuaValue.NIL;
                }
                
                int red = (int) r.checkdouble();
                int green = (int) g.checkdouble();
                int blue = (int) b.checkdouble();
                
                textWidget.setBackgroundColor(red, green, blue);
                return LuaValue.NIL;
            }
        });
        
        // Прозрачность фона (только для TEXT_BUTTON)
        set("bgAlpha", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue alpha) {
                if (!(widget instanceof TextDisplayButtonWidget textWidget)) {
                    return LuaValue.NIL;
                }
                
                int a = (int) alpha.checkdouble();
                textWidget.setBackgroundAlpha(Math.max(0, Math.min(255, a)));
                return LuaValue.NIL;
            }
        });
    }
}
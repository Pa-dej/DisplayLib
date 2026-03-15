package padej.displayLib.lua.api;

import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;
import padej.displayLib.ui.widgets.Widget;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Lua API для работы с виджетом
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
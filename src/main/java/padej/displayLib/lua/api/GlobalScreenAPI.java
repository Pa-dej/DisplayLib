package padej.displayLib.lua.api;

import padej.displayLib.lua.GlobalLuaContext;
import padej.displayLib.ui.GlobalScreenInstance;
import padej.displayLib.ui.widgets.Widget;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Lua API для работы с публичным экраном
 */
public class GlobalScreenAPI extends LuaTable {
    private final GlobalScreenInstance screen;
    private final GlobalLuaContext context;
    
    public GlobalScreenAPI(GlobalScreenInstance screen, GlobalLuaContext context) {
        this.screen = screen;
        this.context = context;
        
        // Получить ID экрана
        set("id", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(screen.getScreenId());
            }
        });
        
        // Для публичных экранов close и switch не имеют смысла
        set("close", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                // Публичные экраны не закрываются по клику игрока
                return LuaValue.NIL;
            }
        });
        
        set("switch", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue screenId) {
                // Для публичных экранов switch не имеет смысла
                return LuaValue.NIL;
            }
        });
        
        // Получить виджет по ID
        set("widget", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue widgetId) {
                String id = widgetId.checkjstring();
                Widget widget = screen.getWidget(id);
                if (widget != null) {
                    return new WidgetAPI(widget);
                }
                return LuaValue.NIL;
            }
        });
        
        // Persistent data - для публичных экранов используем простое хранилище
        set("data", new LuaFunction() {
            @Override
            public LuaValue call() {
                throw new LuaError("screen.data() requires at least one argument");
            }
            
            @Override
            public LuaValue call(LuaValue key) {
                // Геттер с одним аргументом
                String keyStr = key.checkjstring();
                Object data = context.getPersistentData(keyStr);
                return toLuaValue(data);
            }
            
            @Override
            public LuaValue call(LuaValue key, LuaValue value) {
                // Сеттер/удаление с двумя аргументами
                String keyStr = key.checkjstring();
                
                if (value.isnil()) {
                    // Удаление при явной передаче nil
                    context.setPersistentData(keyStr, null);
                } else {
                    // Сеттер
                    Object javaValue = toJavaValue(value);
                    context.setPersistentData(keyStr, javaValue);
                }
                return LuaValue.NIL;
            }
        });
    }
    
    /**
     * Конвертация Java объекта в LuaValue
     */
    private LuaValue toLuaValue(Object obj) {
        if (obj == null) return LuaValue.NIL;
        if (obj instanceof String) return LuaValue.valueOf((String) obj);
        if (obj instanceof Number) return LuaValue.valueOf(((Number) obj).doubleValue());
        if (obj instanceof Boolean) return LuaValue.valueOf((Boolean) obj);
        return LuaValue.NIL;
    }
    
    /**
     * Конвертация LuaValue в Java объект
     */
    private Object toJavaValue(LuaValue value) {
        if (value.isnil()) return null;
        if (value.isstring()) return value.tojstring();
        if (value.isnumber()) return value.todouble();
        if (value.isboolean()) return value.toboolean();
        return null;
    }
}
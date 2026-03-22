package padej.displayLib.lua.api;

import padej.displayLib.lua.LuaContext;
import padej.displayLib.ui.ScreenInstance;
import padej.displayLib.ui.UIManager;
import padej.displayLib.ui.widgets.Widget;
import padej.displayLib.lua.api.WidgetAPI;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Lua API для работы с персональным экраном.
 * 
 * <p>Предоставляет методы для управления экраном, доступа к виджетам
 * и хранения данных между вызовами функций.</p>
 * 
 * <h2>Доступные методы в Lua:</h2>
 * 
 * <p><b>Управление экраном:</b></p>
 * <ul>
 * <li><b>screen.id()</b> - Получить ID текущего экрана</li>
 * <li><b>screen.close()</b> - Закрыть текущий экран</li>
 * <li><b>screen.switch(screenId)</b> - Переключиться на другой экран</li>
 * </ul>
 * 
 * <p><b>Работа с виджетами:</b></p>
 * <ul>
 * <li><b>screen.widget(id)</b> - Получить виджет по ID</li>
 * </ul>
 * 
 * <p><b>Хранение данных:</b></p>
 * <ul>
 * <li><b>screen.data(key)</b> - Получить значение по ключу</li>
 * <li><b>screen.data(key, value)</b> - Установить значение</li>
 * <li><b>screen.data(key, nil)</b> - Удалить значение</li>
 * </ul>
 * 
 * <h2>Примеры использования в Lua:</h2>
 * <pre>{@code
 * -- Получение информации об экране
 * local currentId = screen.id()
 * log.info("Текущий экран: " .. currentId)
 * 
 * -- Управление экраном
 * screen.switch("main_menu")  -- Переключиться на главное меню
 * screen.close()              -- Закрыть экран
 * 
 * -- Работа с виджетами
 * local button = screen.widget("my_button")
 * if button then
 *     button.text("Новый текст")
 *     button.visible(true)
 * end
 * 
 * -- Хранение данных (сохраняется пока экран открыт)
 * screen.data("counter", 0)           -- Установить счетчик
 * local count = screen.data("counter") -- Получить значение
 * screen.data("counter", count + 1)   -- Увеличить
 * screen.data("temp", nil)            -- Удалить временные данные
 * }</pre>
 * 
 * @author DisplayLib
 * @version 1.0
 * @see WidgetAPI
 * @see GlobalScreenAPI
 */
public class ScreenAPI extends LuaTable {
    private final ScreenInstance screen;
    private final LuaContext context;
    
    public ScreenAPI(ScreenInstance screen, LuaContext context) {
        this.screen = screen;
        this.context = context;
        
        // Получить ID экрана
        set("id", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(screen.getScreenId());
            }
        });
        
        // Закрыть экран
        set("close", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                UIManager.getInstance().closeScreen(context.getPlayer());
                return LuaValue.NIL;
            }
        });
        
        // Переключиться на другой экран
        set("switch", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue screenId) {
                String id = screenId.checkjstring();
                UIManager.getInstance().switchScreen(context.getPlayer(), id);
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
        
        // Persistent data - геттер/сеттер/удаление
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
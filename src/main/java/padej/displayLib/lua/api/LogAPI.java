package padej.displayLib.lua.api;

import padej.displayLib.DisplayLib;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

import java.util.logging.Level;

/**
 * Lua API для логирования.
 * 
 * <p>Предоставляет методы для записи сообщений в лог сервера с различными уровнями важности.
 * Полезно для отладки Lua скриптов и мониторинга работы экранов.</p>
 * 
 * <h2>Доступные методы в Lua:</h2>
 * 
 * <p><b>Уровни логирования:</b></p>
 * <ul>
 * <li><b>log.info(message)</b> - Информационное сообщение</li>
 * <li><b>log.warn(message)</b> - Предупреждение</li>
 * <li><b>log.error(message)</b> - Ошибка</li>
 * </ul>
 * 
 * <h2>Примеры использования в Lua:</h2>
 * <pre>{@code
 * -- Информационные сообщения
 * log.info("Экран открыт для игрока: " .. player.name())
 * log.info("Загружены настройки: язык=" .. storage.get("language", "en"))
 * 
 * -- Предупреждения
 * log.warn("Игрок пытается использовать недоступную функцию")
 * log.warn("Низкий уровень здоровья: " .. player.health())
 * 
 * -- Ошибки
 * log.error("Не удалось загрузить данные игрока")
 * log.error("Критическая ошибка в обработке клика")
 * 
 * -- Отладка функций
 * function onButtonClick()
 *     log.info("Кнопка нажата игроком " .. player.name())
 *     
 *     local success = pcall(function()
 *         -- Опасная операция
 *         local data = storage.get("complex_data")
 *         -- ... обработка данных
 *     end)
 *     
 *     if not success then
 *         log.error("Ошибка при обработке данных")
 *     else
 *         log.info("Данные успешно обработаны")
 *     end
 * end
 * 
 * -- Мониторинг состояния
 * function on_open()
 *     log.info("=== Экран открыт ===")
 *     log.info("Игрок: " .. player.name())
 *     log.info("Режим игры: " .. player.gamemode())
 *     log.info("Здоровье: " .. player.health())
 * end
 * 
 * function on_close()
 *     log.info("=== Экран закрыт ===")
 * end
 * }</pre>
 * 
 * <p><b>Примечание:</b> Все сообщения записываются в лог сервера с префиксом "[DisplayLib/Lua]"
 * для удобства фильтрации и поиска.</p>
 * 
 * @author DisplayLib
 * @version 1.0
 */
public class LogAPI extends LuaTable {
    private final DisplayLib plugin;
    
    public LogAPI(DisplayLib plugin) {
        this.plugin = plugin;
        
        // info(message)
        set("info", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue message) {
                String msg = message.checkjstring();
                plugin.getLogger().info("[DisplayLib/Lua] " + msg);
                return LuaValue.NIL;
            }
        });
        
        // warn(message)
        set("warn", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue message) {
                String msg = message.checkjstring();
                plugin.getLogger().warning("[DisplayLib/Lua] " + msg);
                return LuaValue.NIL;
            }
        });
        
        // error(message)
        set("error", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue message) {
                String msg = message.checkjstring();
                plugin.getLogger().log(Level.SEVERE, "[DisplayLib/Lua] " + msg);
                return LuaValue.NIL;
            }
        });
    }
}
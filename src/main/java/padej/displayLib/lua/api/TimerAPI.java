package padej.displayLib.lua.api;

import padej.displayLib.DisplayLib;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.HashMap;
import java.util.Map;

/**
 * Lua API для работы с таймерами.
 * 
 * <p>Предоставляет возможность выполнения отложенных и повторяющихся действий.
 * Все таймеры автоматически отменяются при закрытии экрана.</p>
 * 
 * <h2>Доступные методы в Lua:</h2>
 * 
 * <p><b>Типы таймеров:</b></p>
 * <ul>
 * <li><b>timer.after(ticks, function)</b> - Выполнить функцию через указанное время</li>
 * <li><b>timer.every(ticks, function)</b> - Повторять функцию каждые N тиков
 *     (то же, что {@code timer["repeat"](ticks, function)}; запись {@code timer.repeat(...)}
 *     в Lua невозможна, так как {@code repeat} - зарезервированное слово)</li>
 * <li><b>timer.times(period, count, function)</b> - Выполнить функцию N раз с интервалом</li>
 * <li><b>timer.cancel(timerId)</b> - Отменить таймер по ID</li>
 * </ul>
 * 
 * <h2>Единицы времени:</h2>
 * <ul>
 * <li>1 тик = 1/20 секунды (50 мс)</li>
 * <li>20 тиков = 1 секунда</li>
 * <li>200 тиков = 10 секунд</li>
 * <li>1200 тиков = 1 минута</li>
 * </ul>
 * 
 * <h2>Примеры использования в Lua:</h2>
 * <pre>{@code
 * -- Отложенное выполнение (через 3 секунды)
 * timer.after(60, function()
 *     player.message("Прошло 3 секунды!")
 * end)
 * 
 * -- Повторяющееся действие (каждую секунду)
 * local countdownId = timer.every(20, function()
 *     local count = screen.data("countdown") or 10
 *     if count > 0 then
 *         player.message("Осталось: " .. count)
 *         screen.data("countdown", count - 1)
 *     else
 *         player.message("Время вышло!")
 *         timer.cancel(countdownId)
 *     end
 * end)
 * 
 * -- Выполнить 5 раз с интервалом в 2 секунды
 * timer.times(40, 5, function(i)
 *     player.message("Итерация " .. i .. " из 5")
 *     player.sound("BLOCK_NOTE_BLOCK_PLING", 1.0, 1.0 + i * 0.2)
 * end)
 * 
 * -- Анимация текста кнопки
 * local animationFrames = {".", "..", "...", "...."}
 * local frameIndex = 1
 * 
 * timer.every(10, function()  -- Каждые 0.5 секунды
 *     local button = screen.widget("loading_button")
 *     if button then
 *         button.text("Загрузка" .. animationFrames[frameIndex])
 *         frameIndex = frameIndex + 1
 *         if frameIndex > #animationFrames then
 *             frameIndex = 1
 *         end
 *     end
 * end)
 * 
 * -- Автоматическое закрытие экрана через 30 секунд
 * timer.after(600, function()
 *     player.message("Экран закрывается автоматически")
 *     screen.close()
 * end)
 * }</pre>
 * 
 * <p><b>Примечание:</b> Все таймеры автоматически отменяются при закрытии экрана
 * или выходе игрока, предотвращая утечки памяти.</p>
 * 
 * @author DisplayLib
 * @version 1.0
 */
public class TimerAPI extends LuaTable {
    private final DisplayLib plugin;

    /**
     * Активные таймеры по ID задачи. Завершившиеся таймеры удаляются сразу,
     * поэтому у долго живущих (публичных) экранов таблица не растёт бесконечно.
     */
    private final Map<Integer, BukkitTask> activeTasks = new HashMap<>();

    /** Экран закрыт: новые таймеры больше не создаются (иначе их некому было бы отменить). */
    private boolean closed = false;
    
    public TimerAPI(DisplayLib plugin) {
        this.plugin = plugin;
        
        // after(ticks, function)
        set("after", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue ticks, LuaValue function) {
                long delay = Math.max(0L, ticks.checklong());
                LuaFunction func = function.checkfunction();
                if (closed) return LuaValue.valueOf(-1);
                
                final int[] id = new int[1];
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Одноразовый таймер отработал - больше не держим ссылку на него
                    activeTasks.remove(id[0]);
                    runCallback(func, LuaValue.NONE);
                }, delay);
                
                id[0] = task.getTaskId();
                activeTasks.put(id[0], task);
                return LuaValue.valueOf(id[0]);
            }
        });
        
        // repeat(ticks, function) -> returns timer id
        LuaValue repeat = new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue ticks, LuaValue function) {
                long period = Math.max(1L, ticks.checklong());
                LuaFunction func = function.checkfunction();
                if (closed) return LuaValue.valueOf(-1);
                
                BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                        () -> runCallback(func, LuaValue.NONE), 0L, period);
                
                activeTasks.put(task.getTaskId(), task);
                return LuaValue.valueOf(task.getTaskId());
            }
        };
        set("repeat", repeat);
        // "repeat" - зарезервированное слово Lua, поэтому timer.repeat(...) не компилируется;
        // timer.every(...) - то же самое с обычным синтаксисом вызова
        set("every", repeat);
        
        // times(period, count, function(i))
        set("times", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue period, LuaValue count, LuaValue function) {
                long periodTicks = Math.max(1L, period.checklong());
                int maxCount = count.checkint();
                LuaFunction func = function.checkfunction();
                if (closed) return LuaValue.valueOf(-1);
                
                final int[] counter = {0};
                final BukkitTask[] taskRef = new BukkitTask[1];
                
                taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    int current = ++counter[0];
                    if (current > maxCount) {
                        // Отменяем таймер изнутри
                        taskRef[0].cancel();
                        activeTasks.remove(taskRef[0].getTaskId());
                        return;
                    }
                    
                    runCallback(func, LuaValue.valueOf(current));
                }, 0L, periodTicks);
                
                activeTasks.put(taskRef[0].getTaskId(), taskRef[0]);
                return LuaValue.valueOf(taskRef[0].getTaskId());
            }
        });
        
        // cancel(timerId)
        set("cancel", new org.luaj.vm2.lib.OneArgFunction() {
            @Override
            public LuaValue call(LuaValue timerId) {
                BukkitTask task = activeTasks.remove(timerId.checkint());
                if (task != null) {
                    task.cancel();
                }
                return LuaValue.NIL;
            }
        });
    }
    
    private void runCallback(LuaFunction func, Varargs args) {
        try {
            func.invoke(args);
        } catch (Exception e) {
            plugin.getLogger().warning("Error in timer callback: " + e.getMessage());
        } catch (StackOverflowError e) {
            plugin.getLogger().warning("Stack overflow in timer callback");
        }
    }
    
    /**
     * Отменить все активные таймеры (при закрытии экрана).
     * После этого вызова новые таймеры не создаются.
     */
    public void cancelAllTimers() {
        closed = true;
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }
}

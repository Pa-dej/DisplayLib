package padej.displayLib.lua.api;

import padej.displayLib.DisplayLib;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <li><b>timer.repeat(ticks, function)</b> - Повторять функцию каждые N тиков</li>
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
 * local countdownId = timer.repeat(20, function()
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
 * timer.repeat(10, function()  -- Каждые 0.5 секунды
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
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    
    public TimerAPI(DisplayLib plugin) {
        this.plugin = plugin;
        
        // after(ticks, function)
        set("after", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue ticks, LuaValue function) {
                long delay = ticks.checklong();
                LuaFunction func = function.checkfunction();
                
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        func.call();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in timer callback: " + e.getMessage());
                    }
                }, delay);
                
                activeTasks.add(task);
                return LuaValue.valueOf(task.getTaskId());
            }
        });
        
        // repeat(ticks, function) -> returns timer id
        set("repeat", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue ticks, LuaValue function) {
                long period = ticks.checklong();
                LuaFunction func = function.checkfunction();
                
                BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    try {
                        func.call();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in timer callback: " + e.getMessage());
                    }
                }, 0L, period);
                
                activeTasks.add(task);
                return LuaValue.valueOf(task.getTaskId());
            }
        });
        
        // times(period, count, function(i))
        set("times", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue period, LuaValue count, LuaValue function) {
                long periodTicks = period.checklong();
                int maxCount = count.checkint();
                LuaFunction func = function.checkfunction();
                
                AtomicInteger counter = new AtomicInteger(0);
                
                // Используем массив для хранения ссылки на таск
                final BukkitTask[] taskRef = new BukkitTask[1];
                
                taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    int current = counter.incrementAndGet();
                    if (current > maxCount) {
                        // Отменяем таймер изнутри
                        taskRef[0].cancel();
                        activeTasks.remove(taskRef[0]);
                        return;
                    }
                    
                    try {
                        func.call(LuaValue.valueOf(current));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in timer callback: " + e.getMessage());
                    }
                }, 0L, periodTicks);
                
                activeTasks.add(taskRef[0]);
                return LuaValue.valueOf(taskRef[0].getTaskId());
            }
        });
        
        // cancel(timerId)
        set("cancel", new org.luaj.vm2.lib.OneArgFunction() {
            @Override
            public LuaValue call(LuaValue timerId) {
                int id = timerId.checkint();
                activeTasks.removeIf(task -> {
                    if (task.getTaskId() == id) {
                        task.cancel();
                        return true;
                    }
                    return false;
                });
                return LuaValue.NIL;
            }
        });
    }
    
    /**
     * Отменить все активные таймеры (при закрытии экрана)
     */
    public void cancelAllTimers() {
        for (BukkitTask task : activeTasks) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }
}
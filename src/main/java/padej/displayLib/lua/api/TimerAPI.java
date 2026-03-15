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
 * Lua API для работы с таймерами
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
                
                BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    int current = counter.incrementAndGet();
                    if (current > maxCount) {
                        // Таймер завершен, но мы не можем отменить себя изнутри
                        // Просто не выполняем функцию
                        return;
                    }
                    
                    try {
                        func.call(LuaValue.valueOf(current));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error in timer callback: " + e.getMessage());
                    }
                }, 0L, periodTicks);
                
                // Отменяем таймер через нужное количество тиков
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    activeTasks.remove(task);
                    task.cancel();
                }, periodTicks * maxCount + 1);
                
                activeTasks.add(task);
                return LuaValue.valueOf(task.getTaskId());
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
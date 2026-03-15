package padej.displayLib.lua.api;

import padej.displayLib.DisplayLib;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

import java.util.logging.Level;

/**
 * Lua API для логирования
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
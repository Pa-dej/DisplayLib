package padej.displayLib.lua;

import padej.displayLib.DisplayLib;
import padej.displayLib.lua.api.GlobalScreenAPI;
import padej.displayLib.ui.GlobalScreenInstance;
import org.bukkit.entity.Player;
import org.luaj.vm2.Globals;

/**
 * Контекст выполнения Lua скрипта для глобального экрана
 */
public class GlobalLuaContext extends BaseLuaContext {
    private final GlobalScreenInstance screen;
    private final GlobalScreenAPI screenAPI;

    public GlobalLuaContext(Globals globals, GlobalScreenInstance screen, Player player, DisplayLib plugin) {
        super(globals, player, plugin);
        this.screen = screen;
        this.screenAPI = new GlobalScreenAPI(screen, this);
    }

    public GlobalScreenInstance getScreen() { return screen; }
    public GlobalScreenAPI getScreenAPI() { return screenAPI; }
}

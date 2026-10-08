package padej.displayLib.lua;

import padej.displayLib.DisplayLib;
import padej.displayLib.lua.api.ScreenAPI;
import padej.displayLib.ui.ScreenInstance;
import org.bukkit.entity.Player;
import org.luaj.vm2.Globals;

/**
 * Контекст выполнения Lua скрипта для конкретного экрана и игрока
 */
public class LuaContext extends BaseLuaContext {
    private final ScreenInstance screen;
    private final ScreenAPI screenAPI;

    public LuaContext(Globals globals, ScreenInstance screen, Player player, DisplayLib plugin) {
        super(globals, player, plugin);
        this.screen = screen;
        this.screenAPI = new ScreenAPI(screen, this);
    }

    public ScreenInstance getScreen() { return screen; }
    public ScreenAPI getScreenAPI() { return screenAPI; }
}

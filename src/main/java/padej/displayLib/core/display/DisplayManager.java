package padej.displayLib.core.display;

import padej.displayLib.ui.screen.AbstractScreen;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DisplayManager {
    private final Map<UUID, AbstractScreen> activeScreens = new HashMap<>();
    
    public void showScreen(Player player, AbstractScreen screen) {
        hideScreen(player);
        screen.init();
        activeScreens.put(player.getUniqueId(), screen);
    }
    
    public void hideScreen(Player player) {
        AbstractScreen screen = activeScreens.remove(player.getUniqueId());
        if (screen != null) {
            screen.onClose();
        }
    }
}
package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.widgets.ItemDisplayButtonConfig;
import padej.displayLib.ui.widgets.WidgetPosition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class Branch1Screen extends Screen {
    public Branch1Screen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public void createScreenWidgets() {
        addButton(Material.REDSTONE, "Подветка 1", () -> {
            getViewer().sendMessage("Ветка 1");
        }, 0);

        addButton(Material.GLOWSTONE_DUST, "Подветка 2", () -> {
            getViewer().sendMessage("Ветка 2");
        }, 1);
    }

    @Override
    public Class<? extends Screen> getParentScreen() {
        return MainScreen.class;
    }
}
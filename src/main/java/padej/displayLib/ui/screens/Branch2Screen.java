package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.widgets.ItemDisplayButtonConfig;
import padej.displayLib.ui.widgets.WidgetPosition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class Branch2Screen extends Screen {
    public Branch2Screen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public Class<? extends Screen> getParentScreen() {
        return MainScreen.class;
    }

    @Override
    public void createScreenWidgets() {
        WidgetPosition basePosition = new WidgetPosition(-0.42f, 0.3f);
        
        addButton(Material.DIAMOND, "Подветка 3", () -> {
            getViewer().sendMessage("Ветка 3");
        }, 0, basePosition);

        addButton(Material.EMERALD, "Подветка 4", () -> {
            getViewer().sendMessage("Ветка 4");
        }, 0, new WidgetPosition(-0.42f + 0.15f, 0.3f)); // Горизонтальное смещение
    }
}
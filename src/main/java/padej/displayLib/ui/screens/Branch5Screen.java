package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.widgets.WidgetPosition;
import padej.displayLib.ui.screens.ChangeScreen;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class Branch5Screen extends Screen {
    public Branch5Screen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public Class<? extends Screen> getParentScreen() {
        return MainScreen.class;
    }

    @Override
    public void createScreenWidgets() {
        WidgetPosition basePosition = new WidgetPosition(-0.42f + 0.15f, 0.3f);

        addButton(Material.NETHER_STAR, "Подветка 5.1", () -> {
            ChangeScreen.switchTo(getViewer(), Branch5Screen.class, Branch51Screen.class);
        }, 0, basePosition);
    }
}
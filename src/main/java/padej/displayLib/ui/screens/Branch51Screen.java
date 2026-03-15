package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.screens.ChangeScreen;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class Branch51Screen extends Screen {
    public Branch51Screen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public Class<? extends Screen> getParentScreen() {
        return Branch5Screen.class;
    }

    @Override
    public void createScreenWidgets() {
        addButton(Material.EMERALD, "Подветка 5.1", () -> {
            ChangeScreen.switchTo(getViewer(), Branch5Screen.class, Branch51Screen.class);
        }, 0);
    }
}
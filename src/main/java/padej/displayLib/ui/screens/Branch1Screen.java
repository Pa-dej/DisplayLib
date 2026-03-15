package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.widgets.ItemDisplayButtonConfig;
import padej.displayLib.ui.widgets.WidgetPosition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class Branch1Screen extends Screen {
    public Branch1Screen() {
        super();
    }

    public Branch1Screen(Player viewer, Location location) {
        super(viewer, location);
    }

    public Branch1Screen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public void createScreenWidgets(Player player) {
        WidgetPosition basePosition = new WidgetPosition(-0.42f, 0.3f);
        float step = 0.15f;

        ItemDisplayButtonConfig[] branchButtons = {
                new ItemDisplayButtonConfig(Material.REDSTONE, () -> {
                    player.sendMessage("Ветка 1");
                })
                        .setTooltip("Подветка 1")
                        .setTooltipDelay(30)
                        .setPosition(basePosition.clone()),

                new ItemDisplayButtonConfig(Material.GLOWSTONE_DUST, () -> {
                    player.sendMessage("Ветка 2");
                })
                        .setTooltip("Подветка 2")
                        .setTooltipDelay(30)
                        .setPosition(basePosition.clone().addVertical(step))
        };

        for (ItemDisplayButtonConfig config : branchButtons) {
            createWidget(config);
        }
    }

    @Override
    public Class<? extends Screen> getParentScreen() {
        return MainScreen.class;
    }
}
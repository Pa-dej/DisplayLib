package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.widgets.TextDisplayButtonConfig;
import padej.displayLib.ui.widgets.TextDisplayButtonWidget;
import padej.displayLib.ui.widgets.ItemDisplayButtonConfig;
import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.WidgetPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class Branch3Screen extends Screen {
    private TextDisplayButtonWidget jumpButton;
    private ItemDisplayButtonWidget itemButton;
    private int updateTaskId = -1;
    private final TextColor defaultColor = TextColor.fromHexString("#FFFFFF");
    private final TextColor hoverColor = TextColor.fromHexString("#FCD720");

    public Branch3Screen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public Class<? extends Screen> getParentScreen() {
        return MainScreen.class;
    }

    @Override
    public void createScreenWidgets() {
        Player player = getViewer();
        WidgetPosition basePosition = new WidgetPosition(-0.42f, 0.3f);

        TextDisplayButtonConfig jumpButtonConfig = new TextDisplayButtonConfig(
                Component.text(player.isOnGround() ? "Up" : "Down").color(defaultColor),
                Component.text(player.isOnGround() ? "Up" : "Down").color(hoverColor),
                () -> {
                    double velocityY = player.isOnGround() ? 1.0 : -1.0;
                    player.setVelocity(new Vector(0, velocityY, 0));
                })
                .setPosition(basePosition.clone().addVertical(0.15f).addDepth(-0.01))
                .setBackgroundColor(Color.fromRGB(40, 40, 40))
                .setBackgroundAlpha(150)
                .setHoveredBackgroundColor(Color.fromRGB(60, 60, 60))
                .setHoveredBackgroundAlpha(180);

        jumpButton = createTextWidget(jumpButtonConfig);
        
        ItemDisplayButtonConfig itemButtonConfig = new ItemDisplayButtonConfig(
                player.isOnGround() ? Material.COAL : Material.FEATHER,
                () -> {
                    double velocityY = player.isOnGround() ? 1.0 : -1.0;
                    player.setVelocity(new Vector(0, velocityY, 0));
                })
                .setPosition(basePosition.clone().addHorizontal(0.2).addVertical(0.15f))
                .setScale(0.3f, 0.3f, 0.3f)
                .setTooltip(player.isOnGround() ? "На земле" : "В воздухе");

        itemButton = createWidget(itemButtonConfig);
    }

    @Override
    public void remove() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
        super.remove();
    }
}
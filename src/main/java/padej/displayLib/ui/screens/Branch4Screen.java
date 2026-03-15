package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.widgets.TextDisplayButtonConfig;
import padej.displayLib.ui.widgets.WidgetPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class Branch4Screen extends Screen {
    public Branch4Screen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public Class<? extends Screen> getParentScreen() {
        return MainScreen.class;
    }

    @Override
    public void createScreenWidgets() {
        Player player = getViewer();
        WidgetPosition basePosition = new WidgetPosition(-0.3f, 0.3f);
        WidgetPosition centerPosition = new WidgetPosition(0.1f, -0.1f);
        TextColor defaultColor = TextColor.fromHexString("#FFFFFF");
        TextColor hoverColor = TextColor.fromHexString("#FCD720");

        TextDisplayButtonConfig[] branchButtons = {
                new TextDisplayButtonConfig(
                        Component.text("Выживание").color(defaultColor),
                        Component.text("Выживание").color(hoverColor),
                        () -> player.setGameMode(GameMode.SURVIVAL))
                        .setPosition(basePosition.clone().addDepth(-0.01f))
                        .setTooltip(Component.text("Режим выживания"))
                        .setBackgroundColor(Color.fromRGB(40, 40, 40))
                        .setBackgroundAlpha(150)
                        .setHoveredBackgroundColor(Color.fromRGB(60, 60, 60))
                        .setHoveredBackgroundAlpha(180),

                new TextDisplayButtonConfig(
                        Component.text("Креатив").color(defaultColor),
                        Component.text("Креатив").color(hoverColor),
                        () -> player.setGameMode(GameMode.CREATIVE))
                        .setPosition(basePosition.clone().addVertical(0.15f).addDepth(-0.01f))
                        .setTooltip(Component.text("Творческий режим"))
                        .setBackgroundColor(Color.fromRGB(40, 40, 40))
                        .setBackgroundAlpha(150)
                        .setHoveredBackgroundColor(Color.fromRGB(60, 60, 60))
                        .setHoveredBackgroundAlpha(180),

                new TextDisplayButtonConfig(
                        Component.text("Приключение").color(defaultColor),
                        Component.text("Приключение").color(hoverColor),
                        () -> player.setGameMode(GameMode.ADVENTURE))
                        .setPosition(basePosition.clone().addVertical(0.30f).addDepth(-0.01f))
                        .setTooltip(Component.text("Режим приключения"))
                        .setBackgroundColor(Color.fromRGB(40, 40, 40))
                        .setBackgroundAlpha(150)
                        .setHoveredBackgroundColor(Color.fromRGB(60, 60, 60))
                        .setHoveredBackgroundAlpha(180),

                new TextDisplayButtonConfig(
                        Component.text("Наблюдатель").color(defaultColor),
                        Component.text("Наблюдатель").color(hoverColor),
                        () -> player.setGameMode(GameMode.SPECTATOR))
                        .setPosition(basePosition.clone().addVertical(0.45f).addDepth(-0.01f))
                        .setTooltip(Component.text("Режим наблюдателя"))
                        .setBackgroundColor(Color.fromRGB(40, 40, 40))
                        .setBackgroundAlpha(150)
                        .setHoveredBackgroundColor(Color.fromRGB(60, 60, 60))
                        .setHoveredBackgroundAlpha(180),

                new TextDisplayButtonConfig(
                        Component.text("1111111111111111111").color(defaultColor),
                        Component.text("1111111111111111111").color(hoverColor),
                        () -> player.sendMessage("1"))
                        .setPosition(centerPosition.clone().addVertical(0.45f).addDepth(-0.01f))
                        .setScale(.5f, .5f, .5f)
                        .setTranslation(new Vector3f(0, -0.25f, 0))
                        .setMaxLineWidth(30)
                        .setTolerance(.2f, .17f)
                        .setBackgroundColor(Color.fromRGB(40, 40, 40))
                        .setBackgroundAlpha(150)
                        .setHoveredBackgroundColor(Color.fromRGB(60, 60, 60))
                        .setHoveredBackgroundAlpha(180)
                        .setHoveredTransformation(new Transformation(
                        new Vector3f(0, -0.25f, 0),
                        new AxisAngle4f(),
                        new Vector3f(0.2f, 0.2f, 0.2f),
                        new AxisAngle4f()
                ), 5)
        };

        for (TextDisplayButtonConfig config : branchButtons) {
            createTextWidget(config);
        }
    }
}
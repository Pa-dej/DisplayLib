package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.annotations.Main;
import padej.displayLib.ui.widgets.ItemDisplayButtonConfig;
import padej.displayLib.ui.widgets.WidgetPosition;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

@Main
public class MainScreen extends Screen {
    public MainScreen() {
        super(); // Используем конструктор для временных экранов
    }

    public MainScreen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public void createScreenWidgets(Player player) {
        // Создаем кнопки ветвления
        WidgetPosition basePosition = new WidgetPosition(-0.42f, 0.3f);
        float step = 0.17f;

        ItemDisplayButtonConfig[] branchButtons = {
                new ItemDisplayButtonConfig(Material.COMPASS, () -> {
                    ChangeScreen.switchTo(player, MainScreen.class, Branch1Screen.class);
                })
                        .setTooltip("Ветка 1")
                        .setTooltipDelay(30)
                        .setPosition(basePosition.clone())
                        .setHoveredTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(),
                        new Vector3f(0.2f, 0.2f, 0.15f),
                        new AxisAngle4f()
                ), 2),

                new ItemDisplayButtonConfig(Material.MAP, () -> {
                    ChangeScreen.switchTo(player, MainScreen.class, Branch2Screen.class);
                })
                        .setTooltip("Ветка 2")
                        .setTooltipDelay(30)
                        .setPosition(basePosition.clone().addVertical(step))
                        .setHoveredTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(),
                        new Vector3f(0.2f, 0.2f, 0.15f),
                        new AxisAngle4f()
                ), 2),

                new ItemDisplayButtonConfig(Material.PLAYER_HEAD, () -> {
                    ChangeScreen.switchTo(player, MainScreen.class, Branch3Screen.class);
                })
                        .setDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI)
                        .setGlowColor(Color.RED)
                        .setTooltip("Ветка 3")
                        .setTooltipDelay(30)
                        .setPosition(basePosition.clone().addVertical(step * 2).addDepth(-0.02))
                        .setHoveredTransformation(new Transformation(
                                new Vector3f(0, 0, 0),
                                new AxisAngle4f(),
                                new Vector3f(0.2f, 0.2f, 0.15f),
                                new AxisAngle4f()
                        ), 2)
                        .setItemMeta((meta) -> {
                    SkullMeta skullMeta = (SkullMeta) meta;
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer("Padej_"));
                    return meta;
                }),

                new ItemDisplayButtonConfig(Material.AMETHYST_SHARD, () -> {
                    ChangeScreen.switchTo(player, MainScreen.class, Branch4Screen.class);
                })
                        .setTooltip("Ветка 4")
                        .setTooltipDelay(30)
                        .setPosition(basePosition.clone().addVertical(step * 3))
                        .setHoveredTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(),
                        new Vector3f(0.2f, 0.2f, 0.15f),
                        new AxisAngle4f()
                ), 2),

                new ItemDisplayButtonConfig(Material.GOLD_INGOT, () -> {
                    ChangeScreen.switchTo(player, MainScreen.class, Branch5Screen.class);
                })
                        .setPosition(basePosition.clone().addVertical(-step))
                        .setHoveredTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(),
                        new Vector3f(0.2f, 0.2f, 0.15f),
                        new AxisAngle4f()
                ), 2)
        };

        for (ItemDisplayButtonConfig config : branchButtons) {
            createWidget(config);
        }
    }
}
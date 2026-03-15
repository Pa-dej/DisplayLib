package padej.displayLib.ui.screens;

import padej.displayLib.ui.Screen;
import padej.displayLib.ui.annotations.Main;
import padej.displayLib.ui.widgets.ItemDisplayButtonWidget;
import padej.displayLib.ui.widgets.Widget;
import padej.displayLib.ui.widgets.WidgetPosition;
import padej.displayLib.ui.screens.ChangeScreen;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

@Main
public class MainScreen extends Screen {
    public MainScreen(Player viewer, Location location, String text, float scale) {
        super(viewer, location, text, scale);
    }

    @Override
    public void createScreenWidgets() {
        // Создаем кнопки ветвления с упрощенным API
        addButton(Material.COMPASS, "Ветка 1", () -> {
            ChangeScreen.switchTo(getViewer(), MainScreen.class, Branch1Screen.class);
        }, 0);

        addButton(Material.MAP, "Ветка 2", () -> {
            ChangeScreen.switchTo(getViewer(), MainScreen.class, Branch2Screen.class);
        }, 1);

        addButton(Material.PLAYER_HEAD, "Ветка 3", () -> {
            ChangeScreen.switchTo(getViewer(), MainScreen.class, Branch3Screen.class);
        }, 2);

        addButton(Material.AMETHYST_SHARD, "Ветка 4", () -> {
            ChangeScreen.switchTo(getViewer(), MainScreen.class, Branch4Screen.class);
        }, 3);

        // Золотой слиток выше остальных (индекс -1)
        addButton(Material.GOLD_INGOT, "Ветка 5", () -> {
            ChangeScreen.switchTo(getViewer(), MainScreen.class, Branch5Screen.class);
        }, -1);

        // Настраиваем специальные свойства для головы игрока
        customizePlayerHead();
    }

    private void customizePlayerHead() {
        // Находим виджет с головой игрока и настраиваем его
        for (Widget widget : getChildren()) {
            if (widget instanceof ItemDisplayButtonWidget) {
                ItemDisplayButtonWidget itemWidget = (ItemDisplayButtonWidget) widget;
                if (itemWidget.getDisplay() != null && 
                    itemWidget.getDisplay().getItemStack() != null &&
                    itemWidget.getDisplay().getItemStack().getType() == Material.PLAYER_HEAD) {
                    
                    // Устанавливаем свечение
                    itemWidget.getDisplay().setGlowColorOverride(Color.RED);
                    
                    // Устанавливаем владельца головы
                    ItemStack headItem = itemWidget.getDisplay().getItemStack();
                    if (headItem.getItemMeta() instanceof SkullMeta) {
                        SkullMeta skullMeta = (SkullMeta) headItem.getItemMeta();
                        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer("Padej_"));
                        headItem.setItemMeta(skullMeta);
                        itemWidget.getDisplay().setItemStack(headItem);
                    }
                    break;
                }
            }
        }
    }
}
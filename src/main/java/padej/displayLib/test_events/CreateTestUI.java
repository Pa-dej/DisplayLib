package padej.displayLib.test_events;

import padej.displayLib.utils.ItemUtil;
import padej.displayLib.ui.UIManager;
import padej.displayLib.ui.WidgetManager;
import padej.displayLib.ui.Screen;
import padej.displayLib.ui.screens.MainScreen;
import padej.displayLib.utils.Animation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class CreateTestUI implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (player.getInventory().getItemInMainHand().getType() == Material.BLUE_DYE &&
                ItemUtil.isExperimental(player.getInventory().getItemInMainHand()) &&
                event.getAction().isRightClick()) {

            // Правильно удаляем существующий экран
            UIManager uiManager = UIManager.getInstance();
            WidgetManager existingManager = uiManager.getActiveScreen(player);
            if (existingManager != null) {
                if (existingManager instanceof Screen) {
                    ((Screen) existingManager).remove(true);
                } else {
                    existingManager.remove();
                }
                uiManager.unregisterScreen(player);
            }

            Location spawnLocation = player.getLocation()
                    .add(0, player.getHeight() / 2, 0)
                    .add(player.getLocation().getDirection().multiply(2));

            MainScreen mainScreen = new MainScreen(player, spawnLocation, " ", 10.0f);

            mainScreen.createWithAnimation(player);
            
            player.sendMessage("§a[DisplayLib] §7Создан полный UI! Используй ПКМ для взаимодействия с кнопками!");
        }
    }

    // Удален дублированный обработчик onPlayerQuit - теперь UIManager полностью отвечает за cleanup
}
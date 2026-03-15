package padej.displayLib.test_events;

import padej.displayLib.utils.ItemUtil;
import padej.displayLib.ui.UIManager;
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

        // Создание UI с полной системой экранов
        if (player.getInventory().getItemInMainHand().getType() == Material.BLUE_DYE &&
                ItemUtil.isExperimental(player.getInventory().getItemInMainHand()) &&
                event.getAction().isRightClick()) {

            // Удаляем существующий UI если есть
            UIManager.getInstance().unregisterScreen(player);

            // Создаем новый UI с MainScreen точно так же, как в ChangeScreen
            Location spawnLocation = player.getLocation()
                    .add(0, player.getHeight() / 2, 0)
                    .add(player.getLocation().getDirection().multiply(2));

            // Создаем MainScreen с теми же параметрами, что и в ChangeScreen.switchTo
            MainScreen mainScreen = new MainScreen(player, spawnLocation, " ", 10.0f);
            
            // Используем createWithAnimation для консистентности с ChangeScreen
            mainScreen.createWithAnimation(player);
            
            player.sendMessage("§a[DisplayLib] §7Создан полный UI! Используй ПКМ для взаимодействия с кнопками!");
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UIManager.getInstance().unregisterScreen(player);
    }
}
package padej.displayLib.test_events;

import padej.displayLib.utils.ItemUtil;
import padej.displayLib.ui.UIManager;
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

            // Открываем главное меню через новую систему
            UIManager uiManager = UIManager.getInstance();
            boolean success = uiManager.openScreen(player, "main_menu");
            
            if (success) {
                player.sendMessage("§a[DisplayLib] §7Создан UI из YAML! Используй ПКМ для взаимодействия с кнопками!");
            } else {
                player.sendMessage("§c[DisplayLib] §7Не удалось загрузить экран main_menu. Проверьте YAML файлы.");
            }
        }
    }

    // Удален дублированный обработчик onPlayerQuit - теперь UIManager полностью отвечает за cleanup
}
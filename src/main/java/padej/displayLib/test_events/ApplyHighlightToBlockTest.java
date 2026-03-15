package padej.displayLib.test_events;

import padej.displayLib.render.HighlightStyle;
import padej.displayLib.render.shapes.Highlight;
import padej.displayLib.utils.ItemUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class ApplyHighlightToBlockTest implements Listener {

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (player.getInventory().getItemInMainHand().getType() == Material.COAL && ItemUtil.isExperimental(player.getInventory().getItemInMainHand())) {
            
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && player.getCooldown(Material.COAL) < 1) {
                Location location = clickedBlock.getLocation();

                String blockPosKey = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
                if (Highlight.blockPosDisplays.containsKey(blockPosKey)) {
                    Highlight.removeSelectionOnBlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                } else {
                    Highlight.createSides(location, HighlightStyle.BRONZE, 200);
                }
                player.setCooldown(Material.COAL, 1);
            }
        }
    }
}
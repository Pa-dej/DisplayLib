package padej.displayLib.test_events;

import padej.displayLib.render.particles.ExampleSquareDisplayParticle;
import padej.displayLib.utils.ItemUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class CreateDisplayParticleFirstTest implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() == Material.WHEAT && ItemUtil.isExperimental(player.getInventory().getItemInMainHand())) {
            Location eventLocation = event.getInteractionPoint();
            Location spawnLocation = (eventLocation != null)
                    ? eventLocation
                    : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3));
            Action action = event.getAction();

            if (action.isRightClick()) {
                new ExampleSquareDisplayParticle(spawnLocation).spawn();
            }
        }
    }
}
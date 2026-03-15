package padej.displayLib.test_events;

import padej.displayLib.DisplayLib;
import padej.displayLib.render.particles.PoisonDisplayParticle;
import padej.displayLib.render.shapes.StringRectangle;
import padej.displayLib.utils.ItemUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;

public class CreateDisplayParticleThirdTest implements Listener {
    private final Map<Player, StringRectangle> playerCubes = new HashMap<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() == Material.TURTLE_SCUTE && ItemUtil.isExperimental(player.getInventory().getItemInMainHand())) {
            Location spawnLocation = event.getInteractionPoint();
            Action action = event.getAction();
            if (spawnLocation == null) return;

            if (action.isRightClick()) {
                if (playerCubes.containsKey(player)) {
                    StringRectangle rectangle = playerCubes.get(player);
                    TextDisplay removedDisplay = rectangle.spawn(spawnLocation);

                    if (removedDisplay == null) {
                        playerCubes.remove(player);
                    }
                } else {
                    PoisonDisplayParticle particle = new PoisonDisplayParticle(player.getLocation());
                    DisplayLib.DISPLAY_PARTICLES.add(particle);
                }
            }
        }
    }
}
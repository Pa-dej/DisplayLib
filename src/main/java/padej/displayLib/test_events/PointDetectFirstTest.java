package padej.displayLib.test_events;

import padej.displayLib.DisplayLib;
import padej.displayLib.render.shapes.DefaultCube;
import padej.displayLib.utils.AlignmentType;
import padej.displayLib.utils.ItemUtil;
import padej.displayLib.utils.PointDetection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class PointDetectFirstTest implements Listener {
    private final Map<Player, DefaultCube> playerCubes = new HashMap<>();
    private final Map<Player, BukkitRunnable> playerTasks = new HashMap<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() == Material.SPECTRAL_ARROW && ItemUtil.isExperimental(player.getInventory().getItemInMainHand())) {
            Location spawnLocation = event.getInteractionPoint();
            Action action = event.getAction();
            if (spawnLocation == null) return;

            if (action.isRightClick()) {
                if (playerCubes.containsKey(player)) {
                    DefaultCube cube = playerCubes.get(player);
                    BlockDisplay removedDisplay = cube.spawn(spawnLocation);

                    if (removedDisplay == null) {
                        playerCubes.remove(player);
                        stopPlayerTask(player);
                    }
                } else {
                    DefaultCube cube = new DefaultCube(1,
                            Material.WHITE_STAINED_GLASS.createBlockData(),
                            AlignmentType.CENTER) {};
                    cube.spawn(spawnLocation);
                    playerCubes.put(player, cube);

                    startPlayerTask(player, cube, player);
                }
            }
        }
    }

    private void startPlayerTask(Player player, DefaultCube cube, Player viewer) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (cube.getBlockDisplay() == null) return;

                Vector eye = viewer.getEyeLocation().toVector();
                Vector direction = viewer.getEyeLocation().getDirection();
                Vector point = cube.getBlockDisplay().getLocation().toVector();
                boolean isDetect = PointDetection.lookingAtPoint(eye, direction, point, .55);

                cube.getBlockDisplay().setBlock(isDetect ? Material.RED_STAINED_GLASS.createBlockData() : Material.WHITE_STAINED_GLASS.createBlockData());
            }
        };
        task.runTaskTimer(DisplayLib.getInstance(), 0, 1);
        playerTasks.put(player, task);
    }

    private void stopPlayerTask(Player player) {
        BukkitRunnable task = playerTasks.get(player);
        if (task != null) {
            task.cancel();
            playerTasks.remove(player);
        }
    }
}
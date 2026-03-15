package padej.displayLib.test_events;

import padej.displayLib.DisplayLib;
import padej.displayLib.render.shapes.DefaultCube;
import padej.displayLib.utils.AlignmentType;
import padej.displayLib.utils.Animation;
import padej.displayLib.utils.ItemUtil;
import padej.displayLib.utils.PointDetection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class PointDetectSecondTest implements Listener {
    private final Map<Player, DefaultCube[]> playerCubes = new HashMap<>();
    private final Map<Player, BukkitRunnable> playerTasks = new HashMap<>();
    private final Map<Player, Boolean> playerLookingState = new HashMap<>();
    private final Map<Player, Boolean> isDragging = new HashMap<>();
    private final Map<Player, Double> dragDistance = new HashMap<>();
    private final Map<Player, Vector> initialDirection = new HashMap<>();
    private final Map<Player, Location> initialLocation = new HashMap<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Location spawnLocation = event.getInteractionPoint();
        Action action = event.getAction();

        if (action.isLeftClick() && event.getHand() == EquipmentSlot.HAND) {
            if (playerCubes.containsKey(player)) {
                DefaultCube[] cubes = playerCubes.get(player);
                Vector eye = player.getEyeLocation().toVector();
                Vector direction = player.getEyeLocation().getDirection();
                Vector point = cubes[0].getBlockDisplay().getLocation().toVector();

                if (PointDetection.lookingAtPoint(eye, direction, point, .1)) {
                    event.setCancelled(true);
                    player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f);
                    
                    boolean currentDragging = isDragging.getOrDefault(player, false);
                    if (!currentDragging) {
                        isDragging.put(player, true);
                        dragDistance.put(player, eye.distance(point));
                        cubes[0].getBlockDisplay().setBlock(Material.EMERALD_BLOCK.createBlockData());
                        cubes[1].getBlockDisplay().setBlock(Material.LIME_STAINED_GLASS.createBlockData());
                    } else {
                        isDragging.put(player, false);
                        cubes[0].getBlockDisplay().setBlock(Material.IRON_BLOCK.createBlockData());
                        cubes[1].getBlockDisplay().setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
                    }
                }
            }
            return;
        }

        if (player.getInventory().getItemInMainHand().getType() == Material.SPECTRAL_ARROW && ItemUtil.isExperimental(player.getInventory().getItemInMainHand()) && action.isRightClick()) {

            if (spawnLocation == null) return;

            if (playerCubes.containsKey(player)) {
                DefaultCube[] cubes = playerCubes.get(player);
                BlockDisplay removedDisplay = cubes[0].spawn(spawnLocation);
                BlockDisplay removedOutlineDisplay = cubes[1].spawn(spawnLocation);

                if (removedDisplay == null && removedOutlineDisplay == null) {
                    playerCubes.remove(player);
                    stopPlayerTask(player);
                    initialDirection.remove(player);
                    initialLocation.remove(player);
                }
            } else {
                DefaultCube cube = new DefaultCube(0.15f,
                        Material.IRON_BLOCK.createBlockData(),
                        AlignmentType.CENTER) {};
                cube.spawn(spawnLocation);

                DefaultCube outlineCube = new DefaultCube(0.2f,
                        Material.WHITE_STAINED_GLASS.createBlockData(),
                        AlignmentType.CENTER) {};
                outlineCube.spawn(spawnLocation);

                initialDirection.put(player, player.getEyeLocation().getDirection().clone());
                initialLocation.put(player, spawnLocation.clone());
                
                playerCubes.put(player, new DefaultCube[]{cube, outlineCube});
                startPlayerTask(player, outlineCube, player);
            }
        }
    }

    private void startPlayerTask(Player player, DefaultCube cube, Player viewer) {
        Vector translation = Vector.fromJOML(cube.getTransformation().getTranslation());
        float defaultScale = cube.getScale();
        float growScale = .24f;
        float scaleStep = (growScale - defaultScale) / 2;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (cube.getBlockDisplay() == null) return;

                // Отображаем частицы вдоль вектора
                Location initialLoc = initialLocation.get(player);
                Vector initialDir = initialDirection.get(player);
                if (initialLoc != null && initialDir != null) {
                    // Начальная точка (на 5 блоков назад от центра)
                    Vector startPoint = initialLoc.toVector().clone().add(initialDir.clone().multiply(-5));
                    // Рисуем частицы на протяжении 10 блоков
                    for (double i = 0; i <= 10; i += 0.25) {
                        Vector particlePos = startPoint.clone().add(initialDir.clone().multiply(i));
                        initialLoc.getWorld().spawnParticle(
                            org.bukkit.Particle.BUBBLE_POP,
                            particlePos.getX(),
                            particlePos.getY(),
                            particlePos.getZ(),
                            1, 0, 0, 0, 0
                        );
                    }
                }

                Vector eye = viewer.getEyeLocation().toVector();
                Vector direction = viewer.getEyeLocation().getDirection();
                Vector point = cube.getBlockDisplay().getLocation().toVector();
                boolean isDetect = PointDetection.lookingAtPoint(eye, direction, point, .1);
                boolean wasLooking = playerLookingState.getOrDefault(player, false);

                if (isDragging.getOrDefault(player, false)) {
                    Vector moveDir = initialDirection.get(player);
                    Location moveStart = initialLocation.get(player);
                    
                    Vector currentPos = eye.clone().add(direction.clone().multiply(dragDistance.get(player)));
                    Vector initialToCurrentPos = currentPos.clone().subtract(moveStart.toVector());
                    double projection = initialToCurrentPos.dot(moveDir) / moveDir.lengthSquared();
                    
                    // Ограничиваем проекцию в пределах ±5 блоков
                    projection = Math.max(-5.0, Math.min(5.0, projection));
                    
                    Vector newPosition = moveStart.toVector().clone().add(moveDir.clone().multiply(projection));
                    
                    DefaultCube[] cubes = playerCubes.get(player);
                    Location newLocation = newPosition.toLocation(player.getWorld());

                    cubes[0].getBlockDisplay().teleport(newLocation);
                    cubes[1].getBlockDisplay().teleport(newLocation);
                }

                if (isDetect && !wasLooking) {
                    Animation.applyTransformationWithInterpolation(cube.getBlockDisplay(), new Matrix4f()
                            .translate((float) (translation.getX() - scaleStep), (float) (translation.getY() - scaleStep), (float) (translation.getZ() - scaleStep))
                            .scale(growScale)
                    );
                    playerLookingState.put(player, true);
                } else if (!isDetect && wasLooking) {
                    Animation.applyTransformationWithInterpolation(cube.getBlockDisplay(), new Matrix4f()
                            .translate((float) (translation.getX()), (float) (translation.getY()), (float) (translation.getZ()))
                            .scale(defaultScale)
                    );
                    playerLookingState.put(player, false);
                }
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
        playerLookingState.remove(player);
        isDragging.remove(player);
        dragDistance.remove(player);
        initialDirection.remove(player);
        initialLocation.remove(player);
    }
}
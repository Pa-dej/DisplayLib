package padej.displayLib.test_events;

import padej.displayLib.DisplayLib;
import padej.displayLib.render.shapes.DefaultCube;
import padej.displayLib.utils.AlignmentType;
import padej.displayLib.utils.ItemUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RotationRelativeToCenterPointTest implements Listener {
    private final Map<Player, DefaultCube> playerCubes = new HashMap<>();
    private final Map<Player, BukkitRunnable> playerTasks = new HashMap<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() == Material.IRON_INGOT && ItemUtil.isExperimental(player.getInventory().getItemInMainHand())) {
            Location spawnLocation = event.getInteractionPoint();
            Action action = event.getAction();
            if (spawnLocation == null) return;

            if (action.isRightClick()) {
                if (playerCubes.containsKey(player)) {
                    DefaultCube cube = playerCubes.get(player);
                    BlockDisplay removedDisplay = cube.spawn(spawnLocation);

                    if (removedDisplay == null) {
                        playerCubes.remove(player);
                    }
                } else {
                    DefaultCube cube = new DefaultCube(1 + 1e-3f,
                            Material.YELLOW_STAINED_GLASS.createBlockData(),
                            AlignmentType.CENTER) {};
                    cube.spawn(spawnLocation);
                    startSpin(player);
                    playerCubes.put(player, cube);
                }
            }
        }
    }

    private void startSpin(Player player) {
        final float rotationSpeed = 0.035f; // Скорость вращения

        BukkitRunnable task = new BukkitRunnable() {
            private final boolean CLOCKWISE = new Random().nextBoolean(); // Случайное направление вращения

            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopSpin(player);
                    return;
                }
                DefaultCube cube = playerCubes.get(player);
                if (cube != null) {
                    BlockDisplay blockDisplay = cube.getBlockDisplay();
                    if (blockDisplay != null) {
                        float rotationAmount = CLOCKWISE ? rotationSpeed : -rotationSpeed;

                        // Получаем текущее положение и трансформацию
                        Transformation currentTransform = cube.getTransformation();
                        Vector3f translation = new Vector3f(currentTransform.getTranslation()); // Текущая позиция
                        Vector3f scale = new Vector3f(currentTransform.getScale()); // Размер
                        Quaternionf leftRotation = new Quaternionf(currentTransform.getLeftRotation());
                        Quaternionf rightRotation = new Quaternionf(currentTransform.getRightRotation());

                        // Создаем новые повороты вокруг центра объекта
                        Quaternionf rotation = new Quaternionf().rotateXYZ(rotationAmount, rotationAmount * 0.5f, rotationAmount * 1.5f);
                        leftRotation.mul(rotation);
                        rightRotation.mul(rotation);

                        // Обновляем трансформацию с учетом позиции как центра вращения
                        blockDisplay.setTransformation(new Transformation(
                                translation, // translation используется как центр вращения
                                leftRotation,
                                scale,
                                rightRotation
                        ));
                    }
                }
            }
        };

        task.runTaskTimer(DisplayLib.getInstance(), 0, 1);
        playerTasks.put(player, task);
    }

    private void stopSpin(Player player) {
        BukkitRunnable task = playerTasks.get(player);
        if (task != null) {
            task.cancel();
            playerTasks.remove(player);
        }
    }
}
package padej.displayLib.test_events;

import padej.displayLib.DisplayLib;
import padej.displayLib.render.shapes.DefaultCube;
import padej.displayLib.utils.AlignmentType;
import padej.displayLib.utils.Animation;
import padej.displayLib.utils.ItemUtil;
import padej.displayLib.utils.PointDetection;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Sound;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.*;

public class GizmoTest implements Listener {
    private final Map<Player, Map<Entity, GizmoData>> playerGizmos = new HashMap<>();
    private final Map<Player, BukkitRunnable> updateTasks = new HashMap<>();

    private static class GizmoData {
        DefaultCube mainGizmo;
        DefaultCube outlineGizmo;
        Entity attachedEntity;
        boolean isDragging;
        double dragDistance;
        boolean isLooking;

        public GizmoData(DefaultCube mainGizmo, DefaultCube outlineGizmo, Entity attachedEntity) {
            this.mainGizmo = mainGizmo;
            this.outlineGizmo = outlineGizmo;
            this.attachedEntity = attachedEntity;
            this.isDragging = false;
            this.dragDistance = 0;
            this.isLooking = false;
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        Player player = event.getPlayer();
        if (!(player.getInventory().getItemInMainHand().getType() == Material.CLAY_BALL && ItemUtil.isExperimental(player.getInventory().getItemInMainHand()))) return;

        // Если у игрока уже есть гизмо, удаляем их
        if (playerGizmos.containsKey(player)) {
            removeAllGizmos(player);
            return;
        }

        // Получаем все сущности в радиусе 15 блоков
        Collection<Entity> nearbyEntities = player.getWorld().getNearbyEntities(
            player.getLocation(),
            15, 15, 15,
            entity -> {
                // Проверяем, что это не игрок-создатель
                if (entity.equals(player)) {
                    return false;
                }
                
                // Проверяем, не является ли сущность частью гизмо
                for (Map<Entity, GizmoData> gizmos : playerGizmos.values()) {
                    for (GizmoData gizmoData : gizmos.values()) {
                        if (gizmoData.mainGizmo.getBlockDisplay().equals(entity) ||
                            gizmoData.outlineGizmo.getBlockDisplay().equals(entity)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        );

        Map<Entity, GizmoData> entityGizmos = new HashMap<>();

        // Создаем гизмо для каждой найденной сущности
        for (Entity entity : nearbyEntities) {
            Location spawnLocation = entity.getLocation().add(0, entity.getHeight() / 2, 0);
            
            DefaultCube mainCube = new DefaultCube(0.15f,
                    Material.IRON_BLOCK.createBlockData(),
                    AlignmentType.CENTER) {};
            mainCube.spawn(spawnLocation);
            mainCube.getBlockDisplay().setGlowing(true);
            mainCube.getBlockDisplay().setGlowColorOverride(Color.WHITE);
            mainCube.getBlockDisplay().setVisibleByDefault(false);
            player.showEntity(DisplayLib.getInstance(), mainCube.getBlockDisplay());

            DefaultCube outlineCube = new DefaultCube(0.2f,
                    Material.WHITE_STAINED_GLASS.createBlockData(),
                    AlignmentType.CENTER) {};
            outlineCube.spawn(spawnLocation);
            outlineCube.getBlockDisplay().setVisibleByDefault(false);
            player.showEntity(DisplayLib.getInstance(), outlineCube.getBlockDisplay());

            GizmoData gizmoData = new GizmoData(mainCube, outlineCube, entity);
            entityGizmos.put(entity, gizmoData);
        }

        if (!entityGizmos.isEmpty()) {
            playerGizmos.put(player, entityGizmos);
            startUpdateTask(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeAllGizmos(event.getPlayer());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        for (Map.Entry<Player, Map<Entity, GizmoData>> entry : playerGizmos.entrySet()) {
            if (entry.getValue().containsKey(entity)) {
                removeGizmo(entry.getKey(), entity);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action.isLeftClick() && event.getHand() == EquipmentSlot.HAND) {
            Map<Entity, GizmoData> gizmos = playerGizmos.get(player);
            if (gizmos == null) return;

            Vector eye = player.getEyeLocation().toVector();
            Vector direction = player.getEyeLocation().getDirection();

            for (GizmoData gizmoData : gizmos.values()) {
                Vector point = gizmoData.mainGizmo.getBlockDisplay().getLocation().toVector();
                // Получаем текущий размер из трансформации
                float currentScale = gizmoData.mainGizmo.getBlockDisplay().getTransformation().getScale().x;
                
                if (PointDetection.lookingAtPoint(eye, direction, point, 0.1 + (currentScale * 0.6))) {
                    event.setCancelled(true);
                    player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 2.0f);
                    
                    if (!gizmoData.isDragging) {
                        // Начинаем перетаскивание
                        gizmoData.isDragging = true;
                        gizmoData.mainGizmo.getBlockDisplay().setBlock(Material.EMERALD_BLOCK.createBlockData());
                        gizmoData.mainGizmo.getBlockDisplay().setGlowColorOverride(Color.LIME);
                        gizmoData.outlineGizmo.getBlockDisplay().setBlock(Material.LIME_STAINED_GLASS.createBlockData());
                        
                        // Сохраняем расстояние для перетаскивания
                        gizmoData.dragDistance = eye.distance(point);
                    } else {
                        // Заканчиваем перетаскивание
                        gizmoData.isDragging = false;
                        gizmoData.mainGizmo.getBlockDisplay().setBlock(Material.IRON_BLOCK.createBlockData());
                        gizmoData.mainGizmo.getBlockDisplay().setGlowColorOverride(Color.WHITE);
                        gizmoData.outlineGizmo.getBlockDisplay().setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
                    }
                    break;
                }
            }
        }
    }

    private void startUpdateTask(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Map<Entity, GizmoData> gizmos = playerGizmos.get(player);
                if (gizmos == null) return;

                Iterator<Map.Entry<Entity, GizmoData>> iterator = gizmos.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Entity, GizmoData> entry = iterator.next();
                    Entity entity = entry.getKey();
                    GizmoData gizmoData = entry.getValue();

                    if (!entity.isValid() || entity.isDead()) {
                        removeGizmo(player, entity);
                        iterator.remove();
                        continue;
                    }

                    // Вычисляем размер гизмо на основе расстояния
                    double distance = player.getLocation().distance(entity.getLocation());
                    double maxDistance = 20.0;
                    double maxSizeIncrease = 5.0;
                    
                    // Ограничиваем расстояние и вычисляем коэффициент увеличения
                    distance = Math.min(distance, maxDistance);
                    double sizeMultiplier = distance / maxDistance;
                    double sizeIncrease = sizeMultiplier * maxSizeIncrease;
                    
                    // Базовые размеры + динамическое увеличение
                    float baseMainScale = 0.15f;
                    float baseOutlineScale = 0.2f;
                    float mainScale = (float)(baseMainScale * (1 + sizeIncrease));
                    float outlineScale = (float)(baseOutlineScale * (1 + sizeIncrease));
                    
                    // Обновляем размеры кубов
                    gizmoData.mainGizmo.getBlockDisplay().setTransformationMatrix(
                        new Matrix4f()
                            .translate(-mainScale / 2, -mainScale / 2, -mainScale / 2)
                            .scale(mainScale)
                    );

                    // Проверяем, смотрит ли игрок на гизмо
                    Vector eye = player.getEyeLocation().toVector();
                    Vector direction = player.getEyeLocation().getDirection();
                    Vector point = gizmoData.mainGizmo.getBlockDisplay().getLocation().toVector();
                    // Добавляем 0.1 от текущего размера к базовому радиусу обнаружения
                    boolean isDetect = PointDetection.lookingAtPoint(eye, direction, point, 0.1 + (mainScale * 0.6));

                    // Используем динамический вектор для трансформации
                    Vector translation = new Vector(-outlineScale / 2, -outlineScale / 2, -outlineScale / 2);
                    float growScale = outlineScale * 1.2f;
                    float scaleStep = (growScale - outlineScale) / 2;

                    // Обновляем анимацию при наведении
                    if (isDetect && !gizmoData.isLooking) {
                        Animation.applyTransformationWithInterpolation(gizmoData.outlineGizmo.getBlockDisplay(), 
                            new Matrix4f()
                                .translate((float)(translation.getX() - scaleStep), 
                                         (float)(translation.getY() - scaleStep), 
                                         (float)(translation.getZ() - scaleStep))
                                .scale(growScale)
                        );
                        gizmoData.isLooking = true;
                    } else if (!isDetect && gizmoData.isLooking) {
                        Animation.applyTransformationWithInterpolation(gizmoData.outlineGizmo.getBlockDisplay(), 
                            new Matrix4f()
                                .translate((float)translation.getX(), 
                                         (float)translation.getY(), 
                                         (float)translation.getZ())
                                .scale(outlineScale)
                        );
                        gizmoData.isLooking = false;
                    } else if (!isDetect) {
                        // Обновляем размер даже когда не смотрим
                        gizmoData.outlineGizmo.getBlockDisplay().setTransformationMatrix(
                            new Matrix4f()
                                .translate((float)translation.getX(), 
                                         (float)translation.getY(), 
                                         (float)translation.getZ())
                                .scale(outlineScale)
                        );
                    }

                    if (gizmoData.isDragging) {
                        // Перемещаем гизмо в направлении взгляда игрока
                        Vector newPosition = eye.add(direction.multiply(gizmoData.dragDistance));
                        Location newLocation = newPosition.toLocation(player.getWorld());
                        gizmoData.mainGizmo.getBlockDisplay().teleport(newLocation);
                        gizmoData.outlineGizmo.getBlockDisplay().teleport(newLocation);
                        entity.teleport(newLocation);
                    } else {
                        // Если не перетаскиваем, гизмо следует за сущностью на высоте её центра
                        Location entityLocation = entity.getLocation().add(0, entity.getHeight() / 2, 0);
                        gizmoData.mainGizmo.getBlockDisplay().teleport(entityLocation);
                        gizmoData.outlineGizmo.getBlockDisplay().teleport(entityLocation);
                    }
                }
            }
        };
        task.runTaskTimer(DisplayLib.getInstance(), 0, 1);
        updateTasks.put(player, task);
    }

    private void removeGizmo(Player player, Entity entity) {
        Map<Entity, GizmoData> gizmos = playerGizmos.get(player);
        if (gizmos != null) {
            GizmoData gizmoData = gizmos.get(entity);
            if (gizmoData != null) {
                if (gizmoData.mainGizmo.getBlockDisplay() != null) {
                    gizmoData.mainGizmo.getBlockDisplay().remove();
                }
                if (gizmoData.outlineGizmo.getBlockDisplay() != null) {
                    gizmoData.outlineGizmo.getBlockDisplay().remove();
                }
                gizmos.remove(entity);
            }
            if (gizmos.isEmpty()) {
                playerGizmos.remove(player);
                stopUpdateTask(player);
            }
        }
    }

    private void removeAllGizmos(Player player) {
        Map<Entity, GizmoData> gizmos = playerGizmos.get(player);
        if (gizmos != null) {
            for (GizmoData gizmoData : gizmos.values()) {
                if (gizmoData.mainGizmo.getBlockDisplay() != null) {
                    gizmoData.mainGizmo.getBlockDisplay().remove();
                }
                if (gizmoData.outlineGizmo.getBlockDisplay() != null) {
                    gizmoData.outlineGizmo.getBlockDisplay().remove();
                }
            }
            playerGizmos.remove(player);
            stopUpdateTask(player);
        }
    }

    private void stopUpdateTask(Player player) {
        BukkitRunnable task = updateTasks.remove(player);
        if (task != null) {
            task.cancel();
        }
    }
}
package padej.displayLib.ui.widgets;

import padej.displayLib.DisplayLib;
import padej.displayLib.utils.Animation;
import padej.displayLib.utils.PointDetection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.time.Duration;

public class ItemDisplayButtonWidget implements Widget {
    private ItemDisplay display;
    private Player viewer;
    private boolean isHovered = false;
    private Runnable onClick;
    private Component tooltip;
    private boolean isShowingTooltip = false;
    private int tooltipDelay = 0;
    private int hoverTicks = 0;
    private Material itemType;
    private Location location;
    private TextColor tooltipColor = TextColor.fromHexString("#868788");
    private float scaleX = .15f;
    private float scaleY = .15f;
    private float scaleZ = 1e-6f;
    private double horizontalTolerance = 0.06;
    private double verticalTolerance = 0.06;
    private WidgetPosition position;
    private ItemDisplay.ItemDisplayTransform displayTransform = ItemDisplay.ItemDisplayTransform.NONE;
    private org.bukkit.Sound clickSound = org.bukkit.Sound.UI_BUTTON_CLICK;
    private boolean soundEnabled = true;
    private float soundVolume = 0.5f;
    private float soundPitch = 1.0f;
    private Runnable updateCallback;
    private org.bukkit.inventory.meta.ItemMeta itemMeta;
    private Vector3f translation;
    private Transformation hoveredTransformation;
    private int hoveredTransformationDuration;
    private boolean glowOnHover = true;
    private org.bukkit.Color glowColor;
    
    // Сохранение ориентации для восстановления после пересоздания
    private float savedYaw = 0.0f;
    private float savedPitch = 0.0f;
    private boolean hasRotation = false;
    
    // Сохранение оригинального onClick для setEnabled
    private Runnable originalOnClick;
    private boolean enabled = true;
    
    // Отслеживание видимости
    private boolean visible = true;
    
    private Vector cachedPosition;
    private boolean positionCached = false;

    public static ItemDisplayButtonWidget create(Location location, Player viewer, ItemDisplayButtonConfig config) {
        ItemDisplayButtonWidget widget = new ItemDisplayButtonWidget();
        widget.location = location;
        widget.viewer = viewer;
        widget.onClick = config.getOnClick();
        widget.originalOnClick = config.getOnClick(); // Сохраняем оригинальный onClick
        widget.itemType = config.getMaterial();
        widget.position = config.getPosition();
        widget.displayTransform = config.getDisplayTransform();
        widget.scaleX = config.getScaleX();
        widget.scaleY = config.getScaleY();
        widget.scaleZ = config.getScaleZ();
        widget.horizontalTolerance = config.getToleranceHorizontal();
        widget.verticalTolerance = config.getToleranceVertical();
        widget.clickSound = config.getClickSound();
        widget.soundEnabled = config.isSoundEnabled();
        widget.soundVolume = config.getSoundVolume();
        widget.soundPitch = config.getSoundPitch();
        widget.itemMeta = config.getItemMeta();
        widget.translation = config.getTranslation();
        widget.hoveredTransformation = config.getHoveredTransformation();
        widget.hoveredTransformationDuration = config.getHoveredTransformationDuration();
        widget.glowOnHover = config.isGlowOnHover();
        widget.glowColor = config.getGlowColor();
        
        if (config.hasTooltip()) {
            widget.tooltip = Component.text(config.getTooltip()).color(config.getTooltipColor());
            widget.tooltipDelay = config.getTooltipDelay();
        }

        widget.spawn();
        return widget;
    }

    private void spawn() {
        ItemStack item = new ItemStack(itemType);
        if (itemMeta != null) {
            item.setItemMeta(itemMeta);
        }

        display = (ItemDisplay) location.getWorld().spawnEntity(location, org.bukkit.entity.EntityType.ITEM_DISPLAY);
        display.setItemStack(item);
        display.setItemDisplayTransform(displayTransform);
        
        if (glowColor != null) {
            display.setGlowColorOverride(glowColor);
        }

        display.setTransformation(new Transformation(
                translation,
                new AxisAngle4f(),
                new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f()
        ));

        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        
        // Восстанавливаем ориентацию если она была сохранена
        if (hasRotation) {
            display.setRotation(savedYaw, savedPitch);
            display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        }
    }

    @Override
    public boolean isHovered() {
        if (display == null || viewer == null) return false;

        Vector eye = viewer.getEyeLocation().toVector();
        Vector direction = viewer.getEyeLocation().getDirection();

        if (!positionCached) {
            cachedPosition = display.getLocation().toVector();
            positionCached = true;
        }

        Vector toWidget = cachedPosition.clone().subtract(eye).normalize();
        if (toWidget.dot(direction) < 0.5) return false;

        return PointDetection.lookingAtPoint(eye, direction, cachedPosition, horizontalTolerance, verticalTolerance);
    }
    
    public void updateCachedPosition() {
        if (display != null) {
            cachedPosition = display.getLocation().toVector();
            positionCached = true;
        }
    }
    
    @Override
    public Location getLocation() {
        return display != null ? display.getLocation() : location;
    }

    @Override
    public void handleClick() {
        if (enabled && onClick != null) {
            onClick.run();
            
            if (soundEnabled) {
                viewer.playSound(viewer.getLocation(), clickSound, soundVolume, soundPitch);
            }
        }
    }

    @Override
    public void remove() {
        if (display != null) {
            display.remove();
            display = null;
        }
        hideTooltip();

    }

    public void removeWithAnimation(int duration) {
        if (display != null) {
            Animation.applyTransformationWithInterpolation(display, new Transformation(
                    display.getTransformation().getTranslation(),
                    display.getTransformation().getLeftRotation(),
                    new Vector3f(0, 0, 0),
                    display.getTransformation().getRightRotation()
            ), duration);

            Bukkit.getScheduler().runTaskLater(DisplayLib.getInstance(), this::remove, duration + 1);
        }
    }

    @Override
    public void update() {
        if (display == null || viewer == null) return;

        boolean currentlyHovered = isHovered();
        
        if (currentlyHovered != isHovered) {
            isHovered = currentlyHovered;
            onHoverStateChanged();
        }

        if (isHovered) {
            hoverTicks++;
            if (tooltip != null && hoverTicks >= tooltipDelay && !isShowingTooltip) {
                showTooltip();
            }
        } else {
            hoverTicks = 0;
            if (isShowingTooltip) {
                hideTooltip();
            }
        }
    }

    private void onHoverStateChanged() {
        if (glowOnHover) {
            display.setGlowing(isHovered);
        }

        if (hoveredTransformation != null) {
            if (isHovered) {
                Animation.applyTransformationWithInterpolation(display, hoveredTransformation, hoveredTransformationDuration);
            } else {
                Animation.applyTransformationWithInterpolation(display, new Transformation(
                        translation,
                        new AxisAngle4f(),
                        new Vector3f(scaleX, scaleY, scaleZ),
                        new AxisAngle4f()
                ), hoveredTransformationDuration);
            }
        }
    }

    private void showTooltip() {
        if (tooltip != null && viewer != null) {
            Title title = Title.title(
                    Component.empty(),
                    tooltip,
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(Long.MAX_VALUE), Duration.ofMillis(200))
            );
            viewer.showTitle(title);
            isShowingTooltip = true;
        }
    }

    private void hideTooltip() {
        if (isShowingTooltip && viewer != null) {
            viewer.clearTitle();
            isShowingTooltip = false;
        }
    }

    public boolean isValid() {
        // Виджет валиден если он был создан (независимо от видимости)
        return location != null && viewer != null;
    }

    public ItemDisplay getDisplay() {
        return display;
    }

    public WidgetPosition getPosition() {
        return position;
    }
    
    // Методы для Lua API
    @Override
    public boolean isVisible() {
        return visible && display != null && !display.isDead();
    }
    
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        
        if (display != null) {
            if (visible) {
                if (display.isDead()) {
                    // Пересоздаем entity если он был удален
                    spawn();
                    positionCached = false; // Сбрасываем кеш позиции
                }
            } else {
                display.remove();
            }
        } else if (visible) {
            // Создаем новый display если его нет
            spawn();
            positionCached = false; // Сбрасываем кеш позиции
        }
    }
    
    @Override
    public boolean isEnabled() {
        return onClick != null;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && originalOnClick != null) {
            this.onClick = originalOnClick;
        } else if (!enabled) {
            this.onClick = null;
        }
    }
    
    @Override
    public String getTooltip() {
        return tooltip != null ? tooltip.toString() : null;
    }
    
    @Override
    public void setTooltip(String tooltipText) {
        if (tooltipText != null) {
            this.tooltip = Component.text(tooltipText);
        } else {
            this.tooltip = null;
        }
    }
    
    /**
     * Сохранить ориентацию для восстановления после пересоздания
     */
    public void saveRotation(float yaw, float pitch) {
        // Для ItemDisplay нужно скорректировать ориентацию
        // ItemDisplay имеет другую систему координат чем TextDisplay
        this.savedYaw = yaw + 180.0f;
        this.savedPitch = -pitch;
        this.hasRotation = true;
        
        // Применяем ориентацию если display уже существует
        if (display != null) {
            display.setRotation(this.savedYaw, this.savedPitch);
            display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        }
    }
    
    /**
     * Показать tooltip конкретному игроку (для PUBLIC экранов)
     */
    public void showTooltipTo(Player player) {
        if (tooltip != null && player != null) {
            Title title = Title.title(
                    Component.empty(),
                    tooltip,
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(Long.MAX_VALUE), Duration.ofMillis(200))
            );
            player.showTitle(title);
        }
    }
    
    /**
     * Скрыть tooltip у конкретного игрока (для PUBLIC экранов)
     */
    public void hideTooltipFrom(Player player) {
        if (player != null) {
            player.clearTitle();
        }
    }
}
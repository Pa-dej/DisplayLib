package padej.displayLib.ui.widgets;

import padej.displayLib.DisplayLib;
import padej.displayLib.utils.Animation;
import padej.displayLib.utils.PointDetection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;

public class TextDisplayButtonWidget implements Widget {
    private TextDisplay display;
    private Player viewer;
    private boolean isHovered = false;
    private Runnable onClick;
    private Component tooltip;
    private boolean isShowingTooltip = false;
    private int tooltipDelay = 0;
    private int hoverTicks = 0;
    private Location location;
    private Component text;
    private Component hoveredText;
    private Color backgroundColor;
    private int backgroundAlpha;
    private Color hoveredBackgroundColor;
    private int hoveredBackgroundAlpha;
    private float scaleX = .15f;
    private float scaleY = .15f;
    private float scaleZ = .15f;
    private double horizontalTolerance = 0.06;
    private double verticalTolerance = 0.06;
    private WidgetPosition position;
    private org.bukkit.Sound clickSound = org.bukkit.Sound.BLOCK_DISPENSER_FAIL;
    private boolean soundEnabled = true;
    private float soundVolume = 0.5f;
    private float soundPitch = 2.0f;
    private Vector3f translation;
    private Transformation hoveredTransformation;
    private int hoveredTransformationDuration;

    public static TextDisplayButtonWidget create(Location location, Player viewer, TextDisplayButtonConfig config) {
        TextDisplayButtonWidget widget = new TextDisplayButtonWidget();
        widget.location = location;
        widget.viewer = viewer;
        widget.onClick = config.getOnClick();
        widget.text = config.getText();
        widget.hoveredText = config.getHoveredText();
        widget.position = config.getPosition();
        widget.backgroundColor = config.getBackgroundColor();
        widget.backgroundAlpha = config.getBackgroundAlpha();
        widget.hoveredBackgroundColor = config.getHoveredBackgroundColor();
        widget.hoveredBackgroundAlpha = config.getHoveredBackgroundAlpha();
        widget.scaleX = config.getScaleX();
        widget.scaleY = config.getScaleY();
        widget.scaleZ = config.getScaleZ();
        widget.horizontalTolerance = config.getToleranceHorizontal();
        widget.verticalTolerance = config.getToleranceVertical();
        widget.clickSound = config.getClickSound();
        widget.soundEnabled = config.isSoundEnabled();
        widget.soundVolume = config.getSoundVolume();
        widget.soundPitch = config.getSoundPitch();
        widget.translation = config.getTranslation();
        widget.hoveredTransformation = config.getHoveredTransformation();
        widget.hoveredTransformationDuration = config.getHoveredTransformationDuration();
        
        if (config.getTooltip() != null) {
            widget.tooltip = config.getTooltip().color(config.getTooltipColor());
            widget.tooltipDelay = config.getTooltipDelay();
        }

        widget.spawn();
        return widget;
    }

    private void spawn() {
        display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.text(text);
        display.setBackgroundColor(Color.fromARGB(backgroundAlpha, backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue()));

        display.setTransformation(new Transformation(
                translation,
                new AxisAngle4f(),
                new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f()
        ));

        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
    }

    @Override
    public boolean isHovered() {
        if (display == null || viewer == null) return false;

        Vector eye = viewer.getEyeLocation().toVector();
        Vector direction = viewer.getEyeLocation().getDirection();
        Vector point = display.getLocation().toVector();

        return PointDetection.lookingAtPoint(eye, direction, point, horizontalTolerance, verticalTolerance);
    }

    @Override
    public void handleClick() {
        if (onClick != null) {
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
        if (isHovered) {
            display.text(hoveredText);
            display.setBackgroundColor(Color.fromARGB(hoveredBackgroundAlpha, hoveredBackgroundColor.getRed(), hoveredBackgroundColor.getGreen(), hoveredBackgroundColor.getBlue()));
            
            if (hoveredTransformation != null) {
                Animation.applyTransformationWithInterpolation(display, hoveredTransformation, hoveredTransformationDuration);
            }
        } else {
            display.text(text);
            display.setBackgroundColor(Color.fromARGB(backgroundAlpha, backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue()));
            
            if (hoveredTransformation != null) {
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
        return display != null && !display.isDead();
    }

    public TextDisplay getDisplay() {
        return display;
    }

    public WidgetPosition getPosition() {
        return position;
    }
}
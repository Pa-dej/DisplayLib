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
    private org.bukkit.entity.TextDisplay.TextAlignment textAlignment = org.bukkit.entity.TextDisplay.TextAlignment.CENTER;
    
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

    public static TextDisplayButtonWidget create(Location location, Player viewer, TextDisplayButtonConfig config) {
        TextDisplayButtonWidget widget = new TextDisplayButtonWidget();
        widget.location = location;
        widget.viewer = viewer;
        widget.onClick = config.getOnClick();
        widget.originalOnClick = config.getOnClick(); // Сохраняем оригинальный onClick
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
        widget.textAlignment = config.getTextAlignment();
        
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
        
        // Применяем выравнивание текста
        display.setAlignment(textAlignment);

        // Проверяем translation на null и используем значение по умолчанию
        Vector3f finalTranslation = translation != null ? translation : new Vector3f(0, 0, 0);

        display.setTransformation(new Transformation(
                finalTranslation,
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
        // Виджет валиден если он был создан
        // Для глобальных экранов viewer может быть null
        return location != null && display != null;
    }

    public TextDisplay getDisplay() {
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
    
    // Методы для работы с текстом
    public String getText() {
        return text != null 
            ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text)
            : "";
    }
    
    public void setText(String newText) {
        this.text = Component.text(newText);
        if (display != null && !isHovered) {
            display.text(this.text);
        }
    }
    
    public void setHoveredText(String newText) {
        this.hoveredText = Component.text(newText);
        if (display != null && isHovered) {
            display.text(this.hoveredText);
        }
    }
    
    // Методы для работы с цветом фона
    public void setBackgroundColor(int red, int green, int blue) {
        this.backgroundColor = Color.fromRGB(red, green, blue);
        if (display != null && !isHovered) {
            display.setBackgroundColor(Color.fromARGB(backgroundAlpha, red, green, blue));
        }
    }
    
    public void setBackgroundAlpha(int alpha) {
        this.backgroundAlpha = alpha;
        if (display != null && !isHovered) {
            display.setBackgroundColor(Color.fromARGB(alpha, backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue()));
        }
    }
    
    /**
     * Сохранить ориентацию для восстановления после пересоздания
     */
    public void saveRotation(float yaw, float pitch) {
        this.savedYaw = yaw;
        this.savedPitch = pitch;
        this.hasRotation = true;
        
        // Применяем ориентацию если display уже существует
        if (display != null) {
            display.setRotation(yaw, pitch);
            display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        }
    }
    
    /**
     * Показать tooltip конкретному игроку (для GLOBAL экранов)
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
     * Скрыть tooltip у конкретного игрока (для GLOBAL экранов)
     */
    public void hideTooltipFrom(Player player) {
        if (player != null) {
            player.clearTitle();
        }
    }
}
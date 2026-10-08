package padej.displayLib.ui.widgets;

import padej.displayLib.DisplayLib;
import padej.displayLib.utils.Animation;
import padej.displayLib.utils.HitArea;
import padej.displayLib.utils.ViewRay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
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
    private padej.displayLib.config.HoverAnimation hoverAnimation;
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

    // Виджет удалён окончательно (remove) - пересоздавать сущность нельзя
    private boolean removed = false;

    /** Подсказка висит, пока игрок смотрит на виджет; время показа общее для всех виджетов */
    private static final Title.Times TOOLTIP_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(Long.MAX_VALUE), Duration.ofMillis(200));
    private Title tooltipTitle;

    // Исходный масштаб одним объектом - чтобы не создавать вектор на каждую смену наведения
    private Vector3f baseScale;

    private Color backgroundArgb;
    private Color hoveredBackgroundArgb;
    
    /** Текст TextDisplay виден только с лицевой стороны - наводиться можно только с неё */
    private static final boolean FRONT_ONLY = true;

    // Зона наведения в плоскости display; строится один раз (виджеты экрана неподвижны)
    private final HitArea hitArea = new HitArea();
    private boolean positionCached = false;

    // Переиспользуемые объекты для проверки наведения (без аллокаций в горячем цикле)
    private final ViewRay ownRay = new ViewRay();
    private final Location positionScratch = new Location(null, 0, 0, 0);

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
        widget.hoverAnimation = config.getHoverAnimation();
        widget.textAlignment = config.getTextAlignment();
        
        if (config.getTooltip() != null) {
            widget.tooltip = config.getTooltip();
            widget.tooltipDelay = config.getTooltipDelay();
        }

        widget.baseScale = new Vector3f(widget.scaleX, widget.scaleY, widget.scaleZ);
        widget.spawn();
        return widget;
    }

    private void spawn() {
        display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.text(text);
        display.setBackgroundColor(backgroundArgb());
        
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

        // Сущности экрана временные: не сохраняем их в чанк, иначе после падения
        // или перезапуска сервера в мире остаются "осиротевшие" display
        display.setPersistent(false);
        
        // Восстанавливаем ориентацию если она была сохранена
        if (hasRotation) {
            display.setRotation(savedYaw, savedPitch);
            display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        }
    }

    /**
     * Смотрит ли зритель на виджет прямо сейчас (вычисляется заново при каждом вызове).
     * В цикле обновления экрана используется {@link #update(ViewRay)} с общим лучом.
     */
    @Override
    public boolean isHovered() {
        if (display == null || viewer == null) return false;
        return isHoveredBy(ownRay.set(viewer));
    }

    private boolean isHoveredBy(ViewRay ray) {
        return hitDistance(ray) >= 0.0;
    }

    @Override
    public double hitDistance(ViewRay ray) {
        if (display == null) return -1.0;

        if (!positionCached) {
            cachePosition();
        }

        return hitArea.intersect(ray);
    }

    /**
     * Построить зону наведения по текущему состоянию сущности: позиция, поворот и translation.
     * tolerance задаёт полуширину и полувысоту зоны в плоскости виджета.
     */
    private void cachePosition() {
        display.getLocation(positionScratch);
        float tx = translation != null ? translation.x : 0.0f;
        float ty = translation != null ? translation.y : 0.0f;
        float tz = translation != null ? translation.z : 0.0f;
        hitArea.set(positionScratch, tx, ty, tz, horizontalTolerance, verticalTolerance, FRONT_ONLY);
        positionScratch.setWorld(null);
        positionCached = true;
    }

    public void updateCachedPosition() {
        if (display != null) {
            cachePosition();
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
        removed = true;
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
        update(ownRay.set(viewer));
    }

    @Override
    public void update(ViewRay ray) {
        if (display == null || viewer == null) return;

        boolean currentlyHovered = isHoveredBy(ray);
        
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
            display.setBackgroundColor(hoveredBackgroundArgb());
            
            // Приоритет: новая система анимации, затем старая hoveredTransformation
            if (hoverAnimation != null) {
                // Используем новую систему анимации с правильной easing интерполяцией
                try {
                    hoverAnimation.applyHoverAnimation(display, translation, baseScale, true);
                } catch (Exception e) {
                    DisplayLib.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Error applying hover animation", e);
                }
            } else if (hoveredTransformation != null) {
                // Fallback на старую систему
                Animation.applyTransformationWithInterpolation(display, hoveredTransformation, hoveredTransformationDuration);
            }
        } else {
            display.text(text);
            display.setBackgroundColor(backgroundArgb());
            
            // Возвращаем к исходному состоянию
            if (hoverAnimation != null && hoverAnimation.isReverseOnExit()) {
                // Используем новую систему для возврата
                try {
                    hoverAnimation.applyHoverAnimation(display, translation, baseScale, false);
                } catch (Exception e) {
                    DisplayLib.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Error reversing hover animation", e);
                }
            } else if (hoveredTransformation != null) {
                // Fallback на старую систему
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
            viewer.showTitle(tooltipTitle());
            isShowingTooltip = true;
        }
    }

    /**
     * Заголовок с подсказкой. Собирается один раз и переиспользуется при каждом показе
     * (раньше Title, Times и три Duration создавались заново на каждое наведение).
     */
    private Title tooltipTitle() {
        if (tooltipTitle == null) {
            tooltipTitle = Title.title(Component.empty(), tooltip, TOOLTIP_TIMES);
        }
        return tooltipTitle;
    }

    /**
     * Пересоздать сущность, если она исчезла из мира (например, чанк был выгружен:
     * сущности экранов не сохраняются в чанк). Ничего не делает, если виджет удалён,
     * скрыт или его чанк сейчас не загружен.
     */
    @Override
    public void ensureSpawned() {
        if (removed || !visible) return;
        // isDead(), а не isValid(): только что созданная сущность в чанке на границе
        // загруженной области ещё "не валидна", но существует - пересоздавать её не нужно
        if (display != null && !display.isDead()) return;

        org.bukkit.World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;

        if (display != null) {
            display.remove(); // на случай, если старая сущность ещё числится в мире
        }
        spawn();
        positionCached = false;
        isHovered = false;
    }

    private void hideTooltip() {
        if (isShowingTooltip && viewer != null) {
            viewer.clearTitle();
            isShowingTooltip = false;
        }
    }

    public boolean isValid() {
        // Виджет валиден если он был создан
        // Для публичных экранов viewer может быть null
        return location != null && display != null;
    }

    public TextDisplay getDisplay() {
        return display;
    }

    public WidgetPosition getPosition() {
        return position;
    }
    
    // Методы для API скриптов
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
        this.tooltipTitle = null;
    }
    
    @Override
    public void setTooltip(Component tooltip) {
        this.tooltip = tooltip;
        this.tooltipTitle = null;
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
        this.backgroundArgb = null;
        if (display != null && !isHovered) {
            display.setBackgroundColor(backgroundArgb());
        }
    }
    
    public void setBackgroundAlpha(int alpha) {
        this.backgroundAlpha = alpha;
        this.backgroundArgb = null;
        if (display != null && !isHovered) {
            display.setBackgroundColor(backgroundArgb());
        }
    }

    // Итоговые цвета фона (цвет + прозрачность) собираются один раз, а не на каждую смену наведения
    private Color backgroundArgb() {
        if (backgroundArgb == null) {
            backgroundArgb = Color.fromARGB(backgroundAlpha, backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue());
        }
        return backgroundArgb;
    }

    private Color hoveredBackgroundArgb() {
        if (hoveredBackgroundArgb == null) {
            hoveredBackgroundArgb = Color.fromARGB(hoveredBackgroundAlpha, hoveredBackgroundColor.getRed(), hoveredBackgroundColor.getGreen(), hoveredBackgroundColor.getBlue());
        }
        return hoveredBackgroundArgb;
    }
    
    /**
     * Сохранить ориентацию для восстановления после пересоздания
     */
    public void saveRotation(float yaw, float pitch) {
        this.savedYaw = yaw;
        this.savedPitch = pitch;
        this.hasRotation = true;
        this.positionCached = false; // зона наведения зависит от поворота
        
        // Применяем ориентацию если display уже существует
        if (display != null) {
            display.setRotation(yaw, pitch);
            display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        }
    }
    
    /**
     * Показать tooltip конкретному игроку (для PUBLIC экранов)
     */
    public void showTooltipTo(Player player) {
        if (tooltip != null && player != null) {
            player.showTitle(tooltipTitle());
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
    
    /**
     * Принудительно сбросить hover состояние (для interaction_radius)
     */
    @Override
    public void clearHover() {
        if (isHovered) {
            isHovered = false;
            onHoverStateChanged();
            hideTooltip();
        }
    }
}
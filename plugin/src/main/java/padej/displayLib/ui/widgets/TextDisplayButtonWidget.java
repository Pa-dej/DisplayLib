package padej.displayLib.ui.widgets;

import padej.displayLib.DisplayLib;
import padej.displayLib.utils.Animation;
import padej.displayLib.utils.HitArea;
import padej.displayLib.utils.ViewRay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;

public class TextDisplayButtonWidget implements Widget {
    private TextDisplay display;
    private Player viewer;
    private boolean isHovered = false;
    private Runnable onClick;
    private boolean interactive = true;
    private Component tooltip;
    private boolean isShowingTooltip = false;
    private int tooltipDelay = 0;
    private int hoverTicks = 0;
    private Location location;
    private Component text;
    private Component hoveredText;
    // Текстовая анимация: кадры и интервал (тики); hoveredFrames - при наведении
    private Component[] frames;
    private int frameInterval = 10;
    private Component[] hoveredFrames;
    private int hoveredFrameInterval = 10;
    private int frameIndex = 0;
    private int frameTicks = 0;
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
    private padej.displayLib.config.SoundSpec hoverSound;
    private float soundVolume = 0.5f;
    private float soundPitch = 2.0f;
    private Vector3f translation;
    /** Поворот в плоскости экрана, градусы (+ = против часовой для зрителя), вокруг центра текста. */
    private float rotationDeg = 0.0f;
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
        TextDisplayButtonWidget widget = init(location, viewer, config);
        widget.spawn();
        return widget;
    }

    /**
     * Создать виджет на уже существующей сущности (morph-переход между экранами):
     * сущность прежнего виджета остаётся в мире, а её текст, цвет и трансформация
     * доводятся до целевых значений анимацией в {@link padej.displayLib.ui.MorphTransition}.
     */
    public static TextDisplayButtonWidget createAdopting(Location location, Player viewer, TextDisplayButtonConfig config, TextDisplay entity) {
        TextDisplayButtonWidget widget = init(location, viewer, config);
        widget.display = entity;
        entity.setAlignment(widget.textAlignment);
        entity.setPersistent(false);
        return widget;
    }

    private static TextDisplayButtonWidget init(Location location, Player viewer, TextDisplayButtonConfig config) {
        TextDisplayButtonWidget widget = new TextDisplayButtonWidget();
        widget.location = location;
        widget.viewer = viewer;
        widget.onClick = config.getOnClick();
        widget.interactive = config.isInteractive();
        widget.originalOnClick = config.getOnClick(); // Сохраняем оригинальный onClick
        widget.text = config.getText();
        widget.hoveredText = config.getHoveredText();
        widget.frames = config.getFrames();
        widget.frameInterval = config.getFrameInterval();
        widget.hoveredFrames = config.getHoveredFrames();
        widget.hoveredFrameInterval = config.getHoveredFrameInterval();
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
        widget.hoverSound = config.getHoverSound();
        widget.soundVolume = config.getSoundVolume();
        widget.soundPitch = config.getSoundPitch();
        widget.translation = config.getTranslation();
        widget.rotationDeg = config.getRotation();
        widget.hoveredTransformation = config.getHoveredTransformation();
        widget.hoveredTransformationDuration = config.getHoveredTransformationDuration();
        widget.hoverAnimation = config.getHoverAnimation();
        widget.textAlignment = config.getTextAlignment();
        
        if (config.getTooltip() != null) {
            widget.tooltip = config.getTooltip();
            widget.tooltipDelay = config.getTooltipDelay();
        }

        widget.baseScale = new Vector3f(widget.scaleX, widget.scaleY, widget.scaleZ);
        return widget;
    }

    /**
     * Отдать сущность (для morph) и забыть о ней: виджет после этого ничего не удаляет.
     * @return сущность или null, если её нет
     */
    public TextDisplay detachDisplay() {
        TextDisplay d = display;
        display = null;
        isHovered = false;
        hideTooltip();
        // задачи анимации наведения привязаны к сущности, а не к виджету - иначе они доиграют уже на новом экране
        if (d != null) padej.displayLib.utils.EasedAnimation.cancelAll(d);
        return d;
    }

    /** Целевая трансформация виджета в обычном (не наведённом) состоянии. */
    public Transformation targetTransformation() {
        return new Transformation(effectiveTranslation(), baseRotation(), new Vector3f(scaleX, scaleY, scaleZ), new Quaternionf());
    }

    /** Статический поворот (left rotation): вокруг локальной Z - для TextDisplay она смотрит на зрителя. */
    Quaternionf baseRotation() {
        return new Quaternionf().rotateZ((float) Math.toRadians(rotationDeg));
    }

    /**
     * translation с поправкой на поворот вокруг центра текста: якорь TextDisplay - низ по центру,
     * поэтому при повороте R вокруг центра c = (0, h/2) якорь сдвигается на c - R·c.
     * Зона наведения не меняется: её центр и есть центр поворота.
     */
    Vector3f effectiveTranslation() {
        Vector3f t = translation != null ? new Vector3f(translation) : new Vector3f();
        if (rotationDeg == 0.0f) return t;
        float hc = hitCenterOffsetY();
        double a = Math.toRadians(rotationDeg);
        return t.add((float) (hc * Math.sin(a)), (float) (hc - hc * Math.cos(a)), 0.0f);
    }

    /** Целевой цвет фона (ARGB) в обычном состоянии. */
    public Color targetBackground() { return backgroundArgb(); }

    /** Целевой текст в обычном состоянии. */
    public Component targetText() { return text; }

    /** Целевая мировая позиция (с ориентацией экрана). */
    public Location targetLocation() {
        Location l = location.clone();
        if (hasRotation) { l.setYaw(savedYaw); l.setPitch(savedPitch); }
        return l;
    }

    /** Пересчитать зону наведения при следующем обращении (после перемещения сущности). */
    public void invalidateHitArea() { positionCached = false; }

    private void spawn() {
        display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.text(text);
        display.setBackgroundColor(backgroundArgb());
        
        // Применяем выравнивание текста
        display.setAlignment(textAlignment);

        display.setTransformation(targetTransformation());

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
        if (display == null || !interactive) return -1.0;

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
        // Якорь TextDisplay - низ текста по центру; зону поднимаем на половину высоты фона,
        // чтобы tolerance задавал прямоугольник вокруг центра нарисованного текста.
        float ty = (translation != null ? translation.y : 0.0f) + hitCenterOffsetY();
        float tz = translation != null ? translation.z : 0.0f;
        hitArea.set(positionScratch, tx, ty, tz, horizontalTolerance, verticalTolerance, FRONT_ONLY);
        positionScratch.setWorld(null);
        positionCached = true;
    }

    /** Пиксель текста в блоках (клиент: scale(-0.025)). */
    private static final float TEXT_PIXEL = 1.0f / 40.0f;
    /** Высота строки, px (9 + 1). */
    private static final int LINE_HEIGHT = 10;
    /** Ширина переноса TextDisplay по умолчанию, px. */
    private static final int LINE_WIDTH = 200;

    /**
     * Смещение центра зоны наведения вверх от якоря: половина высоты фона (строки · 10 px) · scaleY
     * (клиент 1.21.11/26.1: фон от -1 до lines*10-1 в координатах шрифта, т.е. ровно lines*10 px).
     * Число строк - по переводам строк и грубой оценке переноса (6 px на символ, предел 200 px);
     * ширину шрифта сервер не знает, так что для длинных строк это приближение.
     */
    float hitCenterOffsetY() {
        int lines = estimateLines(text);
        float heightPx = lines * LINE_HEIGHT;
        return heightPx / 2.0f * TEXT_PIXEL * scaleY;
    }

    /**
     * Оценка числа строк: ширина текста по таблице ширин шрифта клиента (узкие символы 2–5 px,
     * остальные 6, спрайт-объект 9 px), перенос по 200 px по словам, как StringSplitter.
     * Плоская сериализация тут не годится: спрайт в ней выглядит как "[item/apple@items]" и
     * раздувает ширину в разы, из-за чего центр зоны наведения уезжал вверх.
     */
    static int estimateLines(Component c) {
        return estimateSize(c)[1];
    }

    /**
     * Оценка размера текста в px шрифта: {@code [ширина самой длинной строки, число строк]}
     * по той же таблице ширин и переносу, что и {@link #estimateLines}.
     */
    public static int[] estimateSize(Component c) {
        if (c == null) return new int[]{0, 1};
        // собираем «токены»: ширина слова и признак переноса строки
        java.util.List<int[]> words = new java.util.ArrayList<>(); // [width, isNewline]
        StringBuilder word = new StringBuilder();
        int[] cur = {0};
        Runnable flush = () -> { if (cur[0] > 0 || word.length() > 0) { words.add(new int[]{cur[0], 0}); cur[0] = 0; word.setLength(0); } };
        for (Component part : c.iterable(net.kyori.adventure.text.ComponentIteratorType.DEPTH_FIRST)) {
            if (part instanceof net.kyori.adventure.text.ObjectComponent) { cur[0] += SPRITE_ADVANCE; word.append('#'); continue; }
            String txt = part instanceof TextComponent t ? t.content()
                    : part instanceof net.kyori.adventure.text.TranslatableComponent tr ? tr.key() : "";
            txt.codePoints().forEach(cp -> {
                if (cp == '\n') { flush.run(); words.add(new int[]{0, 1}); }
                else if (cp == ' ') { cur[0] += 4; flush.run(); }
                else { cur[0] += charWidth(cp); word.append((char) cp); }
            });
        }
        flush.run();
        int lines = 1, lineW = 0, maxW = 0;
        for (int[] w : words) {
            if (w[1] == 1) { lines++; lineW = 0; continue; }
            if (lineW > 0 && lineW + w[0] > LINE_WIDTH) { lines++; lineW = w[0]; }
            else lineW += w[0];
            maxW = Math.max(maxW, lineW);
        }
        return new int[]{maxW, Math.max(1, lines)};
    }

    /** advance спрайта-объекта в тексте (квадрат 8 px + промежуток) */
    private static final int SPRITE_ADVANCE = 8; // GlyphInfo.simple(8.0f) в AtlasGlyphProvider: без промежутка

    /** Ширина символа шрифта клиента (advance = ширина глифа + 1); для неизвестных - 6. */
    private static int charWidth(int cp) {
        switch (cp) {
            case 'i': case '!': case '.': case ',': case ':': case ';': case '|': case '\'': case '`': return 2;
            case 'l': return 3;
            case 't': case 'I': case '(': case ')': case '[': case ']': case '{': case '}': case '"': case '*': case ' ': return 4;
            case 'f': case 'k': case '<': case '>': return 5;
            default: return cp > 0x2E80 ? 9 : 6; // CJK/эмодзи - широкие
        }
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
        if (display == null || viewer == null || !interactive) return;

        boolean currentlyHovered = isHoveredBy(ray);
        
        if (currentlyHovered != isHovered) {
            isHovered = currentlyHovered;
            onHoverStateChanged();
            if (isHovered && hoverSound != null) hoverSound.play(viewer);
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
        frameIndex = 0; frameTicks = 0;
        if (isHovered) {
            display.text(hoveredFrames != null ? hoveredFrames[0] : hoveredText);
            display.setBackgroundColor(hoveredBackgroundArgb());
            
            // Приоритет: новая система анимации, затем старая hoveredTransformation
            if (hoverAnimation != null) {
                // Используем новую систему анимации с правильной easing интерполяцией
                try {
                    hoverAnimation.applyHoverAnimation(display, effectiveTranslation(), baseScale, true, baseRotation());
                } catch (Exception e) {
                    DisplayLib.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Error applying hover animation", e);
                }
            } else if (hoveredTransformation != null) {
                // Fallback на старую систему
                Animation.applyTransformationWithInterpolation(display, hoveredTransformation, hoveredTransformationDuration);
            }
        } else {
            display.text(frames != null ? frames[0] : text);
            display.setBackgroundColor(backgroundArgb());
            
            // Возвращаем к исходному состоянию
            if (hoverAnimation != null && hoverAnimation.isReverseOnExit()) {
                // Используем новую систему для возврата
                try {
                    hoverAnimation.applyHoverAnimation(display, effectiveTranslation(), baseScale, false, baseRotation());
                } catch (Exception e) {
                    DisplayLib.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Error reversing hover animation", e);
                }
            } else if (hoveredTransformation != null) {
                // Fallback на старую систему
                Animation.applyTransformationWithInterpolation(display, targetTransformation(), hoveredTransformationDuration);
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

    /** Поворот в плоскости экрана, градусы (+ = против часовой для зрителя). */
    public float getRotation() { return rotationDeg; }

    public float[] getScale() { return new float[] {scaleX, scaleY, scaleZ}; }
    public float[] getTranslation() { Vector3f t = translation != null ? translation : new Vector3f(); return new float[] {t.x, t.y, t.z}; }

    /** Сменить масштаб (плавно за ticks тиков - интерполяция клиента). Hover-анимации дальше считаются от нового. */
    public void setScale(float x, float y, float z, int ticks) {
        this.scaleX = x; this.scaleY = y; this.scaleZ = z;
        this.baseScale = new Vector3f(x, y, z);
        this.positionCached = false;
        applyBase(ticks);
    }

    /** Сменить translation (локальные оси сущности: x вправо, y вверх для зрителя). */
    public void setTranslation(float x, float y, float z, int ticks) {
        this.translation = new Vector3f(x, y, z);
        this.positionCached = false;
        applyBase(ticks);
    }

    private void applyBase(int ticks) {
        if (display != null && display.isValid() && !isHovered) {
            Animation.applyTransformationWithInterpolation(display, targetTransformation(), Math.max(1, ticks));
        }
    }

    /** Повернуть виджет; плавно за {@code ticks} тиков (0/1 - сразу). Если виджет наведён, применится при уходе курсора. */
    public void setRotation(float degrees, int ticks) {
        this.rotationDeg = degrees;
        if (display != null && display.isValid() && !isHovered) {
            Animation.applyTransformationWithInterpolation(display, targetTransformation(), Math.max(1, ticks));
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
    
    // ---- текстовая анимация (кадры)

    /** Есть ли у виджета кадры (обычные или при наведении) - его нужно тикать каждый тик. */
    public boolean hasFrames() {
        return frames != null || hoveredFrames != null;
    }

    /**
     * Один серверный тик анимации: по истечении интервала показать следующий кадр активного набора
     * (при наведении - hoveredFrames, если они есть; иначе обычные frames). Вызывается менеджером
     * экрана каждый тик независимо от tickRate; без кадров ничего не делает.
     */
    public void tickFrames() {
        if (display == null) return;
        Component[] set = isHovered && hoveredFrames != null ? hoveredFrames : (!isHovered || hoveredText == text) ? frames : null;
        if (set == null || set.length < 2) return;
        int interval = set == hoveredFrames ? hoveredFrameInterval : frameInterval;
        if (++frameTicks < interval) return;
        frameTicks = 0;
        frameIndex = (frameIndex + 1) % set.length;
        display.text(set[frameIndex]);
    }

    /** Задать кадры (null - убрать анимацию; текст остаётся текущим). */
    public void setFrames(Component[] frames, int interval) {
        this.frames = frames != null && frames.length > 0 ? frames : null;
        this.frameInterval = Math.max(1, interval);
        frameIndex = 0; frameTicks = 0;
        if (this.frames != null) { this.text = this.frames[0]; positionCached = false; }
        if (display != null && !isHovered) display.text(this.frames != null ? this.frames[0] : text);
    }

    public void setHoveredFrames(Component[] frames, int interval) {
        this.hoveredFrames = frames != null && frames.length > 0 ? frames : null;
        this.hoveredFrameInterval = Math.max(1, interval);
        frameIndex = 0; frameTicks = 0;
        if (this.hoveredFrames != null) this.hoveredText = this.hoveredFrames[0];
        if (display != null && isHovered) display.text(this.hoveredFrames != null ? this.hoveredFrames[0] : hoveredText);
    }

    /** Текст - компонент-объект (спрайт атласа), т.е. виджет создан как SPRITE_BUTTON. */
    public boolean isSprite() {
        return text instanceof net.kyori.adventure.text.ObjectComponent;
    }

    // Методы для работы с текстом
    public String getText() {
        return text != null 
            ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text)
            : "";
    }
    
    public void setText(String newText) {
        setText(Component.text(newText));
    }

    /** Установить произвольный компонент (форматированный текст, спрайт атласа). */
    public void setText(Component newText) {
        this.text = newText;
        this.frames = null; // текст из скрипта отменяет кадровую анимацию
        positionCached = false; // высота текста влияет на центр зоны наведения
        if (display != null && !isHovered) {
            display.text(this.text);
        }
    }

    /** Установить компонент, показываемый при наведении. */
    public void setHoveredText(Component newText) {
        this.hoveredText = newText;
        this.hoveredFrames = null;
        if (display != null && isHovered) {
            display.text(this.hoveredText);
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

    @Override
    public boolean isInteractive() {
        return interactive;
    }

    /** Включить/выключить обсчёт наведения (например, когда скрипт назначил виджету поведение). */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) clearHover();
    }
}

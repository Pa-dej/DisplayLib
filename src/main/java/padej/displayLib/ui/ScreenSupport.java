package padej.displayLib.ui;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import padej.displayLib.DisplayLib;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.WidgetDefinition;
import padej.displayLib.script.ScriptContext;
import padej.displayLib.ui.widgets.ItemDisplayButtonConfig;
import padej.displayLib.ui.widgets.TextDisplayButtonConfig;
import padej.displayLib.ui.widgets.WidgetPosition;
import padej.displayLib.utils.TransformationUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Общая логика сборки экрана из {@link ScreenDefinition}.
 *
 * <p>Раньше этот код был продублирован в {@link ScreenInstance} и
 * {@link GlobalScreenInstance}; теперь оба типа экранов используют одну реализацию.</p>
 */
public final class ScreenSupport {

    /** Смещение виджетов по глубине относительно фона для избежания Z-fighting */
    static final float WIDGET_DEPTH_OFFSET = 0.001f;

    /** Увеличенное смещение для ItemDisplay виджетов */
    static final float ITEM_WIDGET_DEPTH_OFFSET = 0.01f;

    private static final float[] ZERO = {0.0f, 0.0f, 0.0f};

    private ScreenSupport() {
    }

    // -------------------------------------------------------------------------
    // Позиционирование
    // -------------------------------------------------------------------------

    /**
     * Перевести локальные координаты экрана (x вправо, y вверх, z в глубину) в мировые.
     */
    static Location resolveLocation(Location screenLocation, float[] pos, float depthOffset) {
        Location base = screenLocation.clone();
        if (pos == null || pos.length < 3) return base;

        Vector dir = base.getDirection();
        Vector right = dir.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = right.getCrossProduct(dir).normalize();

        base.add(right.multiply(pos[0]));
        base.add(up.multiply(pos[1]));
        base.add(dir.multiply(pos[2] - depthOffset)); // Виджеты ближе к игроку для избежания Z-fighting
        return base;
    }

    // -------------------------------------------------------------------------
    // Конфигурации виджетов
    // -------------------------------------------------------------------------

    static Location backgroundLocation(Location screenLocation, ScreenDefinition.BackgroundDefinition bg) {
        float[] p = bg.getPosition() != null ? bg.getPosition() : ZERO;
        return resolveLocation(screenLocation, p, WIDGET_DEPTH_OFFSET);
    }

    static TextDisplayButtonConfig backgroundConfig(ScreenDefinition.BackgroundDefinition bg) {
        int[] c = bg.getColor();
        float[] s = bg.getScale();
        float[] tr = bg.getTranslation() != null ? bg.getTranslation() : ZERO;

        Component text = Component.text(bg.getText());
        Color color = Color.fromRGB(c[0], c[1], c[2]);

        return new TextDisplayButtonConfig(text, text, null) // фон не кликабелен
                .setScale(s[0], s[1], s[2])
                .setBackgroundColor(color)
                .setBackgroundAlpha(bg.getAlpha())
                .setHoveredBackgroundColor(color)           // тот же цвет при наведении
                .setHoveredBackgroundAlpha(bg.getAlpha())
                .setTolerance(0.0, 0.0)                     // нулевая зона наведения
                .setPosition(new WidgetPosition(0, 0, 0))
                .setTranslation(TransformationUtil.createAlignedTranslation(s[0], tr));
    }

    /**
     * @param onClick            действие по клику или null
     * @param withHoverAnimation применять ли hoverAnimation из YAML (для публичных экранов отключено)
     */
    static TextDisplayButtonConfig textConfig(WidgetDefinition def, Runnable onClick, boolean withHoverAnimation, boolean interactive) {
        int[] bg = def.getBackgroundColor();
        int[] hbg = def.getHoveredBackgroundColor();
        float[] s = def.getScale();
        float[] t = def.getTolerance();
        float[] tr = def.getTranslation();

        // Текст - обычная строка или форматированные сегменты; у SPRITE_BUTTON - спрайт из атласа
        Component textComponent;
        if (def.getType() == WidgetDefinition.WidgetType.SPRITE_BUTTON) {
            textComponent = spriteComponent(def.getAtlas(), def.getSprite());
        } else if (def.getFormattedText() != null) {
            textComponent = parseFormattedText(def.getFormattedText());
        } else {
            textComponent = Component.text(def.getText() != null ? def.getText() : "");
        }

        Component hoveredTextComponent;
        if (def.getType() == WidgetDefinition.WidgetType.SPRITE_BUTTON) {
            hoveredTextComponent = def.getHoveredSprite() != null
                    ? spriteComponent(def.getAtlas(), def.getHoveredSprite()) : textComponent;
        } else if (def.getFormattedHoveredText() != null) {
            hoveredTextComponent = parseFormattedText(def.getFormattedHoveredText());
        } else if (def.getHoveredText() != null && !def.getHoveredText().isEmpty()) {
            hoveredTextComponent = Component.text(def.getHoveredText());
        } else {
            hoveredTextComponent = textComponent; // обычный текст как fallback
        }

        TextDisplayButtonConfig cfg = new TextDisplayButtonConfig(textComponent, hoveredTextComponent, onClick)
                .setScale(s[0], s[1], s[2])
                .setTolerance(t[0], t[1])
                .setTranslation(new Vector3f(tr[0], tr[1], tr[2]))
                .setBackgroundColor(Color.fromRGB(bg[0], bg[1], bg[2]))
                .setBackgroundAlpha(def.getBackgroundAlpha())
                .setHoveredBackgroundColor(Color.fromRGB(hbg[0], hbg[1], hbg[2]))
                .setHoveredBackgroundAlpha(def.getHoveredBackgroundAlpha())
                .setTextAlignment(convertAlignment(def.getAlignment()))
                .setInteractive(interactive)
                .setPosition(new WidgetPosition(0, 0, 0)); // позиция уже вычислена в resolveLocation()

        if (def.getTooltip() != null) {
            cfg.setTooltip(parseFormattedText(def.getTooltip()));
            cfg.setTooltipDelay(def.getTooltipDelay());
        }

        if (withHoverAnimation && def.getHoverAnimation() != null) {
            cfg.setHoverAnimation(def.getHoverAnimation());
        }

        return cfg;
    }

    /**
     * @param onClick            действие по клику или null
     * @param withHoverAnimation применять ли hoverAnimation из YAML (для публичных экранов отключено)
     */
    static ItemDisplayButtonConfig itemConfig(WidgetDefinition def, Runnable onClick, boolean withHoverAnimation, boolean interactive) {
        float[] s = def.getScale();
        float[] t = def.getTolerance();
        float[] tr = def.getTranslation();

        ItemDisplayButtonConfig cfg = new ItemDisplayButtonConfig(parseItemMaterial(def.getMaterial()), onClick)
                .setScale(s[0], s[1], s[2])
                .setTolerance(t[0], t[1])
                .setTranslation(new Vector3f(tr[0], tr[1], tr[2]))
                .setGlowOnHover(def.isGlowOnHover())
                .setDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI)
                .setInteractive(interactive)
                .setPosition(new WidgetPosition(0, 0, 0)); // позиция уже вычислена в resolveLocation()

        if (def.getGlowColor() != null) {
            int[] gc = def.getGlowColor();
            cfg.setGlowColor(Color.fromRGB(gc[0], gc[1], gc[2]));
        }

        if (def.getTooltip() != null) {
            cfg.setTooltip(parseFormattedText(def.getTooltip()))
                    .setTooltipDelay(def.getTooltipDelay());
        }

        if (withHoverAnimation && def.getHoverAnimation() != null) {
            cfg.setHoverAnimation(def.getHoverAnimation());
        }

        return cfg;
    }

    /** Есть ли у виджета действие по клику, отличное от NONE. */
    static boolean hasClickAction(WidgetDefinition def) {
        return def.getOnClick() != null
                && def.getOnClick().getAction() != WidgetDefinition.ClickAction.ActionType.NONE;
    }

    /**
     * Сделает ли клик по виджету что-нибудь на самом деле: действие задано, а для {@code RUN_SCRIPT}
     * функция есть в скрипте и её тело не пустое. {@code void f(dyn w, dyn p) {}} - считается «ничего».
     *
     * @param publicScreen на публичном экране SWITCH_SCREEN и CLOSE_SCREEN не работают
     */
    static boolean clickDoesSomething(WidgetDefinition def, ScriptContext scripts, boolean publicScreen, java.util.logging.Logger log, String screenId) {
        if (!hasClickAction(def)) return false;
        WidgetDefinition.ClickAction action = def.getOnClick();
        switch (action.getAction()) {
            case SWITCH_SCREEN -> { return !publicScreen && action.getTarget() != null; }
            case CLOSE_SCREEN -> { return !publicScreen; }
            case RUN_SCRIPT -> {
                String fn = action.getFunction();
                if (scripts == null || !scripts.hasScript()) {
                    if (log != null) log.warning("Screen " + screenId + ": widget '" + def.getId() + "' has onClick \"" + fn + "\" but the screen has no script");
                    return false;
                }
                if (fn == null || !scripts.getScript().hasFunction(fn)) {
                    if (log != null) log.warning("Screen " + screenId + ": function '" + fn + "' for widget '" + def.getId() + "' not found in " + scripts.getScript().file());
                    return false;
                }
                return !scripts.getScript().isEmptyFunction(fn);
            }
            default -> { return false; }
        }
    }

    /**
     * Нужно ли виджету вообще следить за взглядом: есть рабочий клик или видимая реакция на наведение
     * (другой текст/фон/спрайт, подсказка, hover-анимация, свечение предмета).
     */
    static boolean isInteractive(WidgetDefinition def, boolean clickable, boolean withHoverAnimation) {
        if (clickable) return true;
        if (def.getTooltip() != null) return true;
        if (withHoverAnimation && def.getHoverAnimation() != null) return true;
        if (def.getType() == WidgetDefinition.WidgetType.ITEM_BUTTON) {
            return def.isGlowOnHover();
        }
        if (def.getType() == WidgetDefinition.WidgetType.SPRITE_BUTTON && def.getHoveredSprite() != null
                && !def.getHoveredSprite().equals(def.getSprite())) return true;
        if (def.getHoveredText() != null && !def.getHoveredText().isEmpty()) return true;
        if (def.getFormattedHoveredText() != null) return true;
        int[] bg = def.getBackgroundColor(), hbg = def.getHoveredBackgroundColor();
        if (def.getBackgroundAlpha() != def.getHoveredBackgroundAlpha()) return true;
        // цвет фона при наведении имеет значение, только если фон вообще виден
        return def.getBackgroundAlpha() > 0 && !java.util.Arrays.equals(bg, hbg);
    }

    // -------------------------------------------------------------------------
    // Разбор значений из YAML
    // -------------------------------------------------------------------------

    static Material parseItemMaterial(String name) {
        try {
            String materialName = name.toUpperCase(Locale.ROOT);
            // Handle common material name variations
            if ("CARROTS".equals(materialName)) {
                materialName = "CARROT";
            }
            Material material = Material.valueOf(materialName);
            // Verify the material is actually an item
            if (!material.isItem()) {
                DisplayLib.getInstance().getLogger().warning("Material " + materialName + " is not an item, using STONE instead");
                return Material.STONE;
            }
            return material;
        } catch (Exception e) {
            DisplayLib.getInstance().getLogger().warning("Invalid material: " + name + ", using STONE instead. Error: " + e.getMessage());
            return Material.STONE;
        }
    }

    /**
     * Конвертирует наш TextAlignment в Bukkit TextDisplay.TextAlignment
     */
    static TextDisplay.TextAlignment convertAlignment(WidgetDefinition.TextAlignment alignment) {
        if (alignment == null) {
            return TextDisplay.TextAlignment.CENTER;
        }

        return switch (alignment) {
            case LEFT -> TextDisplay.TextAlignment.LEFT;
            case CENTERED -> TextDisplay.TextAlignment.CENTER;
            case RIGHT -> TextDisplay.TextAlignment.RIGHT;
        };
    }

    /**
     * Парсит форматированный текст из YAML конфигурации в Adventure Component.
     *
     * <p>Поддерживает два формата:</p>
     * <ul>
     * <li><b>Простая строка:</b> возвращает Component.text(строка)</li>
     * <li><b>Массив объектов:</b> обрабатывает каждый объект с полями text и color</li>
     * </ul>
     *
     * <p>Поддерживаемые поля в объектах:</p>
     * <ul>
     * <li><b>text</b> - текст компонента (обязательное)</li>
     * <li><b>color</b> - цвет текста (hex "#FF0000" или именованный "red", "blue" и т.д.)</li>
     * </ul>
     *
     * @param formattedText объект из YAML (String или List&lt;Map&gt;)
     * @return Adventure Component для отображения
     */
    public static Component parseFormattedText(Object formattedText) {
        if (formattedText == null) {
            return Component.empty();
        }

        if (formattedText instanceof String string) {
            return Component.text(string);
        }

        if (!(formattedText instanceof List<?> textParts)) {
            return Component.text(formattedText.toString());
        }

        TextComponent.Builder builder = Component.text();

        for (Object part : textParts) {
            if (part instanceof String string) {
                // Простая строка без форматирования
                builder.append(Component.text(string));
            } else if (part instanceof Map<?, ?> partMap) {
                Object sprite = partMap.get("sprite");
                if (sprite != null) {
                    // Сегмент-спрайт: {sprite: "item/iron_ingot"} или {atlas: "minecraft:items", sprite: "..."}
                    Object atlas = partMap.get("atlas");
                    builder.append(spriteComponent(atlas != null ? atlas.toString() : null, sprite.toString()));
                    continue;
                }
                // Объект с форматированием - поддерживаем только text и color
                Object text = partMap.get("text");
                TextComponent.Builder partBuilder = Component.text().content(text != null ? text.toString() : "");

                Object color = partMap.get("color");
                if (color != null) {
                    TextColor parsed = parseColor(color.toString());
                    if (parsed != null) {
                        partBuilder.color(parsed);
                    }
                }

                builder.append(partBuilder.build());
            }
        }

        return builder.build();
    }

    /**
     * Компонент-объект со спрайтом атласа (клиент 1.21.9+): в тексте рисуется квадратом 8×8 пикселей.
     * Атлас по умолчанию: {@code minecraft:items} для {@code item/...}, иначе {@code minecraft:blocks}.
     * Неверный ключ не роняет экран - вместо спрайта получится обычный текст с его именем.
     */
    public static Component spriteComponent(String atlas, String sprite) {
        if (sprite == null || sprite.isBlank()) return Component.empty();
        String a = atlas != null && !atlas.isBlank() ? atlas : (sprite.startsWith("item/") ? "minecraft:items" : "minecraft:blocks");
        try {
            return Component.object(ObjectContents.sprite(Key.key(a), Key.key(sprite)));
        } catch (InvalidKeyException e) {
            return Component.text("[" + sprite + "]");
        }
    }

    /** Цвет по hex-строке ("#FFD700") или имени ("green"); null, если не распознан. */
    private static TextColor parseColor(String color) {
        if (color.startsWith("#")) {
            return TextColor.fromHexString(color);
        }
        return NamedTextColor.NAMES.value(color.toLowerCase(Locale.ROOT));
    }
}

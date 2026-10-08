package padej.displayLib.config;

import me.padej.jumper.interp.Config;
import me.padej.jumper.parser.ParseError;
import me.padej.jumper.runtime.JTable;
import me.padej.jumper.runtime.JmpError;
import padej.displayLib.DisplayLib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static padej.displayLib.config.JmcValues.*;

/**
 * Загрузчик экранов из конфигов Jumper ({@code screens/*.jmc}).
 *
 * <p>Файл читается через {@link Config#load(Path)}: это язык скриптов, урезанный до того,
 * что нужно конфигу - значения, переменные, выражения, таблицы и массивы, {@code if}/{@code else},
 * тернарный оператор. Без циклов, функций, {@code import} и без Java: файл выполняется под
 * пустой политикой доступа, так что экран - это данные, а не код. Переменные верхнего уровня
 * и есть конфиг; файл может вместо этого закончиться {@code return { ... };}.</p>
 *
 * <p>Имена полей - camelCase ({@code tickRate}, {@code screenType}); snake_case из прежних
 * YAML-файлов ({@code tick_rate}) тоже принимается. Строки-перечисления ({@code "PRIVATE"},
 * {@code "TEXT_BUTTON"}) не зависят от регистра.</p>
 */
public class ScreenLoader {
    public static final String EXTENSION = ".jmc";

    private final DisplayLib plugin;
    private final Path screensDirectory;

    public ScreenLoader(DisplayLib plugin) {
        this.plugin = plugin;
        this.screensDirectory = plugin.getDataFolder().toPath().resolve("screens");

        try {
            Files.createDirectories(screensDirectory);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create screens directory", e);
        }
    }

    /**
     * Загрузить все экраны из папки screens (рекурсивно)
     */
    public Map<String, ScreenDefinition> loadAllScreens() {
        Map<String, ScreenDefinition> screens = new HashMap<>();

        try {
            if (!Files.exists(screensDirectory)) {
                return screens;
            }

            // Files.walk держит открытые дескрипторы каталогов, пока поток не закрыт
            try (java.util.stream.Stream<Path> files = Files.walk(screensDirectory)) {
                files.filter(path -> path.toString().endsWith(EXTENSION))
                        .sorted()
                        .forEach(path -> {
                            ScreenDefinition screen = loadScreenFromFile(path);
                            if (screen != null) {
                                ScreenDefinition previous = screens.put(screen.getId(), screen);
                                if (previous != null) {
                                    plugin.getLogger().warning("Duplicate screen id '" + screen.getId()
                                            + "' in " + path.getFileName() + ": the later file wins");
                                }
                            }
                        });
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load screens", e);
        }

        return screens;
    }

    /**
     * Загрузить экран из файла {@code <fileId>.jmc}
     */
    public ScreenDefinition loadScreen(String fileId) {
        Path screenFile = screensDirectory.resolve(fileId + EXTENSION);
        if (Files.exists(screenFile)) {
            return loadScreenFromFile(screenFile);
        }
        return null;
    }

    /**
     * Загрузить экран из файла.
     *
     * @return определение экрана или null, если файл не читается (причина в логе)
     */
    public ScreenDefinition loadScreenFromFile(Path file) {
        Object data;
        try {
            data = Config.load(file);
        } catch (ParseError e) {
            plugin.getLogger().warning("Screen " + file.getFileName() + " does not parse ("
                    + e.line + ":" + e.col + "): " + e.getMessage());
            return null;
        } catch (JmpError e) {
            plugin.getLogger().warning("Screen " + file.getFileName() + " failed at line "
                    + e.line() + ": " + e.message());
            return null;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read screen " + file, e);
            return null;
        }

        if (!(data instanceof JTable table)) {
            plugin.getLogger().warning("Screen " + file.getFileName() + ": expected top-level variables or return { ... }, got "
                    + (data == null ? "nothing" : data.getClass().getSimpleName()));
            return null;
        }

        try {
            return parseScreen(table, file);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Error in screen " + file.getFileName(), e);
            return null;
        }
    }

    private ScreenDefinition parseScreen(JTable data, Path file) {
        ScreenDefinition screen = new ScreenDefinition();

        String id = str(data, "id");
        if (id == null || id.isBlank()) {
            String fileName = file.getFileName().toString();
            id = fileName.substring(0, fileName.length() - EXTENSION.length());
        }
        screen.setId(id);

        Integer tickRate = integer(data, "tickRate", "tick_rate");
        if (tickRate != null) screen.setTickRate(tickRate);

        String typeStr = str(data, "screenType", "screen_type", "type");
        if (typeStr != null) {
            ScreenDefinition.ScreenType type = enumValue(ScreenDefinition.ScreenType.class, typeStr);
            if (type == null) {
                plugin.getLogger().warning("Invalid screenType '" + typeStr + "' in " + file.getFileName() + ", using PRIVATE");
                type = ScreenDefinition.ScreenType.PRIVATE;
            }
            screen.setScreenType(type);
        }

        Double radius = number(data, "interactionRadius", "interaction_radius");
        if (radius != null) screen.setInteractionRadius(radius);

        Integer rangeCheck = integer(data, "rangeCheckInterval", "range_check_interval");
        if (rangeCheck != null) screen.setRangeCheckInterval(rangeCheck);

        Double closeDistance = number(data, "closeDistance", "close_distance");
        if (closeDistance != null) screen.setCloseDistance(closeDistance);

        JTable bgData = table(data, "background");
        if (bgData != null) {
            screen.setBackground(parseBackground(bgData));
        }

        // script: "file.jmp"  (также принимается прежняя форма scripts: { file: "..." })
        String script = str(data, "script");
        if (script == null) {
            JTable scripts = table(data, "scripts");
            if (scripts != null) script = str(scripts, "file");
        }
        if (script != null && !script.isBlank()) {
            screen.setScript(script.trim());
        }

        List<?> widgetsData = list(data, "widgets");
        if (widgetsData != null) {
            List<WidgetDefinition> widgets = new ArrayList<>(widgetsData.size());
            int index = 0;
            for (Object item : widgetsData) {
                index++;
                if (item instanceof JTable widgetTable) {
                    WidgetDefinition widget = parseWidget(widgetTable, file, index);
                    if (widget != null) widgets.add(widget);
                } else {
                    plugin.getLogger().warning("Screen " + file.getFileName() + ": widget #" + index + " is not a table");
                }
            }
            screen.setWidgets(widgets);
        }

        return screen;
    }

    private ScreenDefinition.BackgroundDefinition parseBackground(JTable data) {
        ScreenDefinition.BackgroundDefinition bg = new ScreenDefinition.BackgroundDefinition();

        int[] color = ints(data, 3, "color");
        if (color != null) bg.setColor(color);

        Integer alpha = integer(data, "alpha");
        if (alpha != null) bg.setAlpha(alpha);

        float[] scale = floats(data, 3, "scale");
        if (scale != null) bg.setScale(scale);

        float[] position = floats(data, 3, "position");
        if (position != null) bg.setPosition(position);

        String text = str(data, "text");
        if (text != null) bg.setText(text);

        float[] translation = floats(data, 3, "translation");
        if (translation != null) bg.setTranslation(translation);

        return bg;
    }

    /**
     * Папка экранов
     */
    public Path getScreensDirectory() {
        return screensDirectory;
    }

    /**
     * Разбор виджета.
     *
     * <p>Текстовые поля ({@code text}, {@code hoveredText}, {@code tooltip}, ...) принимают
     * строку или массив сегментов {@code { text, color }}; массив приводится к обычным
     * спискам и картам Java для общего разборщика форматированного текста.</p>
     *
     * @return определение виджета или null при ошибке
     */
    private WidgetDefinition parseWidget(JTable data, Path file, int index) {
        WidgetDefinition widget = new WidgetDefinition();
        String where = file.getFileName() + ", widget #" + index;

        widget.setId(str(data, "id"));

        String typeStr = str(data, "type");
        WidgetDefinition.WidgetType type = enumValue(WidgetDefinition.WidgetType.class, typeStr);
        if (type == null) {
            // Тип можно не писать: предмет есть - предметная кнопка, иначе текстовая
            if (typeStr != null) {
                plugin.getLogger().warning(where + ": invalid widget type '" + typeStr + "'");
                return null;
            }
            type = has(data, "material") ? WidgetDefinition.WidgetType.ITEM_BUTTON : WidgetDefinition.WidgetType.TEXT_BUTTON;
        }
        widget.setType(type);

        // Текстовые поля (строка или массив сегментов)
        Object text = get(data, "text");
        if (text instanceof String s) {
            widget.setText(s);
        } else if (text != null) {
            widget.setFormattedText(toJava(text));
        }

        Object hoveredText = get(data, "hoveredText", "hovered_text");
        if (hoveredText instanceof String s) {
            widget.setHoveredText(s);
        } else if (hoveredText != null) {
            widget.setFormattedHoveredText(toJava(hoveredText));
        }

        Object formattedText = get(data, "formattedText", "formatted_text");
        if (formattedText != null) widget.setFormattedText(toJava(formattedText));

        Object formattedHoveredText = get(data, "formattedHoveredText", "formatted_hovered_text");
        if (formattedHoveredText != null) widget.setFormattedHoveredText(toJava(formattedHoveredText));

        String material = str(data, "material");
        if (material != null) widget.setMaterial(material.trim().toUpperCase(java.util.Locale.ROOT));

        Boolean glowOnHover = bool(data, "glowOnHover", "glow_on_hover");
        if (glowOnHover != null) widget.setGlowOnHover(glowOnHover);

        int[] glowColor = ints(data, 3, "glowColor", "glow_color");
        if (glowColor != null) widget.setGlowColor(glowColor);

        float[] position = floats(data, 3, "position");
        if (position != null) widget.setPosition(position);

        float[] scale = floats(data, 3, "scale");
        if (scale != null) widget.setScale(scale);

        float[] tolerance = floats(data, 2, "tolerance");
        if (tolerance != null) widget.setTolerance(tolerance);

        float[] translation = floats(data, 3, "translation");
        if (translation != null) widget.setTranslation(translation);

        int[] backgroundColor = ints(data, 3, "backgroundColor", "background_color");
        if (backgroundColor != null) widget.setBackgroundColor(backgroundColor);

        int[] hoveredBackgroundColor = ints(data, 3, "hoveredBackgroundColor", "hovered_background_color");
        if (hoveredBackgroundColor != null) widget.setHoveredBackgroundColor(hoveredBackgroundColor);

        Integer backgroundAlpha = integer(data, "backgroundAlpha", "background_alpha");
        if (backgroundAlpha != null) widget.setBackgroundAlpha(backgroundAlpha);

        Integer hoveredBackgroundAlpha = integer(data, "hoveredBackgroundAlpha", "hovered_background_alpha");
        if (hoveredBackgroundAlpha != null) widget.setHoveredBackgroundAlpha(hoveredBackgroundAlpha);

        String alignmentStr = str(data, "alignment");
        if (alignmentStr != null) {
            WidgetDefinition.TextAlignment alignment = enumValue(WidgetDefinition.TextAlignment.class, alignmentStr);
            if (alignment != null) {
                widget.setAlignment(alignment);
            } else {
                plugin.getLogger().warning(where + ": invalid alignment '" + alignmentStr + "'");
            }
        }

        Object tooltip = get(data, "tooltip");
        if (tooltip != null) widget.setTooltip(toJava(tooltip));

        int[] tooltipColor = ints(data, 3, "tooltipColor", "tooltip_color");
        if (tooltipColor != null) widget.setTooltipColor(tooltipColor);

        Integer tooltipDelay = integer(data, "tooltipDelay", "tooltip_delay");
        if (tooltipDelay != null) widget.setTooltipDelay(tooltipDelay);

        Object onClick = get(data, "onClick", "on_click", "click");
        if (onClick != null) {
            WidgetDefinition.ClickAction action = parseClickAction(onClick, where);
            if (action != null) widget.setOnClick(action);
        }

        JTable hoverAnimation = table(data, "hoverAnimation", "hover_animation");
        if (hoverAnimation != null) {
            HoverAnimation animation = parseHoverAnimation(hoverAnimation, where);
            if (animation != null) widget.setHoverAnimation(animation);
        }

        return widget;
    }

    /**
     * Действие по клику. Формы записи:
     * <pre>
     * onClick: "buySword"                                   // функция скрипта
     * onClick: "close"                                       // закрыть экран
     * onClick: { action: "RUN_SCRIPT", function: "buySword" }
     * onClick: { switchTo: "main_menu" }
     * onClick: { action: "SWITCH_SCREEN", target: "main_menu" }
     * onClick: { action: "CLOSE_SCREEN" }
     * onClick: { action: "NONE" }
     * </pre>
     */
    private WidgetDefinition.ClickAction parseClickAction(Object onClick, String where) {
        WidgetDefinition.ClickAction action = new WidgetDefinition.ClickAction();

        if (onClick instanceof String s) {
            String value = s.trim();
            switch (value.toUpperCase(java.util.Locale.ROOT)) {
                case "", "NONE" -> action.setAction(WidgetDefinition.ClickAction.ActionType.NONE);
                case "CLOSE", "CLOSE_SCREEN" -> action.setAction(WidgetDefinition.ClickAction.ActionType.CLOSE_SCREEN);
                default -> {
                    action.setAction(WidgetDefinition.ClickAction.ActionType.RUN_SCRIPT);
                    action.setFunction(value);
                }
            }
            return action;
        }

        if (!(onClick instanceof JTable table)) {
            plugin.getLogger().warning(where + ": onClick must be a string or a table");
            return null;
        }

        String function = str(table, "function", "call");
        String target = str(table, "switchTo", "switch_to", "target", "screen");
        String actionStr = str(table, "action");

        WidgetDefinition.ClickAction.ActionType type;
        if (actionStr != null) {
            type = enumValue(WidgetDefinition.ClickAction.ActionType.class, actionStr);
            if (type == null) {
                plugin.getLogger().warning(where + ": invalid onClick action '" + actionStr + "'");
                return null;
            }
        } else if (function != null) {
            type = WidgetDefinition.ClickAction.ActionType.RUN_SCRIPT;
        } else if (target != null) {
            type = WidgetDefinition.ClickAction.ActionType.SWITCH_SCREEN;
        } else if (Boolean.TRUE.equals(bool(table, "close"))) {
            type = WidgetDefinition.ClickAction.ActionType.CLOSE_SCREEN;
        } else {
            type = WidgetDefinition.ClickAction.ActionType.NONE;
        }

        action.setAction(type);
        action.setFunction(function);
        action.setTarget(target);
        return action;
    }

    /**
     * Разбор hover-анимации
     */
    private HoverAnimation parseHoverAnimation(JTable data, String where) {
        HoverAnimation animation = new HoverAnimation();

        String typeStr = str(data, "type");
        if (typeStr != null) {
            HoverAnimation.AnimationType type = enumValue(HoverAnimation.AnimationType.class, typeStr);
            if (type == null) {
                plugin.getLogger().warning(where + ": invalid hover animation type '" + typeStr + "'");
                return null;
            }
            animation.setType(type);
        }

        Integer duration = integer(data, "duration");
        if (duration != null) animation.setDuration(duration);

        String easingStr = str(data, "easing");
        if (easingStr != null) {
            HoverAnimation.EasingType easing = enumValue(HoverAnimation.EasingType.class, easingStr);
            if (easing != null) {
                animation.setEasing(easing);
            } else {
                plugin.getLogger().warning(where + ": invalid easing '" + easingStr + "'");
            }
        }

        Boolean reverseOnExit = bool(data, "reverseOnExit", "reverse_on_exit");
        if (reverseOnExit != null) animation.setReverseOnExit(reverseOnExit);

        Integer delay = integer(data, "delay");
        if (delay != null) animation.setDelay(delay);

        Boolean loop = bool(data, "loop");
        if (loop != null) animation.setLoop(loop);

        Integer loopCount = integer(data, "loopCount", "loop_count");
        if (loopCount != null) animation.setLoopCount(loopCount);

        String presetStr = str(data, "preset");
        if (presetStr != null) {
            HoverAnimation.AnimationPreset preset = enumValue(HoverAnimation.AnimationPreset.class, presetStr);
            if (preset != null) {
                animation.setPreset(preset);
            } else {
                plugin.getLogger().warning(where + ": invalid animation preset '" + presetStr + "'");
            }
        }

        Double intensity = number(data, "intensity");
        if (intensity != null) animation.setIntensity(intensity.floatValue());

        float[] scale = floats(data, 3, "scale");
        if (scale != null) animation.setScale(scale);

        float[] offset = floats(data, 3, "offset");
        if (offset != null) animation.setOffset(offset);

        float[] rotation = floats(data, 3, "rotation");
        if (rotation != null) animation.setRotation(rotation);

        float[] axis = floats(data, 3, "axis");
        if (axis != null) animation.setAxis(axis);

        float[] translation = floats(data, 3, "translation");
        if (translation != null) animation.setTranslation(translation);

        List<?> effectsData = list(data, "effects");
        if (effectsData != null) {
            List<HoverAnimation> effects = new ArrayList<>(effectsData.size());
            for (Object item : effectsData) {
                if (item instanceof JTable effectTable) {
                    HoverAnimation effect = parseHoverAnimation(effectTable, where);
                    if (effect != null) effects.add(effect);
                }
            }
            animation.setEffects(effects.toArray(new HoverAnimation[0]));
        }

        return animation;
    }
}

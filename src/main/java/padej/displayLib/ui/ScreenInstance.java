package padej.displayLib.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.joml.Vector3f;
import padej.displayLib.DisplayLib;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.WidgetDefinition;
import padej.displayLib.lua.LuaContext;
import padej.displayLib.lua.LuaEngine;
import padej.displayLib.lua.api.WidgetAPI;
import padej.displayLib.ui.widgets.*;
import org.luaj.vm2.LuaValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime экземпляр экрана.
 * Создаёт entity по ScreenDefinition и управляет ими.
 * Не знает ни про follow, ни про save — этого больше нет.
 */
public class ScreenInstance extends WidgetManager {
    private final String screenId;
    private final ScreenDefinition definition;
    private final LuaEngine luaEngine;
    private LuaContext luaContext;
    
    /** Быстрый доступ к виджетам по id из YAML */
    private final Map<String, Widget> widgetById = new HashMap<>();
    
    /** Единая ориентация для всех элементов экрана */
    private final float screenYaw;
    private final float screenPitch;
    
    /** Флаг предотвращения рекурсии при закрытии */
    private boolean isClosing = false;
    
    /** Последнее состояние interaction range для отладки */
    private boolean lastInteractionState = true;
    
    /** Смещение виджетов по глубине относительно фона для избежания Z-fighting */
    private static final float WIDGET_DEPTH_OFFSET = 0.001f;
    
    /** Увеличенное смещение для ItemDisplay виджетов */
    private static final float ITEM_WIDGET_DEPTH_OFFSET = 0.01f;

    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location, LuaEngine luaEngine) {
        super(viewer, location);
        this.screenId = screenId;
        this.definition = definition;
        this.luaEngine = luaEngine;
        
        // Создаем Lua контекст
        if (luaEngine != null) {
            this.luaContext = luaEngine.createContext(this, viewer);
        }
        
        // Вычисляем единую ориентацию экрана один раз
        Location viewerLoc = viewer.getLocation().add(0, viewer.getHeight() / 2, 0);
        double dx = viewerLoc.getX() - location.getX();
        double dy = viewerLoc.getY() - location.getY();
        double dz = viewerLoc.getZ() - location.getZ();

        double yaw = Math.atan2(dz, dx);
        double pitch = Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));

        this.screenYaw = (float) Math.toDegrees(yaw) - 90;
        this.screenPitch = (float) Math.toDegrees(-pitch);

        spawnBackground();
        spawnWidgets();
        
        // Вызываем on_open после создания всех виджетов
        callLuaFunction("on_open");
    }
    
    /**
     * Конструктор с заданной ориентацией (для переключения экранов)
     */
    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location, float yaw, float pitch, LuaEngine luaEngine) {
        super(viewer, location);
        this.screenId = screenId;
        this.definition = definition;
        this.luaEngine = luaEngine;
        
        // Создаем Lua контекст
        if (luaEngine != null) {
            this.luaContext = luaEngine.createContext(this, viewer);
        }
        
        // Используем переданную ориентацию
        this.screenYaw = yaw;
        this.screenPitch = pitch;

        spawnBackground();
        spawnWidgets();
        
        // Вызываем on_open после создания всех виджетов
        callLuaFunction("on_open");
    }

    // -------------------------------------------------------------------------
    // Spawning
    // -------------------------------------------------------------------------

    private void spawnBackground() {
        ScreenDefinition.BackgroundDefinition bg = definition.getBackground();
        if (bg == null) return;

        int[] c = bg.getColor();
        float[] s = bg.getScale();
        float[] p = bg.getPosition() != null ? bg.getPosition() : new float[]{0.0f, 0.0f, 0.0f};
        float[] tr = bg.getTranslation() != null ? bg.getTranslation() : new float[]{0.0f, 0.0f, 0.0f};

        // Вычисляем финальную позицию
        Location backgroundLocation = resolveLocation(p);

        TextDisplayButtonConfig cfg = new TextDisplayButtonConfig(
                Component.text(bg.getText()),
                Component.text(bg.getText()),
                null  // Убираем onClick для фона
        )
                .setScale(s[0], s[1], s[2])
                .setBackgroundColor(Color.fromRGB(c[0], c[1], c[2]))
                .setBackgroundAlpha(bg.getAlpha())
                .setHoveredBackgroundColor(Color.fromRGB(c[0], c[1], c[2]))  // Тот же цвет для hover
                .setHoveredBackgroundAlpha(bg.getAlpha())  // Та же прозрачность для hover
                .setTolerance(0.0, 0.0)  // Убираем толерантность - нельзя кликнуть
                .setPosition(new WidgetPosition(0, 0, 0))
                .setTranslation(padej.displayLib.utils.TransformationUtil.createAlignedTranslation(s[0], tr));

        // Фон создается с учетом position из YAML (используем уже вычисленную позицию)
        TextDisplayButtonWidget backgroundWidget = TextDisplayButtonWidget.create(backgroundLocation, viewer, cfg);
        
        // Сохраняем единую ориентацию экрана
        if (backgroundWidget != null) {
            backgroundWidget.saveRotation(screenYaw, screenPitch);
        }
        
        addDrawableChild(backgroundWidget);
    }

    private void spawnWidgets() {
        if (definition.getWidgets() == null) return;

        for (WidgetDefinition def : definition.getWidgets()) {
            Widget widget = buildWidget(def);
            if (widget == null) continue;

            addDrawableChild(widget);
            if (def.getId() != null) {
                widgetById.put(def.getId(), widget);
            }
        }
    }

    private Widget buildWidget(WidgetDefinition def) {
        Location widgetLoc = resolveLocation(def.getPosition());
        return switch (def.getType()) {
            case TEXT_BUTTON -> buildTextWidget(def, widgetLoc);
            case ITEM_BUTTON -> buildItemWidget(def, null); // Позиция вычисляется внутри buildItemWidget
        };
    }

    // -------------------------------------------------------------------------
    // Widget builders
    // -------------------------------------------------------------------------

    private TextDisplayButtonWidget buildTextWidget(WidgetDefinition def, Location loc) {
        int[] bg = def.getBackgroundColor();
        int[] hbg = def.getHoveredBackgroundColor();
        float[] s = def.getScale();
        float[] t = def.getTolerance();
        float[] tr = def.getTranslation();

        // Определяем onClick только если действие не NONE
        Runnable onClickAction = null;
        if (def.getOnClick() != null && def.getOnClick().getAction() != WidgetDefinition.ClickAction.ActionType.NONE) {
            onClickAction = () -> handleClick(def);
        }

        // Определяем текст - может быть обычной строкой или форматированным JSON
        Component textComponent;
        if (def.getFormattedText() != null) {
            textComponent = parseFormattedText(def.getFormattedText());
        } else {
            textComponent = Component.text(def.getText() != null ? def.getText() : "");
        }
        
        // Определяем hoveredText - может быть обычной строкой или форматированным JSON
        Component hoveredTextComponent;
        if (def.getFormattedHoveredText() != null) {
            hoveredTextComponent = parseFormattedText(def.getFormattedHoveredText());
        } else if (def.getHoveredText() != null && !def.getHoveredText().isEmpty()) {
            hoveredTextComponent = Component.text(def.getHoveredText());
        } else {
            hoveredTextComponent = textComponent; // Используем обычный текст как fallback
        }

        TextDisplayButtonConfig cfg = new TextDisplayButtonConfig(
                textComponent,
                hoveredTextComponent,
                onClickAction
        )
                .setScale(s[0], s[1], s[2])
                .setTolerance(t[0], t[1])
                .setTranslation(new Vector3f(tr[0], tr[1], tr[2]))
                .setBackgroundColor(Color.fromRGB(bg[0], bg[1], bg[2]))
                .setBackgroundAlpha(def.getBackgroundAlpha())
                .setHoveredBackgroundColor(Color.fromRGB(hbg[0], hbg[1], hbg[2]))
                .setHoveredBackgroundAlpha(def.getHoveredBackgroundAlpha())
                .setTextAlignment(convertAlignment(def.getAlignment()))
                .setPosition(new WidgetPosition(0, 0, 0)); // Позиция уже вычислена в resolveLocation()

        if (def.getTooltip() != null) {
            Component tooltipComponent = parseFormattedText(def.getTooltip());
            cfg.setTooltip(tooltipComponent);
            cfg.setTooltipDelay(def.getTooltipDelay());
        }

        TextDisplayButtonWidget widget = TextDisplayButtonWidget.create(loc, viewer, cfg);
        
        // Сохраняем единую ориентацию экрана (как у фона)
        if (widget != null) {
            widget.saveRotation(screenYaw, screenPitch);
        }
        
        return widget;
    }

    private ItemDisplayButtonWidget buildItemWidget(WidgetDefinition def, Location loc) {
        Material material;
        try {
            String materialName = def.getMaterial().toUpperCase();
            // Handle common material name variations
            if ("CARROTS".equals(materialName)) {
                materialName = "CARROT";
            }
            material = Material.valueOf(materialName);
            // Verify the material is actually an item
            if (!material.isItem()) {
                DisplayLib.getInstance().getLogger().warning("Material " + materialName + " is not an item, using STONE instead");
                material = Material.STONE;
            }
        } catch (Exception e) {
            DisplayLib.getInstance().getLogger().warning("Invalid material: " + def.getMaterial() + ", using STONE instead. Error: " + e.getMessage());
            material = Material.STONE;
        }

        float[] s = def.getScale();
        float[] t = def.getTolerance();
        float[] tr = def.getTranslation();

        // Определяем onClick только если действие не NONE
        Runnable onClickAction = null;
        if (def.getOnClick() != null && def.getOnClick().getAction() != WidgetDefinition.ClickAction.ActionType.NONE) {
            onClickAction = () -> handleClick(def);
        }

        // Используем увеличенное смещение для ItemDisplay виджетов
        Location itemLoc = resolveLocation(def.getPosition(), ITEM_WIDGET_DEPTH_OFFSET);

        ItemDisplayButtonConfig cfg = new ItemDisplayButtonConfig(material, onClickAction)
                .setScale(s[0], s[1], s[2])
                .setTolerance(t[0], t[1])
                .setTranslation(new Vector3f(tr[0], tr[1], tr[2]))
                .setGlowOnHover(def.isGlowOnHover())
                .setDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.GUI) // Используем GUI transform
                .setPosition(new WidgetPosition(0, 0, 0)); // Позиция уже вычислена в resolveLocation()

        if (def.getGlowColor() != null) {
            int[] gc = def.getGlowColor();
            cfg.setGlowColor(Color.fromRGB(gc[0], gc[1], gc[2]));
        }

        if (def.getTooltip() != null) {
            Component tooltipComponent = parseFormattedText(def.getTooltip());
            cfg.setTooltip(tooltipComponent)
                    .setTooltipDelay(def.getTooltipDelay());
        }

        ItemDisplayButtonWidget widget = ItemDisplayButtonWidget.create(itemLoc, viewer, cfg);
        
        // Сохраняем единую ориентацию экрана (как у фона)
        if (widget != null) {
            widget.saveRotation(screenYaw, screenPitch);
        }
        
        return widget;
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    private void handleClick(WidgetDefinition def) {
        WidgetDefinition.ClickAction action = def.getOnClick();
        if (action == null) return;

        switch (action.getAction()) {
            case NONE -> {}

            // Сохраняем позицию текущего экрана через switchScreen
            case SWITCH_SCREEN -> {
                if (action.getTarget() != null) {
                    UIManager.getInstance().switchScreen(viewer, action.getTarget());
                }
            }

            case CLOSE_SCREEN -> UIManager.getInstance().closeScreen(viewer);

            // Lua скрипт
            case RUN_SCRIPT -> {
                if (action.getFunction() != null) {
                    callLuaFunctionWithWidget(action.getFunction(), def);
                } else {
                    viewer.sendMessage("§cScript function not specified");
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Location helper
    // -------------------------------------------------------------------------

    private Location resolveLocation(float[] pos) {
        return resolveLocation(pos, WIDGET_DEPTH_OFFSET); // Виджеты чуть впереди фона
    }
    
    private Location resolveLocation(float[] pos, float depthOffset) {
        if (pos == null || pos.length < 3) return location.clone();

        Location base = location.clone();
        var dir = base.getDirection();
        var right = dir.getCrossProduct(new org.bukkit.util.Vector(0, 1, 0)).normalize();
        var up = right.getCrossProduct(dir).normalize();

        base.add(right.multiply(pos[0]));
        base.add(up.multiply(pos[1]));
        base.add(dir.multiply(pos[2] - depthOffset)); // Виджеты ближе к игроку для избежания Z-fighting
        return base;
    }

    // -------------------------------------------------------------------------
    // WidgetManager contract
    // -------------------------------------------------------------------------

    @Override
    protected ScreenDefinition getScreenDefinition() {
        return definition;
    }
    
    @Override
    protected boolean isPlayerInInteractionRange() {
        // Проверяем interaction_radius для оптимизации hover detection
        double interactionRadius = definition.getInteractionRadius();
        if (interactionRadius > 0) {
            double distanceSq = viewer.getLocation().distanceSquared(location);
            double distance = Math.sqrt(distanceSq);
            boolean inRange = distanceSq <= interactionRadius * interactionRadius;
            
            // Update state tracking
            lastInteractionState = inRange;
            
            return inRange;
        }
        return true; // Если радиус не ограничен, всегда в зоне взаимодействия
    }
    
    @Override
    protected boolean isPlayerInRange() {
        double distanceSq = viewer.getLocation().distanceSquared(location);
        double distance = Math.sqrt(distanceSq);
        
        // Для isPlayerInRange проверяем ТОЛЬКО close_distance (автозакрытие)
        // interaction_radius проверяется отдельно в isPlayerInInteractionRange
        double closeDistance = definition.getCloseDistance();
        boolean result = true;
        
        if (closeDistance > 0) {
            result = distanceSq <= closeDistance * closeDistance;
        }
        
        return result;
    }

    @Override
    protected void tryClose() {
        if (isClosing) return;
        isClosing = true;
        
        // Вызываем on_close перед закрытием
        callLuaFunction("on_close");
        
        // Очищаем Lua контекст
        if (luaContext != null) {
            luaContext.cleanup();
        }
        
        UIManager.getInstance().forceCloseScreen(viewer);
    }

    // -------------------------------------------------------------------------
    // Public API (для Lua в будущем)
    // -------------------------------------------------------------------------

    public Widget getWidget(String id) {
        return widgetById.get(id);
    }

    public Map<String, Widget> getWidgets() {
        return Map.copyOf(widgetById);
    }

    public String getScreenId() {
        return screenId;
    }

    public ScreenDefinition getDefinition() {
        return definition;
    }
    
    /**
     * Публичный метод для проверки interaction_radius (для UIManager)
     */
    public boolean checkPlayerInInteractionRange() {
        return isPlayerInInteractionRange();
    }
    
    /**
     * Получить ориентацию экрана (yaw, pitch)
     */
    public float[] getScreenOrientation() {
        return new float[]{screenYaw, screenPitch};
    }
    
    // -------------------------------------------------------------------------
    // Lua integration
    // -------------------------------------------------------------------------
    
    /**
     * Вызвать Lua функцию экрана
     */
    private void callLuaFunction(String functionName) {
        if (luaEngine == null || luaContext == null) return;
        
        Map<String, String> scripts = definition.getScripts();
        if (scripts == null) return;
        
        String scriptFile = scripts.get("file");
        if (scriptFile != null) {
            luaEngine.callFunction(luaContext, scriptFile, functionName);
        }
    }
    
    /**
     * Вызвать Lua функцию с установленным widget контекстом
     */
    private void callLuaFunctionWithWidget(String functionName, WidgetDefinition widgetDef) {
        if (luaEngine == null || luaContext == null) return;
        
        Map<String, String> scripts = definition.getScripts();
        if (scripts == null) return;
        
        String scriptFile = scripts.get("file");
        if (scriptFile != null) {
            // Устанавливаем widget в глобальный контекст
            Widget widget = widgetById.get(widgetDef.getId());
            if (widget != null) {
                luaContext.getGlobals().set("widget", new WidgetAPI(widget));
            }
            
            luaEngine.callFunction(luaContext, scriptFile, functionName);
            
            // Очищаем widget из контекста
            luaContext.getGlobals().set("widget", LuaValue.NIL);
        }
    }
    
    /**
     * Получить Lua контекст (для внешнего использования)
     */
    public LuaContext getLuaContext() {
        return luaContext;
    }
    
    /**
     * Конвертирует наш TextAlignment в Bukkit TextDisplay.TextAlignment
     */
    private org.bukkit.entity.TextDisplay.TextAlignment convertAlignment(WidgetDefinition.TextAlignment alignment) {
        if (alignment == null) {
            return org.bukkit.entity.TextDisplay.TextAlignment.CENTER;
        }
        
        return switch (alignment) {
            case LEFT -> org.bukkit.entity.TextDisplay.TextAlignment.LEFT;
            case CENTERED -> org.bukkit.entity.TextDisplay.TextAlignment.CENTER;
            case RIGHT -> org.bukkit.entity.TextDisplay.TextAlignment.RIGHT;
        };
    }
    
    /**
     * Конвертирует JSON массив форматированного текста в Adventure Component
     */
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
    @SuppressWarnings("unchecked")
    private Component parseFormattedText(Object formattedText) {
        if (formattedText == null) {
            return Component.empty();
        }
        
        if (formattedText instanceof String) {
            return Component.text((String) formattedText);
        }
        
        if (!(formattedText instanceof java.util.List)) {
            return Component.text(formattedText.toString());
        }
        
        java.util.List<Object> textParts = (java.util.List<Object>) formattedText;
        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        
        for (Object part : textParts) {
            if (part instanceof String) {
                // Простая строка без форматирования
                builder.append(Component.text((String) part));
            } else if (part instanceof Map) {
                // Объект с форматированием - поддерживаем только text и color
                Map<String, Object> partMap = (Map<String, Object>) part;
                String text = (String) partMap.getOrDefault("text", "");
                
                net.kyori.adventure.text.TextComponent.Builder partBuilder = 
                    Component.text().content(text);
                
                // Применяем только цвет
                String color = (String) partMap.get("color");
                if (color != null) {
                    try {
                        if (color.startsWith("#")) {
                            // Hex цвет
                            partBuilder.color(net.kyori.adventure.text.format.TextColor.fromHexString(color));
                        } else {
                            // Именованный цвет
                            partBuilder.color(net.kyori.adventure.text.format.NamedTextColor.NAMES.value(color.toLowerCase()));
                        }
                    } catch (Exception e) {
                        // Если цвет не распознан, игнорируем
                    }
                }
                
                builder.append(partBuilder.build());
            }
        }
        
        return builder.build();
    }
}
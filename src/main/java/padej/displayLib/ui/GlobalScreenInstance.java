package padej.displayLib.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import padej.displayLib.DisplayLib;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.WidgetDefinition;
import padej.displayLib.lua.LuaContext;
import padej.displayLib.lua.GlobalLuaContext;
import padej.displayLib.lua.LuaEngine;
import padej.displayLib.lua.api.WidgetAPI;
import padej.displayLib.lua.api.PlayerAPI;
import padej.displayLib.ui.widgets.*;
import padej.displayLib.utils.PointDetection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Публичный экран без владельца, фиксированный в мире.
 * Любой игрок поблизости может взаимодействовать.
 * Hover визуалы отключены, работают только tooltips.
 */
public class GlobalScreenInstance {
    private final String screenId;
    private final ScreenDefinition definition;
    private final Location location;
    private final float screenYaw;
    private final float screenPitch;
    private final LuaEngine luaEngine;
    private GlobalLuaContext luaContext;
    
    /** Все виджеты экрана */
    private final List<Widget> children = new ArrayList<>();
    
    /** Быстрый доступ к виджетам по id */
    private final Map<String, Widget> widgetById = new HashMap<>();
    
    /** Радиус взаимодействия в квадрате для оптимизации */
    private final double radiusSq;
    
    /** Интервал проверки расстояния в тиках */
    private final int rangeCheckInterval;
    
    /** Счетчик тиков для проверки расстояния */
    private int rangeCheckTimer = 0;
    
    /** Игроки поблизости (обновляется каждые rangeCheckInterval тиков) */
    private final List<Player> nearbyPlayers = new ArrayList<>();
    
    /** Какие игроки наводятся на какой виджет */
    private final Map<Widget, Set<Player>> widgetHoveredBy = new ConcurrentHashMap<>();
    
    /** Смещение виджетов по глубине относительно фона */
    private static final float WIDGET_DEPTH_OFFSET = 0.001f;
    private static final float ITEM_WIDGET_DEPTH_OFFSET = 0.01f;

    public GlobalScreenInstance(String screenId, ScreenDefinition definition,
                               Location location, float yaw, float pitch, LuaEngine luaEngine) {
        this.screenId = screenId;
        this.definition = definition;
        this.location = location.clone();
        this.screenYaw = yaw;
        this.screenPitch = pitch;
        this.luaEngine = luaEngine;
        
        // Для публичных экранов создаем постоянный контекст без игрока
        // Игрок будет устанавливаться временно при каждом вызове функции
        this.luaContext = luaEngine.createGlobalContext(this, null);
        
        this.radiusSq = definition.getInteractionRadius() * definition.getInteractionRadius();
        this.rangeCheckInterval = definition.getRangeCheckInterval();
        
        spawnBackground();
        spawnWidgets();
        
        // Вызываем on_open без контекста игрока
        callLuaFunction("on_open", null);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void update() {
        // Фаза 1: обновление кеша nearbyPlayers (каждые rangeCheckInterval тиков)
        rangeCheckTimer++;
        if (rangeCheckTimer >= rangeCheckInterval) {
            rangeCheckTimer = 0;
            refreshNearbyPlayers();
        }

        // Фаза 2: обнаружение hover и tooltip (каждый тик, только nearbyPlayers)
        updateHoverDetection();
    }

    public void remove() {
        // Очищаем все tooltips
        for (Player player : nearbyPlayers) {
            player.clearTitle();
        }
        
        // Удаляем все виджеты
        for (Widget widget : new ArrayList<>(children)) {
            widget.remove();
        }
        children.clear();
        widgetById.clear();
        widgetHoveredBy.clear();
        
        // Вызываем on_close без контекста игрока
        callLuaFunction("on_close", null);
        
        // Очищаем Lua контекст
        if (luaContext != null) {
            luaContext.cleanup();
            luaContext = null;
        }
    }

    public void handleClickBy(Player player) {
        // Для публичных экранов проверяем все виджеты, а не только тот на который наведен игрок
        Widget clickedWidget = null;
        
        // Сначала проверяем hover (для совместимости и точности)
        Widget hoveredWidget = getHoveredWidgetFor(player);
        if (hoveredWidget != null) {
            clickedWidget = hoveredWidget;
        } else {
            // Если игрок не наведен ни на что, проверяем геометрически все виджеты
            for (Widget widget : children) {
                if (isPlayerLookingAtWidget(player, widget)) {
                    clickedWidget = widget;
                    break; // Берем первый найденный виджет
                }
            }
        }
        
        if (clickedWidget != null) {
            // Находим определение виджета для получения onClick действия
            WidgetDefinition widgetDef = findWidgetDefinition(clickedWidget);
            if (widgetDef != null && widgetDef.getOnClick() != null) {
                handleClick(widgetDef, player);
            }
        }
    }

    private WidgetDefinition findWidgetDefinition(Widget widget) {
        if (definition.getWidgets() == null) return null;
        
        // Ищем определение виджета по ID
        for (Map.Entry<String, Widget> entry : widgetById.entrySet()) {
            if (entry.getValue() == widget) {
                String widgetId = entry.getKey();
                // Находим определение с таким ID
                for (WidgetDefinition def : definition.getWidgets()) {
                    if (widgetId.equals(def.getId())) {
                        return def;
                    }
                }
                break;
            }
        }
        return null;
    }

    private void handleClick(WidgetDefinition def, Player player) {
        WidgetDefinition.ClickAction action = def.getOnClick();
        if (action == null) return;

        switch (action.getAction()) {
            case NONE -> {}
            case SWITCH_SCREEN -> {
                // Для публичных экранов SWITCH_SCREEN не имеет смысла
                // Можно логировать предупреждение
            }
            case CLOSE_SCREEN -> {
                // Публичные экраны не закрываются по клику
                // Можно логировать предупреждение
            }
            case RUN_SCRIPT -> {
                if (action.getFunction() != null) {
                    callLuaFunctionWithPlayer(action.getFunction(), def, player);
                }
            }
        }
    }

    public Widget getWidget(String id) {
        return widgetById.get(id);
    }

    public String getScreenId() {
        return screenId;
    }

    public Location getLocation() {
        return location.clone();
    }

    public List<Player> getNearbyPlayers() {
        return new ArrayList<>(nearbyPlayers);
    }

    public Widget getHoveredWidgetFor(Player player) {
        for (Map.Entry<Widget, Set<Player>> entry : widgetHoveredBy.entrySet()) {
            if (entry.getValue().contains(player)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public ScreenDefinition getDefinition() {
        return definition;
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    private void spawnBackground() {
        ScreenDefinition.BackgroundDefinition bg = definition.getBackground();
        if (bg == null) return;

        int[] c = bg.getColor();
        float[] s = bg.getScale();
        float[] p = bg.getPosition() != null ? bg.getPosition() : new float[]{0.0f, 0.0f, 0.0f};
        float[] tr = bg.getTranslation() != null ? bg.getTranslation() : new float[]{0.0f, 0.0f, 0.0f};

        Location backgroundLocation = resolveLocation(p);

        // Создаем фон без viewer (используем null)
        TextDisplayButtonConfig cfg = new TextDisplayButtonConfig(
                Component.text(bg.getText()),
                Component.text(bg.getText()),
                null
        )
                .setScale(s[0], s[1], s[2])
                .setBackgroundColor(Color.fromRGB(c[0], c[1], c[2]))
                .setBackgroundAlpha(bg.getAlpha())
                .setHoveredBackgroundColor(Color.fromRGB(c[0], c[1], c[2]))
                .setHoveredBackgroundAlpha(bg.getAlpha())
                .setTolerance(0.0, 0.0)
                .setPosition(new WidgetPosition(0, 0, 0))
                .setTranslation(padej.displayLib.utils.TransformationUtil.createAlignedTranslation(s[0], tr));

        TextDisplayButtonWidget backgroundWidget = TextDisplayButtonWidget.create(backgroundLocation, null, cfg);
        
        if (backgroundWidget != null) {
            backgroundWidget.saveRotation(screenYaw, screenPitch);
        }
        
        children.add(backgroundWidget);
    }

    private void spawnWidgets() {
        if (definition.getWidgets() == null) return;

        for (WidgetDefinition def : definition.getWidgets()) {
            Widget widget = buildWidget(def);
            if (widget == null) continue;

            children.add(widget);
            if (def.getId() != null) {
                widgetById.put(def.getId(), widget);
            }
        }
    }

    private Widget buildWidget(WidgetDefinition def) {
        Location widgetLoc = resolveLocation(def.getPosition());
        return switch (def.getType()) {
            case TEXT_BUTTON -> buildTextWidget(def, widgetLoc);
            case ITEM_BUTTON -> buildItemWidget(def);
        };
    }

    private TextDisplayButtonWidget buildTextWidget(WidgetDefinition def, Location loc) {
        int[] bg = def.getBackgroundColor();
        int[] hbg = def.getHoveredBackgroundColor();
        float[] s = def.getScale();
        float[] t = def.getTolerance();
        float[] tr = def.getTranslation();

        // Для публичных экранов НЕ создаем onClick действие в виджете
        // Обработка будет через handleClickBy
        Runnable onClickAction = null;

        Component textComponent;
        if (def.getFormattedText() != null) {
            textComponent = parseFormattedText(def.getFormattedText());
        } else {
            textComponent = Component.text(def.getText() != null ? def.getText() : "");
        }
        
        Component hoveredTextComponent;
        if (def.getFormattedHoveredText() != null) {
            hoveredTextComponent = parseFormattedText(def.getFormattedHoveredText());
        } else if (def.getHoveredText() != null && !def.getHoveredText().isEmpty()) {
            hoveredTextComponent = Component.text(def.getHoveredText());
        } else {
            hoveredTextComponent = textComponent;
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
                .setPosition(new WidgetPosition(0, 0, 0));

        if (def.getTooltip() != null) {
            int[] tc = def.getTooltipColor();
            cfg.setTooltip(Component.text(def.getTooltip())
                    .color(TextColor.color(tc[0], tc[1], tc[2])));
            cfg.setTooltipDelay(def.getTooltipDelay());
        }

        TextDisplayButtonWidget widget = TextDisplayButtonWidget.create(loc, null, cfg);
        
        if (widget != null) {
            widget.saveRotation(screenYaw, screenPitch);
        }
        
        return widget;
    }

    private ItemDisplayButtonWidget buildItemWidget(WidgetDefinition def) {
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

        // Для глобальных экранов НЕ создаем onClick действие в виджете
        Runnable onClickAction = null;

        Location itemLoc = resolveLocation(def.getPosition(), ITEM_WIDGET_DEPTH_OFFSET);

        ItemDisplayButtonConfig cfg = new ItemDisplayButtonConfig(material, onClickAction)
                .setScale(s[0], s[1], s[2])
                .setTolerance(t[0], t[1])
                .setTranslation(new Vector3f(tr[0], tr[1], tr[2]))
                .setGlowOnHover(def.isGlowOnHover())
                .setDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.GUI)
                .setPosition(new WidgetPosition(0, 0, 0));

        if (def.getGlowColor() != null) {
            int[] gc = def.getGlowColor();
            cfg.setGlowColor(Color.fromRGB(gc[0], gc[1], gc[2]));
        }

        if (def.getTooltip() != null) {
            int[] tc = def.getTooltipColor();
            cfg.setTooltip(def.getTooltip())
                    .setTooltipColor(TextColor.color(tc[0], tc[1], tc[2]))
                    .setTooltipDelay(def.getTooltipDelay());
        }

        ItemDisplayButtonWidget widget = ItemDisplayButtonWidget.create(itemLoc, null, cfg);
        
        if (widget != null) {
            widget.saveRotation(screenYaw, screenPitch);
        }
        
        return widget;
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Location resolveLocation(float[] pos) {
        return resolveLocation(pos, WIDGET_DEPTH_OFFSET);
    }
    
    private Location resolveLocation(float[] pos, float depthOffset) {
        if (pos == null || pos.length < 3) return location.clone();

        Location base = location.clone();
        var dir = base.getDirection();
        var right = dir.getCrossProduct(new Vector(0, 1, 0)).normalize();
        var up = right.getCrossProduct(dir).normalize();

        base.add(right.multiply(pos[0]));
        base.add(up.multiply(pos[1]));
        base.add(dir.multiply(pos[2] - depthOffset));
        return base;
    }

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
                builder.append(Component.text((String) part));
            } else if (part instanceof Map) {
                Map<String, Object> partMap = (Map<String, Object>) part;
                String text = (String) partMap.getOrDefault("text", "");
                
                net.kyori.adventure.text.TextComponent.Builder partBuilder = 
                    Component.text().content(text);
                
                String color = (String) partMap.get("color");
                if (color != null) {
                    try {
                        if (color.startsWith("#")) {
                            partBuilder.color(net.kyori.adventure.text.format.TextColor.fromHexString(color));
                        } else {
                            partBuilder.color(net.kyori.adventure.text.format.NamedTextColor.NAMES.value(color.toLowerCase()));
                        }
                    } catch (Exception e) {
                        // Ignore invalid colors
                    }
                }
                
                Boolean bold = (Boolean) partMap.get("bold");
                if (bold != null && bold) {
                    partBuilder.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true);
                }
                
                Boolean italic = (Boolean) partMap.get("italic");
                if (italic != null && italic) {
                    partBuilder.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true);
                }
                
                Boolean underlined = (Boolean) partMap.get("underlined");
                if (underlined != null && underlined) {
                    partBuilder.decoration(net.kyori.adventure.text.format.TextDecoration.UNDERLINED, true);
                }
                
                Boolean strikethrough = (Boolean) partMap.get("strikethrough");
                if (strikethrough != null && strikethrough) {
                    partBuilder.decoration(net.kyori.adventure.text.format.TextDecoration.STRIKETHROUGH, true);
                }
                
                Boolean obfuscated = (Boolean) partMap.get("obfuscated");
                if (obfuscated != null && obfuscated) {
                    partBuilder.decoration(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED, true);
                }
                
                builder.append(partBuilder.build());
            }
        }
        
        return builder.build();
    }

    private void refreshNearbyPlayers() {
        nearbyPlayers.clear();
        
        // Очищаем hover состояния для игроков, которые больше не рядом
        Set<Player> playersToRemove = new HashSet<>();
        for (Map.Entry<Widget, Set<Player>> entry : widgetHoveredBy.entrySet()) {
            for (Player player : entry.getValue()) {
                if (player.getLocation().distanceSquared(location) > radiusSq) {
                    playersToRemove.add(player);
                }
            }
        }
        
        for (Player player : playersToRemove) {
            // Очищаем tooltip и hover состояния
            player.clearTitle();
            clearPlayerHover(player);
        }
        
        // Находим новых игроков поблизости
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld()) && 
                player.getLocation().distanceSquared(location) <= radiusSq) {
                nearbyPlayers.add(player);
            }
        }
    }

    private void updateHoverDetection() {
        for (Player player : nearbyPlayers) {
            // Direction gate: проверяем, смотрит ли игрок в сторону экрана
            Vector toScreen = location.toVector().subtract(player.getEyeLocation().toVector()).normalize();
            if (player.getEyeLocation().getDirection().dot(toScreen) < 0.3) {
                // Игрок не смотрит на экран, очищаем его hover состояния
                clearPlayerHover(player);
                continue;
            }

            // Находим ближайший виджет, на который смотрит игрок
            Widget closestWidget = null;
            double closestDistance = Double.MAX_VALUE;

            for (Widget widget : children) {
                if (isPlayerLookingAtWidget(player, widget)) {
                    double distance = player.getEyeLocation().distance(widget.getLocation());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestWidget = widget;
                    }
                }
            }

            // Обновляем hover состояния
            updatePlayerHover(player, closestWidget);
        }
    }

    private boolean isPlayerLookingAtWidget(Player player, Widget widget) {
        if (widget.getLocation() == null) return false;
        
        Vector eye = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection();
        Vector widgetPos = widget.getLocation().toVector();
        
        // Используем tolerance по умолчанию
        double hTolerance = 0.06;
        double vTolerance = 0.06;
        
        return PointDetection.lookingAtPoint(eye, direction, widgetPos, hTolerance, vTolerance);
    }

    private void updatePlayerHover(Player player, Widget newHoveredWidget) {
        // Находим виджет, на который игрок наводился ранее
        Widget previousWidget = null;
        for (Map.Entry<Widget, Set<Player>> entry : widgetHoveredBy.entrySet()) {
            if (entry.getValue().contains(player)) {
                previousWidget = entry.getKey();
                break;
            }
        }

        // Если hover не изменился, ничего не делаем
        if (previousWidget == newHoveredWidget) {
            return;
        }

        // Убираем tooltip с предыдущего виджета
        if (previousWidget != null) {
            hideTooltipFromWidget(previousWidget, player);
            Set<Player> playersOnPrevious = widgetHoveredBy.get(previousWidget);
            if (playersOnPrevious != null) {
                playersOnPrevious.remove(player);
                if (playersOnPrevious.isEmpty()) {
                    widgetHoveredBy.remove(previousWidget);
                }
            }
        }

        // Показываем tooltip на новом виджете
        if (newHoveredWidget != null) {
            widgetHoveredBy.computeIfAbsent(newHoveredWidget, k -> ConcurrentHashMap.newKeySet()).add(player);
            showTooltipFromWidget(newHoveredWidget, player);
        }
    }

    private void clearPlayerHover(Player player) {
        Widget hoveredWidget = null;
        for (Map.Entry<Widget, Set<Player>> entry : widgetHoveredBy.entrySet()) {
            if (entry.getValue().contains(player)) {
                hoveredWidget = entry.getKey();
                break;
            }
        }
        
        if (hoveredWidget != null) {
            hideTooltipFromWidget(hoveredWidget, player);
            Set<Player> playersOnWidget = widgetHoveredBy.get(hoveredWidget);
            if (playersOnWidget != null) {
                playersOnWidget.remove(player);
                if (playersOnWidget.isEmpty()) {
                    widgetHoveredBy.remove(hoveredWidget);
                }
            }
        }
    }

    private void showTooltipFromWidget(Widget widget, Player player) {
        if (widget instanceof TextDisplayButtonWidget textWidget) {
            textWidget.showTooltipTo(player);
        } else if (widget instanceof ItemDisplayButtonWidget itemWidget) {
            itemWidget.showTooltipTo(player);
        }
    }

    private void hideTooltipFromWidget(Widget widget, Player player) {
        if (widget instanceof TextDisplayButtonWidget textWidget) {
            textWidget.hideTooltipFrom(player);
        } else if (widget instanceof ItemDisplayButtonWidget itemWidget) {
            itemWidget.hideTooltipFrom(player);
        }
    }

    private void callLuaFunction(String functionName, Player player) {
        if (luaEngine == null || luaContext == null) return;
        
        Map<String, String> scripts = definition.getScripts();
        if (scripts == null) return;
        
        String scriptFile = scripts.get("file");
        if (scriptFile != null) {
            try {
                if (player != null) {
                    // Временно устанавливаем игрока в постоянный контекст
                    PlayerAPI oldPlayerAPI = luaContext.getPlayerAPI();
                    PlayerAPI tempPlayerAPI = new PlayerAPI(player);
                    luaContext.getGlobals().set("player", tempPlayerAPI);
                    
                    luaEngine.callFunction(luaContext, scriptFile, functionName);
                    
                    // Восстанавливаем предыдущий player API
                    if (oldPlayerAPI != null) {
                        luaContext.getGlobals().set("player", oldPlayerAPI);
                    } else {
                        luaContext.getGlobals().set("player", LuaValue.NIL);
                    }
                } else {
                    // Вызов без игрока (on_open, on_close)
                    // Убеждаемся что player API установлен как nil для безопасности
                    luaContext.getGlobals().set("player", LuaValue.NIL);
                    luaEngine.callFunction(luaContext, scriptFile, functionName);
                }
            } catch (Exception e) {
                DisplayLib.getInstance().getLogger().warning("Error calling Lua function " + functionName + " in " + scriptFile + ": " + e.getMessage());
            }
        }
    }
    
    private void callLuaFunctionWithPlayer(String functionName, WidgetDefinition widgetDef, Player player) {
        if (luaEngine == null || player == null || luaContext == null) return;
        
        Map<String, String> scripts = definition.getScripts();
        if (scripts == null) return;
        
        String scriptFile = scripts.get("file");
        if (scriptFile != null) {
            // Временно устанавливаем игрока в постоянный контекст
            PlayerAPI oldPlayerAPI = luaContext.getPlayerAPI();
            PlayerAPI tempPlayerAPI = new PlayerAPI(player);
            luaContext.getGlobals().set("player", tempPlayerAPI);
            
            // Устанавливаем widget в глобальный контекст
            Widget widget = widgetById.get(widgetDef.getId());
            if (widget != null) {
                luaContext.getGlobals().set("widget", new WidgetAPI(widget));
            }
            
            luaEngine.callFunction(luaContext, scriptFile, functionName);
            
            // Очищаем widget из контекста
            luaContext.getGlobals().set("widget", LuaValue.NIL);
            
            // Восстанавливаем предыдущий player API (или убираем если его не было)
            if (oldPlayerAPI != null) {
                luaContext.getGlobals().set("player", oldPlayerAPI);
            } else {
                luaContext.getGlobals().set("player", LuaValue.NIL);
            }
        }
    }
}
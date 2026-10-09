package padej.displayLib.ui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.WidgetDefinition;
import padej.displayLib.DisplayLib;
import padej.displayLib.script.JumperEngine;
import padej.displayLib.script.ScriptContext;
import padej.displayLib.script.api.ScreenAPI;
import padej.displayLib.ui.widgets.*;
import padej.displayLib.utils.ViewRay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Публичный экран без владельца, фиксированный в мире.
 * Любой игрок поблизости может взаимодействовать.
 * Hover визуалы отключены, работают только tooltips.
 *
 * <p>Все методы вызываются только из основного потока сервера.</p>
 */
public class GlobalScreenInstance implements ScreenAPI.Host {
    private final String screenId;
    private final ScreenDefinition definition;
    private final Location location;
    private final float screenYaw;
    private final float screenPitch;
    private ScriptContext scriptContext;
    
    /** Файл скрипта экрана (поле script в .jmc) или null */
    private final String scriptFile;
    
    /** Все виджеты экрана */
    private final List<Widget> children = new ArrayList<>();
    
    /** Быстрый доступ к виджетам по id (порядок объявления сохраняется) */
    private final Map<String, Widget> widgetById = new LinkedHashMap<>();
    
    /** Определение виджета по самому виджету (для обработки клика без перебора) */
    private final Map<Widget, WidgetDefinition> definitionByWidget = new IdentityHashMap<>();
    
    /** Радиус взаимодействия в квадрате; значение <= 0 означает "без ограничения" */
    private final double radiusSq;
    
    /** Интервал проверки расстояния в тиках */
    private final int rangeCheckInterval;
    
    /** Счетчик тиков для проверки расстояния */
    private int rangeCheckTimer = 0;
    
    /** Игроки поблизости (обновляется каждые rangeCheckInterval тиков) */
    private final List<Player> nearbyPlayers = new ArrayList<>();
    private final Set<UUID> nearbyPlayerIds = new HashSet<>();
    
    /** На какой виджет наведён каждый игрок (по UUID, чтобы не удерживать объекты Player) */
    private final Map<UUID, Widget> hoveredByPlayer = new HashMap<>();
    
    /** Переиспользуемые объекты для цикла обновления */
    private final ViewRay viewRay = new ViewRay();
    private final Location playerScratch = new Location(null, 0, 0, 0);
    
    private boolean removed = false;

    public GlobalScreenInstance(String screenId, ScreenDefinition definition,
                               Location location, float yaw, float pitch, JumperEngine engine) {
        this.screenId = screenId;
        this.definition = definition;
        this.location = location.clone();
        this.screenYaw = yaw;
        this.screenPitch = pitch;
        this.scriptFile = definition.getScript();
        
        // У публичного экрана нет владельца: глобал player равен null,
        // кликнувший игрок приходит аргументом обработчика
        this.scriptContext = new ScriptContext((DisplayLib) DisplayLib.getInstance(), engine, this, null, scriptFile);
        
        double radius = definition.getInteractionRadius();
        this.radiusSq = radius > 0 ? radius * radius : -1;
        this.rangeCheckInterval = definition.getRangeCheckInterval();
        
        spawnBackground();
        spawnWidgets();
        
        // onOpen без игрока
        scriptContext.callHook(ScriptContext.HOOK_OPEN);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void update() {
        if (removed) return;
        
        // Фаза 1: обновление кеша nearbyPlayers (каждые rangeCheckInterval тиков)
        rangeCheckTimer++;
        if (rangeCheckTimer >= rangeCheckInterval) {
            rangeCheckTimer = 0;
            refreshNearbyPlayers();
            
            // Сущности экрана не сохраняются в чанк и исчезают при его выгрузке.
            // Раз рядом есть игроки - возвращаем пропавшие на место.
            if (!nearbyPlayers.isEmpty()) {
                for (int i = 0; i < children.size(); i++) {
                    children.get(i).ensureSpawned();
                }
            }
        }

        // Фаза 2: обнаружение hover и tooltip (каждый тик, только nearbyPlayers)
        updateHoverDetection();
    }

    public void remove() {
        if (removed) return;
        removed = true;
        
        // Очищаем все tooltips
        for (Player player : nearbyPlayers) {
            player.clearTitle();
        }
        nearbyPlayers.clear();
        nearbyPlayerIds.clear();
        hoveredByPlayer.clear();
        
        // Удаляем все виджеты
        for (Widget widget : new ArrayList<>(children)) {
            widget.remove();
        }
        children.clear();
        widgetById.clear();
        definitionByWidget.clear();
        
        // onClose без игрока, затем очистка контекста (таймеры, обёртки)
        ScriptContext context = scriptContext;
        if (context != null) {
            context.callHook(ScriptContext.HOOK_CLOSE);
            context.cleanup();
            scriptContext = null;
        }
    }

    public void handleClickBy(Player player) {
        // Сначала проверяем hover (для совместимости и точности)
        Widget clickedWidget = getHoveredWidgetFor(player);
        
        if (clickedWidget == null) {
            // Если наведение ещё не обновилось, проверяем взгляд игрока прямо сейчас
            clickedWidget = findWidgetUnder(viewRay.set(player));
        }
        
        if (clickedWidget != null) {
            WidgetDefinition widgetDef = definitionByWidget.get(clickedWidget);
            if (widgetDef != null && widgetDef.getOnClick() != null) {
                handleClick(widgetDef, clickedWidget, player);
            }
        }
    }

    private void handleClick(WidgetDefinition def, Widget widget, Player player) {
        WidgetDefinition.ClickAction action = def.getOnClick();
        if (action == null) return;

        switch (action.getAction()) {
            case NONE -> {}
            case SWITCH_SCREEN -> {
                // Для публичных экранов SWITCH_SCREEN не имеет смысла
            }
            case CLOSE_SCREEN -> {
                // Публичные экраны не закрываются по клику
            }
            case RUN_SCRIPT -> {
                if (action.getFunction() != null && scriptContext != null) {
                    scriptContext.callClick(action.getFunction(), def.getId(), widget, player);
                }
            }
        }
    }

    @Override
    public Widget getWidget(String id) {
        return widgetById.get(id);
    }

    @Override
    public Map<String, Widget> getWidgets() {
        return Map.copyOf(widgetById);
    }

    @Override
    public String getScreenId() {
        return screenId;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    /** Публичный экран не закрывается из скрипта: он общий для всех игроков. */
    @Override
    public void closeFromScript() {
    }

    /** Публичный экран не переключается из скрипта. */
    @Override
    public void switchFromScript(String screenId) {
    }

    /** Скриптовое окружение экрана (для Java API других плагинов); null после удаления */
    public ScriptContext getScriptContext() {
        return scriptContext;
    }

    public Location getLocation() {
        return location.clone();
    }

    /** Копия списка игроков поблизости. Для проверки одного игрока используйте {@link #isNearby(Player)}. */
    public List<Player> getNearbyPlayers() {
        return new ArrayList<>(nearbyPlayers);
    }

    /** Находится ли игрок в радиусе взаимодействия (по последнему обновлению списка). */
    public boolean isNearby(Player player) {
        return nearbyPlayerIds.contains(player.getUniqueId());
    }

    public Widget getHoveredWidgetFor(Player player) {
        return hoveredByPlayer.get(player.getUniqueId());
    }

    /**
     * Забыть игрока (выход с сервера, смерть): убрать из списка ближайших и сбросить наведение.
     * Без этого объект игрока оставался бы в состоянии экрана после выхода.
     */
    public void removePlayer(Player player) {
        UUID id = player.getUniqueId();
        if (nearbyPlayerIds.remove(id)) {
            nearbyPlayers.remove(player);
        }
        if (hoveredByPlayer.remove(id) != null) {
            player.clearTitle();
        }
        if (scriptContext != null) {
            scriptContext.forgetPlayer(id);
        }
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

        // Создаем фон без viewer (используем null)
        TextDisplayButtonWidget backgroundWidget = TextDisplayButtonWidget.create(
                ScreenSupport.backgroundLocation(location, bg), null, ScreenSupport.backgroundConfig(bg));
        backgroundWidget.saveRotation(screenYaw, screenPitch);
        
        addChild(backgroundWidget, null);
    }

    private void spawnWidgets() {
        if (definition.getWidgets() == null) return;

        for (WidgetDefinition def : definition.getWidgets()) {
            Widget widget = buildWidget(def);
            if (widget == null) continue;

            addChild(widget, def);
            if (def.getId() != null) {
                widgetById.put(def.getId(), widget);
            }
        }
    }

    private void addChild(Widget widget, WidgetDefinition def) {
        children.add(widget);
        if (def != null) {
            definitionByWidget.put(widget, def);
        }
    }

    private Widget buildWidget(WidgetDefinition def) {
        // Для публичных экранов НЕ создаем onClick действие в виджете:
        // обработка идёт через handleClickBy. Hover-анимации также отключены.
        switch (def.getType()) {
            case TEXT_BUTTON, SPRITE_BUTTON -> {
                Location loc = ScreenSupport.resolveLocation(location, def.getPosition(), ScreenSupport.WIDGET_DEPTH_OFFSET);
                TextDisplayButtonWidget widget = TextDisplayButtonWidget.create(
                        loc, null, ScreenSupport.textConfig(def, null, false));
                widget.saveRotation(screenYaw, screenPitch);
                return widget;
            }
            case ITEM_BUTTON -> {
                Location loc = ScreenSupport.resolveLocation(location, def.getPosition(), ScreenSupport.ITEM_WIDGET_DEPTH_OFFSET);
                ItemDisplayButtonWidget widget = ItemDisplayButtonWidget.create(
                        loc, null, ScreenSupport.itemConfig(def, null, false));
                widget.saveRotation(screenYaw, screenPitch);
                return widget;
            }
        }
        return null;
    }

    private void refreshNearbyPlayers() {
        nearbyPlayers.clear();
        nearbyPlayerIds.clear();
        
        // Находим игроков поблизости
        World world = location.getWorld();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getLocation(playerScratch);
            // Сравнение миров обязательно до distanceSquared: между мирами оно бросает исключение
            if (playerScratch.getWorld() == world
                    && (radiusSq <= 0 || playerScratch.distanceSquared(location) <= radiusSq)) {
                nearbyPlayers.add(player);
                nearbyPlayerIds.add(player.getUniqueId());
            }
        }
        playerScratch.setWorld(null);
        
        // Очищаем hover состояния для игроков, которые больше не рядом
        if (!hoveredByPlayer.isEmpty()) {
            Iterator<UUID> iterator = hoveredByPlayer.keySet().iterator();
            while (iterator.hasNext()) {
                UUID id = iterator.next();
                if (nearbyPlayerIds.contains(id)) continue;
                
                iterator.remove();
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.clearTitle();
                }
            }
        }
    }

    private void updateHoverDetection() {
        for (int i = 0; i < nearbyPlayers.size(); i++) {
            Player player = nearbyPlayers.get(i);
            
            // Луч взгляда вычисляется один раз на игрока и используется для всех виджетов
            updatePlayerHover(player, findWidgetUnder(viewRay.set(player)));
        }
    }

    /**
     * Ближайший к игроку виджет, в зону наведения которого попадает луч взгляда.
     * Зона каждого виджета - прямоугольник в его плоскости с полуразмерами tolerance из YAML.
     */
    private Widget findWidgetUnder(ViewRay ray) {
        if (ray.getWorld() != location.getWorld()) return null;
        
        Widget closestWidget = null;
        double closestDistance = Double.MAX_VALUE;

        for (int i = 0; i < children.size(); i++) {
            Widget widget = children.get(i);
            double distance = widget.hitDistance(ray);
            if (distance >= 0.0 && distance < closestDistance) {
                closestDistance = distance;
                closestWidget = widget;
            }
        }
        return closestWidget;
    }

    private void updatePlayerHover(Player player, Widget newHoveredWidget) {
        if (newHoveredWidget == null) {
            clearPlayerHover(player);
            return;
        }
        
        Widget previousWidget = hoveredByPlayer.put(player.getUniqueId(), newHoveredWidget);

        // Если hover не изменился, ничего не делаем
        if (previousWidget == newHoveredWidget) {
            return;
        }

        // Убираем tooltip с предыдущего виджета и показываем tooltip нового
        if (previousWidget != null) {
            hideTooltipFromWidget(previousWidget, player);
        }
        showTooltipFromWidget(newHoveredWidget, player);
    }

    private void clearPlayerHover(Player player) {
        Widget hoveredWidget = hoveredByPlayer.remove(player.getUniqueId());
        if (hoveredWidget != null) {
            hideTooltipFromWidget(hoveredWidget, player);
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
}

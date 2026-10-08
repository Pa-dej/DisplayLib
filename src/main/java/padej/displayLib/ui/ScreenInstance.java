package padej.displayLib.ui;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.WidgetDefinition;
import padej.displayLib.lua.LuaContext;
import padej.displayLib.lua.LuaEngine;
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
    
    /** Lua-файл экрана (scripts.file) или null */
    private final String scriptFile;
    
    /** Быстрый доступ к виджетам по id из YAML */
    private final Map<String, Widget> widgetById = new HashMap<>();
    
    /** Единая ориентация для всех элементов экрана */
    private final float screenYaw;
    private final float screenPitch;
    
    /** Квадраты радиусов из YAML (значение <= 0 означает "без ограничения") */
    private final double interactionRadiusSq;
    private final double closeDistanceSq;
    
    /** Переиспользуемый объект для проверки расстояния до зрителя */
    private final Location viewerScratch = new Location(null, 0, 0, 0);
    
    /** Флаг предотвращения рекурсии при закрытии */
    private boolean isClosing = false;

    /**
     * Экран, повёрнутый лицом к игроку (ориентация вычисляется один раз).
     */
    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location, LuaEngine luaEngine) {
        this(screenId, definition, viewer, location, facingOrientation(viewer, location), luaEngine);
    }
    
    /**
     * Конструктор с заданной ориентацией (для переключения экранов)
     */
    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location, float yaw, float pitch, LuaEngine luaEngine) {
        this(screenId, definition, viewer, location, new float[]{yaw, pitch}, luaEngine);
    }

    private ScreenInstance(String screenId, ScreenDefinition definition,
                           Player viewer, Location location, float[] orientation, LuaEngine luaEngine) {
        super(viewer, location);
        this.screenId = screenId;
        this.definition = definition;
        this.luaEngine = luaEngine;
        this.screenYaw = orientation[0];
        this.screenPitch = orientation[1];
        
        Map<String, String> scripts = definition.getScripts();
        this.scriptFile = scripts != null ? scripts.get("file") : null;
        
        double interactionRadius = definition.getInteractionRadius();
        this.interactionRadiusSq = interactionRadius > 0 ? interactionRadius * interactionRadius : -1;
        double closeDistance = definition.getCloseDistance();
        this.closeDistanceSq = closeDistance > 0 ? closeDistance * closeDistance : -1;
        
        // Создаем Lua контекст
        if (luaEngine != null) {
            this.luaContext = luaEngine.createContext(this, viewer);
        }

        spawnBackground();
        spawnWidgets();
        
        // Вызываем on_open после создания всех виджетов
        callLifecycleFunction("on_open");
    }

    /** Ориентация (yaw, pitch) экрана в точке location, обращённого к игроку. */
    private static float[] facingOrientation(Player viewer, Location location) {
        Location viewerLoc = viewer.getLocation().add(0, viewer.getHeight() / 2, 0);
        double dx = viewerLoc.getX() - location.getX();
        double dy = viewerLoc.getY() - location.getY();
        double dz = viewerLoc.getZ() - location.getZ();

        double yaw = Math.atan2(dz, dx);
        double pitch = Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));

        return new float[]{(float) Math.toDegrees(yaw) - 90, (float) Math.toDegrees(-pitch)};
    }

    // -------------------------------------------------------------------------
    // Spawning
    // -------------------------------------------------------------------------

    private void spawnBackground() {
        ScreenDefinition.BackgroundDefinition bg = definition.getBackground();
        if (bg == null) return;

        // Фон создается с учетом position из YAML
        TextDisplayButtonWidget backgroundWidget = TextDisplayButtonWidget.create(
                ScreenSupport.backgroundLocation(location, bg), viewer, ScreenSupport.backgroundConfig(bg));
        
        // Сохраняем единую ориентацию экрана
        backgroundWidget.saveRotation(screenYaw, screenPitch);
        
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
        // Определяем onClick только если действие не NONE
        Runnable onClick = ScreenSupport.hasClickAction(def) ? () -> handleClick(def) : null;

        switch (def.getType()) {
            case TEXT_BUTTON -> {
                Location loc = ScreenSupport.resolveLocation(location, def.getPosition(), ScreenSupport.WIDGET_DEPTH_OFFSET);
                TextDisplayButtonWidget widget = TextDisplayButtonWidget.create(
                        loc, viewer, ScreenSupport.textConfig(def, onClick, true));
                // Сохраняем единую ориентацию экрана (как у фона)
                widget.saveRotation(screenYaw, screenPitch);
                return widget;
            }
            case ITEM_BUTTON -> {
                // Используем увеличенное смещение для ItemDisplay виджетов
                Location loc = ScreenSupport.resolveLocation(location, def.getPosition(), ScreenSupport.ITEM_WIDGET_DEPTH_OFFSET);
                ItemDisplayButtonWidget widget = ItemDisplayButtonWidget.create(
                        loc, viewer, ScreenSupport.itemConfig(def, onClick, true));
                widget.saveRotation(screenYaw, screenPitch);
                return widget;
            }
        }
        return null;
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
    // WidgetManager contract
    // -------------------------------------------------------------------------

    @Override
    protected ScreenDefinition getScreenDefinition() {
        return definition;
    }
    
    /**
     * Квадрат расстояния от зрителя до экрана;
     * бесконечность, если зритель оказался в другом мире
     * (Location#distanceSquared в этом случае бросает исключение).
     */
    private double viewerDistanceSquared() {
        viewer.getLocation(viewerScratch);
        double result = viewerScratch.getWorld() == location.getWorld()
                ? viewerScratch.distanceSquared(location)
                : Double.POSITIVE_INFINITY;
        viewerScratch.setWorld(null);
        return result;
    }
    
    @Override
    protected boolean isPlayerInInteractionRange() {
        // Проверяем interaction_radius для оптимизации hover detection
        double distanceSq = viewerDistanceSquared();
        if (distanceSq == Double.POSITIVE_INFINITY) return false; // другой мир
        
        // Если радиус не ограничен, всегда в зоне взаимодействия
        return interactionRadiusSq <= 0 || distanceSq <= interactionRadiusSq;
    }
    
    @Override
    protected boolean isPlayerInRange() {
        // Для isPlayerInRange проверяем ТОЛЬКО close_distance (автозакрытие)
        // interaction_radius проверяется отдельно в isPlayerInInteractionRange
        double distanceSq = viewerDistanceSquared();
        if (distanceSq == Double.POSITIVE_INFINITY) return false; // другой мир - экран закрывается
        
        return closeDistanceSq <= 0 || distanceSq <= closeDistanceSq;
    }

    @Override
    protected void tryClose() {
        if (isClosing) return;
        isClosing = true;
        
        // Вызываем on_close перед закрытием
        callLifecycleFunction("on_close");
        
        // Lua контекст очищается в remove()
        UIManager.getInstance().forceCloseScreen(viewer);
    }

    /**
     * Удалить сущности экрана и освободить Lua контекст.
     *
     * <p>Контекст очищается здесь, а не только в {@link #tryClose()}, потому что экран
     * удаляется и в обход tryClose: при переключении экранов, выходе или смерти игрока,
     * выключении плагина. Иначе таймеры скрипта (timer.every и т.п.) продолжали бы
     * работать после исчезновения экрана и удерживали его в памяти.</p>
     */
    @Override
    public void remove() {
        if (luaContext != null) {
            luaContext.cleanup();
        }
        super.remove();
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
     * Вызвать необязательную Lua функцию жизненного цикла (on_open / on_close)
     */
    private void callLifecycleFunction(String functionName) {
        if (luaEngine == null || luaContext == null || scriptFile == null) return;
        luaEngine.callOptionalFunction(luaContext, scriptFile, functionName);
    }
    
    /**
     * Вызвать Lua функцию с установленным widget контекстом
     */
    private void callLuaFunctionWithWidget(String functionName, WidgetDefinition widgetDef) {
        if (luaEngine == null || luaContext == null || scriptFile == null) return;
        
        // Устанавливаем widget в глобальный контекст
        Widget widget = widgetDef.getId() != null ? widgetById.get(widgetDef.getId()) : null;
        if (widget != null) {
            luaContext.getGlobals().set("widget", luaContext.widgetApi(widget));
        }
        
        try {
            luaEngine.callFunction(luaContext, scriptFile, functionName);
        } finally {
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
}

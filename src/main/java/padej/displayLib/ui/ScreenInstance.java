package padej.displayLib.ui;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.WidgetDefinition;
import padej.displayLib.DisplayLib;
import padej.displayLib.script.JumperEngine;
import padej.displayLib.script.ScriptContext;
import padej.displayLib.script.api.ScreenAPI;
import padej.displayLib.ui.widgets.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime экземпляр экрана.
 * Создаёт entity по ScreenDefinition и управляет ими.
 * Не знает ни про follow, ни про save — этого больше нет.
 */
public class ScreenInstance extends WidgetManager implements ScreenAPI.Host {
    private final String screenId;
    private final ScreenDefinition definition;
    private final ScriptContext scriptContext;
    
    /** Файл скрипта экрана (поле script в .jmc) или null */
    private final String scriptFile;
    
    /** Быстрый доступ к виджетам по id из файла экрана (порядок объявления сохраняется) */
    private final Map<String, Widget> widgetById = new LinkedHashMap<>();
    
    /** Единая ориентация для всех элементов экрана */
    private final float screenYaw;
    private final float screenPitch;
    
    /** Квадраты радиусов из файла экрана (значение <= 0 означает "без ограничения") */
    private final double interactionRadiusSq;
    private final double closeDistanceSq;
    
    /** Переиспользуемый объект для проверки расстояния до зрителя */
    private final Location viewerScratch = new Location(null, 0, 0, 0);
    
    /** Флаг предотвращения рекурсии при закрытии */
    private boolean isClosing = false;

    /** Фон экрана (участвует в morph как отдельная сущность) */
    private TextDisplayButtonWidget backgroundWidget;

    /** Идущий morph-переход к этому экрану; пока он идёт, наведение и клики не обрабатываются */
    private MorphTransition morph;

    /**
     * Экран, повёрнутый лицом к игроку (ориентация вычисляется один раз).
     */
    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location, JumperEngine engine) {
        this(screenId, definition, viewer, location, facingOrientation(viewer, location), engine);
    }
    
    /**
     * Конструктор с заданной ориентацией (для переключения экранов)
     */
    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location, float yaw, float pitch, JumperEngine engine) {
        this(screenId, definition, viewer, location, new float[]{yaw, pitch}, engine, null);
    }

    /**
     * Экран, появляющийся morph-переходом из {@code morphFrom}: сущности одноимённых виджетов
     * того же типа переходят к новому экрану и анимируются к его состоянию, остальные исчезают
     * и появляются (см. {@link MorphTransition}). Старый экран после этого нужно удалить -
     * его сущности уже отданы.
     */
    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location, Float yaw, Float pitch, JumperEngine engine,
                          ScreenInstance morphFrom) {
        this(screenId, definition, viewer, location,
                yaw != null && pitch != null ? new float[]{yaw, pitch} : facingOrientation(viewer, location),
                engine, morphFrom);
    }

    private ScreenInstance(String screenId, ScreenDefinition definition,
                           Player viewer, Location location, float[] orientation, JumperEngine engine) {
        this(screenId, definition, viewer, location, orientation, engine, null);
    }

    private ScreenInstance(String screenId, ScreenDefinition definition,
                           Player viewer, Location location, float[] orientation, JumperEngine engine,
                           ScreenInstance morphFrom) {
        super(viewer, location);
        this.screenId = screenId;
        this.definition = definition;
        this.screenYaw = orientation[0];
        this.screenPitch = orientation[1];
        this.scriptFile = definition.getScript();
        
        double interactionRadius = definition.getInteractionRadius();
        this.interactionRadiusSq = interactionRadius > 0 ? interactionRadius * interactionRadius : -1;
        double closeDistance = definition.getCloseDistance();
        this.closeDistanceSq = closeDistance > 0 ? closeDistance * closeDistance : -1;
        
        // Скриптовое окружение: глобалы player/screen/storage/timer/log и сам скрипт
        this.scriptContext = new ScriptContext((DisplayLib) DisplayLib.getInstance(), engine, this, viewer, scriptFile);

        if (morphFrom != null && definition.getMorph().isEnabled()) {
            morph = new MorphTransition(definition.getMorph());
        }
        spawnBackground(morphFrom);
        spawnWidgets(morphFrom);
        if (morph != null) {
            // всё, что осталось у старого экрана, исчезает
            for (Widget w : morphFrom.children) morph.fadeOut(detach(w));
            morphFrom.children.clear();
            morph.start(() -> morph = null);
        }
        
        // onOpen после создания всех виджетов
        scriptContext.callHook(ScriptContext.HOOK_OPEN);
    }

    /** Забрать сущность у виджета (он после этого ничего не удаляет). */
    private static org.bukkit.entity.Display detach(Widget w) {
        if (w instanceof TextDisplayButtonWidget t) return t.detachDisplay();
        if (w instanceof ItemDisplayButtonWidget i) return i.detachDisplay();
        return null;
    }

    /**
     * Закрыть экран с анимацией исчезновения: сущности отдаются переходу и удаляются,
     * когда он закончится; сам экран (скрипт, реестр) освобождается сразу.
     */
    public void removeAnimated() {
        ScreenDefinition.Morph settings = definition.getMorph();
        if (settings.isEnabled()) {
            if (morph != null) morph.cancel();
            MorphTransition out = new MorphTransition(settings);
            for (Widget w : children) out.fadeOut(detach(w));
            if (!out.isEmpty()) out.start(null);
        }
        remove();
    }

    /**
     * Перед передачей сущностей следующему экрану: текущий переход (если идёт) отпускает их,
     * иначе его завершение вернуло бы им старые цели поверх новых.
     */
    public void handOffMorph() {
        if (morph != null) { morph.handOff(); morph = null; }
    }

    /** Идёт ли morph-переход к этому экрану. */
    public boolean isMorphing() {
        return morph != null;
    }

    @Override
    public void update() {
        if (morph != null) return; // виджеты ещё в движении - наведение не считаем
        super.update();
    }

    @Override
    public Widget getNearestHoveredWidget() {
        if (morph != null) return null;
        return super.getNearestHoveredWidget();
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

    private void spawnBackground(ScreenInstance morphFrom) {
        ScreenDefinition.BackgroundDefinition bg = definition.getBackground();
        if (bg == null) return;

        Location loc = ScreenSupport.backgroundLocation(location, bg);
        TextDisplayButtonConfig cfg = ScreenSupport.backgroundConfig(bg);
        org.bukkit.entity.TextDisplay old = morph != null && morphFrom.backgroundWidget != null
                ? morphFrom.backgroundWidget.detachDisplay() : null;

        // Фон создается с учетом position из файла экрана
        backgroundWidget = old != null
                ? TextDisplayButtonWidget.createAdopting(loc, viewer, cfg, old)
                : TextDisplayButtonWidget.create(loc, viewer, cfg);
        
        // Сохраняем единую ориентацию экрана
        backgroundWidget.saveRotation(screenYaw, screenPitch);
        
        if (morph != null) {
            if (old != null) morph.match(backgroundWidget, old);
            else morph.fadeIn(backgroundWidget);
        }
        addDrawableChild(backgroundWidget);
    }

    private void spawnWidgets(ScreenInstance morphFrom) {
        if (definition.getWidgets() == null) return;

        for (WidgetDefinition def : definition.getWidgets()) {
            Widget old = morph != null && def.getId() != null ? morphFrom.widgetById.get(def.getId()) : null;
            Widget widget = buildWidget(def, old);
            if (widget == null) continue;

            addDrawableChild(widget);
            if (def.getId() != null) {
                widgetById.put(def.getId(), widget);
            }
        }
    }

    /**
     * @param old одноимённый виджет старого экрана при morph-переходе или null; используется,
     *            только если тип сущности совпадает (TextDisplay ↔ TextDisplay, ItemDisplay ↔ ItemDisplay)
     */
    private Widget buildWidget(WidgetDefinition def, Widget old) {
        // onClick только если клик что-то сделает (есть действие; для RUN_SCRIPT - непустая функция).
        // Виджет без клика и без реакции на наведение не обсчитывается на тиках.
        boolean clickable = ScreenSupport.clickDoesSomething(def, scriptContext, false, DisplayLib.getInstance().getLogger(), definition.getId());
        boolean interactive = ScreenSupport.isInteractive(def, clickable, true);
        Runnable onClick = clickable ? () -> handleClick(def) : null;

        switch (def.getType()) {
            case TEXT_BUTTON, SPRITE_BUTTON -> {
                Location loc = ScreenSupport.resolveLocation(location, def.getPosition(), ScreenSupport.WIDGET_DEPTH_OFFSET);
                TextDisplayButtonConfig cfg = ScreenSupport.textConfig(def, onClick, true, interactive);
                org.bukkit.entity.TextDisplay entity = old instanceof TextDisplayButtonWidget t ? t.detachDisplay() : null;
                TextDisplayButtonWidget widget = entity != null
                        ? TextDisplayButtonWidget.createAdopting(loc, viewer, cfg, entity)
                        : TextDisplayButtonWidget.create(loc, viewer, cfg);
                // Сохраняем единую ориентацию экрана (как у фона)
                widget.saveRotation(screenYaw, screenPitch);
                if (morph != null) {
                    if (entity != null) morph.match(widget, entity);
                    else morph.fadeIn(widget);
                }
                return widget;
            }
            case ITEM_BUTTON -> {
                // Используем увеличенное смещение для ItemDisplay виджетов
                Location loc = ScreenSupport.resolveLocation(location, def.getPosition(), ScreenSupport.ITEM_WIDGET_DEPTH_OFFSET);
                ItemDisplayButtonConfig cfg = ScreenSupport.itemConfig(def, onClick, true, interactive);
                org.bukkit.entity.ItemDisplay entity = old instanceof ItemDisplayButtonWidget i ? i.detachDisplay() : null;
                ItemDisplayButtonWidget widget = entity != null
                        ? ItemDisplayButtonWidget.createAdopting(loc, viewer, cfg, entity)
                        : ItemDisplayButtonWidget.create(loc, viewer, cfg);
                widget.saveRotation(screenYaw, screenPitch);
                if (morph != null) {
                    if (entity != null) morph.match(widget, entity);
                    else morph.fadeIn(widget);
                }
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

            // Функция скрипта: name(widget, player)
            case RUN_SCRIPT -> {
                if (action.getFunction() != null) {
                    Widget widget = def.getId() != null ? widgetById.get(def.getId()) : null;
                    scriptContext.callClick(action.getFunction(), def.getId(), widget, viewer);
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
        
        // onClose перед закрытием
        scriptContext.callHook(ScriptContext.HOOK_CLOSE);
        
        // Скриптовый контекст очищается в remove()
        UIManager.getInstance().forceCloseScreen(viewer);
    }

    /**
     * Удалить сущности экрана и освободить скриптовый контекст.
     *
     * <p>Контекст очищается здесь, а не только в {@link #tryClose()}, потому что экран
     * удаляется и в обход tryClose: при переключении экранов, выходе или смерти игрока,
     * выключении плагина. Иначе таймеры скрипта (timer.every и т.п.) продолжали бы
     * работать после исчезновения экрана и удерживали его в памяти.</p>
     */
    @Override
    public void remove() {
        scriptContext.cleanup();
        if (morph != null) { morph.cancel(); morph = null; }
        super.remove();
    }

    // -------------------------------------------------------------------------
    // ScreenAPI.Host: что экран показывает скрипту
    // -------------------------------------------------------------------------

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
        return false;
    }

    @Override
    public void closeFromScript() {
        UIManager.getInstance().closeScreen(viewer);
    }

    @Override
    public void switchFromScript(String targetId) {
        UIManager.getInstance().switchScreen(viewer, targetId);
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
    
    /**
     * Скриптовое окружение экрана (для Java API других плагинов)
     */
    public ScriptContext getScriptContext() {
        return scriptContext;
    }
}

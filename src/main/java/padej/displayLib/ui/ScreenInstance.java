package padej.displayLib.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.joml.Vector3f;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.config.WidgetDefinition;
import padej.displayLib.ui.widgets.*;

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
    
    /** Быстрый доступ к виджетам по id из YAML */
    private final Map<String, Widget> widgetById = new HashMap<>();
    
    /** Единая ориентация для всех элементов экрана */
    private final float screenYaw;
    private final float screenPitch;
    
    /** Смещение виджетов по глубине относительно фона для избежания Z-fighting */
    private static final float WIDGET_DEPTH_OFFSET = 0.001f;

    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location) {
        super(viewer, location);
        this.screenId = screenId;
        this.definition = definition;
        
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
    }
    
    /**
     * Конструктор с заданной ориентацией (для переключения экранов)
     */
    public ScreenInstance(String screenId, ScreenDefinition definition,
                          Player viewer, Location location, float yaw, float pitch) {
        super(viewer, location);
        this.screenId = screenId;
        this.definition = definition;
        
        // Используем переданную ориентацию
        this.screenYaw = yaw;
        this.screenPitch = pitch;

        spawnBackground();
        spawnWidgets();
    }

    // -------------------------------------------------------------------------
    // Spawning
    // -------------------------------------------------------------------------

    private void spawnBackground() {
        ScreenDefinition.BackgroundDefinition bg = definition.getBackground();
        if (bg == null) return;

        int[] c = bg.getColor();
        float[] s = bg.getScale();

        TextDisplayButtonConfig cfg = new TextDisplayButtonConfig(
                Component.text(bg.getText()),
                Component.text(bg.getText()),
                () -> {}
        )
                .setScale(s[0], s[1], s[2])
                .setBackgroundColor(Color.fromRGB(c[0], c[1], c[2]))
                .setBackgroundAlpha(bg.getAlpha())
                .setPosition(new WidgetPosition(0, 0, 0))
                .setTranslation(new Vector3f(0, 0, 0));

        // Фон создается на базовой позиции (без смещения по глубине)
        TextDisplayButtonWidget backgroundWidget = TextDisplayButtonWidget.create(location, viewer, cfg);
        
        // Устанавливаем единую ориентацию экрана
        if (backgroundWidget.getDisplay() != null) {
            backgroundWidget.getDisplay().setRotation(screenYaw, screenPitch);
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
            case ITEM_BUTTON -> buildItemWidget(def, widgetLoc);
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

        TextDisplayButtonConfig cfg = new TextDisplayButtonConfig(
                Component.text(def.getText() != null ? def.getText() : ""),
                Component.text(def.getHoveredText() != null ? def.getHoveredText() : def.getText() != null ? def.getText() : ""),
                () -> handleClick(def)
        )
                .setScale(s[0], s[1], s[2])
                .setTolerance(t[0], t[1])
                .setBackgroundColor(Color.fromRGB(bg[0], bg[1], bg[2]))
                .setBackgroundAlpha(def.getBackgroundAlpha())
                .setHoveredBackgroundColor(Color.fromRGB(hbg[0], hbg[1], hbg[2]))
                .setHoveredBackgroundAlpha(def.getHoveredBackgroundAlpha())
                .setPosition(new WidgetPosition(0, 0, 0)); // Позиция уже вычислена в resolveLocation()

        if (def.getTooltip() != null) {
            int[] tc = def.getTooltipColor();
            cfg.setTooltip(Component.text(def.getTooltip())
                    .color(TextColor.color(tc[0], tc[1], tc[2])));
            cfg.setTooltipDelay(def.getTooltipDelay());
        }

        TextDisplayButtonWidget widget = TextDisplayButtonWidget.create(loc, viewer, cfg);
        
        // Устанавливаем единую ориентацию экрана (как у фона)
        if (widget.getDisplay() != null) {
            widget.getDisplay().setRotation(screenYaw, screenPitch);
            widget.getDisplay().setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        }
        
        return widget;
    }

    private ItemDisplayButtonWidget buildItemWidget(WidgetDefinition def, Location loc) {
        Material material;
        try {
            material = Material.valueOf(def.getMaterial().toUpperCase());
        } catch (Exception e) {
            material = Material.STONE;
        }

        float[] s = def.getScale();
        float[] t = def.getTolerance();

        ItemDisplayButtonConfig cfg = new ItemDisplayButtonConfig(material, () -> handleClick(def))
                .setScale(s[0], s[1], s[2])
                .setTolerance(t[0], t[1])
                .setGlowOnHover(def.isGlowOnHover())
                .setDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.GUI) // Используем GUI transform
                .setPosition(new WidgetPosition(0, 0, 0)); // Позиция уже вычислена в resolveLocation()

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

        ItemDisplayButtonWidget widget = ItemDisplayButtonWidget.create(loc, viewer, cfg);
        
        // Устанавливаем единую ориентацию экрана (как у фона)
        if (widget.getDisplay() != null) {
            widget.getDisplay().setRotation(screenYaw, screenPitch);
            widget.getDisplay().setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
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

            // Lua — будет в следующем шаге
            case RUN_SCRIPT -> viewer.sendMessage(
                    "§eScript not yet implemented: " + action.getScript());
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
    protected boolean isPlayerInRange() {
        // Дистанция не ограничивает экраны
        return true;
    }

    @Override
    protected void tryClose() {
        UIManager.getInstance().closeScreen(viewer);
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
     * Получить ориентацию экрана (yaw, pitch)
     */
    public float[] getScreenOrientation() {
        return new float[]{screenYaw, screenPitch};
    }
}
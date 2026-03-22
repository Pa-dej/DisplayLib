package padej.displayLib.ui;

import padej.displayLib.DisplayLib;
import padej.displayLib.config.ScreenDefinition;
import padej.displayLib.ui.annotations.AlwaysOnScreen;
import padej.displayLib.ui.annotations.Main;
import padej.displayLib.ui.screens.ChangeScreen;
import padej.displayLib.ui.widgets.*;
import padej.displayLib.utils.Animation;
import padej.displayLib.utils.DisplayUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public abstract class Screen extends WidgetManager implements IDisplayable, IParentable, Animatable {
    private TextDisplayButtonWidget background;
    private final Class<? extends Screen> CURRENT_SCREEN_CLASS;

    @AlwaysOnScreen(Screen.class)
    private TextDisplayButtonWidget closeButton;

    public Screen(Player viewer, Location location, String text, float scale) {
        super(viewer, location);
        this.viewer = viewer;
        CURRENT_SCREEN_CLASS = this.getClass();

        TextDisplayButtonConfig backgroundConfig = new TextDisplayButtonConfig(
                Component.text(text),
                Component.text(text),
                () -> {
                }
        )
                .setScale(10, 4, 1)
                .setBackgroundColor(Color.fromRGB(0, 0, 0))
                .setBackgroundAlpha(160)
                .setPosition(new WidgetPosition(0, 0, 0))
                .setTranslation(new Vector3f(0, 0, 0));

        background = createTextWidget(backgroundConfig);

        spawn();
    }

    private void spawn() {
        if (background != null && background.getDisplay() != null) {
            Location viewerLoc = viewer.getLocation().add(0, viewer.getHeight() / 2, 0);
            DisplayUtils.lookAtPos(background.getDisplay(), viewerLoc);
        }
    }

    @Override
    public void remove() {
        remove(false);
    }
    
    // Принудительное удаление (например, при выходе игрока)
    public void remove(boolean force) {
        if (viewer != null) {
            DisplayLib.getInstance().getLogger().info("Removing screen for " + viewer.getName() + " (force=" + force + ")");
        }

        resetVarsAndBackground();

        List<Widget> toRemove = new ArrayList<>(children);
        for (Widget widget : toRemove) {
            widget.remove();
        }
        children.clear();

        if (background != null) {
            background.remove();
            background = null;
        }

        super.remove();
    }

    public void removeWithAnimation() {
        resetVarsAndBackground();
        softRemoveWithAnimation();
    }

    @Override
    public void softRemoveWithAnimation() {
        for (Widget widget : children) {
            if (widget != background) {
                if (widget instanceof TextDisplayButtonWidget) {
                    ((TextDisplayButtonWidget) widget).removeWithAnimation(5);
                } else if (widget instanceof ItemDisplayButtonWidget) {
                    ((ItemDisplayButtonWidget) widget).removeWithAnimation(5);
                }
            }
        }

        if (background != null && background.getDisplay() != null) {
            Animation.applyTransformationWithInterpolation(
                    background.getDisplay(),
                    new Transformation(
                            background.getDisplay().getTransformation().getTranslation().add(0, 0.5f, -1f),
                            background.getDisplay().getTransformation().getLeftRotation(),
                            new Vector3f(0, 0, 0),
                            background.getDisplay().getTransformation().getRightRotation()
                    ),
                    5
            );

            Bukkit.getScheduler().runTaskLater(DisplayLib.getInstance(), () -> {
                if (background != null && background.getDisplay() != null) {
                    background.remove();
                }
            }, 6);
        }
    }

    private void resetVarsAndBackground() {
        updateBackgroundColor(null);
    }

    @Override
    protected ScreenDefinition getScreenDefinition() {
        // Screen класс не использует ScreenDefinition (программные экраны)
        return null;
    }
    
    @Override
    protected boolean isPlayerInInteractionRange() {
        // Для программных экранов используем ту же логику что и isPlayerInRange
        return isPlayerInRangePublic();
    }
    
    @Override
    protected boolean isPlayerInRange() {
        return isPlayerInRangePublic();
    }
    
    // Публичный метод для внешнего использования
    public boolean isPlayerInRangePublic() {
        return viewer.getLocation().distanceSquared(location) <= 25; // 5² = 25, избегаем sqrt
    }

    @Override
    protected void tryClose() {
        tryClosePublic();
    }

    @Override
    public TextDisplay getTextDisplay() {
        return background != null ? background.getDisplay() : null;
    }

    public ItemDisplayButtonWidget createWidget(ItemDisplayButtonConfig config) {
        Location buttonLoc = location.clone();
        Vector direction = buttonLoc.getDirection();
        Vector right = direction.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = right.getCrossProduct(direction).normalize();

        WidgetPosition position = config.getPosition();
        if (position != null) {
            buttonLoc.add(right.multiply(position.getRightMultiplier()));
            buttonLoc.add(up.multiply(position.getUpMultiplier()));
            buttonLoc.add(direction.multiply(position.getDepth()));
        }

        ItemDisplayButtonWidget widget = ItemDisplayButtonWidget.create(
                buttonLoc,
                viewer,
                config
        );

        addDrawableChild(widget);
        return widget;
    }

    public TextDisplayButtonWidget createTextWidget(TextDisplayButtonConfig config) {
        if (location == null) {
            return null;
        }

        Location buttonLoc = location.clone();
        Vector direction = buttonLoc.getDirection();
        Vector right = direction.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = right.getCrossProduct(direction).normalize();

        WidgetPosition position = config.getPosition();
        if (position != null) {
            buttonLoc.add(right.multiply(position.getRightMultiplier()));
            buttonLoc.add(up.multiply(position.getUpMultiplier()));
            buttonLoc.add(direction.multiply(position.getDepth()));
        }

        TextDisplayButtonWidget widget = TextDisplayButtonWidget.create(
                buttonLoc,
                viewer,
                config
        );

        if (background != null && background.getDisplay() != null) {
            widget.getDisplay().setRotation(
                    background.getDisplay().getLocation().getYaw(),
                    background.getDisplay().getLocation().getPitch()
            );
            widget.getDisplay().setBillboard(Display.Billboard.FIXED);
        }

        return addDrawableChild(widget);
    }

    public void setupDefaultWidgets(Player player) {
        if (location == null) {
            return;
        }

        if (closeButton == null) {
            createTitleBarControlWidgets();
        }

        createScreenWidgets();
    }

    public void createScreenWidgets() {
    }

    public static <T extends Screen> T create(Class<T> screenClass, Player viewer, Location location) {
        try {
            return screenClass.getConstructor(Player.class, Location.class, String.class, float.class)
                    .newInstance(viewer, location, " ", 10.0f);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create screen: " + screenClass.getSimpleName(), e);
        }
    }

    public Player getViewer() {
        return viewer;
    }

    protected void addButton(Material material, String tooltip, Runnable action, int index) {
        addButton(material, tooltip, action, index, new WidgetPosition(-0.42f, 0.3f));
    }

    protected void addButton(Material material, String tooltip, Runnable action, int index, WidgetPosition basePosition) {
        addButton(material, tooltip, action, index, basePosition, 0.17f);
    }

    protected void addButton(Material material, String tooltip, Runnable action, int index, WidgetPosition basePosition, float step) {
        ItemDisplayButtonConfig config = new ItemDisplayButtonConfig(material, action)
                .setTooltip(tooltip)
                .setPosition(new WidgetPosition(
                        basePosition.getRightMultiplier(),
                        basePosition.getUpMultiplier() + step * index,
                        basePosition.getDepth()
                ))
                .setHoveredTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(),
                        new Vector3f(0.2f, 0.2f, 0.15f),
                        new AxisAngle4f()
                ), 2);
        
        createWidget(config);
    }

    protected void createTitleBarControlWidgets() {
        if (location == null) {
            return;
        }

        WidgetPosition basePosition = new WidgetPosition(0.52, 0.92);

        // Кнопка возврата (только если не главный экран)
        if (!this.getClass().isAnnotationPresent(Main.class)) {
            TextDisplayButtonConfig returnConfig = new TextDisplayButtonConfig(
                    Component.text("⏴").color(TextColor.fromHexString("#fafeff")),
                    Component.text("⏴").color(TextColor.fromHexString("#aaaeaf")),
                    () -> {
                        ScreenInstance currentScreen = UIManager.getInstance().getActiveScreen(viewer);
                        if (currentScreen != null) {
                            UIManager.getInstance().closeScreen(viewer);
                        }
                    }
            )
                    .setPosition(basePosition.clone().addHorizontal(-0.14))
                    .setScale(0.75f, 0.75f, 0.75f)
                    .setTolerance(0.04)
                    .setBackgroundColor(org.bukkit.Color.fromRGB(30, 30, 30))
                    .setBackgroundAlpha(0)
                    .setHoveredBackgroundAlpha(0)
                    .setHoveredBackgroundColor(org.bukkit.Color.fromRGB(60, 60, 60));

            createTextWidget(returnConfig);
        }

        // Кнопка закрытия
        TextDisplayButtonConfig closeConfig = new TextDisplayButtonConfig(
                Component.text("⏺").color(TextColor.fromHexString("#ff2147")),
                Component.text("⏺").color(TextColor.fromHexString("#af2141")),
                this::tryClosePublic
        )
                .setPosition(basePosition.clone().addHorizontal(0.14))
                .setScale(0.75f, 0.75f, 0.75f)
                .setTolerance(0.035)
                .setBackgroundColor(org.bukkit.Color.fromRGB(30, 30, 30))
                .setBackgroundAlpha(0)
                .setHoveredBackgroundAlpha(0)
                .setHoveredBackgroundColor(org.bukkit.Color.fromRGB(60, 60, 60));

        this.closeButton = createTextWidget(closeConfig);
    }

    private void updateBackgroundColor(String hexColor) {
        if (background != null && background.getDisplay() != null) {
            int alpha = 160; // Стандартная прозрачность

            if (hexColor == null) {
                background.getDisplay().setBackgroundColor(Color.fromARGB(alpha, 0, 0, 0));
            } else {
                java.awt.Color color = java.awt.Color.decode(hexColor);
                background.getDisplay().setBackgroundColor(Color.fromARGB(
                        alpha,
                        color.getRed(),
                        color.getGreen(),
                        color.getBlue()
                ));
            }
        }
    }

    // Публичный метод для внешнего использования
    public void tryClosePublic() {
        tryCloseInternal();
    }
    
    private void tryCloseInternal() {
        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.5f, 1.0f);

        this.removeWithAnimation();

        if (onClose != null) onClose.run();
    }

    private void updateWidgetPosition(ItemDisplayButtonWidget widget) {
        Location buttonLoc = location.clone();
        Vector direction = buttonLoc.getDirection();
        Vector right = direction.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = right.getCrossProduct(direction).normalize();

        WidgetPosition position = widget.getPosition();
        if (position != null) {
            buttonLoc.add(right.multiply(position.getRightMultiplier()));
            buttonLoc.add(up.multiply(position.getUpMultiplier()));
            buttonLoc.add(direction.multiply(position.getDepth()));
        }

        Location currentLoc = widget.getDisplay().getLocation();
        buttonLoc.setYaw(currentLoc.getYaw());
        buttonLoc.setPitch(currentLoc.getPitch());

        widget.getDisplay().teleport(buttonLoc);

        widget.updateCachedPosition();
    }

    private void updateWidgetPosition(TextDisplayButtonWidget widget) {
        Location buttonLoc = location.clone();
        Vector direction = buttonLoc.getDirection();
        Vector right = direction.getCrossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = right.getCrossProduct(direction).normalize();

        WidgetPosition position = widget.getPosition();
        if (position != null) {
            buttonLoc.add(right.multiply(position.getRightMultiplier()));
            buttonLoc.add(up.multiply(position.getUpMultiplier()));
            buttonLoc.add(direction.multiply(position.getDepth()));
        }

        Location currentLoc = widget.getDisplay().getLocation();
        buttonLoc.setYaw(currentLoc.getYaw());
        buttonLoc.setPitch(currentLoc.getPitch());

        widget.getDisplay().teleport(buttonLoc);

        widget.updateCachedPosition();
    }

    private Runnable onClose;

    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    public List<Widget> getChildren() {
        return children;
    }

    @Override
    public void setLocation(Location location) {
        super.setLocation(location);
        if (background != null && background.getDisplay() != null) {
            background.getDisplay().teleport(location);
        }
    }



    public Class<? extends Screen> getCurrentScreenClass() {
        return CURRENT_SCREEN_CLASS;
    }

    public Class<? extends Screen> getParentScreen() {
        return null;
    }

    public ItemDisplayButtonConfig[] getBranchWidgets(Player player) {
        return new ItemDisplayButtonConfig[0];
    }

    public TextDisplayButtonConfig[] getTextWidgets(Player player) {
        return new TextDisplayButtonConfig[0];
    }

    @Override
    public void updateDisplayPosition(Location location, float yaw, float pitch) {
        if (background != null && background.getDisplay() != null) {
            Location displayLoc = background.getDisplay().getLocation();
            displayLoc.setX(location.getX());
            displayLoc.setY(location.getY());
            displayLoc.setZ(location.getZ());
            displayLoc.setYaw(yaw);
            displayLoc.setPitch(pitch);
            background.getDisplay().teleport(displayLoc);
        }
    }

    @Override
    public void setBackgroundColor(Color color) {
        if (background != null && background.getDisplay() != null) {
            background.getDisplay().setBackgroundColor(color);
        }
    }

    @Override
    public Class<? extends WidgetManager> getParentManager() {
        return getParentScreen();
    }

    @Override
    public void createWithAnimation(Player player) {
        Animation.createDefaultScreenWithAnimation(this, player);
    }
}
package padej.displayLib.ui;

import padej.displayLib.DisplayLib;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public abstract class Screen extends WidgetManager implements IDisplayable, IParentable, Animatable {
    private TextDisplayButtonWidget background;
    private final Class<? extends Screen> CURRENT_SCREEN_CLASS;

    private boolean isFollowing = false;
    public static boolean isSaved = false;
    private Vector relativePosition;
    private static Vector savedPosition;

    @AlwaysOnScreen(Screen.class)
    private TextDisplayButtonWidget followButton;
    @AlwaysOnScreen(Screen.class)
    private TextDisplayButtonWidget saveButton;
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
                .setTranslation(new Vector3f(0, 0, 0)); // Устанавливаем правильную позицию сразу

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
        if (isSaved && !isPlayerInSavedRange()) {
            return;
        }

        resetVarsAndBackground();

        // Удаляем все виджеты, включая фон
        List<Widget> toRemove = new ArrayList<>(children);
        for (Widget widget : toRemove) {
            widget.remove();
        }
        children.clear();

        // Явно удаляем фон, если он еще существует
        if (background != null) {
            background.remove();
            background = null;
        }

        super.remove();
    }

    public void removeWithAnimation() {
        if (isSaved && !isPlayerInSavedRange()) {
            return;
        }
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
        isFollowing = false;
        isSaved = false;
        relativePosition = null;
        savedPosition = null;

        updateBackgroundColor(null);
    }

    public boolean isPlayerInRange() {
        return viewer.getLocation().distance(location) <= 5;
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

        if (followButton == null || saveButton == null || closeButton == null) {
            createTitleBarControlWidgets();
        }

        createScreenWidgets(player);
    }

    public void createScreenWidgets(Player player) {
    }

    protected void createTitleBarControlWidgets() {
        if (location == null) {
            return;
        }

        WidgetPosition basePosition = new WidgetPosition(0.52, 0.92);

        if (!this.getClass().isAnnotationPresent(Main.class)) {
            TextDisplayButtonConfig returnConfig = new TextDisplayButtonConfig(
                    Component.text("⏴").color(TextColor.fromHexString("#fafeff")),
                    Component.text("⏴").color(TextColor.fromHexString("#aaaeaf")),
                    () -> {
                        Screen currentScreen = (Screen) UIManager.getInstance().getActiveScreen(viewer);
                        if (currentScreen != null) {
                            ChangeScreen.switchToParent(viewer, currentScreen.getCurrentScreenClass());
                        }
                    }
            )
                    .setPosition(basePosition.clone().addHorizontal(-0.28))
                    .setScale(0.75f, 0.75f, 0.75f)
                    .setTolerance(0.04)
                    .setBackgroundColor(org.bukkit.Color.fromRGB(30, 30, 30))
                    .setBackgroundAlpha(0)
                    .setHoveredBackgroundAlpha(0)
                    .setHoveredBackgroundColor(org.bukkit.Color.fromRGB(60, 60, 60));

            createTextWidget(returnConfig);
        }

        TextDisplayButtonConfig closeConfig = new TextDisplayButtonConfig(
                Component.text("⏺").color(TextColor.fromHexString("#ff2147")),
                Component.text("⏺").color(TextColor.fromHexString("#af2141")),
                this::tryClose
        )
                .setPosition(basePosition.clone().addHorizontal(0.14))
                .setScale(0.75f, 0.75f, 0.75f)
                .setTolerance(0.035)
                .setBackgroundColor(org.bukkit.Color.fromRGB(30, 30, 30))
                .setBackgroundAlpha(0)
                .setHoveredBackgroundAlpha(0)
                .setHoveredBackgroundColor(org.bukkit.Color.fromRGB(60, 60, 60));

        TextDisplayButtonConfig followConfig = new TextDisplayButtonConfig(
                Component.text("⏺").color(TextColor.fromHexString("#ffc72c")),
                Component.text("⏺").color(TextColor.fromHexString("#af802b")),
                this::toggleFollow
        )
                .setPosition(basePosition.clone())
                .setScale(0.75f, 0.75f, 0.75f)
                .setTolerance(0.035)
                .setBackgroundColor(org.bukkit.Color.fromRGB(30, 30, 30))
                .setBackgroundAlpha(0)
                .setHoveredBackgroundAlpha(0)
                .setHoveredBackgroundColor(org.bukkit.Color.fromRGB(60, 60, 60));

        TextDisplayButtonConfig saveConfig = new TextDisplayButtonConfig(
                Component.text("⏺").color(TextColor.fromHexString("#2aff55")),
                Component.text("⏺").color(TextColor.fromHexString("#29af48")),
                this::toggleSave
        )
                .setPosition(basePosition.clone().addHorizontal(-0.14))
                .setScale(0.75f, 0.75f, 0.75f)
                .setTolerance(0.035)
                .setBackgroundColor(org.bukkit.Color.fromRGB(30, 30, 30))
                .setBackgroundAlpha(0)
                .setHoveredBackgroundAlpha(0)
                .setHoveredBackgroundColor(org.bukkit.Color.fromRGB(60, 60, 60));

        this.closeButton = createTextWidget(closeConfig);
        this.followButton = createTextWidget(followConfig);
        this.saveButton = createTextWidget(saveConfig);

        if (followButton != null) {
            followButton.getDisplay().setGlowing(isFollowing);
        }

        if (saveButton != null) {
            saveButton.getDisplay().setGlowing(isSaved);
        }
    }

    public void toggleFollow() {
        if (isSaved) {
            isSaved = false;
            savedPosition = null;
            if (saveButton != null) {
                saveButton.getDisplay().setGlowing(false);
            }
        }

        children.removeIf(widget -> {
            if (widget instanceof ItemDisplayButtonWidget) {
                return ((ItemDisplayButtonWidget) widget).getDisplay() == null ||
                        !((ItemDisplayButtonWidget) widget).getDisplay().isValid();
            } else if (widget instanceof TextDisplayButtonWidget) {
                return ((TextDisplayButtonWidget) widget).getDisplay() == null ||
                        !((TextDisplayButtonWidget) widget).getDisplay().isValid();
            }
            return false;
        });

        isFollowing = !isFollowing;
        if (followButton != null) {
            followButton.getDisplay().setGlowing(isFollowing);
        }

        if (isFollowing) {
            Vector playerPos = viewer.getLocation().toVector();
            Vector displayPos = location.toVector();
            relativePosition = displayPos.subtract(playerPos);

            updateBackgroundColor("#af802b");
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
        } else {
            updateBackgroundColor("#000000");
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.5f);
        }
    }

    private void toggleSave() {
        if (isFollowing) {
            isFollowing = false;
            relativePosition = null;
            if (followButton != null) {
                followButton.getDisplay().setGlowing(false);
            }
        }

        isSaved = !isSaved;
        if (saveButton != null) {
            saveButton.getDisplay().setGlowing(isSaved);
        }

        if (isSaved) {
            savedPosition = location.toVector();

            updateBackgroundColor("#29af48");
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.5f, 2.0f);
        } else {
            updateBackgroundColor("#000000");
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.3f, 1.5f);
        }
    }

    private void updateBackgroundColor(String hexColor) {
        if (background != null && background.getDisplay() != null) {
            int alpha = (isFollowing || isSaved) ? 100 : 160;

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

    public void tryClose() {
        if (!isSaved || isPlayerInSavedRange()) {
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.5f, 1.0f);

            this.removeWithAnimation();

            if (onClose != null) onClose.run();
        }
    }

    private boolean isPlayerInSavedRange() {
        if (!isSaved || savedPosition == null) return true;
        return viewer.getLocation().toVector().distance(savedPosition) <= 5;
    }

    public void updatePosition() {
        if (!isFollowing) return;

        // Удаляем недействительные виджеты перед обновлением позиции
        children.removeIf(widget -> !widget.isValid());

        Location newLoc = viewer.getLocation().clone();
        Vector newPos = newLoc.toVector().add(relativePosition);
        location.setX(newPos.getX());
        location.setY(newPos.getY());
        location.setZ(newPos.getZ());

        // Обновляем позицию фона
        if (background != null && background.isValid()) {
            TextDisplay textDisplay = background.getDisplay();
            Location displayLoc = textDisplay.getLocation();
            displayLoc.setX(location.getX());
            displayLoc.setY(location.getY());
            displayLoc.setZ(location.getZ());
            textDisplay.teleport(displayLoc);
        }

        // Обновляем позиции всех виджетов
        for (Widget widget : new ArrayList<>(children)) {
            if (widget == background) continue;

            if (widget instanceof ItemDisplayButtonWidget) {
                updateWidgetPosition((ItemDisplayButtonWidget) widget);
            } else if (widget instanceof TextDisplayButtonWidget) {
                updateWidgetPosition((TextDisplayButtonWidget) widget);
            }
        }
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

    protected Screen() {
        super(null, new Location(org.bukkit.Bukkit.getWorlds().getFirst(), 0, 0, 0));
        this.viewer = null;
        background = null;
        CURRENT_SCREEN_CLASS = this.getClass();
    }

    protected Screen(Player viewer, Location location) {
        super(viewer, location);
        this.viewer = viewer;
        background = null;
        CURRENT_SCREEN_CLASS = this.getClass();
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

    public boolean isFollowing() {
        return isFollowing;
    }
}
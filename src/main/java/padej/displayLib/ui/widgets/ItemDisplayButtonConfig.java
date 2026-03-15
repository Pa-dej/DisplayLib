package padej.displayLib.ui.widgets;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public class ItemDisplayButtonConfig {
    private Material material;
    private Runnable onClick;
    private String tooltip;
    private TextColor tooltipColor;
    private int tooltipDelay = 30;
    private boolean hasTooltip;
    private WidgetPosition position;
    private ItemDisplay.ItemDisplayTransform displayTransform = ItemDisplay.ItemDisplayTransform.GUI;
    private float scaleX = .15f;
    private float scaleY = .15f;
    private float scaleZ = .03f;
    private Color glowColor;
    private org.bukkit.Sound clickSound = org.bukkit.Sound.UI_BUTTON_CLICK;
    private boolean soundEnabled = true;
    private float soundVolume = 0.5f;
    private float soundPitch = 1.0f;
    private double horizontalTolerance = 0.06;
    private double verticalTolerance = 0.06;
    private ItemMeta itemMeta;
    private Vector3f translation;
    private Transformation hoveredTransformation;
    private int hoveredTransformationDuration;
    private boolean glowOnHover = true;
    private boolean privateVisible = false;

    public ItemDisplayButtonConfig(Material material, Runnable onClick) {
        this.material = material;
        this.onClick = onClick;
        this.hasTooltip = false;
        this.tooltipColor = TextColor.fromHexString("#fcd720");
    }

    public ItemDisplayButtonConfig setTooltip(String tooltip) {
        this.tooltip = tooltip;
        this.hasTooltip = true;
        return this;
    }

    public ItemDisplayButtonConfig setTooltipColor(TextColor color) {
        this.tooltipColor = color;
        return this;
    }

    public ItemDisplayButtonConfig setTooltipDelay(int ticks) {
        this.tooltipDelay = ticks;
        return this;
    }

    public ItemDisplayButtonConfig setPosition(WidgetPosition position) {
        this.position = position;
        return this;
    }

    public ItemDisplayButtonConfig setDisplayTransform(ItemDisplay.ItemDisplayTransform transform) {
        this.displayTransform = transform;
        return this;
    }

    public ItemDisplayButtonConfig setScale(float x, float y, float z) {
        this.scaleX = x;
        this.scaleY = y;
        this.scaleZ = z;
        return this;
    }

    public ItemDisplayButtonConfig setGlowColor(Color color) {
        this.glowColor = color;
        return this;
    }

    public ItemDisplayButtonConfig setClickSound(org.bukkit.Sound sound) {
        this.clickSound = sound;
        return this;
    }

    public ItemDisplayButtonConfig setClickSound(org.bukkit.Sound sound, float volume, float pitch) {
        this.clickSound = sound;
        this.soundVolume = volume;
        this.soundPitch = pitch;
        return this;
    }

    public ItemDisplayButtonConfig disableClickSound() {
        this.soundEnabled = false;
        return this;
    }

    public ItemDisplayButtonConfig setTolerance(double tolerance) {
        this.horizontalTolerance = tolerance;
        this.verticalTolerance = tolerance;
        return this;
    }

    public ItemDisplayButtonConfig setTolerance(double horizontalTolerance, double verticalTolerance) {
        this.horizontalTolerance = horizontalTolerance;
        this.verticalTolerance = verticalTolerance;
        return this;
    }

    public ItemDisplayButtonConfig setToleranceHorizontal(double tolerance) {
        this.horizontalTolerance = tolerance;
        return this;
    }

    public ItemDisplayButtonConfig setToleranceVertical(double tolerance) {
        this.verticalTolerance = tolerance;
        return this;
    }

    public ItemDisplayButtonConfig setItemMeta(ItemMetaModifier modifier) {
        ItemStack tempItem = new ItemStack(material);
        ItemMeta meta = tempItem.getItemMeta();
        if (meta != null) {
            this.itemMeta = modifier.modify(meta);
        }
        return this;
    }

    public ItemDisplayButtonConfig setTranslation(Vector3f translation) {
        this.translation = translation;
        return this;
    }

    public Vector3f getTranslation() {
        return translation != null ? translation : new Vector3f(0, -scaleY / 8, 0);
    }

    public Material getMaterial() {
        return material;
    }

    public Runnable getOnClick() {
        return onClick;
    }

    public String getTooltip() {
        return tooltip;
    }

    public TextColor getTooltipColor() {
        return tooltipColor;
    }

    public int getTooltipDelay() {
        return tooltipDelay;
    }

    public boolean hasTooltip() {
        return hasTooltip;
    }

    public WidgetPosition getPosition() {
        return position;
    }

    public ItemDisplay.ItemDisplayTransform getDisplayTransform() {
        return displayTransform;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public float getScaleZ() {
        return scaleZ;
    }

    public Color getGlowColor() {
        return glowColor;
    }

    public org.bukkit.Sound getClickSound() {
        return clickSound;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public double getTolerance() {
        return horizontalTolerance;
    }

    public double getToleranceHorizontal() {
        return horizontalTolerance;
    }

    public double getToleranceVertical() {
        return verticalTolerance;
    }

    public ItemMeta getItemMeta() {
        return itemMeta;
    }

    public ItemDisplayButtonConfig setHoveredTransformation(Transformation transformation, int duration) {
        this.hoveredTransformation = transformation;
        this.hoveredTransformationDuration = duration;
        return this;
    }

    public Transformation getHoveredTransformation() {
        return hoveredTransformation;
    }

    public int getHoveredTransformationDuration() {
        return hoveredTransformationDuration;
    }

    public ItemDisplayButtonConfig disableHoverGlow() {
        this.glowOnHover = false;
        return this;
    }
    
    public ItemDisplayButtonConfig setGlowOnHover(boolean glowOnHover) {
        this.glowOnHover = glowOnHover;
        return this;
    }

    public boolean isGlowOnHover() {
        return glowOnHover;
    }

    public ItemDisplayButtonConfig setPrivateVisible(boolean privateVisible) {
        this.privateVisible = privateVisible;
        return this;
    }

    public boolean isPrivateVisible() {
        return privateVisible;
    }
}
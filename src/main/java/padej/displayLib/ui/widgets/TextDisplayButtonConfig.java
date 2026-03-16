package padej.displayLib.ui.widgets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public class TextDisplayButtonConfig {
    private Component text;
    private Component hoveredText;
    private Runnable onClick;
    private Component tooltip;
    private TextColor tooltipColor;
    private int tooltipDelay;
    private WidgetPosition position;
    private Color backgroundColor = Color.fromRGB(40, 40, 40);
    private int backgroundAlpha = 150;
    private Color hoveredBackgroundColor = Color.fromRGB(60, 60, 60);
    private int hoveredBackgroundAlpha = 180;
    private float scaleX = .15f;
    private float scaleY = .15f;
    private float scaleZ = .15f;
    private double horizontalTolerance = 0.06;
    private double verticalTolerance = 0.06;
    private org.bukkit.Sound clickSound = org.bukkit.Sound.BLOCK_DISPENSER_FAIL;
    private boolean soundEnabled = true;
    private float soundVolume = 0.5f;
    private float soundPitch = 2.0f;
    private boolean textShadowEnabled = false;
    private TextDisplay.TextAlignment textAlignment = TextDisplay.TextAlignment.CENTER;
    private int maxLineWidth = 200;
    private Vector3f translation;
    private Transformation hoveredTransformation;
    private int hoveredTransformationDuration;
    private boolean privateVisible = false;

    public TextDisplayButtonConfig(Component text, Component hoveredText, Runnable onClick) {
        this.text = text;
        this.hoveredText = hoveredText;
        this.onClick = onClick;
        this.tooltipColor = TextColor.fromHexString("#868788");
    }

    public TextDisplayButtonConfig setTooltip(String tooltip) {
        this.tooltip = Component.text(tooltip);
        return this;
    }

    public TextDisplayButtonConfig setTooltip(Component tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public TextDisplayButtonConfig setTooltipColor(TextColor color) {
        this.tooltipColor = color;
        return this;
    }

    public TextDisplayButtonConfig setTooltipDelay(int ticks) {
        this.tooltipDelay = ticks;
        return this;
    }

    public TextDisplayButtonConfig setPosition(WidgetPosition position) {
        this.position = position;
        return this;
    }

    public TextDisplayButtonConfig setBackgroundColor(Color color) {
        this.backgroundColor = color;
        return this;
    }

    public TextDisplayButtonConfig setBackgroundAlpha(int alpha) {
        this.backgroundAlpha = alpha;
        return this;
    }

    public TextDisplayButtonConfig setHoveredBackgroundColor(Color color) {
        this.hoveredBackgroundColor = color;
        return this;
    }

    public TextDisplayButtonConfig setHoveredBackgroundAlpha(int alpha) {
        this.hoveredBackgroundAlpha = alpha;
        return this;
    }

    public TextDisplayButtonConfig setScale(float x, float y, float z) {
        this.scaleX = x;
        this.scaleY = y;
        this.scaleZ = z;
        return this;
    }

    public TextDisplayButtonConfig setTolerance(double tolerance) {
        this.horizontalTolerance = tolerance;
        this.verticalTolerance = tolerance;
        return this;
    }

    public TextDisplayButtonConfig setTolerance(double horizontalTolerance, double verticalTolerance) {
        this.horizontalTolerance = horizontalTolerance;
        this.verticalTolerance = verticalTolerance;
        return this;
    }

    public TextDisplayButtonConfig setToleranceHorizontal(double tolerance) {
        this.horizontalTolerance = tolerance;
        return this;
    }

    public TextDisplayButtonConfig setToleranceVertical(double tolerance) {
        this.verticalTolerance = tolerance;
        return this;
    }

    public TextDisplayButtonConfig setClickSound(org.bukkit.Sound sound) {
        this.clickSound = sound;
        return this;
    }

    public TextDisplayButtonConfig setClickSound(org.bukkit.Sound sound, float volume, float pitch) {
        this.clickSound = sound;
        this.soundVolume = volume;
        this.soundPitch = pitch;
        return this;
    }

    public TextDisplayButtonConfig disableClickSound() {
        this.soundEnabled = false;
        return this;
    }

    public TextDisplayButtonConfig enableTextShadow() {
        this.textShadowEnabled = true;
        return this;
    }

    public TextDisplayButtonConfig setTextAlignment(TextDisplay.TextAlignment alignment) {
        this.textAlignment = alignment;
        return this;
    }

    public TextDisplayButtonConfig setMaxLineWidth(int width) {
        this.maxLineWidth = width;
        return this;
    }

    public TextDisplayButtonConfig setTranslation(Vector3f translation) {
        this.translation = translation;
        return this;
    }

    public Vector3f getTranslation() {
        return translation != null ? translation : new Vector3f(0, 0, 0);
    }

    public TextDisplayButtonConfig setHoveredTransformation(Transformation transformation, int duration) {
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

    public TextDisplayButtonConfig setPrivateVisible(boolean privateVisible) {
        this.privateVisible = privateVisible;
        return this;
    }

    public boolean isPrivateVisible() {
        return privateVisible;
    }

    public Component getText() {
        return text;
    }

    public Component getHoveredText() {
        return hoveredText;
    }

    public Runnable getOnClick() {
        return onClick;
    }

    public Component getTooltip() {
        return tooltip;
    }

    public TextColor getTooltipColor() {
        return tooltipColor;
    }

    public int getTooltipDelay() {
        return tooltipDelay;
    }

    public WidgetPosition getPosition() {
        return position;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public int getBackgroundAlpha() {
        return backgroundAlpha;
    }

    public Color getHoveredBackgroundColor() {
        return hoveredBackgroundColor;
    }

    public int getHoveredBackgroundAlpha() {
        return hoveredBackgroundAlpha;
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

    public double getTolerance() {
        return horizontalTolerance;
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

    public double getToleranceHorizontal() {
        return horizontalTolerance;
    }

    public double getToleranceVertical() {
        return verticalTolerance;
    }

    public boolean isTextShadowEnabled() {
        return textShadowEnabled;
    }

    public TextDisplay.TextAlignment getTextAlignment() {
        return textAlignment;
    }

    public int getMaxLineWidth() {
        return maxLineWidth;
    }
}
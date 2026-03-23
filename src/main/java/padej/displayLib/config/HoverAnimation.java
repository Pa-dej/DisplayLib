package padej.displayLib.config;

import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/**
 * Конфигурация анимации при наведении на виджет.
 * 
 * <p>Поддерживает различные типы анимаций:</p>
 * <ul>
 * <li><b>PRESET</b> - готовые эффекты (bounce, scale, glow, pulse)</li>
 * <li><b>SCALE</b> - изменение размера</li>
 * <li><b>TRANSLATE</b> - смещение позиции</li>
 * <li><b>ROTATE</b> - поворот</li>
 * <li><b>TRANSFORM</b> - полная трансформация</li>
 * <li><b>COMBINED</b> - комбинация нескольких эффектов</li>
 * </ul>
 * 
 * <h2>Примеры YAML конфигурации:</h2>
 * <pre>{@code
 * # Простой предустановленный эффект
 * hoverAnimation:
 *   type: PRESET
 *   preset: bounce
 *   duration: 10
 *   intensity: 1.2
 * 
 * # Изменение размера
 * hoverAnimation:
 *   type: SCALE
 *   duration: 8
 *   scale: [1.1, 1.1, 1.0]
 *   easing: ease_out
 * 
 * # Смещение позиции
 * hoverAnimation:
 *   type: TRANSLATE
 *   duration: 6
 *   offset: [0.0, 0.02, 0.0]
 *   easing: ease_in_out
 * 
 * # Поворот
 * hoverAnimation:
 *   type: ROTATE
 *   duration: 12
 *   rotation: [0, 15, 0]  # градусы
 *   axis: [0, 1, 0]       # ось поворота
 * 
 * # Комбинированный эффект
 * hoverAnimation:
 *   type: COMBINED
 *   duration: 10
 *   effects:
 *     - type: SCALE
 *       scale: [1.05, 1.05, 1.0]
 *     - type: TRANSLATE
 *       offset: [0.0, 0.01, 0.0]
 * }</pre>
 */
public class HoverAnimation {
    
    /**
     * Тип анимации hover эффекта
     */
    public enum AnimationType {
        /** Готовые предустановленные эффекты */
        PRESET,
        /** Изменение размера виджета */
        SCALE,
        /** Смещение позиции виджета */
        TRANSLATE,
        /** Поворот виджета */
        ROTATE,
        /** Полная трансформация (Matrix4f) */
        TRANSFORM,
        /** Комбинация нескольких эффектов */
        COMBINED,
        /** Непрерывная пульсация (зацикленная) */
        PULSE_CONTINUOUS
    }
    
    /**
     * Готовые предустановки анимаций
     */
    public enum AnimationPreset {
        /** Эффект подпрыгивания */
        BOUNCE,
        /** Плавное увеличение */
        SCALE,
        /** Пульсация */
        PULSE,
        /** Свечение (только для ItemDisplay) */
        GLOW,
        /** Легкое покачивание */
        WOBBLE,
        /** Поднятие вверх */
        LIFT,
        /** Поворот */
        SPIN
    }
    
    /**
     * Типы easing для плавности анимации
     */
    public enum EasingType {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT,
        BOUNCE,
        ELASTIC
    }
    
    // Основные параметры
    private AnimationType type = AnimationType.PRESET;
    private int duration = 10; // тики
    private EasingType easing = EasingType.EASE_OUT;
    private boolean reverseOnExit = true; // возвращать ли к исходному состоянию
    
    // Для PRESET
    private AnimationPreset preset = AnimationPreset.SCALE;
    private float intensity = 1.2f; // интенсивность эффекта
    
    // Для SCALE
    private float[] scale = {1.1f, 1.1f, 1.0f};
    
    // Для TRANSLATE
    private float[] offset = {0.0f, 0.02f, 0.0f};
    
    // Для ROTATE
    private float[] rotation = {0.0f, 15.0f, 0.0f}; // градусы
    private float[] axis = {0.0f, 1.0f, 0.0f}; // ось поворота
    
    // Для TRANSFORM
    private float[] translation = {0.0f, 0.0f, 0.0f};
    private float[] leftRotation = {0.0f, 0.0f, 0.0f, 1.0f}; // quaternion
    private float[] scaleVector = {1.0f, 1.0f, 1.0f};
    private float[] rightRotation = {0.0f, 0.0f, 0.0f, 1.0f}; // quaternion
    
    // Для COMBINED
    private HoverAnimation[] effects;
    
    // Дополнительные параметры
    private int delay = 0; // задержка перед началом анимации
    private boolean loop = false; // зацикливать ли анимацию
    private int loopCount = -1; // количество повторений (-1 = бесконечно)
    
    // Конструкторы
    public HoverAnimation() {}
    
    public HoverAnimation(AnimationType type, int duration) {
        this.type = type;
        this.duration = duration;
    }
    
    // Статические методы для создания популярных эффектов
    public static HoverAnimation bounce() {
        return new HoverAnimation(AnimationType.PRESET, 8)
                .setPreset(AnimationPreset.BOUNCE)
                .setIntensity(1.15f);
    }
    
    public static HoverAnimation scale(float factor) {
        return new HoverAnimation(AnimationType.SCALE, 6)
                .setScale(new float[]{factor, factor, 1.0f});
    }
    
    public static HoverAnimation lift(float height) {
        return new HoverAnimation(AnimationType.TRANSLATE, 8)
                .setOffset(new float[]{0.0f, height, 0.0f});
    }
    
    public static HoverAnimation glow() {
        return new HoverAnimation(AnimationType.PRESET, 4)
                .setPreset(AnimationPreset.GLOW);
    }
    
    /**
     * Создает Transformation для hover состояния на основе конфигурации
     * @deprecated Используйте applyHoverAnimation() для правильной easing анимации
     */
    @Deprecated
    public Transformation createHoverTransformation(Vector3f originalTranslation, Vector3f originalScale) {
        switch (type) {
            case PRESET -> {
                return createPresetTransformation(originalTranslation, originalScale);
            }
            case SCALE -> {
                Vector3f newScale = new Vector3f(
                    originalScale.x * scale[0],
                    originalScale.y * scale[1], 
                    originalScale.z * scale[2]
                );
                return new Transformation(
                    originalTranslation,
                    new AxisAngle4f(),
                    newScale,
                    new AxisAngle4f()
                );
            }
            case TRANSLATE -> {
                Vector3f newTranslation = new Vector3f(
                    originalTranslation.x + offset[0],
                    originalTranslation.y + offset[1],
                    originalTranslation.z + offset[2]
                );
                return new Transformation(
                    newTranslation,
                    new AxisAngle4f(),
                    originalScale,
                    new AxisAngle4f()
                );
            }
            case ROTATE -> {
                AxisAngle4f rotationAngle = new AxisAngle4f(
                    (float) Math.toRadians(rotation[1]), // угол в радианах
                    axis[0], axis[1], axis[2] // ось
                );
                return new Transformation(
                    originalTranslation,
                    rotationAngle,
                    originalScale,
                    new AxisAngle4f()
                );
            }
            case TRANSFORM -> {
                return new Transformation(
                    new Vector3f(translation[0], translation[1], translation[2]),
                    new AxisAngle4f(leftRotation[3], leftRotation[0], leftRotation[1], leftRotation[2]),
                    new Vector3f(scaleVector[0], scaleVector[1], scaleVector[2]),
                    new AxisAngle4f(rightRotation[3], rightRotation[0], rightRotation[1], rightRotation[2])
                );
            }
            default -> {
                return new Transformation(originalTranslation, new AxisAngle4f(), originalScale, new AxisAngle4f());
            }
        }
    }
    
    /**
     * Применяет hover анимацию к display entity с правильной easing интерполяцией
     */
    public void applyHoverAnimation(org.bukkit.entity.Display display, Vector3f originalTranslation, Vector3f originalScale, boolean isHovering) {
        if (display == null || !display.isValid()) return;
        
        java.util.function.DoubleUnaryOperator easingFn = padej.displayLib.utils.EasedAnimation.getEasingFunction(easing);
        
        if (isHovering) {
            // Применяем hover эффект
            switch (type) {
                case PRESET -> applyPresetAnimation(display, originalTranslation, originalScale, easingFn);
                case SCALE -> {
                    Vector3f targetScale = new Vector3f(
                        originalScale.x * scale[0],
                        originalScale.y * scale[1],
                        originalScale.z * scale[2]
                    );
                    
                    // Для тестирования используем простую интерполяцию
                    if (duration <= 10) {
                        padej.displayLib.utils.EasedAnimation.animateScaleSimple(display, originalScale, targetScale, duration);
                    } else {
                        padej.displayLib.utils.EasedAnimation.animateScale(display, originalScale, targetScale, duration, easingFn);
                    }
                }
                case TRANSLATE -> {
                    Vector3f targetTranslation = new Vector3f(
                        originalTranslation.x + offset[0],
                        originalTranslation.y + offset[1],
                        originalTranslation.z + offset[2]
                    );
                    
                    // Для тестирования используем простую интерполяцию
                    if (duration <= 10) {
                        padej.displayLib.utils.EasedAnimation.animateTranslationSimple(display, originalTranslation, targetTranslation, duration);
                    } else {
                        padej.displayLib.utils.EasedAnimation.animateTranslation(display, originalTranslation, targetTranslation, duration, easingFn);
                    }
                }
                case ROTATE -> {
                    // Упрощенная реализация поворота
                    Quaternionf targetRotation = new Quaternionf().rotateAxis(
                        (float) Math.toRadians(rotation[1]),
                        axis[0], axis[1], axis[2]
                    );
                    org.bukkit.util.Transformation targetTransform = new org.bukkit.util.Transformation(
                        originalTranslation,
                        targetRotation,
                        originalScale,
                        new Quaternionf()
                    );
                    // Используем стандартную интерполяцию для поворотов
                    padej.displayLib.utils.Animation.applyTransformationWithInterpolation(display, targetTransform, duration);
                }
                case COMBINED -> {
                    if (effects != null && effects.length > 0) {
                        // Для комбинированных эффектов применяем каждый отдельно
                        for (HoverAnimation effect : effects) {
                            if (effect != null) {
                                effect.applyHoverAnimation(display, originalTranslation, originalScale, true);
                            }
                        }
                    }
                }
                case PULSE_CONTINUOUS -> {
                    // Непрерывная пульсация
                    padej.displayLib.utils.EasedAnimation.animatePulsingScale(
                        display, 
                        originalScale, 
                        intensity, 
                        duration / 2  // Половина длительности для каждого цикла пульсации
                    );
                }
            }
        } else if (reverseOnExit) {
            // Возвращаем к исходному состоянию
            switch (type) {
                case PRESET -> applyPresetReverseAnimation(display, originalTranslation, originalScale, easingFn);
                case SCALE -> {
                    Vector3f currentScale = display.getTransformation().getScale();
                    
                    // Для тестирования используем простую интерполяцию
                    if (duration <= 10) {
                        padej.displayLib.utils.EasedAnimation.animateScaleSimple(display, currentScale, originalScale, duration);
                    } else {
                        padej.displayLib.utils.EasedAnimation.animateScale(display, currentScale, originalScale, duration, easingFn);
                    }
                }
                case TRANSLATE -> {
                    Vector3f currentTranslation = display.getTransformation().getTranslation();
                    
                    // Для тестирования используем простую интерполяцию
                    if (duration <= 10) {
                        padej.displayLib.utils.EasedAnimation.animateTranslationSimple(display, currentTranslation, originalTranslation, duration);
                    } else {
                        padej.displayLib.utils.EasedAnimation.animateTranslation(display, currentTranslation, originalTranslation, duration, easingFn);
                    }
                }
                case ROTATE -> {
                    Quaternionf currentRotation = display.getTransformation().getLeftRotation();
                    Quaternionf originalRotation = new Quaternionf();
                    // Для простоты используем прямое применение трансформации
                    org.bukkit.util.Transformation originalTransform = new org.bukkit.util.Transformation(
                            originalTranslation,
                            originalRotation,
                            originalScale,
                            new Quaternionf()
                    );
                    padej.displayLib.utils.Animation.applyTransformationWithInterpolation(display, originalTransform, duration);
                }
                case COMBINED -> {
                    if (effects != null && effects.length > 0) {
                        for (HoverAnimation effect : effects) {
                            if (effect != null) {
                                effect.applyHoverAnimation(display, originalTranslation, originalScale, false);
                            }
                        }
                    }
                }
                case PULSE_CONTINUOUS -> {
                    // Останавливаем непрерывную анимацию и возвращаем к исходному размеру
                    padej.displayLib.utils.EasedAnimation.stopContinuousAnimation(display);
                    
                    // Возвращаем к исходному размеру
                    Vector3f currentScale = display.getTransformation().getScale();
                    if (duration <= 10) {
                        padej.displayLib.utils.EasedAnimation.animateScaleSimple(display, currentScale, originalScale, duration);
                    } else {
                        padej.displayLib.utils.EasedAnimation.animateScale(display, currentScale, originalScale, duration, easingFn);
                    }
                }
            }
        }
    }
    
    private void applyPresetAnimation(org.bukkit.entity.Display display, Vector3f originalTranslation, Vector3f originalScale, java.util.function.DoubleUnaryOperator easingFn) {
        switch (preset) {
            case BOUNCE, SCALE, PULSE -> {
                Vector3f targetScale = new Vector3f(
                    originalScale.x * intensity,
                    originalScale.y * intensity,
                    originalScale.z
                );
                
                // Для тестирования используем простую интерполяцию
                if (duration <= 10) {
                    padej.displayLib.utils.EasedAnimation.animateScaleSimple(display, originalScale, targetScale, duration);
                } else {
                    padej.displayLib.utils.EasedAnimation.animateScale(display, originalScale, targetScale, duration, easingFn);
                }
            }
            case LIFT -> {
                Vector3f targetTranslation = new Vector3f(
                    originalTranslation.x,
                    originalTranslation.y + (0.02f * intensity),
                    originalTranslation.z
                );
                
                // Для тестирования используем простую интерполяцию
                if (duration <= 10) {
                    padej.displayLib.utils.EasedAnimation.animateTranslationSimple(display, originalTranslation, targetTranslation, duration);
                } else {
                    padej.displayLib.utils.EasedAnimation.animateTranslation(display, originalTranslation, targetTranslation, duration, easingFn);
                }
            }
            case WOBBLE, SPIN -> {
                // Упрощенная реализация поворота
                Quaternionf targetRotation = new Quaternionf().rotateAxis(
                    (float) Math.toRadians(15.0f * intensity),
                    0.0f, 1.0f, 0.0f
                );
                org.bukkit.util.Transformation targetTransform = new org.bukkit.util.Transformation(
                    originalTranslation,
                    targetRotation,
                    originalScale,
                    new Quaternionf()
                );
                // Используем стандартную интерполяцию для поворотов
                padej.displayLib.utils.Animation.applyTransformationWithInterpolation(display, targetTransform, duration);
            }
            case GLOW -> {
                // Glow обрабатывается отдельно в виджетах
            }
        }
    }
    
    private void applyPresetReverseAnimation(org.bukkit.entity.Display display, Vector3f originalTranslation, Vector3f originalScale, java.util.function.DoubleUnaryOperator easingFn) {
        switch (preset) {
            case BOUNCE, SCALE, PULSE -> {
                Vector3f currentScale = display.getTransformation().getScale();
                
                // Для тестирования используем простую интерполяцию
                if (duration <= 10) {
                    padej.displayLib.utils.EasedAnimation.animateScaleSimple(display, currentScale, originalScale, duration);
                } else {
                    padej.displayLib.utils.EasedAnimation.animateScale(display, currentScale, originalScale, duration, easingFn);
                }
            }
            case LIFT -> {
                Vector3f currentTranslation = display.getTransformation().getTranslation();
                
                // Для тестирования используем простую интерполяцию
                if (duration <= 10) {
                    padej.displayLib.utils.EasedAnimation.animateTranslationSimple(display, currentTranslation, originalTranslation, duration);
                } else {
                    padej.displayLib.utils.EasedAnimation.animateTranslation(display, currentTranslation, originalTranslation, duration, easingFn);
                }
            }
            case WOBBLE, SPIN -> {
                Quaternionf currentRotation = display.getTransformation().getLeftRotation();
                Quaternionf originalRotation = new Quaternionf();
                // Для простоты используем прямое применение трансформации
                org.bukkit.util.Transformation originalTransform = new org.bukkit.util.Transformation(
                        originalTranslation,
                        originalRotation,
                        originalScale,
                        new Quaternionf()
                );
                padej.displayLib.utils.Animation.applyTransformationWithInterpolation(display, originalTransform, duration);
            }
            case GLOW -> {
                // Glow обрабатывается отдельно в виджетах
            }
        }
    }
    
    private Transformation createPresetTransformation(Vector3f originalTranslation, Vector3f originalScale) {
        switch (preset) {
            case BOUNCE, SCALE -> {
                Vector3f newScale = new Vector3f(
                    originalScale.x * intensity,
                    originalScale.y * intensity,
                    originalScale.z
                );
                return new Transformation(originalTranslation, new AxisAngle4f(), newScale, new AxisAngle4f());
            }
            case LIFT -> {
                Vector3f newTranslation = new Vector3f(
                    originalTranslation.x,
                    originalTranslation.y + (0.02f * intensity),
                    originalTranslation.z
                );
                return new Transformation(newTranslation, new AxisAngle4f(), originalScale, new AxisAngle4f());
            }
            case WOBBLE, SPIN -> {
                AxisAngle4f rotationAngle = new AxisAngle4f(
                    (float) Math.toRadians(10.0f * intensity),
                    0.0f, 1.0f, 0.0f
                );
                return new Transformation(originalTranslation, rotationAngle, originalScale, new AxisAngle4f());
            }
            case PULSE -> {
                float pulseScale = 1.0f + (0.1f * intensity);
                Vector3f newScale = new Vector3f(
                    originalScale.x * pulseScale,
                    originalScale.y * pulseScale,
                    originalScale.z
                );
                return new Transformation(originalTranslation, new AxisAngle4f(), newScale, new AxisAngle4f());
            }
            default -> {
                return new Transformation(originalTranslation, new AxisAngle4f(), originalScale, new AxisAngle4f());
            }
        }
    }
    
    // Getters и Setters
    public AnimationType getType() { return type; }
    public HoverAnimation setType(AnimationType type) { this.type = type; return this; }
    
    public int getDuration() { return duration; }
    public HoverAnimation setDuration(int duration) { this.duration = duration; return this; }
    
    public EasingType getEasing() { return easing; }
    public HoverAnimation setEasing(EasingType easing) { this.easing = easing; return this; }
    
    public boolean isReverseOnExit() { return reverseOnExit; }
    public HoverAnimation setReverseOnExit(boolean reverseOnExit) { this.reverseOnExit = reverseOnExit; return this; }
    
    public AnimationPreset getPreset() { return preset; }
    public HoverAnimation setPreset(AnimationPreset preset) { this.preset = preset; return this; }
    
    public float getIntensity() { return intensity; }
    public HoverAnimation setIntensity(float intensity) { this.intensity = intensity; return this; }
    
    public float[] getScale() { return scale; }
    public HoverAnimation setScale(float[] scale) { this.scale = scale; return this; }
    
    public float[] getOffset() { return offset; }
    public HoverAnimation setOffset(float[] offset) { this.offset = offset; return this; }
    
    public float[] getRotation() { return rotation; }
    public HoverAnimation setRotation(float[] rotation) { this.rotation = rotation; return this; }
    
    public float[] getAxis() { return axis; }
    public HoverAnimation setAxis(float[] axis) { this.axis = axis; return this; }
    
    public int getDelay() { return delay; }
    public HoverAnimation setDelay(int delay) { this.delay = delay; return this; }
    
    public boolean isLoop() { return loop; }
    public HoverAnimation setLoop(boolean loop) { this.loop = loop; return this; }
    
    public int getLoopCount() { return loopCount; }
    public HoverAnimation setLoopCount(int loopCount) { this.loopCount = loopCount; return this; }
    
    public HoverAnimation[] getEffects() { return effects; }
    public HoverAnimation setEffects(HoverAnimation[] effects) { this.effects = effects; return this; }
}
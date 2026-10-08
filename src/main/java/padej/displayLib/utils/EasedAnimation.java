package padej.displayLib.utils;

import padej.displayLib.DisplayLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;

/**
 * Система нелинейной анимации для Display Entity.
 * 
 * <p>Реализует серверную keyframe-анимацию, где плагин подает множество
 * промежуточных состояний с нелинейно расположенными значениями,
 * а клиент делает линейный lerp между ними.</p>
 * 
 * <p>Принцип: разбить анимацию на множество коротких сегментов (1-2 тика),
 * где каждый сегмент линейный, но вместе они образуют нелинейную кривую.</p>
 */
public class EasedAnimation {
    
    // Шаг обновления анимации в тиках (1 = максимальная точность)
    private static final int STEP_TICKS = 1;
    
    // Длительность интерполяции клиента между кадрами (должна быть больше STEP_TICKS)
    private static final int CLIENT_INTERPOLATION_DURATION = 2;
    
    /**
     * Easing функции (t от 0.0 до 1.0)
     */
    public static class Easing {
        
        public static double linear(double t) {
            return t;
        }
        
        public static double easeIn(double t) {
            return t * t;
        }
        
        public static double easeOut(double t) {
            return 1 - Math.pow(1 - t, 2);
        }
        
        public static double easeInOut(double t) {
            return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
        }
        
        public static double easeOutBack(double t) {
            double c1 = 1.70158;
            double c3 = c1 + 1;
            return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
        }
        
        public static double easeOutBounce(double t) {
            double n1 = 7.5625;
            double d1 = 2.75;
            
            if (t < 1 / d1) {
                return n1 * t * t;
            } else if (t < 2 / d1) {
                return n1 * (t -= 1.5 / d1) * t + 0.75;
            } else if (t < 2.5 / d1) {
                return n1 * (t -= 2.25 / d1) * t + 0.9375;
            } else {
                return n1 * (t -= 2.625 / d1) * t + 0.984375;
            }
        }
        
        public static double easeOutElastic(double t) {
            double c4 = (2 * Math.PI) / 3;
            
            return t == 0 ? 0 : t == 1 ? 1 : 
                Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
        }
    }
    
    /** Канал анимации: анимации разных каналов одной сущности не мешают друг другу. */
    private enum Channel { SCALE, TRANSLATION }

    /**
     * Текущие покадровые анимации по сущности и каналу.
     * Используется только из основного потока сервера.
     */
    private static final Map<UUID, BukkitTask[]> RUNNING = new HashMap<>();

    /**
     * Анимирует масштаб display entity с easing функцией
     */
    public static void animateScale(Display entity, Vector3f from, Vector3f to, 
                                   int totalTicks, DoubleUnaryOperator easingFn) {
        animate(entity, Channel.SCALE, from, to, totalTicks, easingFn);
    }
    
    /**
     * Анимирует позицию display entity с easing функцией
     */
    public static void animateTranslation(Display entity, Vector3f from, Vector3f to,
                                         int totalTicks, DoubleUnaryOperator easingFn) {
        animate(entity, Channel.TRANSLATION, from, to, totalTicks, easingFn);
    }

    /**
     * Покадровая анимация одного компонента трансформации.
     *
     * <p>Вся анимация - одна повторяющаяся задача планировщика (раньше на каждый кадр
     * заранее создавалась отдельная отложенная задача, то есть десятки задач и замыканий
     * на каждое наведение). Новая анимация того же канала отменяет предыдущую, поэтому
     * при быстром наведении и уходе взгляда кадры двух анимаций больше не чередуются.</p>
     */
    private static void animate(Display entity, Channel channel, Vector3f from, Vector3f to,
                                int totalTicks, DoubleUnaryOperator easingFn) {
        if (entity == null || !entity.isValid()) return;
        
        // Предыдущая анимация этого канала больше не актуальна
        cancel(entity, channel);
        
        // Для коротких анимаций используем прямое применение
        if (totalTicks <= 2) {
            applyComponent(entity, channel, to.x, to.y, to.z, totalTicks);
            return;
        }
        
        // Количество кадров и шаг между ними - как в прежней реализации
        final int steps = Math.max(totalTicks, totalTicks * 2);
        final int stepDelay = Math.max(1, totalTicks / steps);
        final UUID entityId = entity.getUniqueId();
        final float fx = from.x, fy = from.y, fz = from.z;
        final float tx = to.x, ty = to.y, tz = to.z;
        
        BukkitTask task = new BukkitRunnable() {
            private int step = 0;
            
            @Override
            public void run() {
                if (!entity.isValid()) {
                    finish();
                    return;
                }
                
                double rawT = (double) step / steps;
                double easedT = easingFn.applyAsDouble(Math.min(1.0, Math.max(0.0, rawT)));
                
                applyComponent(entity, channel,
                        (float) (fx + (tx - fx) * easedT),
                        (float) (fy + (ty - fy) * easedT),
                        (float) (fz + (tz - fz) * easedT),
                        CLIENT_INTERPOLATION_DURATION);
                
                if (++step > steps) {
                    finish();
                }
            }
            
            private void finish() {
                cancel();
                BukkitTask[] tasks = RUNNING.get(entityId);
                // Снимаем регистрацию, только если в канале всё ещё эта задача
                if (tasks != null && tasks[channel.ordinal()] != null
                        && tasks[channel.ordinal()].getTaskId() == getTaskId()) {
                    tasks[channel.ordinal()] = null;
                    if (tasks[0] == null && tasks[1] == null) {
                        RUNNING.remove(entityId);
                    }
                }
            }
        }.runTaskTimer(DisplayLib.getInstance(), 0L, stepDelay);
        
        RUNNING.computeIfAbsent(entityId, k -> new BukkitTask[Channel.values().length])[channel.ordinal()] = task;
    }

    /** Отменить текущую анимацию канала у сущности, если она есть. */
    private static void cancel(Display entity, Channel channel) {
        UUID entityId = entity.getUniqueId();
        BukkitTask[] tasks = RUNNING.get(entityId);
        if (tasks == null) return;
        
        BukkitTask task = tasks[channel.ordinal()];
        if (task != null) {
            task.cancel();
            tasks[channel.ordinal()] = null;
        }
        if (tasks[0] == null && tasks[1] == null) {
            RUNNING.remove(entityId);
        }
    }

    /** Заменить один компонент трансформации (масштаб или смещение), сохранив остальные. */
    private static void applyComponent(Display entity, Channel channel, float x, float y, float z, int interpolationTicks) {
        // getTransformation() возвращает свежую копию - меняем в ней нужный компонент и отдаём обратно,
        // не создавая на каждый кадр новый вектор и новую Transformation с четырьмя копиями внутри.
        // Читать текущее состояние нужно каждый кадр: параллельно могут идти анимация второго канала и поворот.
        Transformation next = entity.getTransformation();
        (channel == Channel.SCALE ? next.getScale() : next.getTranslation()).set(x, y, z);
        
        entity.setTransformation(next);
        entity.setInterpolationDuration(interpolationTicks);
        entity.setInterpolationDelay(0);
    }
    
    /**
     * Анимирует поворот display entity с easing функцией
     * @deprecated Поворот сложен из-за Quaternionf, используйте простые эффекты
     */
    @Deprecated
    public static void animateRotation(Display entity, AxisAngle4f from, AxisAngle4f to,
                                      int totalTicks, DoubleUnaryOperator easingFn) {
        // Упрощенная реализация - используем прямое применение трансформации
        if (entity == null || !entity.isValid()) return;
        
        Transformation current = entity.getTransformation();
        Quaternionf targetRotation = new Quaternionf().rotateAxis(to.angle, to.x, to.y, to.z);
        
        Transformation target = new Transformation(
            current.getTranslation(),
            targetRotation,
            current.getScale(),
            current.getRightRotation()
        );
        
        entity.setTransformation(target);
        entity.setInterpolationDuration(totalTicks);
        entity.setInterpolationDelay(0);
    }
    
    /**
     * Комбинированная анимация (масштаб + позиция)
     * @deprecated Упрощенная версия без поворота из-за сложности с Quaternionf
     */
    @Deprecated
    public static void animateCombined(Display entity, 
                                      Vector3f fromTranslation, Vector3f toTranslation,
                                      Vector3f fromScale, Vector3f toScale,
                                      AxisAngle4f fromRotation, AxisAngle4f toRotation,
                                      int totalTicks, DoubleUnaryOperator easingFn) {
        // Упрощенная версия - только scale и translate
        animateScale(entity, fromScale, toScale, totalTicks, easingFn);
        
        // Небольшая задержка чтобы не конфликтовать
        Bukkit.getScheduler().runTaskLater(DisplayLib.getInstance(), () -> {
            animateTranslation(entity, fromTranslation, toTranslation, totalTicks, easingFn);
        }, 1);
    }
    
    /**
     * Простая анимация через стандартную интерполяцию Minecraft (для тестирования)
     */
    public static void animateScaleSimple(Display entity, Vector3f from, Vector3f to, int duration) {
        if (entity == null || !entity.isValid()) return;
        
        Transformation current = entity.getTransformation();
        Transformation target = new Transformation(
            current.getTranslation(),
            current.getLeftRotation(),
            to,
            current.getRightRotation()
        );
        
        entity.setTransformation(target);
        entity.setInterpolationDuration(duration);
        entity.setInterpolationDelay(0);
    }
    
    /**
     * Простая анимация позиции через стандартную интерполяцию Minecraft (для тестирования)
     */
    public static void animateTranslationSimple(Display entity, Vector3f from, Vector3f to, int duration) {
        if (entity == null || !entity.isValid()) return;
        
        Transformation current = entity.getTransformation();
        Transformation target = new Transformation(
            to,
            current.getLeftRotation(),
            current.getScale(),
            current.getRightRotation()
        );
        
        entity.setTransformation(target);
        entity.setInterpolationDuration(duration);
        entity.setInterpolationDelay(0);
    }
    /**
     * Непрерывная пульсирующая анимация масштаба (зацикленная)
     */
    public static void animatePulsingScale(Display entity, Vector3f baseScale, float intensity, int pulseDuration) {
        if (entity == null || !entity.isValid()) return;
        
        Vector3f minScale = new Vector3f(baseScale.x, baseScale.y, baseScale.z);
        Vector3f maxScale = new Vector3f(
            baseScale.x * intensity,
            baseScale.y * intensity,
            baseScale.z
        );
        
        // Создаем задачу, которая будет повторяться
        final int[] taskId = new int[1];
        final boolean[] isExpanding = {true};
        
        taskId[0] = Bukkit.getScheduler().runTaskTimer(DisplayLib.getInstance(), () -> {
            if (!entity.isValid()) {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                return;
            }
            
            Vector3f from = isExpanding[0] ? minScale : maxScale;
            Vector3f to = isExpanding[0] ? maxScale : minScale;
            
            // Простая интерполяция для плавности
            Transformation current = entity.getTransformation();
            Transformation target = new Transformation(
                current.getTranslation(),
                current.getLeftRotation(),
                to,
                current.getRightRotation()
            );
            
            entity.setTransformation(target);
            entity.setInterpolationDuration(pulseDuration);
            entity.setInterpolationDelay(0);
            
            // Переключаем направление
            isExpanding[0] = !isExpanding[0];
            
        }, 0, pulseDuration).getTaskId();
        
        // Сохраняем ID задачи в метаданных entity для возможности остановки
        entity.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(DisplayLib.getInstance(), "pulse_task"),
            org.bukkit.persistence.PersistentDataType.INTEGER,
            taskId[0]
        );
    }
    
    /**
     * Останавливает непрерывную анимацию
     */
    public static void stopContinuousAnimation(Display entity) {
        if (entity == null || !entity.isValid()) return;
        
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(DisplayLib.getInstance(), "pulse_task");
        if (entity.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) {
            int taskId = entity.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
            Bukkit.getScheduler().cancelTask(taskId);
            entity.getPersistentDataContainer().remove(key);
        }
    }
    
    public static DoubleUnaryOperator getEasingFunction(padej.displayLib.config.HoverAnimation.EasingType type) {
        if (type == null) return Easing::linear;
        
        return switch (type) {
            case LINEAR -> Easing::linear;
            case EASE_IN -> Easing::easeIn;
            case EASE_OUT -> Easing::easeOut;
            case EASE_IN_OUT -> Easing::easeInOut;
            case BOUNCE -> Easing::easeOutBounce;
            case ELASTIC -> Easing::easeOutElastic;
        };
    }
}
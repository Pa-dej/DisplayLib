package padej.displayLib.utils;

import org.joml.Vector3f;

/**
 * Утилиты для вычисления трансформаций виджетов и фона
 */
public class TransformationUtil {
    
    /**
     * Константа для выравнивания по X-оси.
     * Основана на анализе магических чисел в системе Highlight.
     */
    private static final float X_ALIGNMENT_FACTOR = -1.0f / 80.0f; // -0.0125
    
    /**
     * Константа для центрирования по Y-оси.
     * Смещает центр с "внизу по середине" на "середина в середине".
     */
    private static final float Y_CENTERING_FACTOR = -0.5f; // Минус половина высоты
    
    /**
     * Вычисляет оптимальное смещение по X для выравнивания
     * 
     * @param scaleX размер по X-оси
     * @return оптимальное смещение для translation_x
     */
    public static float calculateXAlignment(float scaleX) {
        return scaleX * X_ALIGNMENT_FACTOR;
    }
    
    /**
     * Вычисляет оптимальное смещение по Y для центрирования
     * 
     * @param scaleY размер по Y-оси
     * @return оптимальное смещение для translation_y (минус половина высоты)
     */
    public static float calculateYCentering(float scaleY) {
        return scaleY * Y_CENTERING_FACTOR;
    }
    
    /**
     * Создает Vector3f с автоматическим выравниванием и центрированием
     * 
     * @param scaleX размер по X-оси
     * @param scaleY размер по Y-оси
     * @param userTranslation пользовательские значения translation [x, y, z]
     * @return Vector3f с автоматическим выравниванием + центрированием + пользовательские смещения
     */
    public static Vector3f createAlignedAndCenteredTranslation(float scaleX, float scaleY, float[] userTranslation) {
        if (userTranslation == null || userTranslation.length < 3) {
            return createAlignedAndCenteredTranslation(scaleX, scaleY, 0.0f, 0.0f, 0.0f);
        }
        return createAlignedAndCenteredTranslation(scaleX, scaleY, userTranslation[0], userTranslation[1], userTranslation[2]);
    }
    
    /**
     * Создает Vector3f с автоматическим выравниванием и центрированием
     * 
     * @param scaleX размер по X-оси
     * @param scaleY размер по Y-оси
     * @param userX дополнительное смещение по X от пользователя
     * @param userY дополнительное смещение по Y от пользователя
     * @param userZ смещение по Z от пользователя
     * @return Vector3f с комбинированными смещениями
     */
    public static Vector3f createAlignedAndCenteredTranslation(float scaleX, float scaleY, float userX, float userY, float userZ) {
        float alignedX = calculateXAlignment(scaleX) + userX;
        float centeredY = calculateYCentering(scaleY) + userY;
        return new Vector3f(alignedX, centeredY, userZ);
    }
    
    /**
     * Создает Vector3f с автоматическим выравниванием по X и пользовательскими смещениями
     * 
     * @param scaleX размер по X-оси
     * @param userX дополнительное смещение по X от пользователя
     * @param userY смещение по Y от пользователя
     * @param userZ смещение по Z от пользователя
     * @return Vector3f с комбинированными смещениями
     */
    public static Vector3f createAlignedTranslation(float scaleX, float userX, float userY, float userZ) {
        float alignedX = calculateXAlignment(scaleX) + userX;
        return new Vector3f(alignedX, userY, userZ);
    }
    
    /**
     * Создает Vector3f только с автоматическим выравниванием по X
     * 
     * @param scaleX размер по X-оси
     * @return Vector3f с вычисленным X-смещением и нулевыми Y, Z
     */
    public static Vector3f createAlignedTranslation(float scaleX) {
        return createAlignedTranslation(scaleX, 0.0f, 0.0f, 0.0f);
    }
    
    /**
     * Создает Vector3f с автоматическим выравниванием по X и пользовательскими Y, Z
     * (для обратной совместимости - без автоцентрирования по Y)
     * 
     * @param scaleX размер по X-оси
     * @param userTranslation пользовательские значения translation [x, y, z]
     * @return Vector3f с автоматическим X-выравниванием + пользовательские смещения
     */
    public static Vector3f createAlignedTranslation(float scaleX, float[] userTranslation) {
        if (userTranslation == null || userTranslation.length < 3) {
            return createAlignedTranslation(scaleX, 0.0f, 0.0f, 0.0f);
        }
        return createAlignedTranslation(scaleX, userTranslation[0], userTranslation[1], userTranslation[2]);
    }
    
    /**
     * Проверяет, использует ли translation автоматическое выравнивание
     * 
     * @param translation текущий Vector3f translation
     * @param scaleX размер по X-оси
     * @param tolerance допустимая погрешность
     * @return true, если X-компонент близок к автоматически вычисленному
     */
    public static boolean isAutoAligned(Vector3f translation, float scaleX, float tolerance) {
        if (translation == null) return false;
        float expected = calculateXAlignment(scaleX);
        return Math.abs(translation.x - expected) <= tolerance;
    }
    
    /**
     * Проверяет, использует ли translation автоматическое выравнивание (с допуском 0.001)
     */
    public static boolean isAutoAligned(Vector3f translation, float scaleX) {
        return isAutoAligned(translation, scaleX, 0.001f);
    }
    
    // Устаревшие методы для обратной совместимости
    @Deprecated
    public static float calculateBackgroundXAlignment(float scaleX) {
        return calculateXAlignment(scaleX);
    }
    
    @Deprecated
    public static Vector3f createAlignedBackgroundTranslation(float scaleX, float translationY, float translationZ) {
        return createAlignedTranslation(scaleX, 0.0f, translationY, translationZ);
    }
    
    @Deprecated
    public static Vector3f createAlignedBackgroundTranslation(float scaleX) {
        return createAlignedTranslation(scaleX);
    }
}
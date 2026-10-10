package padej.displayLib.config;

import java.util.List;

/**
 * Определение экрана из конфига Jumper ({@code screens/*.jmc}).
 * 
 * <p>Файл экрана - это {@code .jmc}: переменные верхнего уровня и есть конфиг.
 * Разрешены выражения, {@code if}, тернарный оператор и переменные, но не циклы,
 * функции и Java (см. {@link ScreenLoader}).</p>
 * 
 * <h2>Структура файла экрана:</h2>
 * <pre>{@code
 * // Основные настройки экрана
 * String id = "example_screen";          // Уникальный идентификатор экрана
 * int tickRate = 4;                      // Частота обновления (1-20 тиков)
 * String screenType = "PRIVATE";         // PRIVATE или PUBLIC
 * double interactionRadius = 5;          // Радиус взаимодействия (-1 = без ограничения)
 * int rangeCheckInterval = 10;           // Интервал проверки расстояния в обновлениях
 * double closeDistance = 10;             // Дистанция автозакрытия (-1 = отключено)
 * 
 * // Фон
 * dyn background = {
 *     color: [0, 0, 0],                  // RGB
 *     alpha: 160,                        // 0-255
 *     scale: [10, 4, 1],                 // [ширина, высота, глубина]
 *     position: [0, 0, 0],
 *     text: " ",
 *     translation: [0, 0, 0],
 * };
 * 
 * // Скрипт экрана (scripts/example.jmp)
 * String script = "example.jmp";
 * 
 * // Виджеты
 * dyn widgets = [
 *     { id: "button1", type: "TEXT_BUTTON", text: "Нажми", onClick: "onButton" },
 * ];
 * }</pre>
 * 
 * @see WidgetDefinition
 * @see ScreenLoader
 */
public class ScreenDefinition {
    /**
     * Уникальный идентификатор экрана.
     * Используется для открытия экрана через команды и API скриптов.
     */
    private String id;
    
    /**
     * Частота обновления экрана в тиках (1-20).
     * По умолчанию 4 тика (5 раз в секунду).
     * Меньшие значения = более частое обновление = больше нагрузки.
     */
    private int tickRate = 4;
    
    /**
     * Тип экрана определяет поведение и доступность.
     * PRIVATE - персональный экран для одного игрока.
     * PUBLIC - публичный экран, доступный всем игрокам поблизости.
     */
    private ScreenType screenType = ScreenType.PRIVATE;
    
    /**
     * Радиус взаимодействия с экраном в блоках.
     * -1.0 означает бесконечный радиус.
     * Игроки за пределами радиуса не могут взаимодействовать с экраном.
     */
    private double interactionRadius = -1.0;
    
    /**
     * Интервал проверки расстояния до игрока в тиках.
     * Используется для оптимизации производительности.
     * По умолчанию 10 тиков (0.5 секунды).
     */
    private int rangeCheckInterval = 10;
    
    /**
     * Расстояние автоматического закрытия для PRIVATE экранов.
     * -1.0 означает отключение автозакрытия.
     * Когда игрок отходит дальше этого расстояния, экран закрывается.
     */
    private double closeDistance = -1.0;
    
    /**
     * Настройки фона экрана.
     * Определяет внешний вид подложки экрана.
     */
    private BackgroundDefinition background;
    
    /**
     * Скрипт экрана: путь к {@code .jmp} файлу относительно папки {@code scripts/}; может быть null.
     */
    private String script;
    
    /**
     * Список виджетов экрана.
     * Каждый виджет определяет интерактивный элемент на экране.
     */
    private List<WidgetDefinition> widgets;

    /**
     * Анимация смены и закрытия экрана (morph). При переключении действует настройка
     * открываемого экрана, при закрытии - закрываемого.
     */
    private Morph morph = new Morph();
    
    public ScreenDefinition() {}
    
    public ScreenDefinition(String id, int tickRate, ScreenType screenType, double interactionRadius, 
                           int rangeCheckInterval, double closeDistance, BackgroundDefinition background, 
                           String script, List<WidgetDefinition> widgets) {
        this.id = id;
        this.tickRate = tickRate;
        this.screenType = screenType;
        this.interactionRadius = interactionRadius;
        this.rangeCheckInterval = rangeCheckInterval;
        this.closeDistance = closeDistance;
        this.background = background;
        this.script = script;
        this.widgets = widgets;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public int getTickRate() { return tickRate; }
    public void setTickRate(int tickRate) { 
        // Ограничиваем значение от 1 до 20
        this.tickRate = Math.max(1, Math.min(20, tickRate)); 
    }
    
    public BackgroundDefinition getBackground() { return background; }
    public void setBackground(BackgroundDefinition background) { this.background = background; }
    
    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }
    
    public List<WidgetDefinition> getWidgets() { return widgets; }
    public void setWidgets(List<WidgetDefinition> widgets) { this.widgets = widgets; }
    
    public ScreenType getScreenType() { return screenType; }
    public void setScreenType(ScreenType screenType) { this.screenType = screenType; }
    
    public double getInteractionRadius() { return interactionRadius; }
    public void setInteractionRadius(double interactionRadius) { this.interactionRadius = interactionRadius; }
    
    public int getRangeCheckInterval() { return rangeCheckInterval; }
    public void setRangeCheckInterval(int rangeCheckInterval) { this.rangeCheckInterval = rangeCheckInterval; }
    
    public double getCloseDistance() { return closeDistance; }
    public void setCloseDistance(double closeDistance) { this.closeDistance = closeDistance; }

    public Morph getMorph() { return morph; }
    public void setMorph(Morph morph) { this.morph = morph != null ? morph : new Morph(); }

    /**
     * Настройки morph-перехода.
     *
     * <pre>{@code
     * dyn morph = { enabled: true, duration: 10, steps: 5 };
     * boolean morph = true;   // длительность и шаги по умолчанию
     * int morph = 16;         // = { enabled: true, duration: 16 }
     * }</pre>
     *
     * <p>{@code duration} - длительность в тиках (1–59: столько же длится интерполяция клиента).
     * {@code steps} - на сколько шагов разбивать то, что клиент не интерполирует: текст и его цвет,
     * предмет ItemDisplay; по умолчанию шаг каждые 2 тика. Положение, масштаб и цвет фона
     * интерполирует сам клиент.</p>
     */
    public static class Morph {
        private boolean enabled = false;
        private int duration = 10;
        private int steps = -1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = Math.max(1, Math.min(59, duration)); }

        /** Число шагов для текста/цвета; если не задано - duration / 2 (не меньше 1). */
        public int getSteps() { return steps > 0 ? Math.min(steps, duration) : Math.max(1, duration / 2); }
        public void setSteps(int steps) { this.steps = steps; }
    }
    
    /**
     * Типы экранов определяют поведение и доступность.
     * 
     * <ul>
     * <li><b>PRIVATE</b> - Персональный экран, создается для конкретного игрока.
     *     Только этот игрок может видеть и взаимодействовать с экраном.
     *     Экран следует за игроком или остается в фиксированной позиции.</li>
     * <li><b>PUBLIC</b> - Публичный экран, фиксированный в мире.
     *     Все игроки поблизости могут видеть и взаимодействовать с экраном.
     *     Используется для информационных табло, меню серверов и т.д.</li>
     * </ul>
     */
    public enum ScreenType {
        /** Персональный экран (только для одного игрока) */
        PRIVATE,
        /** Публичный экран (доступен всем игрокам поблизости) */
        PUBLIC
    }
    
    /**
     * Определение фона экрана.
     * 
     * <p>Фон создает подложку для всех виджетов экрана и определяет
     * общий внешний вид интерфейса.</p>
     * 
     * <p><b>Пример:</b></p>
     * <pre>{@code
     * dyn background = {
     *     color: [50, 50, 50],        // Темно-серый цвет RGB
     *     alpha: 200,                 // Полупрозрачный
     *     scale: [12, 6, 1],          // Широкий прямоугольник
     *     position: [0, 1, 0],        // Немного выше центра
     *     text: "▓",                  // Символ для текстуры
     *     translation: [0, 0, 0.01],  // Точная настройка
     * };
     * }</pre>
     */
    public static class BackgroundDefinition {
        /** RGB цвет фона (0-255 для каждого компонента) */
        private int[] color = {0, 0, 0};
        
        /** Прозрачность фона (0-255, где 0 = полностью прозрачный, 255 = непрозрачный) */
        private int alpha = 160;
        
        /** Размеры фона [ширина, высота, глубина] в блоках */
        private float[] scale = {10.0f, 4.0f, 1.0f};
        
        /** Смещение позиции фона [x, y, z] относительно центра экрана */
        private float[] position = {0.0f, 0.0f, 0.0f};
        
        /** Текст/символ для отображения фона (обычно пробел или блочный символ) */
        private String text = " ";
        
        /** Точная настройка позиции [x, y, z] для устранения артефактов */
        private float[] translation = {0.0f, 0.0f, 0.0f};
        
        public BackgroundDefinition() {}
        
        // Getters and setters
        public int[] getColor() { return color; }
        public void setColor(int[] color) { this.color = color; }
        
        public int getAlpha() { return alpha; }
        public void setAlpha(int alpha) { this.alpha = alpha; }
        
        public float[] getScale() { return scale; }
        public void setScale(float[] scale) { this.scale = scale; }
        
        public float[] getPosition() { return position; }
        public void setPosition(float[] position) { this.position = position; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public float[] getTranslation() { return translation; }
        public void setTranslation(float[] translation) { this.translation = translation; }
    }
}
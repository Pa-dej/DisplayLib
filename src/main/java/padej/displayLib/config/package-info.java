/**
 * Пакет конфигурации и загрузки YAML экранов.
 * 
 * <p>Содержит классы для определения структуры экранов и виджетов,
 * загрузки конфигурации из YAML файлов и управления реестром экранов.</p>
 * 
 * <h2>Основные классы:</h2>
 * 
 * <h3>{@link padej.displayLib.config.ScreenDefinition}</h3>
 * <p>Представляет полное определение экрана из YAML файла, включая:</p>
 * <ul>
 * <li>Основные настройки (ID, тип, частота обновления)</li>
 * <li>Настройки взаимодействия (радиус, автозакрытие)</li>
 * <li>Определение фона</li>
 * <li>Список виджетов</li>
 * <li>Ссылки на Lua скрипты</li>
 * </ul>
 * 
 * <h3>{@link padej.displayLib.config.WidgetDefinition}</h3>
 * <p>Определяет виджет экрана со всеми его свойствами:</p>
 * <ul>
 * <li>Тип виджета (TEXT_BUTTON, ITEM_BUTTON)</li>
 * <li>Позиция, размер, область клика</li>
 * <li>Визуальные свойства (текст, цвета, материалы)</li>
 * <li>Поведение при взаимодействии</li>
 * <li>Поддержка форматированного текста с цветами</li>
 * </ul>
 * 
 * <h3>{@link padej.displayLib.config.ScreenLoader}</h3>
 * <p>Загружает YAML файлы экранов из папки screens/ и преобразует их
 * в объекты ScreenDefinition. Поддерживает форматированный текст.</p>
 * 
 * <h3>{@link padej.displayLib.config.ScreenRegistry}</h3>
 * <p>Центральный реестр всех загруженных экранов с возможностью
 * перезагрузки и управления.</p>
 * 
 * <h2>Структура YAML файла экрана:</h2>
 * <pre>{@code
 * # Основные настройки
 * id: "my_screen"                    # Уникальный идентификатор
 * tick_rate: 4                       # Частота обновления (1-20)
 * screen_type: PRIVATE               # PRIVATE или PUBLIC
 * interaction_radius: 5.0            # Радиус взаимодействия
 * close_distance: 10.0               # Расстояние автозакрытия
 * 
 * # Фон экрана
 * background:
 *   color: [50, 50, 50]              # RGB цвет
 *   alpha: 200                       # Прозрачность
 *   scale: [10.0, 6.0, 1.0]          # Размеры
 *   position: [0.0, 0.0, 0.0]        # Смещение
 * 
 * # Lua скрипты
 * scripts:
 *   file: "my_screen.lua"            # Путь к файлу
 * 
 * # Виджеты с форматированным текстом
 * widgets:
 *   - id: "button1"
 *     type: TEXT_BUTTON
 *     text:                          # Форматированный текст
 *       - text: "Красная "
 *         color: "#FF0000"
 *       - text: "кнопка"
 *         color: "blue"
 *     tooltip:                       # Форматированный tooltip
 *       - text: "Урон: "
 *         color: "gray"
 *       - text: "25"
 *         color: "red"
 *     position: [0.0, 0.5, 0.0]
 *     onClick:
 *       action: RUN_SCRIPT
 *       function: "onButtonClick"
 * }</pre>
 * 
 * <h2>Форматирование текста:</h2>
 * <p>Поддерживается два формата для полей text, hoveredText, formattedText, formattedHoveredText и tooltip:</p>
 * <ul>
 * <li><b>Простая строка:</b> "Простой текст"</li>
 * <li><b>Массив объектов:</b> [{text: "Красный", color: "#FF0000"}, {text: "синий", color: "blue"}]</li>
 * </ul>
 * 
 * @author DisplayLib Team
 * @version 2.1.0
 */
package padej.displayLib.config;
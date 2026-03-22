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
 * </ul>
 * 
 * <h3>{@link padej.displayLib.config.ScreenLoader}</h3>
 * <p>Загружает YAML файлы экранов из папки screens/ и преобразует их
 * в объекты ScreenDefinition.</p>
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
 * # Виджеты
 * widgets:
 *   - id: "button1"
 *     type: TEXT_BUTTON
 *     text: "Кнопка"
 *     position: [0.0, 0.5, 0.0]
 *     onClick:
 *       action: RUN_SCRIPT
 *       function: "onButtonClick"
 * }</pre>
 * 
 * @author DisplayLib Team
 * @version 2.0.0
 */
package padej.displayLib.config;
/**
 * Основной пакет DisplayLib - системы интерактивных 3D экранов для Minecraft.
 * 
 * <p>DisplayLib позволяет создавать интерактивные пользовательские интерфейсы
 * прямо в игровом мире с помощью YAML конфигурации и Lua скриптов.</p>
 * 
 * <h2>Архитектура системы:</h2>
 * 
 * <h3>Конфигурация ({@link padej.displayLib.config})</h3>
 * <ul>
 * <li>{@link padej.displayLib.config.ScreenDefinition} - Определение экрана из YAML</li>
 * <li>{@link padej.displayLib.config.WidgetDefinition} - Определение виджета из YAML</li>
 * <li>{@link padej.displayLib.config.ScreenLoader} - Загрузчик YAML файлов</li>
 * <li>{@link padej.displayLib.config.ScreenRegistry} - Реестр экранов</li>
 * </ul>
 * 
 * <h3>Пользовательский интерфейс ({@link padej.displayLib.ui})</h3>
 * <ul>
 * <li>{@link padej.displayLib.ui.UIManager} - Управление экранами</li>
 * <li>{@link padej.displayLib.ui.ScreenInstance} - Экземпляр персонального экрана</li>
 * <li>{@link padej.displayLib.ui.GlobalScreenInstance} - Экземпляр публичного экрана</li>
 * <li>{@link padej.displayLib.ui.widgets} - Виджеты (кнопки, текст, предметы)</li>
 * </ul>
 * 
 * <h3>Lua API ({@link padej.displayLib.lua.api})</h3>
 * <ul>
 * <li>{@link padej.displayLib.lua.api.PlayerAPI} - Работа с игроком</li>
 * <li>{@link padej.displayLib.lua.api.ScreenAPI} - Управление экраном</li>
 * <li>{@link padej.displayLib.lua.api.WidgetAPI} - Работа с виджетами</li>
 * <li>{@link padej.displayLib.lua.api.StorageAPI} - Хранение данных</li>
 * <li>{@link padej.displayLib.lua.api.TimerAPI} - Таймеры и отложенные действия</li>
 * <li>{@link padej.displayLib.lua.api.LogAPI} - Логирование</li>
 * </ul>
 * 
 * <h2>Быстрый старт:</h2>
 * 
 * <h3>1. Создание экрана с форматированным текстом (screens/example.yml):</h3>
 * <pre>{@code
 * id: "example"
 * screen_type: PRIVATE
 * background:
 *   color: [50, 50, 50]
 *   alpha: 200
 * scripts:
 *   file: "example.lua"
 * widgets:
 *   - id: "hello_button"
 *     type: TEXT_BUTTON
 *     text:                              # Форматированный текст
 *       - text: "Привет, "
 *         color: "green"
 *       - text: "мир!"
 *         color: "#FFD700"
 *     tooltip:                           # Форматированный tooltip
 *       - text: "Нажми для приветствия"
 *         color: "gray"
 *     position: [0, 0, 0]
 *     onClick:
 *       action: RUN_SCRIPT
 *       function: "sayHello"
 * }</pre>
 * 
 * <h3>2. Создание скрипта (scripts/example.lua):</h3>
 * <pre>{@code
 * function on_open()
 *     log.info("Экран открыт для " .. player.name())
 * end
 * 
 * function sayHello()
 *     player.message("Привет, " .. player.name() .. "!")
 *     player.sound("ENTITY_EXPERIENCE_ORB_PICKUP")
 * end
 * 
 * function on_close()
 *     log.info("Экран закрыт")
 * end
 * }</pre>
 * 
 * <h3>3. Открытие экрана:</h3>
 * <pre>{@code
 * /displaylib open example
 * }</pre>
 * 
 * <h2>Форматирование текста:</h2>
 * <p>Поддерживается два формата для текстовых полей:</p>
 * <ul>
 * <li><b>Простая строка:</b> "Простой текст"</li>
 * <li><b>Массив объектов:</b> [{text: "Красный", color: "#FF0000"}, {text: "синий", color: "blue"}]</li>
 * </ul>
 * 
 * @author DisplayLib Team
 * @version 2.1.0
 */
package padej.displayLib;
/**
 * Конфигурация: экраны ({@code screens/*.jmc}) и настройки плагина ({@code config.jmc}).
 * 
 * <p>Файл экрана - конфиг Jumper: переменные верхнего уровня и есть конфиг.
 * Разрешены значения, таблицы, массивы, выражения, {@code if}/{@code else} и тернарный
 * оператор; циклов, функций и Java в конфиге нет, так что экран - это данные.</p>
 * 
 * <h2>Основные классы:</h2>
 * <ul>
 * <li>{@link padej.displayLib.config.ScreenDefinition} - определение экрана</li>
 * <li>{@link padej.displayLib.config.WidgetDefinition} - определение виджета</li>
 * <li>{@link padej.displayLib.config.HoverAnimation} - hover-анимация виджета</li>
 * <li>{@link padej.displayLib.config.ScreenLoader} - чтение {@code .jmc} через {@code Config.load}</li>
 * <li>{@link padej.displayLib.config.ScreenRegistry} - реестр экранов и hot reload</li>
 * <li>{@link padej.displayLib.config.PluginConfig} - {@code config.jmc}</li>
 * </ul>
 * 
 * <h2>Структура файла экрана:</h2>
 * <pre>{@code
 * String id = "my_screen";               // Уникальный идентификатор (по умолчанию - имя файла)
 * int tickRate = 4;                      // Частота обновления (1-20)
 * String screenType = "PRIVATE";         // PRIVATE или PUBLIC
 * double interactionRadius = 5;          // Радиус взаимодействия
 * double closeDistance = 10;             // Дистанция автозакрытия
 * 
 * dyn background = { color: [50, 50, 50], alpha: 200, scale: [10, 6, 1] };
 * String script = "my_screen.jmp";       // scripts/my_screen.jmp
 * 
 * dyn gray = "gray";                     // переменные можно переиспользовать
 * dyn widgets = [
 *     {
 *         id: "button1",
 *         type: "TEXT_BUTTON",
 *         text: [ { text: "Красная ", color: "#FF0000" }, { text: "кнопка", color: "blue" } ],
 *         tooltip: [ { text: "Урон: ", color: gray }, { text: "25", color: "red" } ],
 *         position: [0, 0.5, 0],
 *         onClick: "onButtonClick",      // void onButtonClick(dyn widget, dyn player)
 *     },
 *     { id: "back", type: "TEXT_BUTTON", text: "Назад", onClick: { switchTo: "main_menu" } },
 *     { id: "exit", type: "TEXT_BUTTON", text: "Закрыть", onClick: "close" },
 * ];
 * }</pre>
 * 
 * <p>Имена полей - camelCase; snake_case ({@code tick_rate}, {@code screen_type}) тоже
 * принимается, чтобы прежние YAML-экраны переносились почти без правок.</p>
 */
package padej.displayLib.config;

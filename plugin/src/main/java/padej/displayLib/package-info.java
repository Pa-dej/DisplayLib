/**
 * Основной пакет DisplayLib - системы интерактивных 3D экранов для Minecraft.
 * 
 * <p>DisplayLib рисует интерактивные интерфейсы прямо в игровом мире на Display-сущностях.
 * Экран описывается конфигом Jumper ({@code screens/*.jmc}), логика пишется на Jumper
 * ({@code scripts/*.jmp}), доступ скриптов к Java ограничивает политика
 * ({@code scripts.jma}).</p>
 * 
 * <h2>Архитектура:</h2>
 * 
 * <h3>Конфигурация ({@link padej.displayLib.config})</h3>
 * <ul>
 * <li>{@link padej.displayLib.config.ScreenDefinition} - Определение экрана</li>
 * <li>{@link padej.displayLib.config.WidgetDefinition} - Определение виджета</li>
 * <li>{@link padej.displayLib.config.ScreenLoader} - Загрузчик {@code .jmc} файлов</li>
 * <li>{@link padej.displayLib.config.ScreenRegistry} - Реестр экранов и hot reload</li>
 * <li>{@link padej.displayLib.config.PluginConfig} - {@code config.jmc}</li>
 * </ul>
 * 
 * <h3>Пользовательский интерфейс ({@link padej.displayLib.ui})</h3>
 * <ul>
 * <li>{@link padej.displayLib.ui.UIManager} - Управление экранами</li>
 * <li>{@link padej.displayLib.ui.ScreenInstance} - Экземпляр приватного экрана</li>
 * <li>{@link padej.displayLib.ui.GlobalScreenInstance} - Экземпляр публичного экрана</li>
 * <li>{@link padej.displayLib.ui.widgets} - Виджеты (текстовые и предметные кнопки)</li>
 * </ul>
 * 
 * <h3>Скрипты ({@link padej.displayLib.script}, {@link padej.displayLib.script.api})</h3>
 * <ul>
 * <li>{@link padej.displayLib.script.JumperEngine} - Движок: политика, кэш исходников, сторожевой таймер</li>
 * <li>{@link padej.displayLib.script.ScriptContext} - Окружение одного экрана</li>
 * <li>{@link padej.displayLib.script.api.PlayerAPI} - {@code player}</li>
 * <li>{@link padej.displayLib.script.api.ScreenAPI} - {@code screen}</li>
 * <li>{@link padej.displayLib.script.api.WidgetAPI} - виджеты</li>
 * <li>{@link padej.displayLib.script.api.StorageAPI} - {@code storage}</li>
 * <li>{@link padej.displayLib.script.api.TimerAPI} - {@code timer}</li>
 * <li>{@link padej.displayLib.script.api.LogAPI} - {@code log}</li>
 * </ul>
 * 
 * <h2>Быстрый старт:</h2>
 * 
 * <h3>1. Экран (screens/example.jmc):</h3>
 * <pre>{@code
 * String id = "example";
 * dyn background = { color: [50, 50, 50], alpha: 200 };
 * String script = "example.jmp";
 * dyn widgets = [
 *     {
 *         id: "hello",
 *         type: "TEXT_BUTTON",
 *         text: [ { text: "Привет, ", color: "green" }, { text: "мир!", color: "#FFD700" } ],
 *         tooltip: "Нажми для приветствия",
 *         position: [0, 0, 0],
 *         onClick: "sayHello",
 *     },
 * ];
 * }</pre>
 * 
 * <h3>2. Скрипт (scripts/example.jmp):</h3>
 * <pre>{@code
 * void onOpen() { log.info("Экран открыт для " + player.name()); }
 * 
 * void sayHello(dyn widget, dyn player) {
 *     player.message("Привет, " + player.name() + "!");
 *     player.sound("entity.experience_orb.pickup");
 *     widget.text("Нажато!");
 * }
 * 
 * void onClose() { log.info("Экран закрыт"); }
 * }</pre>
 * 
 * <h3>3. Открытие экрана:</h3>
 * <pre>{@code
 * /displaylib open example
 * UIManager.getInstance().openScreen(player, "example");
 * }</pre>
 */
package padej.displayLib;

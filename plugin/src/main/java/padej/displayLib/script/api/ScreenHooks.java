package padej.displayLib.script.api;

/**
 * Функции, которые скрипт экрана может объявить, а плагин вызывает сам.
 *
 * <p>Интерфейс нигде не реализуется: это описание для редакторов (ключ {@code hooks}
 * в {@code META-INF/jumper/host.jmc}) и для документации. Все функции необязательны.</p>
 *
 * <pre>
 * void onOpen() { ... }                 // после создания фона и виджетов
 * void onClose(String reason) { ... }   // перед удалением экрана - при любом закрытии
 * </pre>
 *
 * <p>Обработчики кликов - произвольные функции, на которые ссылается поле
 * {@code onClick} виджета в файле экрана. Они получают два аргумента, оба можно
 * опустить: {@code void onBuy(dyn widget, dyn player)}.</p>
 */
public interface ScreenHooks {
    /** После создания фона и виджетов. */
    void onOpen();

    /**
     * Перед удалением экрана - ровно один раз, при любом способе закрытия. Экран ещё на месте:
     * можно читать виджеты, писать в {@code storage}, отправить игроку сообщение. Закрыть или
     * переключить экран отсюда нельзя (вызовы игнорируются), таймеры после onClose отменяются.
     *
     * <p>{@code reason} - почему экран закрывается (параметр можно не объявлять):</p>
     * <ul>
     * <li>{@code "close"} - кнопка CLOSE_SCREEN, {@code screen.close()}, команда, Java API</li>
     * <li>{@code "distance"} - игрок отошёл дальше {@code closeDistance}</li>
     * <li>{@code "switch"} - игроку открыт другой экран</li>
     * <li>{@code "quit"}, {@code "death"}, {@code "world"}, {@code "teleport"}, {@code "inventory"} -
     *     игрок вышел, умер, сменил мир, телепортировался, открыл инвентарь ({@code closeOn} в config.jmc)</li>
     * <li>{@code "replaced"} - публичный экран открыт заново с тем же id</li>
     * <li>{@code "unload"} - мир публичного экрана выгружен</li>
     * <li>{@code "shutdown"} - выключение сервера или плагина</li>
     * </ul>
     * У публичного экрана глобал {@code player} равен {@code null}.
     */
    void onClose(String reason);
}

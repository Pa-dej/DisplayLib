package padej.displayLib.ui;

/**
 * Почему закрывается экран. Скрипт получает {@link #id()} аргументом {@code onClose}:
 * <pre>
 * void onClose(String reason) {
 *     if (reason == "quit") log.info("Игрок вышел, экран закрыт");
 * }
 * </pre>
 * Аргумент можно не объявлять: {@code void onClose() { ... }} тоже работает.
 */
public enum CloseReason {
    /** Кнопка {@code CLOSE_SCREEN}, {@code screen.close()}, {@code /displaylib close}, закрытие через Java API. */
    CLOSE("close"),
    /** Игрок отошёл дальше {@code closeDistance} экрана. */
    DISTANCE("distance"),
    /** Игроку открыт другой экран ({@code switchTo}, {@code screen.switchTo}, новое открытие поверх). */
    SWITCH("switch"),
    /** Игрок вышел с сервера. */
    QUIT("quit"),
    /** Игрок умер ({@code closeOn.death}). */
    DEATH("death"),
    /** Игрок сменил мир. */
    WORLD_CHANGE("world"),
    /** Телепорт дальше {@code closeOn.teleport} блоков. */
    TELEPORT("teleport"),
    /** Игрок открыл инвентарь ({@code closeOn.inventory}). */
    INVENTORY("inventory"),
    /** Публичный экран открыт заново с тем же id (старый экземпляр заменяется). */
    REPLACED("replaced"),
    /** Мир публичного экрана выгружен (экран вернётся, когда мир загрузится снова, если он сохранён). */
    WORLD_UNLOAD("unload"),
    /** Плагин выключается (остановка или перезапуск сервера, перезагрузка плагина). */
    SHUTDOWN("shutdown");

    private final String id;

    CloseReason(String id) {
        this.id = id;
    }

    /** Строка, которую получает скрипт: {@code "close"}, {@code "quit"}, ... */
    public String id() {
        return id;
    }
}

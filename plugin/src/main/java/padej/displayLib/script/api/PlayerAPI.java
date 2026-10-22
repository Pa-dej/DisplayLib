package padej.displayLib.script.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Игрок, с точки зрения скрипта экрана: глобал {@code player}.
 *
 * <p>У приватного экрана это его владелец. У публичного экрана глобал {@code player}
 * равен {@code null}, а игрок, кликнувший по виджету, приходит вторым аргументом
 * обработчика: {@code void buy(dyn widget, dyn player) { ... }}.</p>
 *
 * <pre>
 * player.message("Привет, " + player.name());
 * player.message("Внимание!", "#FF5555");
 * player.sound("ui.button.click", 1, 1.2);
 * if (player.health() &lt; 10) player.health(20);
 * player.gamemode("creative");
 * player.command("spawn");
 * player.input("Введите цену:", 600, text -&gt; label.text("Цена: " + text));
 * </pre>
 *
 * <p>Числовые параметры объявлены как {@code double}: Jumper расширяет {@code int}
 * до {@code double}, но никогда не сужает обратно, так что {@code player.health(20)}
 * и {@code player.health(19.5)} одинаково допустимы.</p>
 */
public final class PlayerAPI {
    private final Player player;
    private final Input input;

    /**
     * Ввод из чата для экрана, которому принадлежит эта обёртка игрока (см. {@code ScriptContext}).
     */
    public interface Input {
        /** Ждать сообщение; {@code false} - нельзя (экран закрывается или закрыт). */
        boolean request(Player player, Consumer<String> fn, long timeoutTicks);

        void cancel(Player player);

        boolean awaiting(Player player);
    }

    public PlayerAPI(Player player) {
        this(player, null);
    }

    public PlayerAPI(Player player, Input input) {
        this.player = player;
        this.input = input;
    }

    /** Имя игрока. */
    public String name() {
        return player.getName();
    }

    /** UUID игрока строкой. */
    public String uuid() {
        return player.getUniqueId().toString();
    }

    /** Является ли игрок оператором. */
    public boolean op() {
        return player.isOp();
    }

    /** Онлайн ли игрок. */
    public boolean online() {
        return player.isOnline();
    }

    /** Режим игры в нижнем регистре: {@code "survival"}, {@code "creative"}, ... */
    public String gamemode() {
        return player.getGameMode().name().toLowerCase(Locale.ROOT);
    }

    /** Установить режим игры по имени (регистр не важен). Неизвестный режим игнорируется. */
    public void gamemode(String mode) {
        if (mode == null) return;
        try {
            player.setGameMode(GameMode.valueOf(mode.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            // неверное имя режима
        }
    }

    /** Здоровье игрока. */
    public double health() {
        return player.getHealth();
    }

    /** Установить здоровье (зажимается в 0–20). */
    public void health(double value) {
        player.setHealth(Math.max(0, Math.min(20, value)));
    }

    /** Сообщение в чат: строка (с §-кодами) или сегменты {@code [ {text, color}, {sprite} ]}, как в файле экрана. */
    public void message(Object text) {
        // таблицы скрипта (JTable) и массивы - в Map/List, как у widget.text(...); одиночный сегмент - список из одного
        Object v = padej.displayLib.config.JmcValues.toJava(text);
        if (v instanceof java.util.Map<?, ?> m) v = java.util.List.of(m);
        if (v instanceof java.util.List<?>) {
            player.sendMessage(padej.displayLib.ui.ScreenSupport.parseFormattedText(v));
        } else {
            player.sendMessage(String.valueOf(text));
        }
    }

    /** Сообщение в чат с hex-цветом вида {@code "#RRGGBB"}. */
    public void message(Object text, String hexColor) {
        Component component = Component.text(String.valueOf(text));
        TextColor color = hexColor != null && hexColor.startsWith("#") ? TextColor.fromHexString(hexColor) : null;
        player.sendMessage(color != null ? component.color(color) : component);
    }

    /** Звук с громкостью и высотой 1. */
    public void sound(String name) {
        sound(name, 1.0, 1.0);
    }

    /** Звук с заданной громкостью. */
    public void sound(String name, double volume) {
        sound(name, volume, 1.0);
    }

    /**
     * Звук. Имя - либо константа {@link Sound} ({@code ui.button.click} превращается в
     * {@code UI_BUTTON_CLICK}), либо произвольный строковый ключ.
     */
    @SuppressWarnings({"deprecation", "removal"})
    public void sound(String name, double volume, double pitch) {
        if (name == null) return;
        float vol = (float) volume;
        float pit = (float) pitch;
        if (name.indexOf('.') >= 0 || name.indexOf(':') >= 0) {
            // Ключ звука как в игре: "ui.button.click", "minecraft:block.note_block.pling"
            player.playSound(player.getLocation(), name.toLowerCase(Locale.ROOT), vol, pit);
            return;
        }
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, vol, pit);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), name, vol, pit);
        }
    }

    /**
     * Ждать следующее сообщение игрока в чат: оно не уходит в чат, а {@code fn(text)} получает его текст
     * (как набрал игрок, без форматирования). Одно ожидание на игрока - новое заменяет прежнее.
     */
    public void input(Consumer<String> fn) {
        input(null, 0, fn);
    }

    /** То же с подсказкой: {@code prompt} отправляется как {@link #message(Object)} (строка или сегменты). */
    public void input(Object prompt, Consumer<String> fn) {
        input(prompt, 0, fn);
    }

    /**
     * То же с таймаутом: через {@code timeoutTicks} тиков ожидание снимается и {@code fn} не вызывается;
     * {@code <= 0} - без таймаута. Ожидание снимается и при закрытии экрана, и при выходе игрока.
     * Из {@code onClose} новое ожидание не ставится.
     */
    public void input(Object prompt, double timeoutTicks, Consumer<String> fn) {
        if (fn == null || input == null) return;
        if (!input.request(player, fn, Math.round(timeoutTicks))) return;
        if (prompt != null) message(prompt);
    }

    /** Снять ожидание ввода, поставленное этим экраном. */
    public void cancelInput() {
        if (input != null) input.cancel(player);
    }

    /** Ждёт ли этот экран ввода от игрока. */
    public boolean awaitingInput() {
        return input != null && input.awaiting(player);
    }

    /** Выполнить команду от имени игрока (ведущий {@code /} отбрасывается). */
    public void command(String command) {
        if (command == null) return;
        player.performCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    /**
     * Объект Bukkit {@link Player}. Чтобы скрипт мог вызывать его методы, политика
     * доступа ({@code scripts.jma}) должна открыть пакет {@code org.bukkit}; по умолчанию
     * он закрыт.
     */
    public Player handle() {
        return player;
    }

    /** Внутреннее: UUID для кэшей. */
    public UUID id() {
        return player.getUniqueId();
    }

    @Override
    public String toString() {
        return "player " + player.getName();
    }
}

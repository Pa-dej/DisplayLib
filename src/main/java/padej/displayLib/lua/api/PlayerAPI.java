package padej.displayLib.lua.api;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Lua API для работы с игроком.
 * 
 * <p>Предоставляет доступ к информации об игроке и позволяет выполнять
 * различные действия от его имени.</p>
 * 
 * <h2>Доступные методы в Lua:</h2>
 * 
 * <p><b>Получение информации:</b></p>
 * <ul>
 * <li><b>player.name()</b> - Получить имя игрока</li>
 * <li><b>player.op()</b> - Проверить, является ли игрок оператором</li>
 * <li><b>player.gamemode()</b> - Получить режим игры</li>
 * <li><b>player.health()</b> - Получить здоровье игрока</li>
 * </ul>
 * 
 * <p><b>Изменение состояния:</b></p>
 * <ul>
 * <li><b>player.gamemode(mode)</b> - Установить режим игры ("creative", "survival", "adventure", "spectator")</li>
 * <li><b>player.health(value)</b> - Установить здоровье (0-20)</li>
 * </ul>
 * 
 * <p><b>Взаимодействие:</b></p>
 * <ul>
 * <li><b>player.message(text, [color])</b> - Отправить сообщение игроку</li>
 * <li><b>player.sound(name, [volume], [pitch])</b> - Воспроизвести звук</li>
 * <li><b>player.command(cmd)</b> - Выполнить команду от имени игрока</li>
 * </ul>
 * 
 * <h2>Примеры использования в Lua:</h2>
 * <pre>{@code
 * -- Получение информации
 * local name = player.name()
 * local isOp = player.op()
 * local mode = player.gamemode()
 * local hp = player.health()
 * 
 * -- Изменение состояния
 * player.gamemode("creative")
 * player.health(20)
 * 
 * -- Отправка сообщений
 * player.message("Привет!")
 * player.message("Красный текст", "#FF0000")
 * 
 * -- Звуки
 * player.sound("ENTITY_EXPERIENCE_ORB_PICKUP")
 * player.sound("BLOCK_NOTE_BLOCK_PLING", 1.0, 2.0)
 * 
 * -- Команды
 * player.command("give @s diamond 1")
 * }</pre>
 * 
 * @author DisplayLib
 * @version 1.0
 */
public class PlayerAPI extends LuaTable {
    private final Player player;
    
    public PlayerAPI(Player player) {
        this.player = player;
        
        // Геттеры
        set("name", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(player.getName());
            }
        });
        
        set("op", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(player.isOp());
            }
        });
        
        // Геттер/сеттер для gamemode
        set("gamemode", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                if (arg.isnil()) {
                    // Геттер
                    return LuaValue.valueOf(player.getGameMode().name().toLowerCase());
                } else {
                    // Сеттер
                    String mode = arg.checkjstring().toUpperCase();
                    try {
                        GameMode gameMode = GameMode.valueOf(mode);
                        player.setGameMode(gameMode);
                    } catch (IllegalArgumentException e) {
                        // Игнорируем неверные режимы
                    }
                    return LuaValue.NIL;
                }
            }
        });
        
        // Геттер/сеттер для health
        set("health", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                if (arg.isnil()) {
                    // Геттер
                    return LuaValue.valueOf(player.getHealth());
                } else {
                    // Сеттер
                    double health = arg.checkdouble();
                    player.setHealth(Math.max(0, Math.min(20, health)));
                    return LuaValue.NIL;
                }
            }
        });
        
        // Отправка сообщений
        set("message", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue message, LuaValue color) {
                String text = message.checkjstring();
                if (!color.isnil()) {
                    String colorCode = color.checkjstring();
                    if (colorCode.startsWith("#")) {
                        // Hex цвет (для 1.16+)
                        text = net.md_5.bungee.api.ChatColor.of(colorCode) + text;
                    }
                }
                player.sendMessage(text);
                return LuaValue.NIL;
            }
        });
        
        // Воспроизведение звуков
        set("sound", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue soundName, LuaValue volume, LuaValue pitch) {
                String sound = soundName.checkjstring();
                float vol = volume.isnil() ? 1.0f : (float) volume.checkdouble();
                float pit = pitch.isnil() ? 1.0f : (float) pitch.checkdouble();
                
                try {
                    Sound bukkitSound = Sound.valueOf(sound.toUpperCase().replace('.', '_'));
                    player.playSound(player.getLocation(), bukkitSound, vol, pit);
                } catch (IllegalArgumentException e) {
                    // Пробуем как строку (для кастомных звуков)
                    player.playSound(player.getLocation(), sound, vol, pit);
                }
                return LuaValue.NIL;
            }
        });
        
        // Выполнение команд
        set("command", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue command) {
                String cmd = command.checkjstring();
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }
                player.performCommand(cmd);
                return LuaValue.NIL;
            }
        });
    }
}
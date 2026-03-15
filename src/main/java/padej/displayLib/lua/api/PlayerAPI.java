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
 * Lua API для работы с игроком
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
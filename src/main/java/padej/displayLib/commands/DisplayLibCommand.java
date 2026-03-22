package padej.displayLib.commands;

import padej.displayLib.DisplayLib;
import padej.displayLib.ui.UIManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Основная команда плагина для тестирования и управления
 */
public class DisplayLibCommand implements CommandExecutor, TabCompleter {
    private final DisplayLib plugin;
    
    public DisplayLibCommand(DisplayLib plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                showHelp(player);
            } else {
                sender.sendMessage("§cИспользование: /displaylib <open|close|list|reload|examples>");
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "open" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /displaylib open <screen_id> [player] [x y z] [yaw pitch]");
                    return true;
                }
                
                String screenId = args[1];
                Player targetPlayer = null;
                Location customLocation = null;
                Float customYaw = null;
                Float customPitch = null;
                
                // Определяем целевого игрока
                if (sender instanceof Player player) {
                    // Команда от игрока - открываем ему
                    targetPlayer = player;
                    
                    // Проверяем, есть ли координаты
                    if (args.length >= 5) {
                        if (args[2].equals("~") && args[3].equals("~") && args[4].equals("~")) {
                            // Используем позицию игрока
                            customLocation = player.getLocation();
                        } else {
                            try {
                                double x = Double.parseDouble(args[2]);
                                double y = Double.parseDouble(args[3]);
                                double z = Double.parseDouble(args[4]);
                                customLocation = new Location(player.getWorld(), x, y, z);
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§cНеверные координаты: " + args[2] + " " + args[3] + " " + args[4]);
                                return true;
                            }
                        }
                        
                        // Проверяем, есть ли yaw и pitch (args[5], args[6])
                        if (args.length >= 7) {
                            try {
                                if (!args[5].equals("~")) {
                                    customYaw = Float.parseFloat(args[5]);
                                }
                                if (!args[6].equals("~")) {
                                    customPitch = Float.parseFloat(args[6]);
                                }
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§cНеверные углы поворота: " + args[5] + " " + args[6]);
                                return true;
                            }
                        }
                    }
                } else {
                    // Команда от командного блока или другого источника
                    if (args.length < 3) {
                        sender.sendMessage("§cДля командного блока укажите игрока: /displaylib open <screen_id> <player> [x y z] [yaw pitch]");
                        return true;
                    }
                    
                    targetPlayer = plugin.getServer().getPlayer(args[2]);
                    if (targetPlayer == null) {
                        sender.sendMessage("§cИгрок '" + args[2] + "' не найден");
                        return true;
                    }
                    
                    // Проверяем, есть ли координаты (args[3], args[4], args[5])
                    if (args.length >= 6) {
                        if (args[3].equals("~") && args[4].equals("~") && args[5].equals("~")) {
                            // Используем позицию отправителя команды (для execute at)
                            if (sender instanceof org.bukkit.command.BlockCommandSender blockSender) {
                                customLocation = blockSender.getBlock().getLocation().add(0.5, 0, 0.5);
                            } else if (sender instanceof org.bukkit.entity.Entity entity) {
                                customLocation = entity.getLocation();
                            } else {
                                // Fallback - используем позицию игрока
                                customLocation = targetPlayer.getLocation();
                            }
                        } else {
                            try {
                                double x = Double.parseDouble(args[3]);
                                double y = Double.parseDouble(args[4]);
                                double z = Double.parseDouble(args[5]);
                                customLocation = new Location(targetPlayer.getWorld(), x, y, z);
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§cНеверные координаты: " + args[3] + " " + args[4] + " " + args[5]);
                                return true;
                            }
                        }
                        
                        // Проверяем, есть ли yaw и pitch (args[6], args[7])
                        if (args.length >= 8) {
                            try {
                                if (!args[6].equals("~")) {
                                    customYaw = Float.parseFloat(args[6]);
                                }
                                if (!args[7].equals("~")) {
                                    customPitch = Float.parseFloat(args[7]);
                                }
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§cНеверные углы поворота: " + args[6] + " " + args[7]);
                                return true;
                            }
                        }
                    } else {
                        // Если координаты не указаны, но команда выполняется через execute at,
                        // пытаемся получить позицию из контекста
                        if (sender instanceof org.bukkit.command.BlockCommandSender blockSender) {
                            customLocation = blockSender.getBlock().getLocation().add(0.5, 0, 0.5);
                        } else if (sender instanceof org.bukkit.entity.Entity entity) {
                            customLocation = entity.getLocation();
                        }
                    }
                }
                
                boolean success;
                if (customLocation != null) {
                    if (customYaw != null || customPitch != null) {
                        // Используем yaw/pitch если они указаны, иначе null для автоматического определения
                        success = UIManager.getInstance().openScreen(targetPlayer, screenId, customLocation, customYaw, customPitch);
                        
                        String locationStr = String.format("%.1f %.1f %.1f", 
                                customLocation.getX(), customLocation.getY(), customLocation.getZ());
                        String rotationStr = "";
                        if (customYaw != null || customPitch != null) {
                            rotationStr = String.format(" (yaw: %s, pitch: %s)", 
                                    customYaw != null ? String.format("%.1f", customYaw) : "auto",
                                    customPitch != null ? String.format("%.1f", customPitch) : "auto");
                        }
                        
                        sender.sendMessage("§aЭкран '" + screenId + "' открыт для " + targetPlayer.getName() + 
                                         " в позиции " + locationStr + rotationStr);
                    } else {
                        success = UIManager.getInstance().openScreen(targetPlayer, screenId, customLocation);
                        sender.sendMessage("§aЭкран '" + screenId + "' открыт для " + targetPlayer.getName() + 
                                         " в позиции " + String.format("%.1f %.1f %.1f", 
                                         customLocation.getX(), customLocation.getY(), customLocation.getZ()));
                    }
                } else {
                    success = UIManager.getInstance().openScreen(targetPlayer, screenId);
                    sender.sendMessage("§aЭкран '" + screenId + "' открыт для " + targetPlayer.getName());
                }
                
                if (!success) {
                    sender.sendMessage("§cНе удалось открыть экран '" + screenId + "' для " + targetPlayer.getName());
                }
            }
            
            case "close" -> {
                Player targetPlayer = null;
                
                if (sender instanceof Player player) {
                    targetPlayer = player;
                } else {
                    if (args.length < 2) {
                        sender.sendMessage("§cДля командного блока укажите игрока: /displaylib close <player>");
                        return true;
                    }
                    
                    targetPlayer = plugin.getServer().getPlayer(args[1]);
                    if (targetPlayer == null) {
                        sender.sendMessage("§cИгрок '" + args[1] + "' не найден");
                        return true;
                    }
                }
                
                UIManager.getInstance().closeScreen(targetPlayer);
                sender.sendMessage("§aЭкран закрыт для " + targetPlayer.getName());
            }
            
            case "openglobal" -> {
                if (!sender.hasPermission("displaylib.admin")) {
                    sender.sendMessage("§cНет прав доступа");
                    return true;
                }
                
                if (args.length < 5) {
                    sender.sendMessage("§cИспользование: /displaylib openglobal <screen_id> <x> <y> <z> [yaw] [pitch]");
                    return true;
                }
                
                String screenId = args[1];
                Location location;
                
                try {
                    double x, y, z;
                    
                    // Обработка координат ~ для игрока
                    if (sender instanceof Player player) {
                        Location playerLoc = player.getLocation();
                        x = args[2].equals("~") ? playerLoc.getX() : Double.parseDouble(args[2]);
                        y = args[3].equals("~") ? playerLoc.getY() : Double.parseDouble(args[3]);
                        z = args[4].equals("~") ? playerLoc.getZ() : Double.parseDouble(args[4]);
                        location = new Location(player.getWorld(), x, y, z);
                    } else {
                        x = Double.parseDouble(args[2]);
                        y = Double.parseDouble(args[3]);
                        z = Double.parseDouble(args[4]);
                        // Для консоли используем первый мир
                        location = new Location(plugin.getServer().getWorlds().get(0), x, y, z);
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНеверные координаты: " + args[2] + " " + args[3] + " " + args[4]);
                    return true;
                }
                
                float yaw = location.getYaw();
                float pitch = location.getPitch();
                
                // Проверяем yaw и pitch если указаны
                if (args.length >= 6) {
                    try {
                        yaw = Float.parseFloat(args[5]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cНеверный yaw: " + args[5]);
                        return true;
                    }
                }
                
                if (args.length >= 7) {
                    try {
                        pitch = Float.parseFloat(args[6]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cНеверный pitch: " + args[6]);
                        return true;
                    }
                }
                
                boolean success = UIManager.getInstance().openGlobalScreen(screenId, location, yaw, pitch);
                if (success) {
                    sender.sendMessage("§aГлобальный экран '" + screenId + "' открыт в позиции " + 
                                     String.format("%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ()));
                } else {
                    sender.sendMessage("§cНе удалось открыть глобальный экран '" + screenId + "'");
                }
            }
            
            case "closeglobal" -> {
                if (!sender.hasPermission("displaylib.admin")) {
                    sender.sendMessage("§cНет прав доступа");
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /displaylib closeglobal <screen_id>");
                    return true;
                }
                
                String screenId = args[1];
                boolean success = UIManager.getInstance().closeGlobalScreenById(screenId);
                
                if (success) {
                    sender.sendMessage("§aГлобальный экран '" + screenId + "' закрыт");
                } else {
                    sender.sendMessage("§cГлобальный экран '" + screenId + "' не найден");
                }
            }
            
            case "listglobal" -> {
                if (!sender.hasPermission("displaylib.admin")) {
                    sender.sendMessage("§cНет прав доступа");
                    return true;
                }
                
                var globalScreens = UIManager.getInstance().getGlobalScreens();
                if (globalScreens.isEmpty()) {
                    sender.sendMessage("§eНет активных глобальных экранов");
                } else {
                    sender.sendMessage("§eАктивные глобальные экраны:");
                    for (var screen : globalScreens) {
                        Location loc = screen.getLocation();
                        int nearbyCount = screen.getNearbyPlayers().size();
                        sender.sendMessage("§7- " + screen.getScreenId() + " в " + 
                                         String.format("%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ()) +
                                         " (игроков поблизости: " + nearbyCount + ")");
                    }
                }
            }
            
            case "reload" -> {
                if (sender instanceof Player player && !player.hasPermission("displaylib.admin")) {
                    sender.sendMessage("§cНет прав доступа");
                    return true;
                }
                
                plugin.getScreenRegistry().reloadAll();
                plugin.getLuaEngine().clearCache();
                sender.sendMessage("§aЭкраны и скрипты перезагружены");
            }
            
            case "list" -> {
                var screens = plugin.getScreenRegistry().getAllScreens();
                sender.sendMessage("§eДоступные экраны:");
                for (String screenId : screens.keySet()) {
                    sender.sendMessage("§7- " + screenId);
                }
            }
            
            case "examples" -> {
                if (sender instanceof Player player && !player.hasPermission("displaylib.admin")) {
                    sender.sendMessage("§cНет прав доступа");
                    return true;
                }
                
                plugin.getScreenRegistry().getScreenLoader().createExampleScreensManually();
                plugin.getScreenRegistry().reloadAll();
                sender.sendMessage("§aПримеры экранов созданы и загружены");
            }
            
            default -> {
                if (sender instanceof Player player) {
                    showHelp(player);
                } else {
                    sender.sendMessage("§cНеизвестная команда. Доступные: open, close, list, reload, examples");
                }
            }
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§6=== DisplayLib Commands ===");
        player.sendMessage("§e/displaylib open <screen_id> §7- Открыть экран");
        player.sendMessage("§e/displaylib close §7- Закрыть текущий экран");
        player.sendMessage("§e/displaylib list §7- Список доступных экранов");
        
        if (player.hasPermission("displaylib.admin")) {
            player.sendMessage("§c=== Admin Commands ===");
            player.sendMessage("§e/displaylib reload §7- Перезагрузить экраны и скрипты");
            player.sendMessage("§e/displaylib examples §7- Создать примеры экранов");
            player.sendMessage("§e/displaylib openglobal <id> <x> <y> <z> [yaw] [pitch] §7- Открыть глобальный экран");
            player.sendMessage("§7  (если экран с таким ID уже существует, он будет пересоздан)");
            player.sendMessage("§e/displaylib closeglobal <id> §7- Закрыть глобальные экраны");
            player.sendMessage("§e/displaylib listglobal §7- Список активных глобальных экранов");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = Arrays.asList("open", "close", "list");
            if (sender.hasPermission("displaylib.admin")) {
                commands = Arrays.asList("open", "close", "list", "reload", "examples", "openglobal", "closeglobal", "listglobal");
            }
            return commands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("open")) {
                // Для open показываем список экранов
                return plugin.getScreenRegistry().getAllScreens().keySet().stream()
                        .filter(screen -> screen.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            } else if (args[0].equalsIgnoreCase("openglobal")) {
                // Для openglobal показываем список GLOBAL экранов
                return plugin.getScreenRegistry().getAllScreens().entrySet().stream()
                        .filter(entry -> entry.getValue().getScreenType() == padej.displayLib.config.ScreenDefinition.ScreenType.GLOBAL)
                        .map(entry -> entry.getKey())
                        .filter(screen -> screen.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            } else if (args[0].equalsIgnoreCase("closeglobal")) {
                // Для closeglobal показываем активные глобальные экраны
                return UIManager.getInstance().getGlobalScreens().stream()
                        .map(screen -> screen.getScreenId())
                        .distinct()
                        .filter(screen -> screen.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            } else if (args[0].equalsIgnoreCase("close") && !(sender instanceof Player)) {
                // Для close из командного блока показываем список игроков
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("open") && !(sender instanceof Player)) {
            // Для open из командного блока третий аргумент - игрок
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }
        
        return List.of();
    }
}
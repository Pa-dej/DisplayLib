package padej.displayLib.commands;

import padej.displayLib.DisplayLib;
import padej.displayLib.ui.UIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "open" -> {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /displaylib open <screen_id>");
                    return true;
                }
                
                String screenId = args[1];
                boolean success = UIManager.getInstance().openScreen(player, screenId);
                if (success) {
                    player.sendMessage("§aЭкран '" + screenId + "' открыт");
                } else {
                    player.sendMessage("§cНе удалось открыть экран '" + screenId + "'");
                }
            }
            
            case "close" -> {
                UIManager.getInstance().closeScreen(player);
                player.sendMessage("§aЭкран закрыт");
            }
            
            case "reload" -> {
                if (!player.hasPermission("displaylib.admin")) {
                    player.sendMessage("§cНет прав доступа");
                    return true;
                }
                
                plugin.getScreenRegistry().reloadAll();
                plugin.getLuaEngine().clearCache();
                player.sendMessage("§aЭкраны и скрипты перезагружены");
            }
            
            case "list" -> {
                var screens = plugin.getScreenRegistry().getAllScreens();
                player.sendMessage("§eДоступные экраны:");
                for (String screenId : screens.keySet()) {
                    player.sendMessage("§7- " + screenId);
                }
            }
            
            case "examples" -> {
                if (!player.hasPermission("displaylib.admin")) {
                    player.sendMessage("§cНет прав доступа");
                    return true;
                }
                
                plugin.getScreenRegistry().getScreenLoader().createExampleScreensManually();
                plugin.getScreenRegistry().reloadAll();
                player.sendMessage("§aПримеры экранов созданы и загружены");
            }
            
            default -> showHelp(player);
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
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = Arrays.asList("open", "close", "list");
            if (sender.hasPermission("displaylib.admin")) {
                commands = Arrays.asList("open", "close", "list", "reload", "examples");
            }
            return commands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return plugin.getScreenRegistry().getAllScreens().keySet().stream()
                    .filter(screen -> screen.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        
        return List.of();
    }
}
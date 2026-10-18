package padej.displayLib.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import padej.displayLib.particles.ParticleEffect;
import padej.displayLib.particles.ParticleEmission;
import padej.displayLib.particles.Particles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /displaylib particle ...}:
 * <ul>
 * <li>{@code particle <effect> [x y z] [player] [count]} - запустить эффект (координаты с {@code ~});
 *     без координат - перед игроком; {@code player} - для кого (при {@code viewers = "owner"} видит только он)</li>
 * <li>{@code particle list} - эффекты и сколько частиц живо</li>
 * <li>{@code particle stop} - убрать все частицы</li>
 * <li>{@code particle shatter <текст>} - слово перед игроком рассыпается на буквы (эффект {@code letters})</li>
 * </ul>
 */
final class ParticleCommand {
    static final String USAGE = "§cИспользование: /displaylib particle <effect> [x y z] [player] [count] | list | stop | shatter <текст>";

    private ParticleCommand() {}

    static void execute(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("displaylib.admin")) {
            sender.sendMessage("§cНет прав доступа");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(USAGE);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> {
                Map<String, Integer> alive = Particles.aliveByEffect();
                sender.sendMessage("§eЭффекты частиц §7(живо сейчас: " + Particles.alive() + ", показ: "
                        + ("packets".equals(Particles.mode()) ? "пакетами" : "сущностями") + "):");
                for (String id : Particles.ids()) {
                    ParticleEffect e = Particles.effect(id);
                    int n = alive.getOrDefault(id, 0);
                    sender.sendMessage("§7- §f" + id + " §8" + e.render().type() + ", " + e.viewers().name().toLowerCase()
                            + (n > 0 ? " §a" + n + " живых" : ""));
                }
                if (Particles.ids().isEmpty()) sender.sendMessage("§7  нет - положите файлы в plugins/DisplayLib/particles/ или /displaylib examples");
                return;
            }
            case "stop" -> {
                int n = Particles.alive();
                Particles.stopAll();
                sender.sendMessage("§aУбрано частиц: " + n);
                return;
            }
            case "shatter" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cТолько для игрока"); return; }
                if (args.length < 3) { sender.sendMessage("§cИспользование: /displaylib particle shatter <текст>"); return; }
                if (Particles.effect("letters") == null) { sender.sendMessage("§cНет эффекта 'letters' - /displaylib examples"); return; }
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).replace('&', '§');
                Location eye = p.getEyeLocation();
                Location at = eye.clone().add(eye.getDirection().setY(0).normalize().multiply(3)).subtract(0, 0.3, 0);
                Particles.shatter(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text))
                        .at(at).facing(p).owner(p).hold(20).scale(1.5).play();
                return;
            }
            default -> { }
        }

        String id = args[1];
        ParticleEffect effect = Particles.effect(id);
        if (effect == null) {
            sender.sendMessage("§cНет эффекта '" + id + "'. Список: /displaylib particle list");
            return;
        }

        Location origin = origin(sender);
        int i = 2;
        Location at;
        // координаты - три подряд; одиночное целое без них - игрок или число частиц
        boolean hasCoords = args.length >= 5 && isCoord(args[2]) && isCoord(args[3]) && isCoord(args[4]);
        boolean looksLikeCoord = args.length >= 3 && (args[2].startsWith("~") || isNumber(args[2]) && !isInteger(args[2]));
        if (looksLikeCoord && !hasCoords) {
            sender.sendMessage("§cНужны все три координаты: x y z");
            return;
        }
        if (hasCoords) {
            if (origin == null && (args[2].startsWith("~") || args[3].startsWith("~") || args[4].startsWith("~"))) {
                sender.sendMessage("§c~ из консоли: укажите абсолютные координаты");
                return;
            }
            Location base = origin != null ? origin : new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
            try {
                at = new Location(base.getWorld(), coord(args[2], base.getX()), coord(args[3], base.getY()), coord(args[4], base.getZ()));
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cНеверные координаты: " + args[2] + " " + args[3] + " " + args[4]);
                return;
            }
            i = 5;
        } else if (sender instanceof Player p) {
            at = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(2.5));
        } else if (origin != null) {
            at = origin;
        } else {
            sender.sendMessage("§cИз консоли укажите координаты: /displaylib particle " + id + " <x> <y> <z>");
            return;
        }

        Player owner = sender instanceof Player p ? p : null;
        if (args.length > i) {
            // сначала игрок в сети (имя может состоять из цифр), иначе - число частиц
            Player named = Bukkit.getPlayerExact(args[i]);
            if (named != null) {
                owner = named;
                i++;
            } else if (!isInteger(args[i])) {
                sender.sendMessage("§cИгрок '" + args[i] + "' не в сети");
                return;
            }
        }
        if (args.length > i) {
            if (!isInteger(args[i])) {
                sender.sendMessage("§cЧисло частиц - целое: " + args[i]);
                return;
            }
            effect = effect.withCount(Integer.parseInt(args[i]));
            i++;
        }
        if (args.length > i) {
            sender.sendMessage(USAGE);
            return;
        }

        ParticleEmission emission = Particles.play(effect, at, owner);
        if (emission == null) {
            sender.sendMessage("§cЧастицы выключены (плагин перезагружается?)");
        }
    }

    static List<String> complete(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            options.addAll(Particles.ids());
            options.add("list");
            options.add("stop");
            options.add("shatter");
        } else if (args[1].equalsIgnoreCase("shatter")) {
            if (args.length == 3) options.add("слово");
        } else if (Particles.effect(args[1]) != null) {
            boolean afterCoords = args.length == 6 && isCoord(args[2]) && isCoord(args[3]) && isCoord(args[4]);
            if (args.length >= 3 && args.length <= 5 && (args.length == 3 || isCoord(args[2]))) options.add("~");
            if (args.length == 3 || afterCoords) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix)).toList();
    }

    /** Откуда считать ~: игрок, сущность, командный блок; null - консоль. */
    private static Location origin(CommandSender sender) {
        if (sender instanceof Entity e) return e.getLocation();
        if (sender instanceof BlockCommandSender b) return b.getBlock().getLocation().add(0.5, 0.5, 0.5);
        return null;
    }

    private static boolean isCoord(String s) {
        return s.startsWith("~") ? s.length() == 1 || isNumber(s.substring(1)) : isNumber(s);
    }

    private static boolean isNumber(String s) {
        try {
            return Double.isFinite(Double.parseDouble(s)) && !s.contains("N") && !s.contains("I");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double coord(String s, double base) {
        if (s.startsWith("~")) return base + (s.length() == 1 ? 0 : Double.parseDouble(s.substring(1)));
        return Double.parseDouble(s);
    }
}

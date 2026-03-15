package padej.displayLib.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

public class ItemUtil {
    public static boolean isExperimental(ItemStack item) {
        return item.getItemMeta() != null &&
               Component.text("Experimental Item").equals(item.getItemMeta().displayName());
    }
}
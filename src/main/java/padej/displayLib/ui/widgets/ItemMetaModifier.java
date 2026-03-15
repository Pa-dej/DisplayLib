package padej.displayLib.ui.widgets;

import org.bukkit.inventory.meta.ItemMeta;

@FunctionalInterface
public interface ItemMetaModifier {
    ItemMeta modify(ItemMeta meta);
}
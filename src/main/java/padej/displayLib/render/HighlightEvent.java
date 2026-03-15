package padej.displayLib.render;

import padej.displayLib.render.shapes.Highlight;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class HighlightEvent implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Получаем координаты разрушенного блока
        Location brokenBlockLocation = event.getBlock().getLocation();

        // Удаляем выделение для этого блока
        Highlight.removeSelectionOnBlockPos(
                brokenBlockLocation.getBlockX(),
                brokenBlockLocation.getBlockY(),
                brokenBlockLocation.getBlockZ()
        );
    }
}
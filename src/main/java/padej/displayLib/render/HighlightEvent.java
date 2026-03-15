package padej.displayLib.render;

import padej.displayLib.render.shapes.Highlight;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class HighlightEvent implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location brokenBlockLocation = event.getBlock().getLocation();

        Highlight.removeSelectionOnBlockPos(
                brokenBlockLocation.getBlockX(),
                brokenBlockLocation.getBlockY(),
                brokenBlockLocation.getBlockZ()
        );
    }
}
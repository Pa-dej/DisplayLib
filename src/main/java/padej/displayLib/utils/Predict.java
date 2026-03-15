package padej.displayLib.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class Predict {

    public static Location predictSurvivalRun(Player player) {
        return new Location(
                player.getWorld(),
                player.getX() + player.getVelocity().getX() * 2,
                player.getY() + player.getVelocity().getY() * 2,
                player.getZ() + player.getVelocity().getZ() * 2
        );
    }
}
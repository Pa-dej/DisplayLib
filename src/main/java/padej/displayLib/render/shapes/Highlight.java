package padej.displayLib.render.shapes;

import padej.displayLib.DisplayLib;
import padej.displayLib.render.HighlightStyle;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Highlight {

    private static final float MIN_OFFSET = 1e-4f;
    private static final int COLOR_CHANGE_SPEED = 5;
    private static Color currentStartColor;
    private static Color currentEndColor;
    private static Color currentColor;
    private static int currentAlpha = 150;

    public static Map<String, List<TextDisplay>> blockPosDisplays = new HashMap<>();

    private static float t = 0.0f;
    private static boolean increasing = true;

    private static void setHighlightStyle(HighlightStyle style) {
        switch (style) {
            case SILVER:
                currentStartColor = Color.fromARGB(100, 127, 112, 138);
                currentEndColor = Color.fromARGB(100, 199, 220, 208);
                break;
            case GOLD:
                currentStartColor = Color.fromARGB(100, 232, 59, 59);
                currentEndColor = Color.fromARGB(100, 251, 107, 29);
                break;
            case BRONZE:
                currentStartColor = Color.fromARGB(100, 205, 104, 61);
                currentEndColor = Color.fromARGB(100, 230, 144, 78);
                break;
            case OLIVE:
                currentStartColor = Color.fromARGB(100, 162, 169, 71);
                currentEndColor = Color.fromARGB(100, 213, 224, 75);
                break;
            case EMERALD:
                currentStartColor = Color.fromARGB(100, 30, 188, 115);
                currentEndColor = Color.fromARGB(100, 145, 219, 105);
                break;
            case AQUA:
                currentStartColor = Color.fromARGB(100, 14, 175, 155);
                currentEndColor = Color.fromARGB(100, 48, 225, 185);
                break;
            case BLUE:
                currentStartColor = Color.fromARGB(100, 77, 101, 180);
                currentEndColor = Color.fromARGB(100, 77, 155, 230);
                break;
            case PURPLE:
                currentStartColor = Color.fromARGB(100, 144, 94, 169);
                currentEndColor = Color.fromARGB(100, 168, 132, 243);
                break;
            case RUBY:
                currentStartColor = Color.fromARGB(120, 206, 33, 36);
                currentEndColor = Color.fromARGB(120, 122, 15, 17);
                break;
            case PINK:
                currentStartColor = Color.fromARGB(100, 240, 79, 120);
                currentEndColor = Color.fromARGB(100, 246, 129, 129);
                break;
            default:
                currentStartColor = Color.fromARGB(100, 130, 25, 40);
                currentEndColor = Color.fromARGB(100, 190, 20, 40);
                break;
        }
    }

    public static void createSides(Location location, HighlightStyle style, int alpha) {
        setHighlightStyle(style);
        addIfNotNull(southSide(location), location);
        addIfNotNull(eastSide(location), location);
        addIfNotNull(northSide(location), location);
        addIfNotNull(westSide(location), location);
        addIfNotNull(downSide(location), location);
        addIfNotNull(upSide(location), location);
        currentAlpha = alpha;
    }

    private static void addIfNotNull(TextDisplay display, Location location) {
        if (display != null) {
            String blockPosKey = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
            blockPosDisplays.computeIfAbsent(blockPosKey, k -> new ArrayList<>()).add(display);
        }
    }

    public static void removeSelectionOnBlockPos(int x, int y, int z) {
        String blockPosKey = x + "," + y + "," + z;
        List<TextDisplay> displaysToRemove = blockPosDisplays.remove(blockPosKey);
        if (displaysToRemove != null) {
            for (TextDisplay display : displaysToRemove) {
                display.remove();
            }
        }
    }

    public static void removeAllSelections() {
        for (List<TextDisplay> displaysList : blockPosDisplays.values()) {
            for (TextDisplay display : displaysList) {
                display.remove();
            }
        }
        blockPosDisplays.clear();
    }

    public static void startColorUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateGradientColor();
                updateAllDisplaysColor();
            }
        }.runTaskTimer(DisplayLib.getInstance(), 0L, 1L);
    }

    private static void updateGradientColor() {
        if (currentStartColor == null || currentEndColor == null) return;
        currentColor = lerpColor(currentStartColor, currentEndColor, t);

        if (increasing) {
            t += COLOR_CHANGE_SPEED / 100.0f;
            if (t >= 1.0f) {
                t = 1.0f;
                increasing = false;
            }
        } else {
            t -= COLOR_CHANGE_SPEED / 100.0f;
            if (t <= 0.0f) {
                t = 0.0f;
                increasing = true;
            }
        }
    }

    private static Color lerpColor(Color color1, Color color2, float t) {
        int r = (int) (color1.getRed() + t * (color2.getRed() - color1.getRed()));
        int g = (int) (color1.getGreen() + t * (color2.getGreen() - color1.getGreen()));
        int b = (int) (color1.getBlue() + t * (color2.getBlue() - color1.getBlue()));

        return Color.fromARGB(currentAlpha, r, g, b);
    }

    private static void updateAllDisplaysColor() {
        for (List<TextDisplay> displaysList : blockPosDisplays.values()) {
            for (TextDisplay display : displaysList) {
                if (display != null) {
                    display.setBackgroundColor(currentColor);
                }
            }
        }
    }

    private static TextDisplay southSide(Location location) {
        Block southBlock = location.getBlock().getRelative(BlockFace.SOUTH);
        if (southBlock.getType() == Material.AIR || southBlock.getType() == Material.SNOW_BLOCK || southBlock.getType() == Material.POWDER_SNOW) {
            org.bukkit.World world = location.getWorld();
            Location displayLoc = new Location(world,
                    location.getBlockX() + 0.5,
                    location.getBlockY(),
                    location.getBlockZ() + 1 + MIN_OFFSET
            );
            return createTextDisplay(displayLoc, 0f, 0f);
        }
        return null;
    }

    private static TextDisplay eastSide(Location location) {
        Block eastBlock = location.getBlock().getRelative(BlockFace.EAST);
        if (eastBlock.getType() == Material.AIR || eastBlock.getType() == Material.SNOW_BLOCK || eastBlock.getType() == Material.POWDER_SNOW) {
            org.bukkit.World world = location.getWorld();
            Location displayLoc = new Location(world,
                    location.getBlockX() + 1 + MIN_OFFSET,
                    location.getBlockY(),
                    location.getBlockZ() + 0.5
            );
            return createTextDisplay(displayLoc, -90f, 0f);
        }
        return null;
    }

    private static TextDisplay northSide(Location location) {
        Block northBlock = location.getBlock().getRelative(BlockFace.NORTH);
        if (northBlock.getType() == Material.AIR || northBlock.getType() == Material.SNOW_BLOCK || northBlock.getType() == Material.POWDER_SNOW) {
            org.bukkit.World world = location.getWorld();
            Location displayLoc = new Location(world,
                    location.getBlockX() + 1 - 0.5,
                    location.getBlockY(),
                    location.getBlockZ() - MIN_OFFSET
            );
            return createTextDisplay(displayLoc, -180f, 0f);
        }
        return null;
    }

    private static TextDisplay westSide(Location location) {
        Block westBlock = location.getBlock().getRelative(BlockFace.WEST);
        if (westBlock.getType() == Material.AIR) {
            org.bukkit.World world = location.getWorld();
            Location displayLoc = new Location(world,
                    location.getBlockX() - MIN_OFFSET,
                    location.getBlockY(),
                    location.getBlockZ() + 0.5
            );
            return createTextDisplay(displayLoc, 90f, 0f);
        }
        return null;
    }

    private static TextDisplay downSide(Location location) {
        Block downBlock = location.getBlock().getRelative(BlockFace.DOWN);
        if (downBlock.getType() == Material.AIR) {
            org.bukkit.World world = location.getWorld();
            Location displayLoc = new Location(world,
                    location.getBlockX() + 0.5,
                    location.getBlockY() - MIN_OFFSET,
                    location.getBlockZ()
            );
            return createTextDisplay(displayLoc, 0f, 90f);
        }
        return null;
    }

    private static TextDisplay upSide(Location location) {
        Block upBlock = location.getBlock().getRelative(BlockFace.UP);
        if (upBlock.getType() == Material.AIR) {
            org.bukkit.World world = location.getWorld();
            Location displayLoc = new Location(world,
                    location.getBlockX() + 0.5,
                    location.getBlockY() + 1 + MIN_OFFSET,
                    location.getBlockZ() + 1
            );
            return createTextDisplay(displayLoc, 0f, -90f);
        }
        return null;
    }

    private static TextDisplay createTextDisplay(Location displayLoc, float rotationX, float rotationY) {
        org.bukkit.World world = displayLoc.getWorld();
        TextDisplay textDisplay = (TextDisplay) world.spawnEntity(displayLoc, EntityType.TEXT_DISPLAY);
        textDisplay.setRotation(rotationX, rotationY);
        textDisplay.setInterpolationDuration(1);
        textDisplay.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(-0.1f, 0f, 0f),
                new AxisAngle4f(),
                new Vector3f(8f, 4f, 1f),
                new AxisAngle4f()
        ));
        textDisplay.text(Component.text(" "));
        textDisplay.setBackgroundColor(currentColor);
        textDisplay.setBrightness(new Display.Brightness(15, 15));
        return textDisplay;
    }
}
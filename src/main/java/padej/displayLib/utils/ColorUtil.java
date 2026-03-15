package padej.displayLib.utils;

import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class ColorUtil {

    public static Color formARGBColor(int alpha, Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        return Color.fromARGB(alpha, r, g, b);
    }

    public static java.awt.Color getRandomPartyPopperRGBColor() {
        int[][] colors = {
                {221, 220, 245},
                {200, 173, 236},
                {150, 112, 209},
                {112, 224, 231},
                {121, 180, 218}
        };

        int[] rgb = colors[new Random().nextInt(colors.length)];
        return new java.awt.Color(rgb[0], rgb[1], rgb[2]);
    }

    public static @NotNull java.awt.Color getRandomValentineRGBColor() {
        int[][] colors = {
                {139, 0, 0},
                {171, 20, 21},
                {201, 39, 28},
                {212, 61, 50},
                {220, 20, 60},
                {252, 90, 159},
                {253, 105, 147},
                {255, 160, 180},
                {253, 208, 213},
                {255, 228, 232}
        };

        int[] rgb = colors[new Random().nextInt(colors.length)];
        return new java.awt.Color(rgb[0], rgb[1], rgb[2]);
    }

    public static @NotNull java.awt.Color getRandomDefaultRGBColor() {
        int[][] colors = {
                {154, 8, 15}, {180, 10, 26}, {210, 17, 41}, {239, 33, 66}, {255, 87, 116},
                {187, 121, 7}, {204, 145, 4}, {237, 181, 8}, {252, 215, 32}, {254, 243, 100},
                {13, 126, 54}, {18, 147, 61}, {17, 170, 56}, {28, 201, 61}, {41, 230, 77},
                {4, 75, 143}, {9, 85, 168}, {18, 107, 195}, {23, 130, 219}, {51, 154, 252},
                {205, 62, 0}, {230, 91, 0}, {243, 120, 0}, {248, 149, 32}, {253, 175, 64},
                {2, 168, 193}, {12, 195, 202}, {23, 209, 199}, {56, 222, 189}, {91, 233, 183}
        };

        int[] rgb = colors[new Random().nextInt(colors.length)];
        return new java.awt.Color(rgb[0], rgb[1], rgb[2]);
    }
}
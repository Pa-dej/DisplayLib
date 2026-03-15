package padej.displayLib.render;

import org.bukkit.Color;

public enum HighlightStyle {
    SILVER(Color.fromARGB(100, 127, 112, 138), Color.fromARGB(100, 199, 220, 208)),
    GOLD(Color.fromARGB(100, 232, 59, 59), Color.fromARGB(100, 251, 107, 29)),
    BRONZE(Color.fromARGB(100, 205, 104, 61), Color.fromARGB(100, 230, 144, 78)),
    OLIVE(Color.fromARGB(100, 162, 169, 71), Color.fromARGB(100, 213, 224, 75)),
    EMERALD(Color.fromARGB(100, 30, 188, 115), Color.fromARGB(100, 145, 219, 105)),
    AQUA(Color.fromARGB(100, 14, 175, 155), Color.fromARGB(100, 48, 225, 185)),
    BLUE(Color.fromARGB(100, 77, 101, 180), Color.fromARGB(100, 77, 155, 230)),
    PURPLE(Color.fromARGB(100, 144, 94, 169), Color.fromARGB(100, 168, 132, 243)),
    RUBY(Color.fromARGB(120, 206, 33, 36), Color.fromARGB(120, 122, 15, 17)),
    PINK(Color.fromARGB(100, 240, 79, 120), Color.fromARGB(100, 246, 129, 129));

    HighlightStyle(Color startColor, Color endColor) {
    }
}
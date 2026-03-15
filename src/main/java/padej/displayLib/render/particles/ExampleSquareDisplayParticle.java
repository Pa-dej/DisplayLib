package padej.displayLib.render.particles;

import padej.displayLib.render.shapes.DefaultSquare;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;

import java.util.Random;

import static padej.displayLib.DisplayLib.DISPLAY_PARTICLES;

public class ExampleSquareDisplayParticle implements DisplayParticle {
    private int age;
    private static final Random random = new Random();
    private static final int MIN_LIFE = 200;
    private static final int MAX_LIFE = 300;

    private static final double GRAVITY_ACCELERATION = 0.002;
    private static final double AIR_DRAG_COEFFICIENT = 0.0015;
    private static final double BOUNCE_DAMPENING = 0.35;
    private static final double SPEED_DAMPENING = 0.06;
    private static final double SWAY_AMPLITUDE = 0.002;
    private static final double SWAY_FREQUENCY = 0.02;

    private final Vector velocity;
    private final Location position;
    private final Location source;
    private final DefaultSquare square;
    private final int maxAge;

    public ExampleSquareDisplayParticle(Location spawnLocation) {
        this.age = 0;
        this.source = spawnLocation.clone();
        this.position = spawnLocation.clone();
        this.maxAge = random.nextInt(MAX_LIFE - MIN_LIFE + 1) + MIN_LIFE;

        this.velocity = getInitialVelocity();
        this.square = new DefaultSquare(1.5f, getRandomColor(), 255, Display.Billboard.CENTER, false) {};
        square.spawn(spawnLocation);
    }

    private Vector getInitialVelocity() {
        double speed = 0.005 + (0.015 - 0.005) * random.nextDouble();
        double angle = Math.toRadians(random.nextInt(360));
        return new Vector(
                speed * Math.cos(angle),
                speed * 0.15,
                speed * Math.sin(angle)
        );
    }

    private Color getRandomColor() {
        int[][] colors = {
                {255, 0, 0},
                {0, 255, 0},
                {0, 0, 255},
                {255, 255, 0},
                {255, 0, 255},
                {0, 255, 255},
                {255, 255, 255},
        };

        int[] rgb = colors[random.nextInt(colors.length)];
        return Color.fromRGB(rgb[0], rgb[1], rgb[2]);
    }

    @Override
    public void update() {
        age++;
        if (age > maxAge) {
            square.removeEntity();
            this.remove();
            return;
        }

        square.getTextDisplay().text(Component.text(age));

        // Проверка, есть ли твёрдый блок под частицей
        if (!position.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
            velocity.setY(velocity.getY() - GRAVITY_ACCELERATION);
        } else {
            velocity.setX(0).setY(0).setZ(0);
            return;
        }

        double sway = SWAY_AMPLITUDE * Math.sin(age * SWAY_FREQUENCY);
        velocity.setX(velocity.getX() + sway);
        velocity.setZ(velocity.getZ() + sway);

        velocity.multiply(1 - AIR_DRAG_COEFFICIENT);

        Location oldPosition = position.clone();
        position.add(velocity);

        if (position.getBlock().getType().isSolid()) {
            velocity.multiply(0.3);
        }

        if (oldPosition.getY() > source.getY() && position.getY() <= source.getY()) {
            position.setY(source.getY());
            velocity.setY(-velocity.getY() * BOUNCE_DAMPENING);
            velocity.setX(velocity.getX() * SPEED_DAMPENING);
            velocity.setZ(velocity.getZ() * SPEED_DAMPENING);
        }

        square.getTextDisplay().teleport(position);
    }
}
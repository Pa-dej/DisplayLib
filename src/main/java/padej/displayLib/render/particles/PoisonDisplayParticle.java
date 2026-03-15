package padej.displayLib.render.particles;

import padej.displayLib.render.shapes.DefaultSquare;
import padej.displayLib.render.shapes.StringRectangle;
import padej.displayLib.utils.Animation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Random;

import static padej.displayLib.DisplayLib.DISPLAY_PARTICLES;

public class PoisonDisplayParticle implements DisplayParticle {
    private int age;
    private static final Random random = new Random();
    private static final int MAX_AGE = 80;
    private static final double MIN_SCALE = 0.5;
    private static final double VELOCITY_DECAY = 0.9; // Коэффициент замедления
    private static final double INITIAL_VELOCITY = 0.1; // Начальная скорость вверх
    private static final double SPAWN_OFFSET = 0.7; // Разброс по координатам

    private final Location position;
    private final DefaultSquare square;
    private double velocityY;

    public PoisonDisplayParticle(Location spawnLocation) {
        this.age = 0;
        this.velocityY = INITIAL_VELOCITY;

        // Генерация случайного смещения
        double offsetX = random.nextDouble() * (2 * SPAWN_OFFSET) - SPAWN_OFFSET; // [-0.7, 0.7]
        double offsetY = random.nextDouble() * (2 * SPAWN_OFFSET) - SPAWN_OFFSET;
        double offsetZ = random.nextDouble() * (2 * SPAWN_OFFSET) - SPAWN_OFFSET;

        this.position = spawnLocation.clone().add(offsetX, offsetY, offsetZ); // Смещение точки спавна

        this.square = new StringRectangle(
                0,
                Color.BLACK,
                0,
                Display.Billboard.CENTER,
                false,
                "§a☠"
        ) {};
        square.spawn(position); // Спавн с учётом разброса
        square.getTextDisplay().setBrightness(new Display.Brightness(15, 15));
    }

    @Override
    public void update() {
        age++;
        if (age > MAX_AGE) {
            square.removeEntity();
            DISPLAY_PARTICLES.remove(this);
            return;
        }

        if (age == 5) {
            float scale = (float) (random.nextDouble() + MIN_SCALE);
            Animation.applyTransformationWithInterpolation(square.getTextDisplay(), new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f()
            ), 40);
        }

        if (age == MAX_AGE - 12) {
            Animation.applyTransformationWithInterpolation(square.getTextDisplay(), new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f()
            ));
        }

        // Двигаем частицу вверх
        position.add(0, velocityY, 0);

        // Замедляем скорость
        velocityY *= VELOCITY_DECAY;

        square.getTextDisplay().teleport(position);
    }
}
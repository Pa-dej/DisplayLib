package padej.displayLib.render.particles;

import padej.displayLib.render.shapes.DefaultSquare;
import padej.displayLib.render.shapes.ComponentRectangle;
import padej.displayLib.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;

import static padej.displayLib.DisplayLib.DISPLAY_PARTICLES;

public class ExampleComponentDisplayParticle implements DisplayParticle {
    private int age;
    private static final Random random = new Random();
    private static final int MIN_LIFE = 200;
    private static final int MAX_LIFE = 300;

    private static final double GRAVITY_ACCELERATION = 0.0015;
    private static final double AIR_DRAG_COEFFICIENT = 0.0015;
    private static final double SPEED_DAMPENING = 0.06;
    private static final double SWAY_AMPLITUDE = 0.005;

    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 1.5;

    private final Vector velocity;
    private final Location position;
    private final Location source;
    private final DefaultSquare square;
    private final int maxAge;
    private boolean isRapidSpeed;  // Переменная для отслеживания первых 10 тиков

    public ExampleComponentDisplayParticle(Player player, Location spawnLocation) {
        this.age = 0;
        this.source = spawnLocation.clone();
        this.position = spawnLocation.clone();
        this.maxAge = random.nextInt(MAX_LIFE - MIN_LIFE + 1) + MIN_LIFE;
        this.isRapidSpeed = true;  // Устанавливаем начальную скорость высокой

        this.velocity = getInitialVelocity(player);
        
        // Получаем случайный цвет
        java.awt.Color awtColor = ColorUtil.getRandomPartyPopperRGBColor();
        
        // Создаем компонент с цветом
        Component coloredText = Component.text(getRandomChar())
                .color(TextColor.color(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue()));
        
        this.square = new ComponentRectangle(
                (float) (random.nextDouble() * (MAX_SCALE - MIN_SCALE) + MIN_SCALE),
                Color.BLACK,
                0,
                Display.Billboard.CENTER,
                false,
                coloredText
        ) {};
        square.spawn(spawnLocation);

        square.getTextDisplay().setBrightness(new Display.Brightness(15, 15));
    }

    // Метод для вычисления начальной скорости с разбросом
    private Vector getInitialVelocity(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize(); // Вектор взгляда игрока

        // Разброс: конусный угол (30 градусов в радианах)
        double spread = Math.toRadians(30);
        double randomYaw = (random.nextDouble() - 0.5) * spread; // Отклонение по горизонтали
        double randomPitch = (random.nextDouble() - 0.5) * spread; // Отклонение по вертикали

        // Создаем случайное вращение вектора взгляда
        Vector spreadVector = direction.clone().rotateAroundY(randomYaw).rotateAroundX(randomPitch);

        // Увеличенная начальная скорость (для первых 10 тиков)
        double speed = isRapidSpeed ? 0.25 + (0.2 - 0.1) * random.nextDouble() : 0.005 + (0.015 - 0.005) * random.nextDouble();
        return spreadVector.multiply(speed);
    }

    private String getRandomChar() {
        String[] chars = {"❤", "\uD83D\uDD25", "★", "☠", "█", "☯", "☀", "☽", "♦", "☂", "\uD83C\uDF0A", "♪", "♬", "☁", "⛏", "☄", "■", "\uD83E\uDDEA", "\uD83C\uDF56", "☃"};
        return chars[random.nextInt(chars.length)];
    }

    private String getRandomColoredChar() {
        return getRandomChar();
    }

    @Override
    public void update() {
        age++;
        if (age > maxAge) {
            square.removeEntity();
            DISPLAY_PARTICLES.remove(this);
            return;
        }

        // Первые 10 тиков с высокой скоростью
        if (isRapidSpeed && age > 5) {
            isRapidSpeed = false;  // После 10 тиков сбрасываем скорость на обычную
            velocity.setX(velocity.getX() / 2).setY(velocity.getY() / 2).setZ(velocity.getZ() / 2);
        }

        if (!position.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
            velocity.setY(velocity.getY() - GRAVITY_ACCELERATION);
        } else {
            velocity.setX(0).setY(0).setZ(0);
            age += 2;
            return;
        }

        double swayX = SWAY_AMPLITUDE * (random.nextDouble() * 2 - 1);
        double swayZ = SWAY_AMPLITUDE * (random.nextDouble() * 2 - 1);
        velocity.setX(velocity.getX() + swayX);
        velocity.setZ(velocity.getZ() + swayZ);

        velocity.multiply(1 - AIR_DRAG_COEFFICIENT);  // Замедление из-за сопротивления воздуха

        Location oldPosition = position.clone();
        position.add(velocity);

        if (position.getBlock().getType().isSolid()) {
            velocity.multiply(0.3);
        }

        if (oldPosition.getY() > source.getY() && position.getY() <= source.getY()) {
            position.setY(source.getY());
            velocity.setX(velocity.getX() * SPEED_DAMPENING);
            velocity.setZ(velocity.getZ() * SPEED_DAMPENING);
        }

        square.getTextDisplay().teleport(position);
    }
}
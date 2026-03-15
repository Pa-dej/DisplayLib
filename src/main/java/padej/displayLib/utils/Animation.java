package padej.displayLib.utils;

import padej.displayLib.DisplayLib;
import padej.displayLib.ui.Screen;
import padej.displayLib.ui.UIManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Animation {

    public static void applyTransformationWithInterpolation(Display display, Transformation transformation, int transformationDuration) {
        if ( transformation == display.getTransformation()) return;
        display.setTransformation(transformation);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(transformationDuration);
    }

    public static void applyTransformationWithInterpolation(Display display, Transformation transformation) {
        if (transformation == display.getTransformation()) return;
        display.setTransformation(transformation);
        display.setInterpolationDelay(0);
    }

    public static void applyTransformationWithInterpolation(Display display, Matrix4f matrix4f) {
        if (matrix4f == Matrix4fUtil.transformationToMatrix4f(display.getTransformation())) return;
        display.setTransformationMatrix(matrix4f);
        display.setInterpolationDelay(0);
    }

    public static void createDefaultScreenWithAnimation(Screen screen, Player player) {
        Bukkit.getScheduler().runTaskLater(DisplayLib.getInstance(), () -> {
            if (screen.getTextDisplay() == null) {
                return;
            }

            // Устанавливаем правильную трансформацию сразу без анимации
            screen.getTextDisplay().setTransformation(
                    new Transformation(
                            new Vector3f(0, 0, 0), // Нормальная позиция
                            new AxisAngle4f(),
                            new Vector3f(10, 4, 1), // Правильный масштаб
                            new AxisAngle4f()
                    )
            );

            screen.setOnClose(() -> UIManager.getInstance().unregisterScreen(player));
            screen.setupDefaultWidgets(player);
        }, 2);

        UIManager.getInstance().registerScreen(player, screen);
    }

}
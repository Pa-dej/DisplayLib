package padej.displayLib.utils;

import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;

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
}

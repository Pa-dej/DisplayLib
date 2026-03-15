package padej.displayLib.utils;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Matrix4fUtil {
    public static Matrix4f transformationToMatrix4f(Transformation transformation) {
        Matrix4f matrix = new Matrix4f().identity();
        Vector3f translation = transformation.getTranslation();
        Quaternionf rotation = transformation.getLeftRotation();
        Vector3f scale = transformation.getScale();

        matrix.translate(translation.x, translation.y, translation.z);
        matrix.rotate(rotation);
        matrix.scale(scale.x, scale.y, scale.z);

        return matrix;
    }
}
package padej.displayLib.particles;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Частица в мире: Display-сущность и её физическое тело.
 *
 * <p>Сервер не шлёт пакет каждый тик. Раз в {@code keyframeTicks} (или сразу после удара о блок)
 * тело просчитывается вперёд, и сущность получает цель: позицию через телепорт с
 * {@code teleportDuration} и размер/поворот через трансформацию с {@code interpolationDuration} -
 * дальше клиент плавно ведёт её сам.</p>
 */
final class LiveParticle {
    final ParticleEmission emission;
    final Display entity;
    final ParticleSim.Body body;
    final int life;
    final double startScale;
    final double startRotation;
    final double spin;
    /** Точка в локальных координатах сущности, вокруг которой она масштабируется и вращается (центр картинки) */
    final float centerX, centerY, centerZ;

    int age;
    /** Возраст, до которого просчитано тело (он же возраст текущей цели клиента) */
    int simAge;
    /** Когда слать следующий кадр */
    int nextKeyframe = 1; // первый - на следующем тике: интерполяция только что появившейся сущности не работает
    double sentScale = Double.NaN, sentRotation = Double.NaN;
    boolean dead;

    LiveParticle(ParticleEmission emission, Display entity, ParticleSim.Body body, int life,
                 double startScale, double startRotation, double spin, float[] center) {
        this.emission = emission;
        this.entity = entity;
        this.body = body;
        this.life = life;
        this.startScale = startScale;
        this.startRotation = startRotation;
        this.spin = spin;
        this.centerX = center[0];
        this.centerY = center[1];
        this.centerZ = center[2];
    }

    Transformation transformation(double s, double deg) {
        return transformation(new float[]{centerX, centerY, centerZ}, s, deg);
    }

    /**
     * Трансформация с размером {@code s} и углом {@code deg} (вокруг локальной Z), масштаб и поворот -
     * вокруг центра картинки {@code center}: T = -R·(S·c), так что центр остаётся в точке сущности.
     */
    static Transformation transformation(float[] center, double s, double deg) {
        float scale = (float) s;
        float a = (float) Math.toRadians(deg);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        float cx = center[0] * scale, cy = center[1] * scale, cz = center[2] * scale;
        Vector3f translation = new Vector3f(-(cx * cos - cy * sin), -(cx * sin + cy * cos), -cz);
        return new Transformation(translation, new Quaternionf().rotateZ(a), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /**
     * Послать клиенту цель: просчитать тело на {@code steps} тиков вперёд (не дальше удара о блок)
     * и задать позицию и вид к этому моменту.
     */
    void keyframe(int keyframeTicks, ParticleSim.Solid solid) {
        ParticleEffect effect = emission.effect;
        // догнать текущий возраст (первый кадр идёт через тик после появления)
        while (simAge < age) { ParticleSim.step(body, effect.motion(), solid); simAge++; }

        int steps = Math.min(keyframeTicks, life - age);
        // поворот за кадр должен быть меньше 180°, иначе клиент повернёт в обратную сторону
        if (spin != 0) steps = Math.min(steps, Math.max(1, (int) (170 / Math.abs(spin))));
        if (steps <= 0) return;

        boolean wasResting = body.resting;
        double px = body.x, py = body.y, pz = body.z;
        if (!wasResting) steps = ParticleSim.advance(body, effect.motion(), solid, steps);
        simAge = age + steps;
        nextKeyframe = simAge;

        if (!wasResting && (body.x != px || body.y != py || body.z != pz)) {
            World world = entity.getWorld();
            entity.teleport(new Location(world, body.x, body.y, body.z, 0, 0));
        }

        double s = ParticleSim.scaleAt(effect.animation(), startScale, simAge, life);
        double r = ParticleSim.rotationAt(startRotation, spin, simAge);
        if (s != sentScale || r != sentRotation) {
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(steps);
            entity.setTransformation(transformation(s, r));
            sentScale = s;
            sentRotation = r;
        }
    }

    void kill() {
        if (dead) return;
        dead = true;
        if (entity.isValid()) entity.remove();
    }
}

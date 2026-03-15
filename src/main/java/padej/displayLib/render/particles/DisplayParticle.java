package padej.displayLib.render.particles;

import padej.displayLib.DisplayLib;

public interface DisplayParticle {
    void update();

    default void spawn() {
        DisplayLib.DISPLAY_PARTICLES.add(this);
    }

    default void remove() {
        DisplayLib.DISPLAY_PARTICLES.remove(this);
    }
}
package padej.displayLib;

import padej.displayLib.render.particles.DisplayParticle;
import padej.displayLib.render.shapes.Highlight;
import padej.displayLib.test_events.*;
import padej.displayLib.ui.UIManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public final class DisplayLib extends JavaPlugin {

    public static final List<DisplayParticle> DISPLAY_PARTICLES = new ArrayList<>();

    @Override
    public void onEnable() {
        UIManager.getInstance();

        getServer().getPluginManager().registerEvents(new ApplyHighlightToBlockTest(), this);
        getServer().getPluginManager().registerEvents(new CreateDisplayParticleFirstTest(), this);
        getServer().getPluginManager().registerEvents(new CreateDisplayParticleSecondTest(), this);
        getServer().getPluginManager().registerEvents(new CreateDisplayParticleThirdTest(), this);
        getServer().getPluginManager().registerEvents(new CreateTestUI(), this);
        getServer().getPluginManager().registerEvents(new GizmoTest(), this);
        getServer().getPluginManager().registerEvents(new PointDetectFirstTest(), this);
        getServer().getPluginManager().registerEvents(new PointDetectSecondTest(), this);
        getServer().getPluginManager().registerEvents(new RotationRelativeToCenterPointTest(), this);
        getServer().getPluginManager().registerEvents(new SmoothMotionAndRotationTest(), this);

        Highlight.removeAllSelections();
        Highlight.startColorUpdateTask();
        startParticleTask();
    }

    @Override
    public void onDisable() {
        UIManager manager = UIManager.getInstance();
        if (manager.hasActiveScreens()) {
            getLogger().info("Cleaning up active UI screens...");
            manager.cleanup();
        }

        DISPLAY_PARTICLES.clear();
    }

    public static JavaPlugin getInstance() {
        return JavaPlugin.getPlugin(DisplayLib.class);
    }

    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (DISPLAY_PARTICLES.isEmpty()) return;
                for (DisplayParticle displayParticle : new ArrayList<>(DISPLAY_PARTICLES)) {
                    displayParticle.update();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }
}

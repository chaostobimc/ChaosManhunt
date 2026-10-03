package de.chaostobimc.chaosmanhunt.task;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.ManhuntConfig;
import de.chaostobimc.chaosmanhunt.game.ManhuntGame;
import de.chaostobimc.chaosmanhunt.game.RespawnLock;
import de.chaostobimc.chaosmanhunt.util.Text;
import de.chaostobimc.chaosmanhunt.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Zeichnet den Actionbar-Timer (inkl. Hex-Gradient und Respawn-Countdown).
 *
 * <p>Der Task wird vom {@link ManhuntGame} gestartet und beim Stoppen des
 * Events zuverlaessig beendet.</p>
 */
public final class ActionBarTask implements Runnable {

    private final ChaosManhunt plugin;
    private double phase;

    public ActionBarTask(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        final ManhuntGame game = this.plugin.game();
        if (!game.isRunning()) {
            return;
        }

        final ManhuntConfig config = this.plugin.config();
        if (!config.actionBarEnabled()) {
            return;
        }

        if (config.gradientAnimated()) {
            this.phase += config.gradientSpeed();
            if (this.phase >= 1.0D) {
                this.phase -= Math.floor(this.phase);
            }
        }

        final String timeText = game.elapsedText();
        final Component timer = config.gradientEnabled()
                ? config.timerGradient().render(timeText, config.gradientAnimated() ? this.phase : 0.0D)
                : Text.parse("&f" + timeText);

        final Player runner = game.runner();
        final String runnerName = runner != null ? runner.getName() : "—";
        final String hunterCount = String.valueOf(game.hunterCount());
        final String state = game.state().display();

        for (final Player viewer : game.actionBarViewers()) {
            final RespawnLock lock = game.lock(viewer.getUniqueId());
            final Component message;

            if (lock != null && !lock.expired()) {
                message = this.plugin.messages().actionBarTemplate(config.actionBarLockFormat(), timer,
                        "lock", TimeFormat.format(lock.remaining(), false),
                        "runner", runnerName,
                        "hunters", hunterCount,
                        "state", state);
            } else {
                message = this.plugin.messages().actionBarTemplate(config.actionBarFormat(), timer,
                        "runner", runnerName,
                        "hunters", hunterCount,
                        "state", state);
            }

            viewer.sendActionBar(message);
        }
    }
}

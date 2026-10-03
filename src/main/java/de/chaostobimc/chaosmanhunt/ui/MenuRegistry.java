package de.chaostobimc.chaosmanhunt.ui;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Haelt geoeffnete Admin-Panels aktuell und raeumt sie beim Schliessen auf.
 *
 * <p>Der Refresh-Task laeuft nur, solange mindestens ein Panel geoeffnet ist,
 * und wird bei Plugin-Deaktivierung gestoppt.</p>
 */
public final class MenuRegistry {

    private final ChaosManhunt plugin;
    private final Set<AdminMenu> tracked = new LinkedHashSet<>();
    private BukkitTask refreshTask;

    public MenuRegistry(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
    }

    /** Registriert ein geoeffnetes Panel. */
    public void track(final @NotNull AdminMenu menu) {
        this.tracked.add(menu);
        ensureTask();
    }

    /** Entfernt ein geschlossenes Panel. */
    public void untrack(final @NotNull AdminMenu menu) {
        this.tracked.remove(menu);
    }

    private void ensureTask() {
        if (this.refreshTask != null && !this.refreshTask.isCancelled()) {
            return;
        }
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::refresh, 20L, 20L);
    }

    private void refresh() {
        if (this.tracked.isEmpty()) {
            stopTask();
            return;
        }

        this.tracked.removeIf(menu -> {
            if (!menu.isViewing()) {
                return true;
            }
            menu.refresh();
            return false;
        });

        if (this.tracked.isEmpty()) {
            stopTask();
        }
    }

    private void stopTask() {
        if (this.refreshTask != null) {
            this.refreshTask.cancel();
            this.refreshTask = null;
        }
    }

    /** Beim Deaktivieren des Plugins aufrufen. */
    public void shutdown() {
        stopTask();
        this.tracked.clear();
    }
}

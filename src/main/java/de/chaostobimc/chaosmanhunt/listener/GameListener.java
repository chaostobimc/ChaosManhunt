package de.chaostobimc.chaosmanhunt.listener;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.game.RespawnLock;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Spielereignisse rund um Tod, Respawn, Join, Quit und Dimensionswechsel.
 */
public final class GameListener implements Listener {

    private final ChaosManhunt plugin;

    public GameListener(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDeath(final @NotNull PlayerDeathEvent event) {
        this.plugin.game().handleDeath(event.getEntity(), event);
    }

    /**
     * Sicherheitsnetz: sollte ein Hunter trotz Sperre respawnen (z. B. weil ein
     * anderes Plugin den Tod erzwungen hat), wird er bis zum Ablauf der Sperre
     * wieder in den Zuschauer-Modus gesetzt.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final @NotNull PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final RespawnLock lock = this.plugin.game().lock(player.getUniqueId());
        if (lock == null || lock.expired()) {
            return;
        }

        final Location deathLocation = lock.deathLocation();
        if (deathLocation != null) {
            event.setRespawnLocation(deathLocation);
        }

        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && this.plugin.game().isLocked(player.getUniqueId())) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    @EventHandler
    public void onJoin(final @NotNull PlayerJoinEvent event) {
        this.plugin.game().handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final @NotNull PlayerQuitEvent event) {
        this.plugin.game().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(final @NotNull PlayerChangedWorldEvent event) {
        this.plugin.game().handleWorldChange(event.getPlayer());
    }
}

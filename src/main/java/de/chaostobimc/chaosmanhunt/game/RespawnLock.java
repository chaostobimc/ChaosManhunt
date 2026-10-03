package de.chaostobimc.chaosmanhunt.game;

import java.time.Duration;
import java.util.UUID;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Respawn-Sperre eines Hunters.
 *
 * <p>Die Sperre wird beim Tod gesetzt und laeuft zeitbasiert ab. Dadurch
 * uebersteht sie auch einen Server-Neustart des Clients (Reconnect) korrekt,
 * solange das Plugin laeuft.</p>
 */
public final class RespawnLock {

    private final UUID playerId;
    private final long startedAtMillis;
    private final long unlockAtMillis;
    private final Location deathLocation;

    public RespawnLock(final @NotNull UUID playerId, final long startedAtMillis, final long lockMillis,
                       final @Nullable Location deathLocation) {
        this.playerId = playerId;
        this.startedAtMillis = startedAtMillis;
        this.unlockAtMillis = startedAtMillis + Math.max(0L, lockMillis);
        this.deathLocation = deathLocation == null ? null : deathLocation.clone();
    }

    public @NotNull UUID playerId() {
        return this.playerId;
    }

    public long startedAtMillis() {
        return this.startedAtMillis;
    }

    public long unlockAtMillis() {
        return this.unlockAtMillis;
    }

    public @Nullable Location deathLocation() {
        return this.deathLocation == null ? null : this.deathLocation.clone();
    }

    /** Verbleibende Sperrzeit in Millisekunden (nie negativ). */
    public long remainingMillis() {
        return Math.max(0L, this.unlockAtMillis - System.currentTimeMillis());
    }

    /** Verbleibende Sperrzeit als Dauer. */
    public @NotNull Duration remaining() {
        return Duration.ofMillis(remainingMillis());
    }

    /** Ist die Sperre abgelaufen? */
    public boolean expired() {
        return remainingMillis() <= 0L;
    }
}

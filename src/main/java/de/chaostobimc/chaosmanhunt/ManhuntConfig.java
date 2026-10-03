package de.chaostobimc.chaosmanhunt;

import de.chaostobimc.chaosmanhunt.util.Gradient;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Typisierter Zugriff auf die {@code config.yml}.
 *
 * <p>Alle Werte werden bei jedem Zugriff aus der Datei gelesen, damit
 * {@code /mh reload} sofort wirkt. Der Timer-Gradient wird gecacht und beim
 * Reload verworfen.</p>
 */
public final class ManhuntConfig {

    /** Wohin Hunter nach der Respawn-Sperre zurueckkehren. */
    public enum RespawnStrategy {
        WORLD_SPAWN,
        DEATH_LOCATION,
        BED
    }

    /** Verhalten des Trackers bei unterschiedlichen Dimensionen. */
    public enum CrossDimensionMode {
        FROZEN,
        PORTAL_SCALED
    }

    /** Empfaengerkreis der Actionbar. */
    public enum ActionBarViewers {
        ALL,
        PARTICIPANTS
    }

    private final ChaosManhunt plugin;
    private volatile Gradient timerGradient;

    ManhuntConfig(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
    }

    private @NotNull FileConfiguration yaml() {
        return this.plugin.getConfig();
    }

    /* ------------------------------------------------------------------ Spiel */

    public int countdownSeconds() {
        return clamp(yaml().getInt("game.countdown-seconds", 5), 0, 300);
    }

    public int respawnLockSeconds() {
        return clamp(yaml().getInt("game.respawn-lock-seconds", 120), 1, 3600);
    }

    public boolean autoHuntersOnJoin() {
        return yaml().getBoolean("game.auto-hunters-on-join", true);
    }

    public boolean endGameWhenRunnerQuits() {
        return yaml().getBoolean("game.end-game-when-runner-quits", false);
    }

    public boolean restoreGamemodeOnStop() {
        return yaml().getBoolean("game.restore-gamemode-on-stop", true);
    }

    public @NotNull String spawnWorldName() {
        return yaml().getString("game.spawn-world", "");
    }

    /* ------------------------------------------------------------------ Hunter */

    public @NotNull RespawnStrategy respawnStrategy() {
        return enumValue("hunter.respawn-strategy", RespawnStrategy.WORLD_SPAWN);
    }

    public boolean dropInventoryOnDeath() {
        return yaml().getBoolean("hunter.drop-inventory", true);
    }

    public boolean dropExperienceOnDeath() {
        return yaml().getBoolean("hunter.drop-experience", true);
    }

    public boolean clearEffectsOnDeath() {
        return yaml().getBoolean("hunter.clear-effects", true);
    }

    public boolean showVanillaDeathMessage() {
        return yaml().getBoolean("hunter.show-vanilla-death-message", true);
    }

    /* ----------------------------------------------------------------- Tracker */

    public @NotNull String trackerName() {
        return yaml().getString("tracker.name", "&c&lGorgii-Tracker");
    }

    public @NotNull List<String> trackerLore() {
        return yaml().getStringList("tracker.lore");
    }

    public boolean giveTrackerOnStart() {
        return yaml().getBoolean("tracker.give-on-start", true);
    }

    public boolean giveTrackerAfterRespawn() {
        return yaml().getBoolean("tracker.give-after-respawn", true);
    }

    public boolean removeTrackerOnStop() {
        return yaml().getBoolean("tracker.remove-on-stop", true);
    }

    public int trackerUpdateInterval() {
        return clamp(yaml().getInt("tracker.update-interval-ticks", 5), 1, 100);
    }

    public int trackerLoreUpdateInterval() {
        return clamp(yaml().getInt("tracker.lore-update-interval-ticks", 20), 0, 600);
    }

    public @NotNull CrossDimensionMode crossDimensionMode() {
        return enumValue("tracker.cross-dimension-mode", CrossDimensionMode.FROZEN);
    }

    public long trackerClickCooldownMillis() {
        return clamp(yaml().getLong("tracker.click-cooldown-millis", 750L), 0L, 10_000L);
    }

    public boolean protectTrackerFromLoss() {
        return yaml().getBoolean("tracker.protect-from-loss", true);
    }

    /* --------------------------------------------------------------- Actionbar */

    public boolean actionBarEnabled() {
        return yaml().getBoolean("actionbar.enabled", true);
    }

    public @NotNull ActionBarViewers actionBarViewers() {
        return enumValue("actionbar.viewers", ActionBarViewers.PARTICIPANTS);
    }

    public int actionBarInterval() {
        return clamp(yaml().getInt("actionbar.interval-ticks", 1), 1, 100);
    }

    public @NotNull String actionBarFormat() {
        return yaml().getString("actionbar.format", "%time% &8▪ &7Runner: &c%runner% &8▪ &7Hunter: &f%hunters%");
    }

    public @NotNull String actionBarLockFormat() {
        return yaml().getString("actionbar.lock-format", "&c&l☠ &r&7Respawn in &f%lock% &8▪ &7%time%");
    }

    public boolean gradientEnabled() {
        return yaml().getBoolean("actionbar.gradient.enabled", true);
    }

    public @NotNull List<String> gradientColors() {
        return yaml().getStringList("actionbar.gradient.colors");
    }

    public boolean gradientAnimated() {
        return yaml().getBoolean("actionbar.gradient.animate", true);
    }

    public double gradientSpeed() {
        return clampDouble(yaml().getDouble("actionbar.gradient.speed", 0.015D), 0.0D, 1.0D);
    }

    /** Gecachter Gradient des Actionbar-Timers. */
    public @NotNull Gradient timerGradient() {
        Gradient cached = this.timerGradient;
        if (cached == null) {
            cached = Gradient.of(gradientColors());
            this.timerGradient = cached;
        }
        return cached;
    }

    /** Cache nach einem Reload verwerfen. */
    public void invalidate() {
        this.timerGradient = null;
    }

    /* ------------------------------------------------------------------ Timer */

    public boolean padZeroUnits() {
        return yaml().getBoolean("timer.pad-zero-units", true);
    }

    /* ------------------------------------------------------------------- Menue */

    public @NotNull String menuTitle() {
        return yaml().getString("menu.title", "&8Manhunt &7▪ &fAdmin-Panel");
    }

    public @NotNull String runnerMenuTitle() {
        return yaml().getString("menu.runner-title", "&8Manhunt &7▪ &fRunner waehlen");
    }

    public @NotNull String hunterMenuTitle() {
        return yaml().getString("menu.hunter-title", "&8Manhunt &7▪ &fHunter verwalten");
    }

    /* ------------------------------------------------------------------ Sounds */

    public boolean soundsEnabled() {
        return yaml().getBoolean("sounds.enabled", true);
    }

    public float soundVolume() {
        return (float) clampDouble(yaml().getDouble("sounds.volume", 1.0D), 0.0D, 4.0D);
    }

    public float soundPitch() {
        return (float) clampDouble(yaml().getDouble("sounds.pitch", 1.0D), 0.5D, 2.0D);
    }

    /* ------------------------------------------------------------------- Helfer */

    private <E extends Enum<E>> @NotNull E enumValue(final @NotNull String path, final @NotNull E fallback) {
        final String raw = yaml().getString(path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(),
                    raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (final IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Ungueltiger Wert fuer '" + path + "': '" + raw
                    + "' - es wird " + fallback.name() + " verwendet.");
            return fallback;
        }
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(final long value, final long min, final long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }
}

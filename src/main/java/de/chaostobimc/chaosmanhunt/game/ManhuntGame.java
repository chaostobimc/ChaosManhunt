package de.chaostobimc.chaosmanhunt.game;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.ManhuntConfig;
import de.chaostobimc.chaosmanhunt.task.ActionBarTask;
import de.chaostobimc.chaosmanhunt.util.Nav;
import de.chaostobimc.chaosmanhunt.util.Text;
import de.chaostobimc.chaosmanhunt.util.TimeFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Herzstueck des Plugins: Zustandsmaschine, Rollen, Respawn-Sperren und
 * Aufgabenverwaltung des Manhunt-Events.
 *
 * <p>Alle hier gestarteten Tasks werden gesammelt und bei Stop, Server-Stop und
 * Plugin-Deaktivierung zuverlaessig beendet - es laufen keine Timer weiter.</p>
 */
public final class ManhuntGame {

    private static final Title.Times TITLE_TIMES = Title.Times.times(
            Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(400));

    private final ChaosManhunt plugin;

    private final Map<UUID, RespawnLock> locks = new LinkedHashMap<>();
    private final Set<UUID> hunters = new LinkedHashSet<>();
    private final Set<UUID> excluded = new LinkedHashSet<>();
    private final Map<UUID, GameMode> storedModes = new LinkedHashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>(4);

    private GameState state = GameState.IDLE;
    private UUID runnerId;
    private long startedAtMillis;
    private long stoppedAtMillis;
    private int countdownRemaining;
    private GameEndReason lastEndReason = GameEndReason.ADMIN_STOP;

    public ManhuntGame(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------------ Status */

    public @NotNull GameState state() {
        return this.state;
    }

    public boolean isIdle() {
        return this.state == GameState.IDLE;
    }

    public boolean isRunning() {
        return this.state == GameState.RUNNING;
    }

    public boolean isActive() {
        return this.state != GameState.IDLE;
    }

    public @Nullable GameEndReason lastEndReason() {
        return this.state == GameState.IDLE ? this.lastEndReason : null;
    }

    public long stoppedAtMillis() {
        return this.stoppedAtMillis;
    }

    public int countdownRemaining() {
        return this.state == GameState.COUNTDOWN ? this.countdownRemaining : 0;
    }

    public @Nullable UUID runnerId() {
        return this.runnerId;
    }

    /** Der Runner, falls online. */
    public @Nullable Player runner() {
        return this.runnerId == null ? null : Bukkit.getPlayer(this.runnerId);
    }

    public @NotNull String runnerName() {
        final Player runner = runner();
        return runner != null ? runner.getName() : "—";
    }

    public boolean isRunner(final @Nullable UUID uniqueId) {
        return uniqueId != null && uniqueId.equals(this.runnerId);
    }

    public boolean isRunner(final @Nullable Player player) {
        return player != null && isRunner(player.getUniqueId());
    }

    public boolean isHunter(final @Nullable UUID uniqueId) {
        return uniqueId != null && this.hunters.contains(uniqueId);
    }

    public boolean isHunter(final @Nullable Player player) {
        return player != null && isHunter(player.getUniqueId());
    }

    public boolean isParticipant(final @Nullable Player player) {
        return player != null && (isRunner(player) || isHunter(player));
    }

    public boolean isExcluded(final @Nullable UUID uniqueId) {
        return uniqueId != null && this.excluded.contains(uniqueId);
    }

    public @NotNull Set<UUID> hunterIds() {
        return Collections.unmodifiableSet(this.hunters);
    }

    /** Alle Hunter, die gerade online sind. */
    public @NotNull List<Player> onlineHunters() {
        final List<Player> online = new ArrayList<>(this.hunters.size());
        for (final UUID uniqueId : this.hunters) {
            final Player player = Bukkit.getPlayer(uniqueId);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    /** Anzahl der Hunter, die gerade online sind. */
    public int hunterCount() {
        return onlineHunters().size();
    }

    /** Runner + Hunter (online). */
    public @NotNull List<Player> onlineParticipants() {
        final List<Player> participants = new ArrayList<>(onlineHunters());
        final Player runner = runner();
        if (runner != null && runner.isOnline()) {
            participants.add(runner);
        }
        return participants;
    }

    /** Empfaenger der Actionbar entsprechend der Konfiguration. */
    public @NotNull List<Player> actionBarViewers() {
        if (this.plugin.config().actionBarViewers() == ManhuntConfig.ActionBarViewers.ALL) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }
        return onlineParticipants();
    }

    /** Laufzeit seit Event-Start. */
    public @NotNull Duration elapsed() {
        if (this.state == GameState.IDLE) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.max(0L, System.currentTimeMillis() - this.startedAtMillis));
    }

    /** Laufzeit als BastiGHG-formatierter Text (z. B. {@code 4m 12s}). */
    public @NotNull String elapsedText() {
        return TimeFormat.format(elapsed(), this.plugin.config().padZeroUnits());
    }

    public @Nullable RespawnLock lock(final @Nullable UUID uniqueId) {
        return uniqueId == null ? null : this.locks.get(uniqueId);
    }

    public boolean isLocked(final @Nullable UUID uniqueId) {
        return uniqueId != null && this.locks.containsKey(uniqueId);
    }

    /* ------------------------------------------------------------ Spielstart */

    /**
     * Startet das Event (optional mit Countdown).
     *
     * @return {@code true}, wenn das Event gestartet bzw. der Countdown gestartet wurde
     */
    public boolean start(final @Nullable Player runner, final @NotNull CommandSender initiator) {
        if (isActive()) {
            this.plugin.messages().send(initiator, "game.already-running");
            return false;
        }

        if (runner == null || !runner.isOnline()) {
            this.plugin.messages().send(initiator, "game.no-runner");
            return false;
        }

        this.runnerId = runner.getUniqueId();
        this.excluded.remove(this.runnerId);
        rebuildHunters();

        if (this.hunters.isEmpty()) {
            this.plugin.messages().send(initiator, "game.not-enough-players");
            reset();
            return false;
        }

        this.storedModes.clear();
        this.locks.clear();

        final int countdown = this.plugin.config().countdownSeconds();
        if (countdown > 0) {
            this.state = GameState.COUNTDOWN;
            this.countdownRemaining = countdown;
            this.tasks.add(Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickCountdown, 0L, 20L));
        } else {
            beginRun();
        }
        return true;
    }

    private void tickCountdown() {
        if (this.state != GameState.COUNTDOWN) {
            return;
        }
        if (this.countdownRemaining <= 0) {
            beginRun();
            return;
        }

        this.plugin.messages().broadcast("game.countdown", "seconds", String.valueOf(this.countdownRemaining));
        final Component title = this.plugin.messages().component("game.countdown-title",
                "seconds", String.valueOf(this.countdownRemaining));
        final Component subtitle = this.plugin.messages().component("game.countdown-subtitle");
        for (final Player participant : onlineParticipants()) {
            participant.showTitle(Title.title(title, subtitle, TITLE_TIMES));
            sound(participant, Sound.BLOCK_NOTE_BLOCK_PLING, 1.4F);
        }
        this.countdownRemaining--;
    }

    private void beginRun() {
        cancelTasks();
        this.state = GameState.RUNNING;
        this.startedAtMillis = System.currentTimeMillis();

        this.plugin.messages().broadcast("game.started",
                "runner", runnerName(), "hunters", String.valueOf(hunterCount()));

        final Component title = this.plugin.messages().component("game.started-title", "runner", runnerName());
        final Component subtitle = this.plugin.messages().component("game.started-subtitle", "runner", runnerName());
        for (final Player participant : onlineParticipants()) {
            participant.showTitle(Title.title(title, subtitle, TITLE_TIMES));
            sound(participant, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6F);
        }

        if (this.plugin.config().giveTrackerOnStart()) {
            for (final Player hunter : onlineHunters()) {
                this.plugin.tracker().give(hunter);
            }
        }

        this.tasks.add(Bukkit.getScheduler().runTaskTimer(this.plugin, new ActionBarTask(this.plugin),
                0L, this.plugin.config().actionBarInterval()));
        this.tasks.add(Bukkit.getScheduler().runTaskTimer(this.plugin, this.plugin.tracker()::tick,
                10L, this.plugin.config().trackerUpdateInterval()));
        this.tasks.add(Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickLocks, 20L, 10L));

        this.plugin.getLogger().info("Manhunt gestartet - Runner: " + runnerName()
                + ", Hunter: " + hunterCount() + ".");
    }

    /* -------------------------------------------------------------- Spielende */

    public void stop(final @NotNull GameEndReason reason) {
        stop(reason, true);
    }

    /**
     * Beendet das Event und raeumt vollstaendig auf.
     *
     * @param reason  Grund (bestimmt die Chat-Nachricht)
     * @param notify  {@code false}, wenn die Meldung bereits gesendet wurde
     */
    public void stop(final @NotNull GameEndReason reason, final boolean notify) {
        if (this.state == GameState.IDLE) {
            return;
        }

        cancelTasks();

        final boolean wasRunning = this.state == GameState.RUNNING;
        this.state = GameState.IDLE;
        this.stoppedAtMillis = System.currentTimeMillis();
        this.lastEndReason = reason;

        // Respawn-Sperren aufloesen und Spielmodi wiederherstellen
        this.locks.clear();
        restoreGameModes();

        // Tracker entfernen
        if (this.plugin.config().removeTrackerOnStop()) {
            for (final Player participant : onlineParticipants()) {
                this.plugin.tracker().remove(participant);
            }
        }

        // Actionbar leeren
        for (final Player viewer : actionBarViewers()) {
            viewer.sendActionBar(Component.empty());
        }

        if (notify) {
            if (reason == GameEndReason.PLUGIN_DISABLE) {
                this.plugin.messages().broadcast("plugin.shutting-down");
            } else if (reason.broadcastStop()) {
                this.plugin.messages().broadcast("game.stopped", "reason", reason.display());
            }
        }

        if (wasRunning) {
            this.plugin.getLogger().info("Manhunt beendet (" + reason.display() + ") - Laufzeit: " + elapsedText() + ".");
        }

        reset();
    }

    private void reset() {
        this.runnerId = null;
        this.hunters.clear();
        this.storedModes.clear();
        this.countdownRemaining = 0;
    }

    /** Wird beim Deaktivieren des Plugins aufgerufen. */
    public void shutdown() {
        if (isActive()) {
            stop(GameEndReason.PLUGIN_DISABLE, true);
        } else {
            cancelTasks();
        }
        this.locks.clear();
        this.hunters.clear();
        this.storedModes.clear();
    }

    private void restoreGameModes() {
        for (final Map.Entry<UUID, GameMode> entry : this.storedModes.entrySet()) {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            final GameMode target = this.plugin.config().restoreGamemodeOnStop() && entry.getValue() != null
                    ? entry.getValue()
                    : GameMode.SURVIVAL;
            if (player.getGameMode() != target) {
                player.setGameMode(target);
            }
        }
        this.storedModes.clear();
    }

    private void cancelTasks() {
        for (final BukkitTask task : this.tasks) {
            if (task != null) {
                task.cancel();
            }
        }
        this.tasks.clear();
    }

    /* ------------------------------------------------------------------- Tod */

    /** Wird vom {@code PlayerDeathEvent} aufgerufen. */
    public void handleDeath(final @NotNull Player player, final @NotNull PlayerDeathEvent event) {
        // Wurde der Tod bereits von einem anderen Plugin abgebrochen, bleibt der
        // Spieler am Leben - dann greift die Manhunt-Logik nicht.
        if (event.isCancelled()) {
            return;
        }

        if (this.state == GameState.COUNTDOWN && isRunner(player.getUniqueId())) {
            this.plugin.messages().broadcast("game.countdown-cancelled");
            stop(GameEndReason.ABORTED, false);
            return;
        }

        if (this.state != GameState.RUNNING) {
            return;
        }

        if (isRunner(player.getUniqueId())) {
            handleRunnerDeath(player, event);
        } else if (isHunter(player.getUniqueId())) {
            handleHunterDeath(player, event);
        }
    }

    private void handleRunnerDeath(final @NotNull Player runner, final @NotNull PlayerDeathEvent event) {
        this.plugin.messages().broadcast("game.win-runner-died", "runner", runner.getName(),
                "cause", event.deathMessage() == null ? "—" : Text.toPlain(event.deathMessage()));

        final Component title = this.plugin.messages().component("game.win-title", "runner", runner.getName());
        final Component subtitle = this.plugin.messages().component("game.win-subtitle", "runner", runner.getName());
        for (final Player viewer : actionBarViewers()) {
            viewer.showTitle(Title.title(title, subtitle, TITLE_TIMES));
            sound(viewer, Sound.ENTITY_WITHER_SPAWN, 0.5F);
        }

        stop(GameEndReason.RUNNER_DIED, false);
    }

    private void handleHunterDeath(final @NotNull Player hunter, final @NotNull PlayerDeathEvent event) {
        final ManhuntConfig config = this.plugin.config();
        final Location deathLocation = hunter.getLocation().clone();

        // Der Vanilla-Tod wird abgebrochen (Paper unterstuetzt das offiziell) -
        // dadurch gibt es keinen Death-Screen und keinen Client-Desync.
        event.setCancelled(true);
        event.setKeepInventory(false);
        event.setKeepLevel(false);
        event.setShowDeathMessages(config.showVanillaDeathMessage());

        // Weil der Tod abgebrochen wird, verschickt der Server selbst keine
        // Todesmeldung mehr - ist sie erwuenscht, wird sie hier nachgeholt.
        if (config.showVanillaDeathMessage() && event.deathMessage() != null) {
            Bukkit.broadcast(event.deathMessage());
        }

        final int droppedExp = event.getDroppedExp();
        dropInventory(hunter, deathLocation);
        if (config.dropExperienceOnDeath() && droppedExp > 0) {
            dropExperience(deathLocation, droppedExp);
        }
        hunter.setLevel(0);
        hunter.setExp(0.0F);
        hunter.setTotalExperience(0);
        if (config.clearEffectsOnDeath()) {
            clearEffects(hunter);
        }

        // Respawn-Sperre setzen
        final long lockMillis = config.respawnLockSeconds() * 1000L;
        final RespawnLock lock = new RespawnLock(hunter.getUniqueId(), System.currentTimeMillis(), lockMillis, deathLocation);
        this.locks.put(hunter.getUniqueId(), lock);
        this.storedModes.putIfAbsent(hunter.getUniqueId(), hunter.getGameMode());

        // Tracker einsammeln - er geht nicht verloren, sondern wird neu ausgegeben
        this.plugin.tracker().remove(hunter);
        hunter.setGameMode(GameMode.SPECTATOR);

        final String lockText = TimeFormat.format(lock.remaining(), config.padZeroUnits());
        this.plugin.messages().broadcast("hunter.death-info", "player", hunter.getName(), "lock", lockText);
        hunter.showTitle(Title.title(
                this.plugin.messages().component("hunter.death-title", "lock", lockText),
                this.plugin.messages().component("hunter.death-subtitle", "lock", lockText),
                TITLE_TIMES));

        // Vanilla-Todessound wird vom abgebrochenen Event nicht abgespielt - hier nachholen.
        final Sound deathSound = event.getDeathSound();
        if (deathSound != null) {
            hunter.getWorld().playSound(hunter.getLocation(), deathSound,
                    event.getDeathSoundVolume(), event.getDeathSoundPitch());
        } else {
            sound(hunter, Sound.ENTITY_PLAYER_DEATH, 1.0F);
        }
        sound(hunter, Sound.BLOCK_BEACON_DEACTIVATE, 1.0F);
    }

    private void dropInventory(final @NotNull Player player, final @NotNull Location location) {
        final PlayerInventory inventory = player.getInventory();
        final boolean drop = this.plugin.config().dropInventoryOnDeath();
        final World world = location.getWorld();

        for (final ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir() || this.plugin.tracker().isTracker(item)) {
                continue;
            }
            if (drop && world != null) {
                world.dropItemNaturally(location, item.clone());
            }
        }
        inventory.clear();

        final ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir() && !this.plugin.tracker().isTracker(cursor)) {
            if (drop && world != null) {
                world.dropItemNaturally(location, cursor.clone());
            }
        }
        player.setItemOnCursor(null);
    }

    private void dropExperience(final @NotNull Location location, final int amount) {
        final World world = location.getWorld();
        if (world == null || amount <= 0) {
            return;
        }
        world.spawn(location, ExperienceOrb.class, orb -> orb.setExperience(amount));
    }

    private void clearEffects(final @NotNull Player player) {
        for (final PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    /* --------------------------------------------------------- Respawn-Sperre */

    private void tickLocks() {
        if (this.locks.isEmpty()) {
            return;
        }
        for (final RespawnLock lock : List.copyOf(this.locks.values())) {
            if (lock.expired()) {
                finishLock(lock);
            }
        }
    }

    /** Beendet eine Respawn-Sperre (regulaerer Ablauf oder Sofort-Freigabe). */
    public void finishLock(final @NotNull RespawnLock lock) {
        this.locks.remove(lock.playerId());

        final Player player = Bukkit.getPlayer(lock.playerId());
        if (player == null || !player.isOnline()) {
            return;
        }

        final GameMode stored = this.storedModes.remove(lock.playerId());
        player.setGameMode(stored != null ? stored : GameMode.SURVIVAL);

        final Location destination = resolveRespawnLocation(player, lock);
        if (destination != null) {
            player.teleport(destination);
        }
        resetPlayerState(player);

        if (this.plugin.config().giveTrackerAfterRespawn()) {
            this.plugin.tracker().give(player);
        }

        this.plugin.messages().send(player, "hunter.respawned", "location", respawnLocationLabel());
        player.showTitle(Title.title(
                this.plugin.messages().component("hunter.respawn-title"),
                this.plugin.messages().component("hunter.respawn-subtitle"),
                TITLE_TIMES));
        sound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.2F);
    }

    private @Nullable Location resolveRespawnLocation(final @NotNull Player player, final @NotNull RespawnLock lock) {
        final ManhuntConfig config = this.plugin.config();
        switch (config.respawnStrategy()) {
            case DEATH_LOCATION -> {
                final Location death = lock.deathLocation();
                if (death != null && death.getWorld() != null) {
                    return death;
                }
            }
            case BED -> {
                final Location bed = player.getRespawnLocation();
                if (bed != null && bed.getWorld() != null) {
                    return bed;
                }
            }
            default -> {
                // WORLD_SPAWN
            }
        }
        return worldSpawn();
    }

    private @Nullable Location worldSpawn() {
        World world = null;
        final String configured = this.plugin.config().spawnWorldName();
        if (configured != null && !configured.isBlank()) {
            world = Bukkit.getWorld(configured.trim());
            if (world == null) {
                this.plugin.getLogger().warning("Die Welt '" + configured + "' aus game.spawn-world existiert nicht - "
                        + "es wird die Hauptwelt verwendet.");
            }
        }
        if (world == null) {
            final List<World> worlds = Bukkit.getWorlds();
            world = worlds.isEmpty() ? null : worlds.get(0);
        }
        return world == null ? null : world.getSpawnLocation().clone();
    }

    private @NotNull String respawnLocationLabel() {
        final String key = switch (this.plugin.config().respawnStrategy()) {
            case DEATH_LOCATION -> "hunter.respawn-location-death";
            case BED -> "hunter.respawn-location-bed";
            default -> "hunter.respawn-location-spawn";
        };
        return Text.toPlain(this.plugin.messages().component(key));
    }

    private void resetPlayerState(final @NotNull Player player) {
        player.setHealth(maxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(5.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setRemainingAir(player.getMaximumAir());
        player.setInvulnerable(false);
        player.setNoDamageTicks(20);
    }

    private double maxHealth(final @NotNull Player player) {
        final AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0D : attribute.getValue();
    }

    /* ------------------------------------------------- Verbindung / Dimension */

    /** Wird beim Betreten des Servers aufgerufen. */
    public void handleJoin(final @NotNull Player player) {
        if (!isActive()) {
            return;
        }

        if (isRunner(player.getUniqueId())) {
            this.plugin.messages().send(player, "game.you-are-runner");
            return;
        }

        final RespawnLock lock = this.locks.get(player.getUniqueId());
        if (lock != null) {
            if (lock.expired()) {
                finishLock(lock);
                return;
            }
            player.setGameMode(GameMode.SPECTATOR);
            this.plugin.tracker().remove(player);
            this.plugin.messages().send(player, "hunter.locked-rejoin",
                    "lock", TimeFormat.format(lock.remaining(), this.plugin.config().padZeroUnits()));
            return;
        }

        if (isHunter(player.getUniqueId())) {
            if (this.plugin.config().giveTrackerOnStart()) {
                this.plugin.tracker().give(player);
            }
            return;
        }

        if (this.plugin.config().autoHuntersOnJoin() && !isExcluded(player.getUniqueId())) {
            this.hunters.add(player.getUniqueId());
            this.plugin.messages().broadcast("hunter.assigned", "player", player.getName());
            this.plugin.messages().send(player, "hunter.you-are-hunter");
            if (this.plugin.config().giveTrackerOnStart()) {
                this.plugin.tracker().give(player);
            }
        }
    }

    /** Wird beim Verlassen des Servers aufgerufen. */
    public void handleQuit(final @NotNull Player player) {
        if (!isActive() || !isRunner(player.getUniqueId())) {
            return;
        }
        if (this.plugin.config().endGameWhenRunnerQuits()) {
            stop(GameEndReason.RUNNER_QUIT);
        } else {
            this.plugin.messages().broadcast("game.runner-quit", "player", player.getName());
            this.plugin.getLogger().info("Runner " + player.getName() + " hat das Spiel verlassen - Event laeuft weiter.");
        }
    }

    /** Hinweis, wenn ein Hunter die Dimension wechselt. */
    public void handleWorldChange(final @NotNull Player player) {
        if (!isRunning() || !isHunter(player.getUniqueId())) {
            return;
        }
        final Player runner = runner();
        if (runner == null || runner.getWorld().equals(player.getWorld())) {
            return;
        }
        this.plugin.messages().send(player, "tracker.dimension-hint",
                "dimension", Text.dimensionName(runner.getWorld()));
    }

    /* ------------------------------------------------------------ Verwaltung */

    /** Setzt den Runner (nur ohne laufendes Event). */
    public boolean setRunner(final @NotNull Player target, final @NotNull CommandSender sender) {
        if (isActive()) {
            this.plugin.messages().send(sender, "game.already-running");
            return false;
        }
        this.runnerId = target.getUniqueId();
        this.hunters.remove(this.runnerId);
        this.excluded.remove(this.runnerId);
        this.plugin.messages().broadcast("game.runner-set", "player", target.getName());
        return true;
    }

    /** Fuegt einen Hunter hinzu. */
    public boolean addHunter(final @NotNull Player target, final @NotNull CommandSender sender) {
        if (isRunner(target)) {
            this.plugin.messages().send(sender, "hunter.runner-blocked", "player", target.getName());
            return false;
        }
        this.excluded.remove(target.getUniqueId());
        if (!this.hunters.add(target.getUniqueId())) {
            return false;
        }
        this.plugin.messages().broadcast("hunter.assigned", "player", target.getName());
        if (isRunning()) {
            this.plugin.messages().send(target, "hunter.you-are-hunter");
            if (this.plugin.config().giveTrackerOnStart()) {
                this.plugin.tracker().give(target);
            }
        }
        return true;
    }

    /** Entfernt einen Hunter (wird nicht automatisch wieder hinzugefuegt). */
    public boolean removeHunter(final @NotNull Player target, final @NotNull CommandSender sender) {
        this.excluded.add(target.getUniqueId());
        if (!this.hunters.remove(target.getUniqueId())) {
            return false;
        }
        this.plugin.tracker().remove(target);
        this.plugin.messages().send(target, "tracker.removed");
        this.plugin.messages().broadcast("hunter.removed", "player", target.getName());
        return true;
    }

    /** Alle Online-Spieler ausser dem Runner (und ausser Ausgeschlossenen) werden Hunter. */
    public void rebuildHunters() {
        this.hunters.clear();
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (isRunner(online) || isExcluded(online.getUniqueId())) {
                continue;
            }
            this.hunters.add(online.getUniqueId());
        }
    }

    /** Hunter-Namen fuer Listen (GUI/Chat). */
    public @NotNull List<String> hunterNames() {
        final List<String> names = new ArrayList<>();
        for (final Player hunter : onlineHunters()) {
            names.add(hunter.getName());
        }
        return names;
    }

    /** Aufloesbarer Hauptwelt-Spawn (Basis fuer Todes-Respawns). */
    public @Nullable Location mainWorldSpawn() {
        return worldSpawn();
    }

    /** Anzahl der laufenden internen Tasks (Diagnose). */
    public int taskCount() {
        return this.tasks.size();
    }

    /** Blockiert das Verschieben des Trackers in Behaelter (Sicherheitsnetz). */
    public boolean isTrackerProtected() {
        return this.plugin.config().protectTrackerFromLoss();
    }

    /** Himmelsrichtung vom Spieler zum Runner (nur bei gleicher Welt). */
    public @Nullable String runnerDirection(final @NotNull Player player) {
        final Player runner = runner();
        if (runner == null || !Objects.equals(runner.getWorld(), player.getWorld())) {
            return null;
        }
        return Nav.direction(player.getLocation(), runner.getLocation());
    }

    private void sound(final @NotNull Player player, final @NotNull Sound sound, final float pitch) {
        final ManhuntConfig config = this.plugin.config();
        if (!config.soundsEnabled()) {
            return;
        }
        player.playSound(player.getLocation(), sound, config.soundVolume(), config.soundPitch() * pitch);
    }
}

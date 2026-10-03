package de.chaostobimc.chaosmanhunt.game;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.ManhuntConfig;
import de.chaostobimc.chaosmanhunt.util.ItemBuilder;
import de.chaostobimc.chaosmanhunt.util.Nav;
import de.chaostobimc.chaosmanhunt.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Verwaltet den "Gorgii-Tracker" - einen speziell markierten Kompass, der
 * jederzeit auf den Runner zeigt.
 *
 * <p>Der Kompass wird ueber einen {@link NamespacedKey} im PersistentDataContainer
 * eindeutig erkannt. Dadurch koennen normale Kompasse der Spieler nicht mit dem
 * Tracker verwechselt werden.</p>
 */
public final class TrackerService {

    private static final String KEY_NAME = "tracker";

    private final ChaosManhunt plugin;
    private final NamespacedKey trackerKey;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private int ticks;

    public TrackerService(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
        this.trackerKey = new NamespacedKey(plugin, KEY_NAME);
    }

    public @NotNull NamespacedKey key() {
        return this.trackerKey;
    }

    /* ------------------------------------------------------------------- Item */

    /** Erzeugt einen neuen Tracker-Kompass. */
    public @NotNull ItemStack create(final @Nullable Player owner) {
        final ManhuntConfig config = this.plugin.config();
        final ItemBuilder builder = ItemBuilder.of(Material.COMPASS)
                .name(Text.parseItem(config.trackerName()))
                .glint(true)
                .meta(meta -> meta.getPersistentDataContainer().set(this.trackerKey, PersistentDataType.BYTE, (byte) 1));
        builder.addLore(loreLines(owner));
        return builder.build();
    }

    /** Ist das Item ein Tracker? */
    public boolean isTracker(final @Nullable ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.trackerKey, PersistentDataType.BYTE);
    }

    /* ------------------------------------------------------------- Ausgabe */

    /** Gibt dem Spieler genau einen Tracker (entfernt vorher alte/doppelte). */
    public void give(final @NotNull Player player) {
        remove(player);
        final ItemStack tracker = create(player);
        final PlayerInventory inventory = player.getInventory();

        final ItemStack hand = inventory.getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            inventory.setItemInMainHand(tracker);
        } else {
            final int slot = inventory.firstEmpty();
            if (slot >= 0) {
                inventory.setItem(slot, tracker);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), tracker);
            }
        }
        this.plugin.messages().send(player, "tracker.given");
    }

    /** Entfernt alle Tracker aus Inventar und Cursor. */
    public boolean remove(final @NotNull Player player) {
        boolean removed = false;
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isTracker(contents[slot])) {
                inventory.setItem(slot, null);
                removed = true;
            }
        }
        if (isTracker(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed = true;
        }
        return removed;
    }

    /** Hat der Spieler (irgendwo im Inventar) einen Tracker? */
    public boolean hasTracker(final @NotNull Player player) {
        for (final ItemStack item : player.getInventory().getContents()) {
            if (isTracker(item)) {
                return true;
            }
        }
        return false;
    }

    /* --------------------------------------------------------------- Tracking */

    /** Wird vom Tracker-Task im konfigurierten Intervall aufgerufen. */
    public void tick() {
        final ManhuntGame game = this.plugin.game();
        if (!game.isRunning()) {
            return;
        }

        final Player runner = game.runner();
        final ManhuntConfig config = this.plugin.config();
        this.ticks++;

        final int updateInterval = Math.max(1, config.trackerUpdateInterval());
        final int loreInterval = config.trackerLoreUpdateInterval();
        final boolean refreshLore = loreInterval > 0 && this.ticks % Math.max(1, loreInterval / updateInterval) == 0;

        for (final Player hunter : game.onlineHunters()) {
            if (game.isLocked(hunter.getUniqueId()) || !hasTracker(hunter)) {
                continue;
            }
            updateTarget(hunter, runner);
            if (refreshLore) {
                refreshLore(hunter);
            }
        }
    }

    /**
     * Richtet die Kompass-Nadel aus.
     *
     * <p>Nur in derselben Dimension wird das Ziel gesetzt. Ist der Runner in
     * einer anderen Dimension, bleibt die Nadel stabil (kein Herumspinnen) -
     * je nach Konfiguration werden Oberwelt/Nether-Koordinaten umgerechnet.</p>
     */
    private void updateTarget(final @NotNull Player hunter, final @Nullable Player runner) {
        if (runner == null || !runner.isOnline()) {
            return;
        }

        final World hunterWorld = hunter.getWorld();
        final World runnerWorld = runner.getWorld();

        if (hunterWorld.equals(runnerWorld)) {
            hunter.setCompassTarget(runner.getLocation().clone());
            return;
        }

        if (this.plugin.config().crossDimensionMode() == ManhuntConfig.CrossDimensionMode.PORTAL_SCALED) {
            final Location scaled = scaleToWorld(runner.getLocation(), hunterWorld);
            if (scaled != null) {
                hunter.setCompassTarget(scaled);
            }
        }
    }

    /** Rechnet eine Position zwischen Oberwelt und Nether um (Faktor 8). */
    private @Nullable Location scaleToWorld(final @NotNull Location runnerLocation, final @NotNull World hunterWorld) {
        final World runnerWorld = runnerLocation.getWorld();
        if (runnerWorld == null) {
            return null;
        }

        final World.Environment runnerEnvironment = runnerWorld.getEnvironment();
        final World.Environment hunterEnvironment = hunterWorld.getEnvironment();

        if (runnerEnvironment == World.Environment.NETHER && hunterEnvironment == World.Environment.NORMAL) {
            return new Location(hunterWorld, runnerLocation.getX() * 8.0D, runnerLocation.getY(), runnerLocation.getZ() * 8.0D);
        }
        if (runnerEnvironment == World.Environment.NORMAL && hunterEnvironment == World.Environment.NETHER) {
            return new Location(hunterWorld, runnerLocation.getX() / 8.0D, runnerLocation.getY(), runnerLocation.getZ() / 8.0D);
        }
        return null;
    }

    /** Aktualisiert die Lore des getragenen Trackers (Ziel, Entfernung, Richtung). */
    private void refreshLore(final @NotNull Player hunter) {
        final PlayerInventory inventory = hunter.getInventory();
        final ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack item = contents[slot];
            if (!isTracker(item)) {
                continue;
            }
            final ItemStack refreshed = ItemBuilder.of(item).addLore(loreLines(hunter)).build();
            inventory.setItem(slot, refreshed);
            return;
        }
    }

    /* -------------------------------------------------------- Rechtsklick-Info */

    /**
     * Behandelt den Rechtsklick auf den Tracker.
     *
     * <p>In derselben Dimension werden Details zum Runner ausgegeben, in einer
     * anderen Dimension erfolgt der deutliche Hinweis auf die Dimension.</p>
     */
    public void handleInteract(final @NotNull Player player, final @NotNull PlayerInteractEvent event) {
        if (this.plugin.config().protectTrackerFromLoss()) {
            event.setCancelled(true);
        }
        event.setUseItemInHand(Event.Result.DENY);

        final ManhuntGame game = this.plugin.game();
        final Player runner = game.runner();

        if (!game.isActive() || runner == null) {
            this.plugin.messages().send(player, "tracker.no-target");
            return;
        }

        final long cooldown = this.plugin.config().trackerClickCooldownMillis();
        final long now = System.currentTimeMillis();
        final Long last = this.clickCooldowns.get(player.getUniqueId());
        if (last != null) {
            final long remaining = cooldown - (now - last);
            if (remaining > 0L) {
                // Nur bei deutlich spuerbarer Wartezeit hinweisen (kein Spam).
                if (remaining > 250L) {
                    this.plugin.messages().send(player, "tracker.click-cooldown");
                }
                return;
            }
        }
        this.clickCooldowns.put(player.getUniqueId(), now);

        final Location runnerLocation = runner.getLocation();
        final String dimension = Text.dimensionName(runner.getWorld());

        if (player.getWorld().equals(runner.getWorld())) {
            this.plugin.messages().send(player, "tracker.click-same-world",
                    "runner", runner.getName(),
                    "dimension", dimension,
                    "distance", Nav.formatDistance(player.getLocation(), runnerLocation),
                    "direction", Nav.direction(player.getLocation(), runnerLocation),
                    "x", coordinate(runnerLocation.getX()),
                    "y", coordinate(runnerLocation.getY()),
                    "z", coordinate(runnerLocation.getZ()));
        } else {
            this.plugin.messages().send(player, "tracker.click-other-dimension",
                    "runner", runner.getName(),
                    "dimension", dimension);
        }
    }

    /** Ziel-Info fuer die Lore (bzw. Platzhalter, wenn niemand gejagt wird). */
    private @NotNull List<String> loreLines(final @Nullable Player owner) {
        final List<String> template = this.plugin.config().trackerLore();
        final List<String> result = new ArrayList<>(template.size());

        final ManhuntGame game = this.plugin.game();
        final Player runner = game.runner();
        if (runner == null || !game.isActive()) {
            for (final String line : template) {
                result.add(replace(line, "—", "—", "—", "—", "—", "—", "—"));
            }
            return result;
        }

        final Location runnerLocation = runner.getLocation();
        final boolean sameWorld = owner != null && owner.getWorld().equals(runner.getWorld());
        final String distance = sameWorld
                ? Nav.formatDistance(owner.getLocation(), runnerLocation)
                : "—";
        final String direction = sameWorld
                ? Nav.direction(owner.getLocation(), runnerLocation)
                : "—";

        for (final String line : template) {
            result.add(replace(line,
                    Text.dimensionName(runner.getWorld()),
                    distance,
                    direction,
                    coordinate(runnerLocation.getX()),
                    coordinate(runnerLocation.getY()),
                    coordinate(runnerLocation.getZ()),
                    runner.getName()));
        }
        return result;
    }

    private static @NotNull String replace(final @NotNull String line, final @NotNull String target,
                                           final @NotNull String distance, final @NotNull String direction,
                                           final @NotNull String x, final @NotNull String y, final @NotNull String z,
                                           final @NotNull String runner) {
        return line
                .replace("%target%", target)
                .replace("%distance%", distance)
                .replace("%direction%", direction)
                .replace("%x%", x)
                .replace("%y%", y)
                .replace("%z%", z)
                .replace("%runner%", runner);
    }

    private static @NotNull String coordinate(final double value) {
        return String.format(Locale.ROOT, "%d", Math.round(value));
    }

    /** Aufraeumen (z. B. bei Plugin-Deaktivierung). */
    public void clearCooldowns() {
        this.clickCooldowns.clear();
    }
}

package de.chaostobimc.chaosmanhunt.listener;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.ui.ManhuntMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Schuetzt den Tracker-Kompass und liefert die Rechtsklick-Informationen.
 *
 * <p>Der Tracker kann weder weggeworfen noch in Behaelter gelegt werden - damit
 * geht er auch beim Tod nicht verloren (dort wird er neu ausgegeben).</p>
 */
public final class TrackerListener implements Listener {

    private final ChaosManhunt plugin;

    public TrackerListener(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInteract(final @NotNull PlayerInteractEvent event) {
        if (!this.plugin.tracker().isTracker(event.getItem())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        this.plugin.tracker().handleInteract(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final @NotNull PlayerDropItemEvent event) {
        if (!this.plugin.config().protectTrackerFromLoss()) {
            return;
        }
        if (!this.plugin.tracker().isTracker(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        this.plugin.messages().send(event.getPlayer(), "tracker.drop-denied");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final @NotNull InventoryClickEvent event) {
        if (!this.plugin.config().protectTrackerFromLoss()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ManhuntMenu) {
            return; // Plugin-Menues behandelt der MenuListener
        }

        final boolean involvesTracker = this.plugin.tracker().isTracker(event.getCurrentItem())
                || this.plugin.tracker().isTracker(event.getCursor());
        if (!involvesTracker) {
            return;
        }

        final Inventory clicked = event.getClickedInventory();
        final boolean ownInventory = clicked != null && clicked.getHolder() instanceof Player;
        final boolean containerOpen = !(top.getHolder() instanceof Player);

        if (!ownInventory || (event.isShiftClick() && containerOpen)) {
            event.setCancelled(true);
            this.plugin.messages().send(player, "tracker.store-denied");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final @NotNull InventoryDragEvent event) {
        if (!this.plugin.config().protectTrackerFromLoss()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof Player) {
            return;
        }

        final boolean trackerInvolved = this.plugin.tracker().isTracker(event.getCursor())
                || event.getNewItems().values().stream().anyMatch(this.plugin.tracker()::isTracker);
        if (!trackerInvolved) {
            return;
        }

        event.setCancelled(true);
        this.plugin.messages().send(player, "tracker.store-denied");
    }

    /** Verhindert, dass der Tracker beim Tod gedroppt wird (Sicherheitsnetz). */
    @EventHandler(ignoreCancelled = true)
    public void onDeathDrops(final @NotNull PlayerDeathEvent event) {
        event.getDrops().removeIf(this.plugin.tracker()::isTracker);
        final ItemStack cursor = event.getEntity().getItemOnCursor();
        if (this.plugin.tracker().isTracker(cursor)) {
            event.getEntity().setItemOnCursor(null);
        }
    }
}

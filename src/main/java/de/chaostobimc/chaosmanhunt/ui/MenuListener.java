package de.chaostobimc.chaosmanhunt.ui;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Schuetzt alle Plugin-Menues vor Item-Interaktionen.
 *
 * <p>Da alle Klicks in eigenen Inventaren abgebrochen werden, sind Duplizieren
 * oder Verschieben von Items ausgeschlossen (auch per Shift-Klick, Doppelklick,
 * Zahlen-Taste oder Drag).</p>
 */
public final class MenuListener implements Listener {

    private final ChaosManhunt plugin;

    public MenuListener(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInventoryClick(final @NotNull InventoryClickEvent event) {
        final Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ManhuntMenu menu)) {
            return;
        }

        // Grundschutz: im Plugin-Inventar wird nichts verschoben oder dupliziert.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        menu.handleClick(player, event.getRawSlot());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInventoryDrag(final @NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ManhuntMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final @NotNull InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AdminMenu menu) {
            this.plugin.menus().untrack(menu);
        }
    }
}

package de.chaostobimc.chaosmanhunt.ui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker-Interface fuer alle GUI-Inventare des Plugins.
 *
 * <p>Der {@link MenuListener} erkennt daran seine eigenen Inventare und
 * unterbindet dort jeden Item-Transfer (kein Verschieben, kein Duplizieren).</p>
 */
public interface ManhuntMenu extends InventoryHolder {

    /** Inventar neu aufbauen (z. B. nach einem Statuswechsel). */
    void refresh();

    /**
     * Verarbeitet einen Klick auf den angegebenen Slot.
     *
     * @return {@code true}, wenn der Klick behandelt wurde
     */
    boolean handleClick(final @NotNull Player player, final int slot);
}

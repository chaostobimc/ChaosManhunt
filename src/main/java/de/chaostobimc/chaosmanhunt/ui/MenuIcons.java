package de.chaostobimc.chaosmanhunt.ui;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.util.ItemBuilder;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

/** Gemeinsame Item-Helfer fuer die Menues. */
public final class MenuIcons {

    private MenuIcons() {
    }

    /** Graue Glasscheibe als Hintergrund-Fuellung. */
    public static @NotNull ItemStack filler(final @NotNull ChaosManhunt plugin) {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(plugin.messages().raw("gui.filler-name"))
                .hideAttributes()
                .build();
    }

    /** Spielerkopf mit konfigurierbarer Lore. */
    public static @NotNull ItemStack head(final @NotNull OfflinePlayer owner, final @NotNull ItemBuilder builder) {
        return builder.meta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(owner);
            }
        }).build();
    }

    /** Lore-Zeilen aus der messages.yml mit Platzhaltern. */
    public static @NotNull List<String> lore(final @NotNull ChaosManhunt plugin, final @NotNull String key,
                                             final String @NotNull... placeholders) {
        final List<String> lines = plugin.messages().lines(key);
        final List<String> result = new ArrayList<>(lines.size());
        for (final String line : lines) {
            String prepared = line;
            for (int index = 0; index + 1 < placeholders.length; index += 2) {
                prepared = prepared.replace("%" + placeholders[index] + "%", placeholders[index + 1]);
            }
            result.add(prepared);
        }
        return result;
    }

    /** Titel-Element, das den Listen-Platzhalter durch konkrete Zeilen ersetzt. */
    public static @NotNull List<String> loreWithList(final @NotNull ChaosManhunt plugin, final @NotNull String key,
                                                     final @NotNull String listPlaceholder, final @NotNull List<String> entries,
                                                     final String @NotNull... placeholders) {
        final List<String> source = lore(plugin, key, placeholders);
        final List<String> result = new ArrayList<>(source.size() + entries.size());
        final String empty = plugin.messages().raw("gui.no-players");

        for (final String line : source) {
            if (!line.contains(listPlaceholder)) {
                result.add(line);
                continue;
            }
            if (entries.isEmpty()) {
                result.add(line.replace(listPlaceholder, empty));
                continue;
            }
            for (final String entry : entries) {
                result.add(line.replace(listPlaceholder, entry));
            }
        }
        return result;
    }

    /** Einheitlicher Klick-Sound (respektiert sounds.enabled). */
    public static void click(final @NotNull ChaosManhunt plugin, final @NotNull Player player) {
        if (!plugin.config().soundsEnabled()) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,
                plugin.config().soundVolume() * 0.6F, plugin.config().soundPitch() * 1.4F);
    }

    /** Fehler-Sound bei gesperrten Aktionen. */
    public static void deny(final @NotNull ChaosManhunt plugin, final @NotNull Player player) {
        if (!plugin.config().soundsEnabled()) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK,
                plugin.config().soundVolume() * 0.5F, plugin.config().soundPitch() * 0.8F);
    }
}

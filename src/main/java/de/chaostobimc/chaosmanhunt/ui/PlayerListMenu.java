package de.chaostobimc.chaosmanhunt.ui;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.game.ManhuntGame;
import de.chaostobimc.chaosmanhunt.util.ItemBuilder;
import de.chaostobimc.chaosmanhunt.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Spielerauswahl zum Festlegen des Runners bzw. zum Verwalten der Hunter.
 */
public final class PlayerListMenu implements ManhuntMenu {

    /** Auswahlmodus. */
    public enum Mode {
        RUNNER,
        HUNTER
    }

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 49;
    private static final int MAX_PLAYERS = 45;

    private final ChaosManhunt plugin;
    private final Player viewer;
    private final Mode mode;
    private final Inventory inventory;
    private final Map<Integer, UUID> slotToPlayer = new HashMap<>();

    private PlayerListMenu(final @NotNull ChaosManhunt plugin, final @NotNull Player viewer, final @NotNull Mode mode) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.mode = mode;
        final String title = mode == Mode.RUNNER ? plugin.config().runnerMenuTitle() : plugin.config().hunterMenuTitle();
        this.inventory = Bukkit.createInventory(this, SIZE, Text.parse(title));
        build();
    }

    /** Oeffnet die Spielerauswahl. */
    public static void open(final @NotNull ChaosManhunt plugin, final @NotNull Player player, final @NotNull Mode mode) {
        final PlayerListMenu menu = new PlayerListMenu(plugin, player, mode);
        player.openInventory(menu.getInventory());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }

    private void build() {
        this.slotToPlayer.clear();
        final ItemStack filler = MenuIcons.filler(this.plugin);
        for (int slot = 0; slot < SIZE; slot++) {
            this.inventory.setItem(slot, filler);
        }

        final List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int slot = 0;
        for (final Player target : players) {
            if (slot >= MAX_PLAYERS) {
                break;
            }
            this.inventory.setItem(slot, headItem(target));
            this.slotToPlayer.put(slot, target.getUniqueId());
            slot++;
        }

        if (players.isEmpty()) {
            this.inventory.setItem(22, ItemBuilder.of(Material.BARRIER)
                    .name(this.plugin.messages().raw("gui.picker.empty-name"))
                    .build());
        }

        this.inventory.setItem(BACK_SLOT, ItemBuilder.of(Material.ARROW)
                .name(this.plugin.messages().raw("gui.picker.back-name"))
                .addLore(MenuIcons.lore(this.plugin, "gui.picker.back-lore"))
                .hideAttributes()
                .build());
    }

    private @NotNull ItemStack headItem(final @NotNull Player target) {
        final ManhuntGame game = this.plugin.game();

        final String roleKey;
        final String actionKey;
        if (game.isRunner(target)) {
            roleKey = "gui.picker.head-status-runner";
            actionKey = "gui.picker.action-runner";
        } else if (game.isHunter(target)) {
            roleKey = "gui.picker.head-status-hunter";
            actionKey = this.mode == Mode.HUNTER ? "gui.picker.action-hunter-remove" : "gui.picker.action-runner";
        } else {
            roleKey = "gui.picker.head-status-none";
            actionKey = this.mode == Mode.HUNTER ? "gui.picker.action-hunter-add" : "gui.picker.action-runner";
        }

        final List<String> lore = MenuIcons.lore(this.plugin, "gui.picker.head-lore",
                "role", this.plugin.messages().raw(roleKey),
                "action", this.plugin.messages().raw(actionKey));

        return MenuIcons.head(target, ItemBuilder.of(Material.PLAYER_HEAD)
                .name(this.plugin.messages().text("gui.picker.head-name", "player", target.getName()))
                .addLore(lore));
    }

    @Override
    public void refresh() {
        build();
    }

    @Override
    public boolean handleClick(final @NotNull Player player, final int slot) {
        if (!player.getUniqueId().equals(this.viewer.getUniqueId())) {
            return false;
        }

        if (slot == BACK_SLOT) {
            MenuIcons.click(this.plugin, player);
            AdminMenu.open(this.plugin, player);
            return true;
        }

        final UUID targetId = this.slotToPlayer.get(slot);
        if (targetId == null) {
            return false;
        }

        final Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            MenuIcons.deny(this.plugin, player);
            refresh();
            return true;
        }

        final ManhuntGame game = this.plugin.game();
        if (this.mode == Mode.RUNNER) {
            if (game.setRunner(target, player)) {
                MenuIcons.click(this.plugin, player);
                AdminMenu.open(this.plugin, player);
            } else {
                MenuIcons.deny(this.plugin, player);
            }
            return true;
        }

        final boolean handled = game.isHunter(target)
                ? game.removeHunter(target, player)
                : game.addHunter(target, player);

        if (handled) {
            MenuIcons.click(this.plugin, player);
        } else {
            MenuIcons.deny(this.plugin, player);
        }
        refresh();
        return true;
    }
}

package de.chaostobimc.chaosmanhunt.ui;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.game.GameEndReason;
import de.chaostobimc.chaosmanhunt.game.GameState;
import de.chaostobimc.chaosmanhunt.game.ManhuntGame;
import de.chaostobimc.chaosmanhunt.util.ItemBuilder;
import de.chaostobimc.chaosmanhunt.util.Text;
import de.chaostobimc.chaosmanhunt.util.TimeFormat;
import java.time.Duration;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Admin-Panel des Manhunt-Events.
 *
 * <p>Das Inventar gehoert dem Plugin ({@link ManhuntMenu}), daher kann kein
 * Spieler Items hinein- oder herausschieben. Der {@link MenuRegistry} haelt das
 * Panel bei geoeffnetem Fenster automatisch aktuell.</p>
 */
public final class AdminMenu implements ManhuntMenu {

    private static final int SIZE = 54;
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_RUNNER = 20;
    private static final int SLOT_START = 22;
    private static final int SLOT_STOP = 24;
    private static final int SLOT_HUNTERS = 29;
    private static final int SLOT_INFO = 31;
    private static final int SLOT_TRACKER = 33;
    private static final int SLOT_CLOSE = 49;

    private final ChaosManhunt plugin;
    private final Player viewer;
    private final Inventory inventory;

    private AdminMenu(final @NotNull ChaosManhunt plugin, final @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, SIZE, Text.parse(plugin.config().menuTitle()));
        build();
    }

    /** Oeffnet das Panel fuer einen Admin. */
    public static void open(final @NotNull ChaosManhunt plugin, final @NotNull Player player) {
        final AdminMenu menu = new AdminMenu(plugin, player);
        player.openInventory(menu.getInventory());
        plugin.menus().track(menu);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }

    public @NotNull Player viewer() {
        return this.viewer;
    }

    /** Ist das Panel beim Spieler gerade geoeffnet? */
    public boolean isViewing() {
        return this.viewer.isOnline()
                && this.viewer.getOpenInventory().getTopInventory().getHolder() == this;
    }

    private void build() {
        final ItemStack filler = MenuIcons.filler(this.plugin);
        for (int slot = 0; slot < SIZE; slot++) {
            this.inventory.setItem(slot, filler);
        }
        refresh();
    }

    @Override
    public void refresh() {
        this.inventory.setItem(SLOT_STATUS, statusItem());
        this.inventory.setItem(SLOT_RUNNER, runnerItem());
        this.inventory.setItem(SLOT_START, startItem());
        this.inventory.setItem(SLOT_STOP, stopItem());
        this.inventory.setItem(SLOT_HUNTERS, huntersItem());
        this.inventory.setItem(SLOT_INFO, infoItem());
        this.inventory.setItem(SLOT_TRACKER, trackerItem());
        this.inventory.setItem(SLOT_CLOSE, closeItem());
    }

    /* ------------------------------------------------------------------ Items */

    private @NotNull ItemStack statusItem() {
        final ManhuntGame game = this.plugin.game();
        final GameState state = game.state();
        final Material material = switch (state) {
            case RUNNING -> Material.LIME_DYE;
            case COUNTDOWN -> Material.CLOCK;
            default -> Material.RED_DYE;
        };
        final String nameKey = switch (state) {
            case RUNNING -> "gui.status.name-running";
            case COUNTDOWN -> "gui.status.name-countdown";
            default -> "gui.status.name-idle";
        };
        final List<String> lore = MenuIcons.lore(this.plugin, "gui.status.lore",
                "runner", game.runnerName(),
                "hunters", String.valueOf(game.hunterCount()),
                "time", game.isActive() ? game.elapsedText() : "0s",
                "lock", lockText(),
                "countdown", String.valueOf(this.plugin.config().countdownSeconds()));

        return ItemBuilder.of(material)
                .name(this.plugin.messages().raw(nameKey))
                .addLore(lore)
                .hideAttributes()
                .build();
    }

    private @NotNull ItemStack runnerItem() {
        final ManhuntGame game = this.plugin.game();
        final Player runner = game.runner();
        final String name = this.plugin.messages().text(
                runner == null ? "gui.runner.none-name" : "gui.runner.name", "runner", game.runnerName());
        final List<String> lore = MenuIcons.lore(this.plugin, "gui.runner.lore", "runner", game.runnerName());

        final ItemBuilder builder = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(name)
                .addLore(lore)
                .hideAttributes();
        return runner == null ? builder.build() : MenuIcons.head(runner, builder);
    }

    private @NotNull ItemStack startItem() {
        final ManhuntGame game = this.plugin.game();
        if (game.isActive()) {
            return ItemBuilder.of(Material.GRAY_WOOL)
                    .name(this.plugin.messages().raw("gui.start.running-name"))
                    .addLore(MenuIcons.lore(this.plugin, "gui.start.running-lore"))
                    .hideAttributes()
                    .build();
        }
        return ItemBuilder.of(Material.GREEN_WOOL)
                .name(this.plugin.messages().raw("gui.start.name"))
                .addLore(MenuIcons.lore(this.plugin, "gui.start.lore", "runner", game.runnerName()))
                .hideAttributes()
                .build();
    }

    private @NotNull ItemStack stopItem() {
        final ManhuntGame game = this.plugin.game();
        if (!game.isActive()) {
            return ItemBuilder.of(Material.GRAY_WOOL)
                    .name(this.plugin.messages().raw("gui.stop.idle-name"))
                    .addLore(MenuIcons.lore(this.plugin, "gui.stop.idle-lore"))
                    .hideAttributes()
                    .build();
        }
        return ItemBuilder.of(Material.RED_WOOL)
                .name(this.plugin.messages().raw("gui.stop.name"))
                .addLore(MenuIcons.lore(this.plugin, "gui.stop.lore"))
                .hideAttributes()
                .build();
    }

    private @NotNull ItemStack huntersItem() {
        final ManhuntGame game = this.plugin.game();
        final List<String> names = game.hunterNames();
        final List<String> lore = MenuIcons.loreWithList(this.plugin, "gui.hunters.lore", "%list%", names,
                "hunters", String.valueOf(names.size()));

        return ItemBuilder.of(Material.IRON_SWORD)
                .name(this.plugin.messages().text("gui.hunters.name", "hunters", String.valueOf(names.size())))
                .addLore(lore)
                .hideAttributes()
                .build();
    }

    private @NotNull ItemStack infoItem() {
        final List<String> lore = MenuIcons.lore(this.plugin, "gui.info.lore",
                "lock", lockText(),
                "countdown", String.valueOf(this.plugin.config().countdownSeconds()),
                "update", String.valueOf(this.plugin.config().trackerUpdateInterval()),
                "format", Text.stripColors(this.plugin.config().actionBarFormat()));

        return ItemBuilder.of(Material.PAPER)
                .name(this.plugin.messages().raw("gui.info.name"))
                .addLore(lore)
                .hideAttributes()
                .build();
    }

    private @NotNull ItemStack trackerItem() {
        return ItemBuilder.of(Material.COMPASS)
                .name(this.plugin.messages().raw("gui.tracker.name"))
                .addLore(MenuIcons.lore(this.plugin, "gui.tracker.lore"))
                .hideAttributes()
                .build();
    }

    private @NotNull ItemStack closeItem() {
        return ItemBuilder.of(Material.BARRIER)
                .name(this.plugin.messages().raw("gui.close-name"))
                .addLore(MenuIcons.lore(this.plugin, "gui.close-lore"))
                .hideAttributes()
                .build();
    }

    private @NotNull String lockText() {
        return TimeFormat.format(Duration.ofSeconds(this.plugin.config().respawnLockSeconds()),
                this.plugin.config().padZeroUnits());
    }

    /* ------------------------------------------------------------- Klick-Logik */

    @Override
    public boolean handleClick(final @NotNull Player player, final int slot) {
        if (!player.getUniqueId().equals(this.viewer.getUniqueId())) {
            return false;
        }

        final ManhuntGame game = this.plugin.game();

        switch (slot) {
            case SLOT_RUNNER -> {
                MenuIcons.click(this.plugin, player);
                PlayerListMenu.open(this.plugin, player, PlayerListMenu.Mode.RUNNER);
            }
            case SLOT_START -> {
                MenuIcons.click(this.plugin, player);
                game.start(game.runner(), player);
                refresh();
            }
            case SLOT_STOP -> {
                MenuIcons.click(this.plugin, player);
                if (game.isActive()) {
                    game.stop(GameEndReason.ADMIN_STOP);
                } else {
                    this.plugin.messages().send(player, "game.not-running");
                    MenuIcons.deny(this.plugin, player);
                }
                refresh();
            }
            case SLOT_HUNTERS -> {
                MenuIcons.click(this.plugin, player);
                PlayerListMenu.open(this.plugin, player, PlayerListMenu.Mode.HUNTER);
            }
            case SLOT_TRACKER -> {
                giveTrackers(player);
                refresh();
            }
            case SLOT_CLOSE -> {
                MenuIcons.click(this.plugin, player);
                player.closeInventory();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void giveTrackers(final @NotNull Player player) {
        final ManhuntGame game = this.plugin.game();
        if (!game.isActive()) {
            this.plugin.messages().send(player, "game.not-running");
            MenuIcons.deny(this.plugin, player);
            return;
        }

        final List<Player> hunters = game.onlineHunters();
        for (final Player hunter : hunters) {
            this.plugin.tracker().give(hunter);
        }
        MenuIcons.click(this.plugin, player);
        this.plugin.messages().send(player, "gui.tracker.given", "count", String.valueOf(hunters.size()));
    }

    /** Status fuer Debug-Zwecke. */
    @Override
    public @NotNull String toString() {
        return "AdminMenu{" + this.viewer.getName() + "}";
    }
}

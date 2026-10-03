package de.chaostobimc.chaosmanhunt.command;

import de.chaostobimc.chaosmanhunt.ChaosManhunt;
import de.chaostobimc.chaosmanhunt.ManhuntConfig;
import de.chaostobimc.chaosmanhunt.game.GameEndReason;
import de.chaostobimc.chaosmanhunt.game.ManhuntGame;
import de.chaostobimc.chaosmanhunt.ui.AdminMenu;
import de.chaostobimc.chaosmanhunt.util.Gradient;
import de.chaostobimc.chaosmanhunt.util.Text;
import de.chaostobimc.chaosmanhunt.util.TimeFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Der Befehl {@code /manhunt} (Alias {@code /mh}).
 *
 * <p>Alle Unterbefehle erfordern {@code manhunt.admin}. Ohne Argument oeffnen
 * Spieler direkt das Admin-Panel.</p>
 */
public final class ManhuntCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "menu", "start", "stop", "runner", "hunter", "status", "reload", "diagnose", "help");
    private static final List<String> HUNTER_ACTIONS = List.of("add", "remove", "list");

    private final ChaosManhunt plugin;

    public ManhuntCommand(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final @NotNull Command command,
                             final @NotNull String label, final String @NotNull[] args) {
        if (!sender.hasPermission("manhunt.admin")) {
            this.plugin.messages().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            openMenu(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu", "gui", "panel" -> openMenu(sender);
            case "start" -> start(sender, args);
            case "stop" -> stop(sender);
            case "runner" -> runner(sender, args);
            case "hunter" -> hunter(sender, args);
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "diagnose" -> diagnose(sender);
            case "help", "?" -> help(sender);
            default -> {
                this.plugin.messages().send(sender, "command.unknown");
                help(sender);
            }
        }
        return true;
    }

    /* --------------------------------------------------------------- Befehle */

    private void openMenu(final @NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "players-only");
            help(sender);
            return;
        }
        AdminMenu.open(this.plugin, player);
    }

    private void start(final @NotNull CommandSender sender, final String @NotNull[] args) {
        final ManhuntGame game = this.plugin.game();
        Player runner = game.runner();

        if (args.length > 1) {
            runner = Bukkit.getPlayerExact(args[1]);
            if (runner == null) {
                this.plugin.messages().send(sender, "game.runner-offline", "player", args[1]);
                return;
            }
        }
        game.start(runner, sender);
    }

    private void stop(final @NotNull CommandSender sender) {
        final ManhuntGame game = this.plugin.game();
        if (!game.isActive()) {
            this.plugin.messages().send(sender, "game.not-running");
            return;
        }
        game.stop(GameEndReason.ADMIN_STOP);
    }

    private void runner(final @NotNull CommandSender sender, final String @NotNull[] args) {
        if (args.length < 2) {
            this.plugin.messages().sendRaw(sender, "command.usage", "usage", "/mh runner <Spieler>");
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            this.plugin.messages().send(sender, "game.runner-offline", "player", args[1]);
            return;
        }
        this.plugin.game().setRunner(target, sender);
    }

    private void hunter(final @NotNull CommandSender sender, final String @NotNull[] args) {
        final ManhuntGame game = this.plugin.game();

        if (args.length < 2) {
            this.plugin.messages().sendRaw(sender, "command.usage", "usage", "/mh hunter <add|remove|list> [Spieler]");
            return;
        }

        final String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                final List<String> names = game.hunterNames();
                this.plugin.messages().sendRaw(sender, "command.hunter-list",
                        "count", String.valueOf(names.size()),
                        "hunters", names.isEmpty() ? this.plugin.messages().raw("gui.no-players") : String.join(", ", names));
            }
            case "add", "remove" -> {
                if (args.length < 3) {
                    this.plugin.messages().sendRaw(sender, "command.usage", "usage", "/mh hunter " + action + " <Spieler>");
                    return;
                }
                final Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    this.plugin.messages().send(sender, "game.runner-offline", "player", args[2]);
                    return;
                }
                if (action.equals("add")) {
                    game.addHunter(target, sender);
                } else {
                    game.removeHunter(target, sender);
                }
            }
            default -> this.plugin.messages().send(sender, "command.unknown");
        }
    }

    private void status(final @NotNull CommandSender sender) {
        final ManhuntGame game = this.plugin.game();
        for (final String line : this.plugin.messages().lines("command.status")) {
            sender.sendMessage(Text.parse(line
                    .replace("%state%", game.state().display())
                    .replace("%runner%", game.runnerName())
                    .replace("%hunters%", String.valueOf(game.hunterCount()))
                    .replace("%time%", game.isActive() ? game.elapsedText() : "0s")));
        }
    }

    private void reload(final @NotNull CommandSender sender) {
        this.plugin.reloadAll();
        this.plugin.messages().send(sender, "command.reloaded");
        this.plugin.getLogger().info("Konfiguration wurde von " + sender.getName() + " neu geladen.");
    }

    /** Selbsttest: prueft Konfiguration, Gradient, Tracker-Item und Nachrichten. */
    private void diagnose(final @NotNull CommandSender sender) {
        final ChaosManhunt plugin = this.plugin;
        final ManhuntGame game = plugin.game();
        final ManhuntConfig config = plugin.config();

        sender.sendMessage(Text.parse("&8&m        &r &c&lChaosManhunt &7Diagnose &8&m        "));

        sender.sendMessage(check("Konfiguration", true, "Countdown " + config.countdownSeconds() + "s &8▪ &7Sperre "
                + config.respawnLockSeconds() + "s &8▪ &7Tracker-Update " + config.trackerUpdateInterval() + " Ticks"));

        final Gradient gradient = config.timerGradient();
        final boolean gradientOk = gradient.stops().size() >= 2;
        sender.sendMessage(check("Farbverlauf", gradientOk, gradient.stops().size() + " Stuetzfarben &8▪ &7"
                + Text.toHex(gradient.stops().get(0)) + " -> " + Text.toHex(gradient.stops().get(gradient.stops().size() - 1))));

        final String timeSample = TimeFormat.format(3822L, config.padZeroUnits());
        sender.sendMessage(check("Timer-Format", timeSample.contains("h"), "Beispiel: " + timeSample));

        final ItemStack tracker = plugin.tracker().create(null);
        final boolean trackerOk = plugin.tracker().isTracker(tracker);
        sender.sendMessage(check("Tracker-Item", trackerOk, tracker.getType().name() + " &8▪ &7Erkennung "
                + (trackerOk ? "OK" : "FEHLGESCHLAGEN")));

        final boolean messagesOk = !"game.started".equals(plugin.messages().raw("game.started"))
                && !"prefix".equals(plugin.messages().raw("prefix"))
                && !"tracker.click-other-dimension".equals(plugin.messages().raw("tracker.click-other-dimension"));
        sender.sendMessage(check("Nachrichten", messagesOk, messagesOk ? "messages.yml geladen"
                : "Schluessel fehlen - siehe Server-Log"));

        final Location spawn = game.mainWorldSpawn();
        sender.sendMessage(check("Respawn-Welt", spawn != null && spawn.getWorld() != null,
                spawn == null ? "nicht gefunden"
                        : spawn.getWorld().getName() + " &8▪ &7" + Math.round(spawn.getX()) + "/" + Math.round(spawn.getZ())));

        sender.sendMessage(check("Status", true, game.state().display() + " &8▪ &7Hunter " + game.hunterCount()
                + " &8▪ &7Tasks " + game.taskCount()));
    }

    private static @NotNull String check(final @NotNull String label, final boolean ok, final @NotNull String detail) {
        return (ok ? "&a✔ " : "&c✘ ") + "&f" + label + " &8▪ &7" + detail;
    }

    private void help(final @NotNull CommandSender sender) {
        this.plugin.messages().sendRaw(sender, "command.header");
        for (final String line : this.plugin.messages().lines("command.help")) {
            sender.sendMessage(Text.parse(line));
        }
    }

    /* -------------------------------------------------------- Tab-Vervollstaendigung */

    @Override
    public @NotNull List<String> onTabComplete(final @NotNull CommandSender sender, final @NotNull Command command,
                                               final @NotNull String alias, final String @NotNull[] args) {
        if (!sender.hasPermission("manhunt.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "start", "runner" -> filter(onlineNames(), args[1]);
                case "hunter" -> filter(HUNTER_ACTIONS, args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3 && sub.equals("hunter")) {
            final String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("add") || action.equals("remove")) {
                return filter(onlineNames(), args[2]);
            }
        }
        return List.of();
    }

    private static @NotNull List<String> onlineNames() {
        final List<String> names = new ArrayList<>();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static @NotNull List<String> filter(final @NotNull Collection<String> options, final @Nullable String prefix) {
        final String search = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        final List<String> result = new ArrayList<>();
        for (final String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(search)) {
                result.add(option);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}

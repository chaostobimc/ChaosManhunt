package de.chaostobimc.chaosmanhunt;

import de.chaostobimc.chaosmanhunt.command.ManhuntCommand;
import de.chaostobimc.chaosmanhunt.game.ManhuntGame;
import de.chaostobimc.chaosmanhunt.game.TrackerService;
import de.chaostobimc.chaosmanhunt.listener.GameListener;
import de.chaostobimc.chaosmanhunt.listener.TrackerListener;
import de.chaostobimc.chaosmanhunt.ui.MenuListener;
import de.chaostobimc.chaosmanhunt.ui.MenuRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Einstiegspunkt des Plugins.
 *
 * <p>ChaosManhunt steuert ein komplettes Manhunt-Event: Admin-Panel, Rollen,
 * Tracker-Kompass, Respawn-Sperre und den dynamischen Actionbar-Timer.
 * Alle Dienste werden hier verdrahtet und beim Deaktivieren sauber beendet.</p>
 */
public final class ChaosManhunt extends JavaPlugin {

    private ManhuntConfig config;
    private Messages messages;
    private ManhuntGame game;
    private TrackerService tracker;
    private MenuRegistry menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.config = new ManhuntConfig(this);
        this.messages = new Messages(this);
        this.game = new ManhuntGame(this);
        this.tracker = new TrackerService(this);
        this.menus = new MenuRegistry(this);

        final PluginCommand command = getCommand("manhunt");
        if (command == null) {
            getLogger().severe("Der Befehl /manhunt konnte nicht registriert werden - bitte plugin.yml pruefen.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        final ManhuntCommand executor = new ManhuntCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        final PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new GameListener(this), this);
        pluginManager.registerEvents(new TrackerListener(this), this);
        pluginManager.registerEvents(new MenuListener(this), this);

        getLogger().info("ChaosManhunt " + getPluginMeta().getVersion() + " aktiviert (Server: "
                + Bukkit.getVersion() + ").");
    }

    @Override
    public void onDisable() {
        // Laufendes Event beenden, alle Tasks stoppen, Spielerzustand zuruecksetzen.
        if (this.game != null) {
            this.game.shutdown();
        }
        if (this.tracker != null) {
            this.tracker.clearCooldowns();
        }
        if (this.menus != null) {
            this.menus.shutdown();
        }
        getServer().getScheduler().cancelTasks(this);
    }

    /** Laedt config.yml und messages.yml neu (Befehl {@code /mh reload}). */
    public void reloadAll() {
        reloadConfig();
        this.messages.reload();
        this.config.invalidate();
    }

    public @NotNull ManhuntConfig config() {
        return this.config;
    }

    public @NotNull Messages messages() {
        return this.messages;
    }

    public @NotNull ManhuntGame game() {
        return this.game;
    }

    public @NotNull TrackerService tracker() {
        return this.tracker;
    }

    public @NotNull MenuRegistry menus() {
        return this.menus;
    }
}

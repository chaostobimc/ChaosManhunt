package de.chaostobimc.chaosmanhunt.game;

import org.jetbrains.annotations.NotNull;

/** Grund fuer das Ende eines Manhunt-Events. */
public enum GameEndReason {

    RUNNER_DIED("Runner gestorben", true),
    ADMIN_STOP("Admin-Stop", true),
    RUNNER_QUIT("Runner hat das Spiel verlassen", true),
    PLUGIN_DISABLE("Plugin deaktiviert", false),
    ABORTED("Abgebrochen", false);

    private final String display;
    private final boolean broadcastStop;

    GameEndReason(final @NotNull String display, final boolean broadcastStop) {
        this.display = display;
        this.broadcastStop = broadcastStop;
    }

    /** Anzeigename (z. B. fuer {@code %reason%}). */
    public @NotNull String display() {
        return this.display;
    }

    /** Soll beim Stoppen automatisch eine Stop-Nachricht gesendet werden? */
    public boolean broadcastStop() {
        return this.broadcastStop;
    }
}

package de.chaostobimc.chaosmanhunt.game;

import org.jetbrains.annotations.NotNull;

/** Zustand des Manhunt-Events. */
public enum GameState {

    /** Kein Event aktiv. */
    IDLE("Bereit"),
    /** Countdown laeuft, Event startet gleich. */
    COUNTDOWN("Countdown"),
    /** Event laeuft, Timer aktiv. */
    RUNNING("Laeuft");

    private final String display;

    GameState(final @NotNull String display) {
        this.display = display;
    }

    /** Anzeigename fuer Status-Ausgaben. */
    public @NotNull String display() {
        return this.display;
    }
}

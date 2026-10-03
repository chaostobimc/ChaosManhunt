package de.chaostobimc.chaosmanhunt.util;

import java.util.Locale;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Kleine Navigations-Helfer fuer die Tracker-Anzeige (Entfernung, Himmelsrichtung).
 */
public final class Nav {

    private static final String[] POINTS = {"S", "SW", "W", "NW", "N", "NO", "O", "SO"};

    private Nav() {
    }

    /**
     * Liefert die Himmelsrichtung vom Start- zum Zielpunkt.
     *
     * @return Kuerzel wie {@code N}, {@code NO}, {@code SO}
     */
    public static @NotNull String direction(final double fromX, final double fromZ, final double toX, final double toZ) {
        final double deltaX = toX - fromX;
        final double deltaZ = toZ - fromZ;
        if (Math.abs(deltaX) < 0.001D && Math.abs(deltaZ) < 0.001D) {
            return "—";
        }

        final double angle = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        final int index = (int) Math.round(((angle + 360.0D) % 360.0D) / 45.0D) % 8;
        return POINTS[index];
    }

    /** Himmelsrichtung zwischen zwei Standorten (gleiche Welt vorausgesetzt). */
    public static @NotNull String direction(final @NotNull Location from, final @NotNull Location to) {
        return direction(from.getX(), from.getZ(), to.getX(), to.getZ());
    }

    /**
     * Rechnet eine Position zwischen Oberwelt und Nether um (Portal-Faktor 8).
     *
     * @return umgerechnete Entfernung in Bloecken oder {@code -1}, wenn nicht moeglich
     */
    public static double scaleDistance(final double distance, final boolean fromOverworld) {
        return fromOverworld ? distance / 8.0D : distance * 8.0D;
    }

    /** Formatiert eine Entfernung als ganzzahligen Block-Wert. */
    public static @NotNull String formatDistance(final @Nullable Location from, final @Nullable Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return "—";
        }
        return String.format(Locale.ROOT, "%d", Math.round(from.distance(to)));
    }
}

package de.chaostobimc.chaosmanhunt.util;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

/**
 * Formatiert Laufzeiten im "BastiGHG-Stil".
 *
 * <p>Es werden ausschliesslich die Einheiten angezeigt, die wirklich gebraucht
 * werden - keine fuehrenden Nullen wie {@code 0h 0m 10s}:</p>
 *
 * <pre>
 *    15s            (unter einer Minute)
 *    4m 12s         (unter einer Stunde)
 *    1h 03m 42s     (ab einer Stunde, mit Auffuellen der kleineren Einheiten)
 *    1h             (exakt eine Stunde)
 * </pre>
 */
public final class TimeFormat {

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;

    private TimeFormat() {
    }

    /**
     * Formatiert eine Dauer.
     *
     * @param duration Dauer (negative Werte werden als {@code 0s} dargestellt)
     * @param padZeroUnits {@code true} fuellt Minuten/Sekunden auf zwei Stellen
     *                     auf, sobald eine groessere Einheit angezeigt wird
     */
    public static @NotNull String format(final @NotNull Duration duration, final boolean padZeroUnits) {
        return format(duration.getSeconds(), padZeroUnits);
    }

    /**
     * Formatiert eine Anzahl Sekunden.
     *
     * @param totalSeconds Sekunden seit Event-Start
     * @param padZeroUnits Auffuellen kleinerer Einheiten (z. B. {@code 03m})
     */
    public static @NotNull String format(final long totalSeconds, final boolean padZeroUnits) {
        final long safe = Math.max(0L, totalSeconds);
        final long hours = safe / SECONDS_PER_HOUR;
        final long minutes = (safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        final long seconds = safe % SECONDS_PER_MINUTE;

        final StringBuilder builder = new StringBuilder(16);

        if (hours > 0L) {
            builder.append(hours).append('h');
            final boolean showMinutes = minutes > 0L || (padZeroUnits && seconds > 0L);
            if (showMinutes) {
                builder.append(' ').append(unit(minutes, padZeroUnits)).append('m');
            }
            if (seconds > 0L) {
                builder.append(' ').append(unit(seconds, padZeroUnits)).append('s');
            }
        } else if (minutes > 0L) {
            builder.append(minutes).append('m');
            if (seconds > 0L) {
                builder.append(' ').append(unit(seconds, padZeroUnits)).append('s');
            }
        } else {
            builder.append(seconds).append('s');
        }

        return builder.toString();
    }

    /** Formatiert Millisekunden. */
    public static @NotNull String formatMillis(final long millis, final boolean padZeroUnits) {
        return format(millis / 1000L, padZeroUnits);
    }

    private static @NotNull String unit(final long value, final boolean pad) {
        return pad ? Text.pad2(value) : Long.toString(value);
    }
}

package de.chaostobimc.chaosmanhunt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft das dynamische Timer-Format (BastiGHG-Stil). */
class TimeFormatTest {

    @Test
    @DisplayName("zeigt nur die Einheiten an, die wirklich gebraucht werden")
    void showsOnlyNecessaryUnits() {
        assertEquals("0s", TimeFormat.format(0L, true));
        assertEquals("15s", TimeFormat.format(15L, true));
        assertEquals("59s", TimeFormat.format(59L, true));
        assertEquals("4m 12s", TimeFormat.format(252L, true));
        assertEquals("1h 03m 42s", TimeFormat.format(3822L, true));
    }

    @Test
    @DisplayName("laesst leere Einheiten weg")
    void omitsEmptyUnits() {
        assertEquals("4m", TimeFormat.format(240L, true));
        assertEquals("1h", TimeFormat.format(3600L, true));
        assertEquals("1h 03m", TimeFormat.format(3780L, true));
    }

    @Test
    @DisplayName("fuellt Minuten und Sekunden ab einer Stunde auf")
    void padsSmallerUnitsWithLargerOnes() {
        assertEquals("1h 00m 10s", TimeFormat.format(3610L, true));
        assertEquals("1h 03m 42s", TimeFormat.format(3822L, true));
    }

    @Test
    @DisplayName("respektiert pad-zero-units=false")
    void supportsUnpaddedOutput() {
        assertEquals("1h 3m 42s", TimeFormat.format(3822L, false));
        assertEquals("1h 10s", TimeFormat.format(3610L, false));
        assertEquals("4m 12s", TimeFormat.format(252L, false));
    }

    @Test
    @DisplayName("negative Werte werden als 0s dargestellt")
    void clampsNegativeValues() {
        assertEquals("0s", TimeFormat.format(-42L, true));
    }

    @Test
    @DisplayName("Duration-Ueberladung verhaelt sich identisch")
    void supportsDurationOverload() {
        assertEquals("2m", TimeFormat.format(Duration.ofMinutes(2L), true));
        assertEquals("1h 01m 05s", TimeFormat.format(Duration.ofSeconds(3665L), true));
        assertEquals("0s", TimeFormat.format(Duration.ofMillis(500L), true));
    }
}

package de.chaostobimc.chaosmanhunt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft Himmelsrichtung und Entfernungsformatierung. */
class NavTest {

    @Test
    @DisplayName("erkennt die acht Himmelsrichtungen")
    void resolvesCardinalPoints() {
        assertEquals("N", Nav.direction(0.0D, 0.0D, 0.0D, -100.0D));
        assertEquals("S", Nav.direction(0.0D, 0.0D, 0.0D, 100.0D));
        assertEquals("O", Nav.direction(0.0D, 0.0D, 100.0D, 0.0D));
        assertEquals("W", Nav.direction(0.0D, 0.0D, -100.0D, 0.0D));
        assertEquals("NO", Nav.direction(0.0D, 0.0D, 100.0D, -100.0D));
        assertEquals("SW", Nav.direction(0.0D, 0.0D, -100.0D, 100.0D));
    }

    @Test
    @DisplayName("gleiche Position ergibt einen Strich")
    void handlesSamePosition() {
        assertEquals("—", Nav.direction(10.0D, 20.0D, 10.0D, 20.0D));
    }

    @Test
    @DisplayName("Portal-Umrechnung nutzt den Faktor 8")
    void scalesPortalDistances() {
        assertEquals(100.0D, Nav.scaleDistance(800.0D, true), 0.001D);
        assertEquals(800.0D, Nav.scaleDistance(100.0D, false), 0.001D);
    }
}

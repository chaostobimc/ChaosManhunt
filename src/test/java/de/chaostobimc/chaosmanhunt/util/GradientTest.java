package de.chaostobimc.chaosmanhunt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft die RGB-Interpolation und das Zuenden des Farbverlaufs. */
class GradientTest {

    private static final TextColor RED = TextColor.color(0xFF0000);
    private static final TextColor YELLOW = TextColor.color(0xFFFF00);
    /** Klassische Codes mappen auf die Minecraft-Palette (0xFF5555). */
    private static final TextColor LEGACY_RED = NamedTextColor.RED;

    @Test
    @DisplayName("erste und letzte Zeile entsprechen den Stuetzfarben")
    void rendersStopsAtBothEnds() {
        final Gradient gradient = Gradient.of(List.of("#FF0000", "#FFFF00"));
        final Component rendered = gradient.render("ab", 0.0D);

        assertEquals(2, rendered.children().size());
        assertEquals(RED, rendered.children().get(0).color());
        assertEquals(YELLOW, rendered.children().get(1).color());
    }

    @Test
    @DisplayName("interpoliert echte RGB-Werte dazwischen")
    void interpolatesBetweenStops() {
        final Gradient gradient = Gradient.of(List.of("#FF0000", "#FFFF00"));
        final Component rendered = gradient.render("abc", 0.0D);

        assertEquals(RED, rendered.children().get(0).color());
        assertEquals(TextColor.color(0xFF8000), rendered.children().get(1).color());
        assertEquals(YELLOW, rendered.children().get(2).color());
    }

    @Test
    @DisplayName("Animation verschiebt den Verlauf")
    void animationShiftsColors() {
        final Gradient gradient = Gradient.of(List.of("#FF0000", "#FFFF00"));
        final Component rendered = gradient.render("ab", 0.5D);

        assertEquals(TextColor.color(0xFF8000), rendered.children().get(0).color());
        assertEquals(TextColor.color(0xFF8000), rendered.children().get(1).color());
    }

    @Test
    @DisplayName("akzeptiert Hex-, Legacy- und Farbnamen")
    void parsesDifferentColorFormats() {
        assertEquals(RED, Gradient.parseColor("#FF0000"));
        assertEquals(RED, Gradient.parseColor("FF0000"));
        assertEquals(RED, Gradient.parseColor("&FF0000".substring(1)));
        assertEquals(LEGACY_RED, Gradient.parseColor("&c"));
        assertEquals(LEGACY_RED, Gradient.parseColor("red"));
        assertNotNull(Gradient.parseColor("#00FF00"));
    }

    @Test
    @DisplayName("faellt bei leerer Konfiguration auf einen Standardverlauf zurueck")
    void fallsBackToDefaultColors() {
        final Gradient gradient = Gradient.of(List.of());
        assertTrue(gradient.stops().size() >= 2);
        assertNotNull(gradient.render("12m 04s", 0.25D));
    }

    @Test
    @DisplayName("kommt mit Sonderzeichen und einzelnen Zeichen klar")
    void handlesSpecialCharacters() {
        final Gradient gradient = Gradient.of(List.of("#FF4D4D", "#FFFF66"));
        assertEquals(1, gradient.render("A", 0.0D).children().size());
        assertEquals(3, gradient.render("1h2", 0.0D).children().size());
        assertNotNull(gradient.render("", 0.5D));
    }
}

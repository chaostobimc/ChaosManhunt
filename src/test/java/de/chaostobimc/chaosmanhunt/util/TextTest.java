package de.chaostobimc.chaosmanhunt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft den Legacy-/Hex-Parser (ersetzt die nicht mehr vorhandene ChatColor-API). */
class TextTest {

    @AfterEach
    void clearCache() {
        Text.clearCache();
    }

    @Test
    @DisplayName("parst klassische Farben und Hex-Farben")
    void parsesLegacyAndHexColors() {
        final Component component = Text.parse("&cRot&#00FF00Gruen");
        assertEquals(2, component.children().size());
        assertEquals(NamedTextColor.RED, component.children().get(0).color());
        assertEquals(TextColor.color(0x00FF00), component.children().get(1).color());
    }

    @Test
    @DisplayName("parst das erweiterte Hex-Format &x&F&F&0&0&0&0")
    void parsesExtendedHexFormat() {
        final Component component = Text.parse("&x&F&F&4&D&4&DTimer");
        assertEquals(TextColor.color(0xFF4D4D), component.children().get(0).color());
    }

    @Test
    @DisplayName("parst Formatierungen wie fett und unterstrichen")
    void parsesDecorations() {
        final Component component = Text.parse("&l&nFett");
        assertEquals(TextDecoration.State.TRUE, component.children().get(0).decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.TRUE, component.children().get(0).decoration(TextDecoration.UNDERLINED));
    }

    @Test
    @DisplayName("reset beendet vorherige Formatierung")
    void supportsResetCode() {
        final Component component = Text.parse("&c&lA&rB");
        assertEquals(2, component.children().size());
        assertEquals(TextDecoration.State.TRUE, component.children().get(0).decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.NOT_SET, component.children().get(1).decoration(TextDecoration.BOLD));
    }

    @Test
    @DisplayName("entfernt alle Farbcodes und liefert reinen Text")
    void stripsColors() {
        assertEquals("AB", Text.stripColors("&cA&#00FF00B"));
        assertEquals("Timer", Text.stripColors("&l&nTimer"));
        assertEquals("1h 03m 42s", Text.stripColors("&#FF4D4D1h 03m 42s"));
    }

    @Test
    @DisplayName("null und leere Eingaben sind erlaubt")
    void handlesEmptyInput() {
        assertNotNull(Text.parse(null));
        assertNotNull(Text.parse(""));
        assertEquals("", Text.toPlain(Text.parse("")));
    }

    @Test
    @DisplayName("formatiert Zahlen sprachunabhaengig")
    void padsNumbers() {
        assertEquals("03", Text.pad2(3L));
        assertEquals("42", Text.pad2(42L));
    }
}

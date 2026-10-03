package de.chaostobimc.chaosmanhunt.util;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.World;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Zentrale Text-Werkstatt von ChaosManhunt.
 *
 * <p>Der Parser dieser Klasse versteht klassische Farbcodes ({@code &a}), echte
 * Hex-Farben ({@code &#FF4D4D}) sowie das Minecraft-eigene Hex-Format
 * ({@code &x&F&F&4&D&4&D}). Die Hex-Werte werden ueber
 * {@link Gradient#rgb(String)} erzeugt und damit - wie gewuenscht - aus
 * {@code ChatColor.of()} abgeleitet.</p>
 *
 * <p>Ergebnisse werden gecacht, damit der Actionbar-Task nicht pro Tick neue
 * Komponenten zusammensetzen muss.</p>
 */
public final class Text {

    /** Abschnittszeichen ({@code §}). */
    public static final char SECTION = '\u00A7';

    private static final int CACHE_LIMIT = 1024;
    private static final Style NO_ITALIC = Style.empty().decoration(TextDecoration.ITALIC, false);

    private static final Map<String, Component> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Component> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    private Text() {
    }

    /**
     * Wandelt einen Legacy-Text in eine Adventure-Komponente um.
     *
     * @param input Text mit {@code &}-Farbcodes (darf {@code null} sein)
     * @return fertige Komponente, niemals {@code null}
     */
    @Contract("null -> !null")
    public static @NotNull Component parse(final @Nullable String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        final Component cached = CACHE.get(input);
        if (cached != null) {
            return cached;
        }

        final Component component = parseUncached(input);
        CACHE.put(input, component);
        return component;
    }

    /**
     * Wie {@link #parse(String)}, aber fuer Item-Namen und Lore gedacht:
     * kursiver Standardstil des Clients wird deaktiviert.
     */
    @Contract("null -> !null")
    public static @NotNull Component parseItem(final @Nullable String input) {
        return parse(input).applyFallbackStyle(NO_ITALIC);
    }

    /** Wandelt eine Liste von Legacy-Zeilen in Komponenten um (Item-Lore). */
    public static @NotNull List<Component> parseItemLines(final @NotNull List<String> lines) {
        return lines.stream().map(Text::parseItem).toList();
    }

    /** Entfernt alle Farbcodes aus einem Text. */
    public static @NotNull String stripColors(final @NotNull String input) {
        return input
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)" + SECTION + "x(" + SECTION + "[0-9a-f]){6}", "")
                .replaceAll("(?i)[" + SECTION + "&][0-9a-fk-or]", "");
    }

    /** Wandelt eine Komponente in reinen Text um (ohne Farben). */
    public static @NotNull String toPlain(final @NotNull Component component) {
        return stripColors(LegacyComponentSerializer.legacySection().serialize(component));
    }

    /** Formatiert eine Zahl mit fuehrenden Nullen (sprachunabhaengig). */
    public static @NotNull String pad2(final long value) {
        return String.format(Locale.ROOT, "%02d", value);
    }

    /** Deutsche Bezeichnung einer Dimension. */
    public static @NotNull String dimensionName(final @Nullable World world) {
        if (world == null) {
            return "Unbekannt";
        }
        return dimensionName(world.getEnvironment(), world.getName());
    }

    /** Deutsche Bezeichnung einer Dimension. */
    public static @NotNull String dimensionName(final World.@NotNull Environment environment, final @NotNull String worldName) {
        return switch (environment) {
            case NORMAL -> "Oberwelt";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> worldName;
        };
    }

    /** Erzeugt einen Hex-String fuer Debug-Ausgaben. */
    public static @NotNull String toHex(final @NotNull TextColor color) {
        return String.format(Locale.ROOT, "#%06X", color.value());
    }

    private static @NotNull Component parseUncached(final @NotNull String input) {
        final net.kyori.adventure.text.TextComponent.Builder root = Component.text();
        final StringBuilder buffer = new StringBuilder();
        Style style = Style.empty();

        int index = 0;
        while (index < input.length()) {
            final char current = input.charAt(index);

            if ((current == '&' || current == SECTION) && index + 1 < input.length()) {
                final char code = Character.toLowerCase(input.charAt(index + 1));

                // &#RRGGBB
                if (code == '#' && index + 8 <= input.length() && isHex6(input, index + 2)) {
                    flush(root, buffer, style);
                    style = style.color(Gradient.rgb(input.substring(index + 2, index + 8)));
                    index += 8;
                    continue;
                }

                // &x&R&R&G&G&B&B
                if (code == 'x' && index + 14 <= input.length()) {
                    final String hex = readExtendedHex(input, index, current);
                    if (hex != null) {
                        flush(root, buffer, style);
                        style = style.color(Gradient.rgb(hex));
                        index += 14;
                        continue;
                    }
                }

                final TextColor legacyColor = legacyColor(code);
                if (legacyColor != null) {
                    flush(root, buffer, style);
                    style = style.color(legacyColor);
                    index += 2;
                    continue;
                }

                final TextDecoration decoration = legacyDecoration(code);
                if (decoration != null) {
                    flush(root, buffer, style);
                    style = style.decoration(decoration, true);
                    index += 2;
                    continue;
                }

                if (code == 'r') {
                    flush(root, buffer, style);
                    style = Style.empty();
                    index += 2;
                    continue;
                }
            }

            final int codePoint = input.codePointAt(index);
            buffer.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }

        flush(root, buffer, style);
        return root.build();
    }

    private static void flush(final net.kyori.adventure.text.TextComponent.Builder root,
                              final @NotNull StringBuilder buffer, final @NotNull Style style) {
        if (buffer.isEmpty()) {
            return;
        }
        root.append(Component.text(buffer.toString(), style));
        buffer.setLength(0);
    }

    private static boolean isHex6(final @NotNull String input, final int start) {
        if (start + 6 > input.length()) {
            return false;
        }
        for (int i = start; i < start + 6; i++) {
            if (Character.digit(input.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable String readExtendedHex(final @NotNull String input, final int start, final char marker) {
        final StringBuilder hex = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            final int position = start + 2 + (i * 2);
            if (position + 1 >= input.length() || input.charAt(position) != marker) {
                return null;
            }
            final char digit = input.charAt(position + 1);
            if (Character.digit(digit, 16) < 0) {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString();
    }

    private static @Nullable TextColor legacyColor(final char code) {
        return switch (code) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    private static @Nullable TextDecoration legacyDecoration(final char code) {
        return switch (code) {
            case 'k' -> TextDecoration.OBFUSCATED;
            case 'l' -> TextDecoration.BOLD;
            case 'm' -> TextDecoration.STRIKETHROUGH;
            case 'n' -> TextDecoration.UNDERLINED;
            case 'o' -> TextDecoration.ITALIC;
            default -> null;
        };
    }

    /** Nur fuer Tests / Diagnose: aktuelle Groesse des Text-Caches. */
    public static int cacheSize() {
        return CACHE.size();
    }

    /** Nur fuer Tests / Diagnose: Cache leeren. */
    public static void clearCache() {
        CACHE.clear();
    }

    /** Alle unterstuetzten Formatierungszeichen (Dokumentation/Tab-Vervollstaendigung). */
    public static @NotNull Set<TextDecoration> knownDecorations() {
        return EnumSet.allOf(TextDecoration.class);
    }
}

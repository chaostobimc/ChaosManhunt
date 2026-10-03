package de.chaostobimc.chaosmanhunt.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Echter Hex-Farbverlauf (Gradient) mit optionaler Animation.
 *
 * <p>Der Verlauf wird Zeichen fuer Zeichen (codepoint-sicher, also auch fuer
 * Emoji/Sonderzeichen) interpoliert. {@code phase} verschiebt den Verlauf,
 * wodurch der "fliessende" BastiGHG-Look entsteht.</p>
 */
public final class Gradient {

    private static final Gradient FALLBACK = new Gradient(List.of(
            rgb("#FF4D4D"), rgb("#FFC24D"), rgb("#FFFF66")));

    private final List<TextColor> stops;

    private Gradient(final @NotNull List<TextColor> stops) {
        this.stops = List.copyOf(stops);
    }

    /**
     * Erzeugt einen Verlauf aus Konfigurationswerten ({@code #RRGGBB},
     * {@code RRGGBB}, {@code &c} oder Farbnamen).
     *
     * @param colors Rohwerte aus der config.yml
     * @return Verlauf mit mindestens zwei Stuetzfarben
     */
    public static @NotNull Gradient of(final @Nullable List<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return FALLBACK;
        }

        final List<TextColor> parsed = new ArrayList<>(colors.size());
        for (final String raw : colors) {
            final TextColor color = parseColor(raw);
            if (color != null) {
                parsed.add(color);
            }
        }

        if (parsed.isEmpty()) {
            return FALLBACK;
        }
        if (parsed.size() == 1) {
            final TextColor only = parsed.get(0);
            parsed.add(only);
        }
        return new Gradient(parsed);
    }

    /** Erzeugt einen Verlauf aus bereits fertigen Farben. */
    public static @NotNull Gradient of(final @NotNull TextColor first, final @NotNull TextColor second,
                                       final @NotNull TextColor @NotNull... more) {
        final List<TextColor> list = new ArrayList<>(2 + more.length);
        list.add(first);
        list.add(second);
        list.addAll(List.of(more));
        return new Gradient(list);
    }

    /** Parst eine einzelne Farbangabe. */
    public static @Nullable TextColor parseColor(final @Nullable String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("&") || value.startsWith(String.valueOf(Text.SECTION))) {
            value = value.substring(1);
        }
        if (value.startsWith("#")) {
            value = value.substring(1);
        }

        if (value.length() == 6 && isHex(value)) {
            return rgb(value);
        }
        if (value.length() == 1) {
            return switch (Character.toLowerCase(value.charAt(0))) {
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
        return NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
    }

    /**
     * Erzeugt eine echte RGB-Farbe aus einem Hex-Wert ({@code RRGGBB}) ueber
     * {@link ChatColor#of(String) ChatColor.of()}.
     *
     * <p>Paper liefert die Bungee-Chat-API mit, {@code ChatColor.of()} ist damit
     * der klassische Weg zu Hex-Farben. Die fertige Farbe wird anschliessend in
     * eine Adventure-{@link TextColor} uebernommen, weil Paper 1.21.11 alle Texte
     * ueber Components faerbt und die alte Schreibweise ({@code §x§R§R§G§G§B§B})
     * dafuer nicht mehr noetig ist.</p>
     *
     * @param hex sechsstelliger Hex-Wert, mit oder ohne {@code #}
     * @return RGB-Farbe
     */
    public static @NotNull TextColor rgb(final @NotNull String hex) {
        final String normalized = hex.startsWith("#") ? hex : "#" + hex;
        try {
            return TextColor.color(ChatColor.of(normalized).getColor().getRGB() & 0xFFFFFF);
        } catch (final NoClassDefFoundError | IllegalArgumentException exception) {
            // Sicherheitsnetz: falls die Bungee-Chat-API in einer kuenftigen
            // Serverversion fehlt, wird der Hex-Wert direkt gelesen.
            return TextColor.color(Integer.parseInt(normalized.substring(1), 16));
        }
    }

    /** Liefert die Stuetzfarben dieses Verlaufs. */
    public @NotNull List<TextColor> stops() {
        return this.stops;
    }

    /**
     * Interpoliert eine Farbe an der Position {@code t}.
     *
     * @param t Position im Verlauf ({@code 0} = erste Farbe, {@code 1} = letzte Farbe);
     *          Werte ausserhalb werden begrenzt
     */
    public @NotNull TextColor sample(final double t) {
        if (this.stops.size() == 1) {
            return this.stops.get(0);
        }

        final double clamped = Math.max(0.0D, Math.min(1.0D, t));
        final double scaled = clamped * (this.stops.size() - 1);
        final int index = (int) Math.floor(scaled);
        if (index >= this.stops.size() - 1) {
            return this.stops.get(this.stops.size() - 1);
        }
        return lerp(this.stops.get(index), this.stops.get(index + 1), scaled - index);
    }

    /**
     * Rendert einen Text mit diesem Verlauf.
     *
     * @param text  Text (darf Leerzeichen/Sonderzeichen enthalten)
     * @param phase Verschiebung des Verlaufs, z. B. {@code 0.0} bis {@code 1.0}
     * @return Komponente mit farbigem Verlauf
     */
    public @NotNull Component render(final @NotNull String text, final double phase) {
        final Component result = text.isEmpty()
                ? Component.empty()
                : renderInternal(text, phase);
        return result;
    }

    private @NotNull Component renderInternal(final @NotNull String text, final double phase) {
        final int[] codePoints = text.codePoints().toArray();
        final net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        final int length = codePoints.length;

        for (int i = 0; i < length; i++) {
            final double base = length <= 1 ? 0.5D : (double) i / (length - 1);
            final double position = wrap(base + phase);
            builder.append(Component.text(new String(Character.toChars(codePoints[i]))).color(this.sample(position)));
        }
        return builder.build();
    }

    private static double wrap(double value) {
        double wrapped = value % 1.0D;
        if (wrapped < 0.0D) {
            wrapped += 1.0D;
        }
        return wrapped;
    }

    private static @NotNull TextColor lerp(final @NotNull TextColor from, final @NotNull TextColor to, final double factor) {
        final int red = (int) Math.round(from.red() + (to.red() - from.red()) * factor);
        final int green = (int) Math.round(from.green() + (to.green() - from.green()) * factor);
        final int blue = (int) Math.round(from.blue() + (to.blue() - from.blue()) * factor);
        return TextColor.color(red, green, blue);
    }

    private static boolean isHex(final @NotNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}

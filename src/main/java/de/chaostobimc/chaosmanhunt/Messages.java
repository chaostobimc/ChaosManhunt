package de.chaostobimc.chaosmanhunt;

import de.chaostobimc.chaosmanhunt.util.Text;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Nachrichten-Verwaltung.
 *
 * <p>Die Texte liegen in der {@code messages.yml}. Fehlt ein Schluessel (z. B.
 * weil eine aeltere Datei weiterverwendet wird), greift automatisch der
 * Standardwert aus der im Jar mitgelieferten Datei. Dadurch bleiben
 * Aktualisierungen kompatibel, ohne die Anpassungen der Nutzer zu ueberschreiben.</p>
 */
public final class Messages {

    private static final String FILE_NAME = "messages.yml";

    private final ChaosManhunt plugin;
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, String> defaults = new HashMap<>();

    Messages(final @NotNull ChaosManhunt plugin) {
        this.plugin = plugin;
        loadDefaults();
        reload();
    }

    /** Laedt die Standardwerte aus dem Jar. */
    private void loadDefaults() {
        try (InputStream stream = this.plugin.getResource(FILE_NAME)) {
            if (stream == null) {
                return;
            }
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            flatten(yaml, this.defaults);
        } catch (final IOException exception) {
            this.plugin.getLogger().warning("Standard-Nachrichten konnten nicht gelesen werden: " + exception.getMessage());
        }
    }

    /** Liest die Datei vom Server neu ein (legt sie bei Bedarf an). */
    public void reload() {
        this.plugin.saveResource(FILE_NAME, false);
        this.values.clear();

        final File file = new File(this.plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        flatten(yaml, this.values);
    }

    private static void flatten(final @NotNull ConfigurationSection section, final @NotNull Map<String, String> target) {
        flatten(section, "", target);
    }

    private static void flatten(final @NotNull ConfigurationSection section, final @NotNull String prefix,
                                final @NotNull Map<String, String> target) {
        for (final String key : section.getKeys(false)) {
            final String path = prefix.isEmpty() ? key : prefix + "." + key;
            final Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                flatten(nested, path, target);
            } else if (value instanceof List<?> list) {
                for (int index = 0; index < list.size(); index++) {
                    target.put(path + "." + index, String.valueOf(list.get(index)));
                }
            } else if (value != null) {
                target.put(path, String.valueOf(value));
            }
        }
    }

    /* --------------------------------------------------------------- Zugriff */

    /** Rohtext eines Schluessels (mit Standardwert-Fallback). */
    public @NotNull String raw(final @NotNull String key) {
        final String value = this.values.getOrDefault(key, this.defaults.get(key));
        if (value == null) {
            this.plugin.getLogger().warning("Fehlender Nachrichten-Schluessel: " + key);
            return key;
        }
        return value.replace("\\n", "\n");
    }

    /** Zeilenliste eines Schluessels ({@code key.0}, {@code key.1}, ...). */
    public @NotNull List<String> lines(final @NotNull String key) {
        final List<String> result = new ArrayList<>(8);
        for (int index = 0; index < 64; index++) {
            final String path = key + "." + index;
            final String value = this.values.getOrDefault(path, this.defaults.get(path));
            if (value == null) {
                break;
            }
            result.add(value.replace("\\n", "\n"));
        }
        return result;
    }

    /** Rohtext eines Schluessels mit eingesetzten Platzhaltern (ohne Farb-Parsing). */
    public @NotNull String text(final @NotNull String key, final String @NotNull... placeholders) {
        return replace(raw(key), placeholders);
    }

    /** Komponente ohne Prefix. */
    public @NotNull Component component(final @NotNull String key, final String @NotNull... placeholders) {
        return Text.parse(withPrefix(replace(raw(key), placeholders)));
    }

    /**
     * Komponente mit Prefix aus der {@code messages.yml}. Enthaelt die Nachricht
     * selbst ein {@code %prefix%}, wird nur dieses ersetzt.
     */
    public @NotNull Component prefixed(final @NotNull String key, final String @NotNull... placeholders) {
        final String message = replace(raw(key), placeholders);
        return Text.parse(message.contains("%prefix%")
                ? message.replace("%prefix%", raw("prefix"))
                : raw("prefix") + message);
    }

    /**
     * Actionbar-Text: ersetzt Platzhalter und setzt den fertigen Gradient-Timer
     * an die Stelle von {@code %time%}.
     *
     * @param template     Vorlage (aus der {@code config.yml})
     * @param time         bereits gefaerbte Zeit-Komponente
     * @param placeholders Paare aus Platzhalter und Wert
     */
    public @NotNull Component actionBarTemplate(final @NotNull String template, final @NotNull Component time,
                                                final String @NotNull... placeholders) {
        final String[] parts = replace(template, placeholders).split("%time%", -1);

        Component result = Component.empty();
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                result = result.append(time);
            }
            if (!parts[index].isEmpty()) {
                result = result.append(Text.parse(parts[index]));
            }
        }
        return result;
    }

    /** Actionbar-Text mit einer einfachen Zeitangabe (ohne Gradient). */
    public @NotNull Component actionBarPlain(final @NotNull String template, final @NotNull String timeText,
                                             final String @NotNull... placeholders) {
        return actionBarTemplate(template, Text.parse("&f" + timeText), placeholders);
    }

    /* ---------------------------------------------------------------- Senden */

    public void send(final @NotNull CommandSender sender, final @NotNull String key, final String @NotNull... placeholders) {
        sender.sendMessage(prefixed(key, placeholders));
    }

    public void sendRaw(final @NotNull CommandSender sender, final @NotNull String key, final String @NotNull... placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void broadcast(final @NotNull String key, final String @NotNull... placeholders) {
        final Component message = prefixed(key, placeholders);
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /* ---------------------------------------------------------------- Helfer */

    private @NotNull String withPrefix(final @NotNull String message) {
        return message.replace("%prefix%", raw("prefix"));
    }

    private static @NotNull String replace(final @NotNull String message, final String @NotNull... placeholders) {
        if (placeholders.length < 2) {
            return message;
        }
        String result = message;
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            result = result.replace("%" + placeholders[index] + "%", placeholders[index + 1]);
        }
        return result;
    }
}

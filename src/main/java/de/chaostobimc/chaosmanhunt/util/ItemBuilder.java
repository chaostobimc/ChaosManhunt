package de.chaostobimc.chaosmanhunt.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Kleiner, fliessender Item-Builder.
 *
 * <p>Arbeitet ausschliesslich mit Adventure-Komponenten, damit Hex-Farben in
 * Namen und Lore ohne Umwege funktionieren.</p>
 */
public final class ItemBuilder {

    private final ItemStack template;
    private final List<Component> lore = new ArrayList<>(4);
    private Component name;
    private boolean glint;
    private boolean hideAttributes;
    private Consumer<ItemMeta> customizer;
    private boolean replaceLore = true;

    private ItemBuilder(final @NotNull ItemStack template) {
        this.template = template;
    }

    /** Neues Item auf Basis eines Materials. */
    public static @NotNull ItemBuilder of(final @NotNull Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    /** Neues Item auf Basis eines vorhandenen Stacks (PDC/Name bleiben erhalten). */
    public static @NotNull ItemBuilder of(final @NotNull ItemStack template) {
        return new ItemBuilder(template.clone());
    }

    public @NotNull ItemBuilder amount(final int amount) {
        this.template.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public @NotNull ItemBuilder name(final @NotNull Component displayName) {
        this.name = displayName;
        return this;
    }

    public @NotNull ItemBuilder name(final @NotNull String legacyText) {
        return this.name(Text.parseItem(legacyText));
    }

    public @NotNull ItemBuilder addLore(final @NotNull String... legacyLines) {
        Arrays.stream(legacyLines).map(Text::parseItem).forEach(this.lore::add);
        return this;
    }

    public @NotNull ItemBuilder addLore(final @NotNull List<String> legacyLines) {
        legacyLines.stream().map(Text::parseItem).forEach(this.lore::add);
        return this;
    }

    public @NotNull ItemBuilder addLoreComponent(final @NotNull Component line) {
        this.lore.add(line);
        return this;
    }

    /** Ersetzt die vorhandene Lore des Templates nicht (Standard: ja). */
    public @NotNull ItemBuilder keepExistingLore() {
        this.replaceLore = false;
        return this;
    }

    public @NotNull ItemBuilder glint(final boolean enchantGlint) {
        this.glint = enchantGlint;
        return this;
    }

    public @NotNull ItemBuilder hideAttributes() {
        this.hideAttributes = true;
        return this;
    }

    public @NotNull ItemBuilder meta(final @NotNull Consumer<ItemMeta> metaCustomizer) {
        this.customizer = this.customizer == null
                ? metaCustomizer
                : this.customizer.andThen(metaCustomizer);
        return this;
    }

    /** Baut das fertige Item. */
    public @NotNull ItemStack build() {
        final ItemStack result = this.template.clone();
        if (result.getType() == Material.AIR) {
            return result;
        }

        result.editMeta(meta -> {
            if (this.name != null) {
                meta.displayName(this.name);
            }
            if (!this.lore.isEmpty()) {
                if (this.replaceLore || meta.lore() == null) {
                    meta.lore(List.copyOf(this.lore));
                }
            }
            if (this.glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (this.hideAttributes) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            if (this.customizer != null) {
                this.customizer.accept(meta);
            }
        });
        return result;
    }
}

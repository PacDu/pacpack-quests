package fr.pacdu.pacpackquests.client.util;

import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.util.RegistryType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class TranslationUtils {

    private TranslationUtils() {}

    /**
     * Formats the visual display text of a reward with appropriate styling and color.
     */
    public static Text getRewardText(RewardType type, String reward, int amount, boolean isClaimed) {
        if (type == null) return Text.empty();

        MutableText text = switch (type) {
            case XP -> Text.literal(amount + " XP");
            case LEVEL -> Text.literal(amount + " ").append(Text.translatable("gui.pacpack-quests.levels"));
            case ITEM -> Text.literal(amount + "x ").append(getTranslatedRewardName(reward, RegistryType.ITEM));
            case ENCHANTED_BOOK -> Text.literal(amount + "x ").append(getTranslatedRewardName(reward, RegistryType.ENCHANTMENT));
        };

        if (isClaimed) {
            return text.formatted(Formatting.DARK_GRAY, Formatting.STRIKETHROUGH);
        }

        return switch (type) {
            case XP -> text.formatted(Formatting.GREEN);
            case LEVEL -> text.formatted(Formatting.DARK_GREEN, Formatting.BOLD);
            case ITEM -> text.formatted(Formatting.WHITE);
            case ENCHANTED_BOOK -> text.formatted(Formatting.LIGHT_PURPLE);
        };
    }

    /**
     * Translate an id (e.g. "minecraft:stone", "#minecraft:logs", or an enchanted book) into a readable name.
     * @param entryId a valid ID or tag.
     * @param type the type of the ID corresponding to a Minecraft Registry.
     */
    public static String getTranslatedName(String entryId, RegistryType type) {
        return getTranslatedName(entryId, type, false);
    }

    /**
     * Translate an id specifically for a reward into a readable name (e.g. "Random Logs" instead of "Any Logs").
     */
    public static String getTranslatedRewardName(String entryId, RegistryType type) {
        return getTranslatedName(entryId, type, true);
    }

    public static String getTranslatedName(String entryId, RegistryType type, boolean isReward) {
        if (entryId == null || entryId.isEmpty()) return "...";

        if (type == RegistryType.ENCHANTMENT || entryId.startsWith("enchanted_book:") || entryId.startsWith("minecraft:enchanted_book:")) {
            return getEnchantedBookName(entryId);
        }

        boolean isTag = entryId.startsWith("#");
        String rawId = isTag ? entryId.substring(1) : entryId;
        Identifier id = Identifier.tryParse(rawId);

        if (id == null) return entryId;

        if (isTag) {
            String path = id.getPath();
            String prefix = isReward ? "Random " : "Any ";
            return prefix + path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
        }

        return switch (type) {
            case ITEM -> Registries.ITEM.containsId(id) ? Registries.ITEM.get(id).getName().getString() : rawId;
            case BLOCK -> Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id).getName().getString() : rawId;
            case MOB -> Registries.ENTITY_TYPE.containsId(id) ? Registries.ENTITY_TYPE.get(id).getName().getString() : rawId;
            default -> {
                String path = id.getPath();
                yield path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
            }
        };
    }

    private static String getEnchantedBookName(String entryId) {
        String cleanId = entryId.startsWith("minecraft:") ? entryId.substring("minecraft:".length()) : entryId;
        if (!cleanId.startsWith("enchanted_book:")) {
            return entryId;
        }

        String[] parts = cleanId.split(":");
        if (parts.length >= 4) {
            Identifier enchId = Identifier.of(parts[1], parts[2]);
            int level = 1;
            try {
                level = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {}

            var world = MinecraftClient.getInstance().world;
            if (world != null) {
                var optReg = world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
                if (optReg.isPresent()) {
                    Registry<Enchantment> reg = optReg.get();
                    Enchantment ench = reg.get(enchId);
                    if (ench != null) {
                        RegistryEntry<Enchantment> entry = reg.getEntry(ench);
                        Text enchName = Enchantment.getName(entry, level);
                        return I18n.translate("item.minecraft.enchanted_book") + " (" + enchName.getString() + ")";
                    }
                }
            }

            String path = enchId.getPath();
            String formattedEnch = path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
            return "Enchanted Book (" + formattedEnch + " " + level + ")";
        }

        return entryId;
    }
}

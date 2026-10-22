package fr.pacdu.pacpackquests.client.util;

import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.client.PacPackQuestsClient;
import fr.pacdu.pacpackquests.util.ItemUtils;
import fr.pacdu.pacpackquests.util.RegistryType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

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
            case COMMAND -> Text.literal(amount + "x ").append(Text.literal((reward.startsWith("/") ? "" : "/") + (reward.length() > 64 ? reward.substring(0, 64) + "..." : reward)));
        };

        if (isClaimed) {
            return text.formatted(Formatting.DARK_GRAY, Formatting.STRIKETHROUGH);
        }

        return switch (type) {
            case XP -> text.formatted(Formatting.GREEN);
            case LEVEL -> text.formatted(Formatting.DARK_GREEN, Formatting.BOLD);
            case ITEM -> text.formatted(Formatting.WHITE);
            case COMMAND -> text.formatted(Formatting.YELLOW);
        };
    }

    /**
     * Translate an id (e.g. "minecraft:stone", "#minecraft:logs", or item with components) into a readable name.
     * @param entryId a valid ID or tag.
     * @param type the taskType of the ID corresponding to a Minecraft Registry.
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
        if (entryId == null || entryId.isEmpty() || type == null) return "...";

        boolean isTag = entryId.startsWith("#");
        boolean isComplexItem = entryId.contains("[");
        String rawId = isTag ? entryId.substring(1) : entryId;
        rawId = isComplexItem ? rawId.substring(0, entryId.indexOf('[')) : rawId;
        Identifier id = Identifier.tryParse(rawId);

        if (id == null) return entryId;

        if (isTag) {
            String path = id.getPath();
            String prefix = isReward ? "Random " : "Any ";
            return prefix + path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
        }

        return switch (type) {
            case ITEM -> {
                if (isComplexItem) {
                    var world = MinecraftClient.getInstance().world;
                    var reg = world != null ? world.getRegistryManager() : null;
                    ItemStack stack = ItemUtils.fromCommandString(entryId, reg);
                    if (!stack.isEmpty()) {
                        if (stack.isOf(Items.ENCHANTED_BOOK)) {
                            var stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
                            if (stored != null && !stored.isEmpty()) {
                                var firstEntry = stored.getEnchantmentEntries().iterator().next();
                                Text enchText = Enchantment.getName(firstEntry.getKey(), firstEntry.getIntValue());
                                yield stack.getName().getString() + " (" + enchText.getString() + ")";
                            }
                        }
                        yield stack.getName().getString();
                    }
                }
                yield Registries.ITEM.get(id).getName().getString();
            }
            case BLOCK -> Registries.BLOCK.get(id).getName().getString();
            case MOB -> Registries.ENTITY_TYPE.get(id).getName().getString();
            case COMMAND -> {
                String command = (entryId.startsWith("/") ? "" : "/") + entryId;
                if (command.length() > 24) {
                    command = command.substring(0, 24) + "...";
                }
                yield command;
            }
            default -> {
                String path = id.getPath();
                yield path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
            }
        };
    }

    // Helper to format the long IDs into short readable button texts (e.g., "minecraft:stone" -> "Stone")
    public static String formatDisplayString(String raw) {
        if (raw == null || raw.isEmpty()) return "...";
        boolean isTag = raw.startsWith("#");
        String clean = isTag ? raw.substring(1) : raw;
        if (clean.contains("[")) clean = clean.substring(0, clean.indexOf('['));
        Identifier id = Identifier.tryParse(clean);
        if (id == null) return raw;

        String path = id.getPath();
        String formatted = path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
        if (formatted.length() > 18) formatted = formatted.substring(0, 15) + "..."; // Truncate long names
        return isTag ? "# " + formatted : formatted;
    }

    /**
     * Resolves human-readable mod name for a given namespace.
     */
    public static String getModName(String namespace) {
        if (namespace == null || namespace.isEmpty() || namespace.equals("minecraft")) {
            return "Minecraft";
        } else if (namespace.equals("c")) {
            return "Common";
        }
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getName())
                .orElse(namespace);
    }

    /**
     * Returns the number of elements contained in a tag for any given RegistryType.
     */
    public static int getTagSize(Identifier tagId, RegistryType type) {
        if (tagId == null || type == null) return -1;
        MinecraftClient client = MinecraftClient.getInstance();

        return switch (type) {
            case ITEM -> Registries.ITEM.getOptional(TagKey.of(RegistryKeys.ITEM, tagId))
                    .map(RegistryEntryList::size).orElse(-1);
            case BLOCK -> Registries.BLOCK.getOptional(TagKey.of(RegistryKeys.BLOCK, tagId))
                    .map(RegistryEntryList::size).orElse(-1);
            case MOB -> Registries.ENTITY_TYPE.getOptional(TagKey.of(RegistryKeys.ENTITY_TYPE, tagId))
                    .map(RegistryEntryList::size).orElse(-1);
            case BIOME -> {
                if (client != null && client.world != null) {
                    yield client.world.getRegistryManager().getOrThrow(RegistryKeys.BIOME)
                            .getOptional(TagKey.of(RegistryKeys.BIOME, tagId))
                            .map(RegistryEntryList::size).orElse(-1);
                }
                yield -1;
            }
            case STRUCTURE -> {
                if (client != null && client.getServer() != null) {
                    var opt = client.getServer().getRegistryManager().getOptional(RegistryKeys.STRUCTURE);
                    if (opt.isPresent()) {
                        yield opt.get().getOptional(TagKey.of(RegistryKeys.STRUCTURE, tagId))
                                .map(RegistryEntryList::size).orElse(-1);
                    }
                }
                yield -1;
            }
            case LOOTABLE -> {
                if (tagId.equals(Identifier.of("pacpackquests", "loot_chest"))) {
                    yield PacPackQuestsClient.SERVER_LOOT_TABLES.size();
                }
                yield -1;
            }
            default -> -1;
        };
    }
}

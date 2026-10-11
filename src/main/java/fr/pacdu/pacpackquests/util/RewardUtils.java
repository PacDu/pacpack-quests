package fr.pacdu.pacpackquests.util;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.data.RewardEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public class RewardUtils {

    private RewardUtils() {}

    /**
     * Creates an ItemStack representing the reward, preserving components like stored enchantments.
     * If the target is a tag (e.g. "#minecraft:logs"), returns the first item from the tag for representation.
     */
    public static ItemStack createRewardStack(String rewardId, RewardType rewardType, DynamicRegistryManager registryManager) {
        if (rewardType == null) return ItemStack.EMPTY;
        return switch (rewardType) {
            case XP -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case LEVEL -> new ItemStack(Items.ENCHANTED_BOOK);
            case ENCHANTED_BOOK -> createEnchantedBookStack(rewardId, registryManager);
            case ITEM -> {
                if (rewardId == null) yield ItemStack.EMPTY;
                if (rewardId.startsWith("#")) {
                    Identifier tagId = Identifier.tryParse(rewardId.substring(1));
                    yield tagId != null ? new ItemStack(TagUtils.getFirstItemInTag(tagId)) : ItemStack.EMPTY;
                }
                Identifier id = Identifier.tryParse(rewardId);
                yield id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
            }
        };
    }

    /**
     * Builds an enchanted book ItemStack for entry strings formatted as "enchanted_book:namespace:path:level".
     */
    public static ItemStack createEnchantedBookStack(String rewardId, DynamicRegistryManager registryManager) {
        if (rewardId == null) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }

        String cleanId = rewardId.startsWith("minecraft:enchanted_book:") ? rewardId.substring("minecraft:".length()) : rewardId;
        if (!cleanId.startsWith("enchanted_book:")) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }

        String[] parts = cleanId.split(":");
        if (parts.length >= 4) {
            Identifier enchId = Identifier.of(parts[1], parts[2]);
            int level = 1;
            try {
                level = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {}

            if (registryManager != null) {
                var optReg = registryManager.getOptional(RegistryKeys.ENCHANTMENT);
                if (optReg.isPresent()) {
                    Registry<Enchantment> reg = optReg.get();
                    Enchantment ench = reg.get(enchId);
                    if (ench != null) {
                        RegistryEntry<Enchantment> entry = reg.getEntry(ench);
                        return EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(entry, level));
                    }
                }
            }
        }
        return new ItemStack(Items.ENCHANTED_BOOK);
    }

    /**
     * Grants the reward from a quest definition to a server player.
     */
    public static void grantReward(ServerPlayerEntity player, QuestDefinition quest, DynamicRegistryManager registryManager) {
        if (quest != null) {
            grantReward(player, quest.rewardType(), quest.reward(), quest.rewardAmount(), registryManager);
        }
    }

    /**
     * Grants an individual RewardEntry to a server player.
     */
    public static void grantReward(ServerPlayerEntity player, RewardEntry entry, DynamicRegistryManager registryManager) {
        if (entry != null) {
            grantReward(player, entry.type(), entry.target(), entry.amount(), registryManager);
        }
    }

    /**
     * Grants a specific reward type, target, and amount to a server player.
     * Supports:
     * - XP points
     * - Experience levels
     * - Enchanted books with specific enchantments and levels
     * - Fixed items
     * - Item tags (picks a random item from the tag)
     */
    public static void grantReward(ServerPlayerEntity player, RewardType type, String target, int amount, DynamicRegistryManager registryManager) {
        if (type == null || player == null || amount <= 0) return;

        switch (type) {
            case XP -> player.addExperience(amount);

            case LEVEL -> player.addExperienceLevels(amount);

            case ENCHANTED_BOOK -> {
                ItemStack book = createEnchantedBookStack(target, registryManager);
                if (!book.isEmpty()) {
                    book.setCount(amount);
                    giveItemStack(player, book);
                }
            }

            case ITEM -> {
                if (target == null || target.isEmpty()) return;

                // Random reward from a tag (e.g. "#minecraft:logs")
                if (target.startsWith("#")) {
                    Identifier tagId = Identifier.tryParse(target.substring(1));
                    if (tagId != null) {
                        List<Item> items = TagUtils.getAllItemsInTag(tagId);
                        if (!items.isEmpty()) {
                            Item randomItem = items.get(player.getRandom().nextInt(items.size()));
                            giveItemStack(player, new ItemStack(randomItem, amount));
                            return;
                        }
                    }
                }

                // Regular item
                Identifier id = Identifier.tryParse(target);
                if (id != null && Registries.ITEM.containsId(id)) {
                    giveItemStack(player, new ItemStack(Registries.ITEM.get(id), amount));
                }
            }
        }
    }

    /**
     * Safely gives an ItemStack to a player, dropping it at their feet if the inventory is full.
     */
    public static void giveItemStack(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}

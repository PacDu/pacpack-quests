package fr.pacdu.pacpackquests.util;

import fr.pacdu.pacpackquests.RewardType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class RewardUtils {

    private RewardUtils() {}

    /**
     * Creates an ItemStack representing the reward, preserving components like stored enchantments.
     */
    public static ItemStack createRewardStack(String rewardId, RewardType rewardType, DynamicRegistryManager registryManager) {
        if (rewardType == null) return ItemStack.EMPTY;
        return switch (rewardType) {
            case XP -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case LEVEL -> new ItemStack(Items.ENCHANTED_BOOK);
            case ENCHANTED_BOOK -> createEnchantedBookStack(rewardId, registryManager);
            case ITEM -> {
                if (rewardId == null) yield ItemStack.EMPTY;
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
}

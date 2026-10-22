package fr.pacdu.pacpackquests.util;

import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.data.RewardEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public class RewardUtils {

    private RewardUtils() {}

    /**
     * Creates an ItemStack representing the reward, preserving components like stored enchantments or potion contents.
     * If the target is a tag (e.g. "#minecraft:logs"), returns the first item from the tag for representation.
     */
    public static ItemStack createRewardStack(String rewardId, RewardType rewardType, RegistryWrapper.WrapperLookup registryManager) {
        if (rewardType == null) return ItemStack.EMPTY;
        return switch (rewardType) {
            case XP -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case LEVEL -> new ItemStack(Items.ENCHANTED_BOOK);
            case ITEM -> {
                if (rewardId == null) yield ItemStack.EMPTY;
                yield ItemUtils.fromCommandString(rewardId, registryManager);
            }
            case COMMAND -> new ItemStack(Items.WRITABLE_BOOK);
        };
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
     * Grants a specific reward type, target, and amount to a server player.
     */
    public static void grantReward(ServerPlayerEntity player, RewardType type, String target, int amount, DynamicRegistryManager registryManager) {
        if (type == null || player == null || amount <= 0) return;

        switch (type) {
            case XP -> player.addExperience(amount);

            case LEVEL -> player.addExperienceLevels(amount);

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

                // Item with components or standard item
                ItemStack rewardStack = ItemUtils.fromCommandString(target, registryManager);
                if (!rewardStack.isEmpty()) {
                    rewardStack.setCount(amount);
                    giveItemStack(player, rewardStack);
                }
            }

            case COMMAND -> {
                if (target == null || target.isEmpty()) return;

                MinecraftServer server = player.getCommandSource().getServer();
                String cleanCommand = target.startsWith("/") ? target.substring(1) : target;

                ServerCommandSource source = server.getCommandSource()
                        .withEntity(player)
                        .withPosition(player.getEntityPos())
                        .withRotation(player.getRotationClient())
                        .withWorld(player.getEntityWorld())
                        .withSilent();

                try {
                    for (int i = 0; i < amount; i++) {
                        server.getCommandManager().getDispatcher().execute(cleanCommand, source);
                    }
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    PacPackQuests.LOGGER.error("Failed to execute quest reward command: {}", cleanCommand, e);
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

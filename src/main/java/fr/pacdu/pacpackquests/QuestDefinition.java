package fr.pacdu.pacpackquests;

import net.minecraft.item.ItemStack;
import java.util.List;

/**
 * Represents the static configuration of a quest.
 * This record holds all defining properties of a quest such as its objectives, rewards, and display settings.
 */
public record QuestDefinition(
        String id, String title, String category, TaskType type, String target,
        int requiredAmount, ItemStack icon, ItemStack reward, RewardType rewardType,
        int rewardAmount, List<String> parents, int displayX, int displayY
) {}
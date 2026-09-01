package fr.pacdu.pacpackquests;

import net.minecraft.item.ItemStack;
import java.util.List;

public record QuestDefinition(
        String id, String title, String category, TaskType type, String target,
        int requiredAmount, ItemStack icon, ItemStack reward, int rewardAmount,
        List<String> parents, int displayX, int displayY
) {}
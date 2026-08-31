package fr.pacdu.pacpackquests;

import net.minecraft.item.ItemStack;

// The "target" field will contain for exemple "#minecraft:logs" or "minecraft:zombie"
public record QuestDefinition(String id, String title, String category, TaskType type, String target, int requiredAmount, ItemStack icon, ItemStack reward, int rewardAmount) {}
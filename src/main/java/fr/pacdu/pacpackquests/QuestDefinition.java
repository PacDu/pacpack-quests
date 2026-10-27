package fr.pacdu.pacpackquests;

import fr.pacdu.pacpackquests.reward.*;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents the static configuration of a quest.
 * A quest is an aggregate holding display metadata, an extensible list of tasks (objectives),
 * an extensible list of rewards, and arbitrary properties/descriptions.
 */
public record QuestDefinition(
        String id,
        String title,
        String category,
        ItemStack icon,
        int displayX,
        int displayY,
        List<String> parents,
        Map<String, String> properties,
        List<QuestTask> tasks,
        List<QuestReward> rewards
) {

    public QuestDefinition {
        if (parents == null) parents = new ArrayList<>();
        if (properties == null) properties = Map.of();
        if (tasks == null) tasks = List.of(new QuestTask(TaskType.MINE_BLOCK, "minecraft:stone", 1));
        if (rewards == null) rewards = List.of(new ItemReward("minecraft:diamond", 1));
    }

    public static QuestReward createReward(RewardType type, String target, int amount, String description) {
        if (type == null) return new ItemReward("minecraft:diamond", 1);
        return switch (type) {
            case ITEM -> new ItemReward(target != null ? target : "", amount);
            case XP -> new XpReward(amount);
            case LEVEL -> new LevelReward(amount);
            case COMMAND -> new CommandReward(target != null ? target : "", description != null ? description : "", amount);
        };
    }

    // --- Convenience Delegating Accessors ---

    public QuestTask primaryTask() {
        return (tasks != null && !tasks.isEmpty()) ? tasks.getFirst() : new QuestTask(TaskType.MINE_BLOCK, "minecraft:stone", 1);
    }

    public QuestReward primaryReward() {
        return (rewards != null && !rewards.isEmpty()) ? rewards.getFirst() : new ItemReward("minecraft:stone", 1);
    }

    public TaskType taskType() {
        return primaryTask().type();
    }

    public String target() {
        return primaryTask().target();
    }

    public int requiredAmount() {
        return primaryTask().requiredAmount();
    }

    public RewardType rewardType() {
        return primaryReward().type();
    }

    public String reward() {
        return switch (primaryReward()) {
            case ItemReward item -> item.target();
            case CommandReward cmd -> cmd.command();
            case XpReward xp -> "xp";
            case LevelReward lvl -> "level";
        };
    }

    public int rewardAmount() {
        return primaryReward().amount();
    }

    public String commandDescription() {
        return primaryReward() instanceof CommandReward cmd && cmd.description() != null && !cmd.description().isBlank() ? cmd.description() : "";
    }

    // --- Withers ---

    public QuestDefinition withPosition(int x, int y) {
        return new QuestDefinition(id, title, category, icon, x, y, parents, properties, tasks, rewards);
    }

    public QuestDefinition withCategory(String newCategory) {
        return new QuestDefinition(id, title, newCategory, icon, displayX, displayY, parents, properties, tasks, rewards);
    }

    public QuestDefinition withIdAndTitle(String newId, String newTitle) {
        return new QuestDefinition(newId, newTitle, category, icon, displayX, displayY, parents, properties, tasks, rewards);
    }

    public QuestDefinition withParents(List<String> newParents) {
        return new QuestDefinition(id, title, category, icon, displayX, displayY, newParents, properties, tasks, rewards);
    }
}

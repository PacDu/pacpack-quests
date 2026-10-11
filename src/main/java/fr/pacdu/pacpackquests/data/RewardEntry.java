package fr.pacdu.pacpackquests.data;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;

/**
 * Represents an individual reward entry (e.g. an item, an amount of XP, a level, or an enchanted book).
 * Used both for standard quest rewards and as items in future reward pools / random lists.
 */
public record RewardEntry(RewardType type, String target, int amount) {

    public static RewardEntry of(RewardType type, String target, int amount) {
        return new RewardEntry(type, target, amount);
    }

    public static RewardEntry fromQuest(QuestDefinition quest) {
        return new RewardEntry(quest.rewardType(), quest.reward(), quest.rewardAmount());
    }

    public boolean isTag() {
        return target != null && target.startsWith("#");
    }
}

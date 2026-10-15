package fr.pacdu.pacpackquests;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines the taskType of reward a player receives upon completing a quest.
 */
public enum RewardType {
    ITEM,
    XP,
    LEVEL,
    ENCHANTED_BOOK,
    COMMAND;

    // Caching values at startup
    private static final Map<String, RewardType> REWARD_TYPE_MAP = new HashMap<>();

    static {
        for (RewardType type : values()) {
            REWARD_TYPE_MAP.put(type.name(), type);
        }
    }

    public static boolean isValid(String name) {
        return name != null && REWARD_TYPE_MAP.containsKey(name);
    }
}
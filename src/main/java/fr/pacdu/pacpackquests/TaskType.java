package fr.pacdu.pacpackquests;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines the different types of tasks a player can complete in a quest.
 * These types determine how progress is tracked (e.g., inventory tracking vs event listening).
 */
public enum TaskType {
    MINE_BLOCK,
    KILL_MOB,
    CRAFT_ITEM,
    EXPLORE_BIOME,
    EXPLORE_STRUCTURE,
    EXPLORE_DIMENSION,
    OBTAIN_ITEM;

    // Caching values at startup
    private static final Map<String, TaskType> TASK_TYPE_MAP = new HashMap<>();

    static {
        for (TaskType type : values()) {
            TASK_TYPE_MAP.put(type.name(), type);
        }
    }

    public static boolean isValid(String name) {
        return name != null && TASK_TYPE_MAP.containsKey(name);
    }
}
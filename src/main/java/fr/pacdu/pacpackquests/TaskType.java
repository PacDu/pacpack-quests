package fr.pacdu.pacpackquests;

import fr.pacdu.pacpackquests.util.RegistryType;

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
    OBTAIN_ITEM,
    OPEN_LOOT_CHEST;

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

    public static RegistryType getRegistryType(TaskType type) {
        return switch (type) {
            case MINE_BLOCK -> RegistryType.BLOCK;
            case KILL_MOB -> RegistryType.MOB;
            case CRAFT_ITEM, OBTAIN_ITEM -> RegistryType.ITEM;
            case EXPLORE_BIOME -> RegistryType.BIOME;
            case EXPLORE_STRUCTURE -> RegistryType.STRUCTURE;
            case EXPLORE_DIMENSION -> RegistryType.DIMENSION;
            case OPEN_LOOT_CHEST -> RegistryType.LOOTABLE;
        };
    }
}
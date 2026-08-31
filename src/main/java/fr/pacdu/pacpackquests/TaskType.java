package fr.pacdu.pacpackquests;

import java.util.HashMap;
import java.util.Map;

public enum TaskType {
    MINE_BLOCK,
    KILL_MOB,
    CRAFT_ITEM;

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
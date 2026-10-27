package fr.pacdu.pacpackquests.data;

import com.google.gson.*;
import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.QuestTask;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.reward.*;
import fr.pacdu.pacpackquests.util.ItemUtils;
import fr.pacdu.pacpackquests.util.PathSecurity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Handles the loading and management of quest definitions from JSON files on the server.
 * Quests are loaded from the config directory and stored in memory.
 */
public class QuestManager {

    public static final Map<String, QuestDefinition> LOADED_QUESTS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> QUEST_DEFINITION_REQUIRED_LIST = Arrays.asList(
            "title", "icon", "displayX", "displayY", "tasks", "rewards"
    );

    public static void loadQuests(MinecraftServer server) {
        loadQuests(server != null ? server.getRegistryManager() : null);
    }

    public static void loadQuests(RegistryWrapper.WrapperLookup registries) {
        LOADED_QUESTS.clear();
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests");

        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (Exception e) {
                PacPackQuests.LOGGER.error("Failed to create quests directory.", e);
            }
            return;
        }

        try (Stream<Path> paths = Files.walk(configDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path))) {
                            JsonObject json = GSON.fromJson(reader, JsonObject.class);

                            Path relativePath = configDir.relativize(path);
                            String pathStr = relativePath.toString().replace('\\', '/');

                            assertParseAndAddQuest(pathStr, json, registries);
                        } catch (Exception e) {
                            PacPackQuests.LOGGER.error("Failed to load config quest: {}", path.getFileName(), e);
                        }
                    });
        } catch (Exception e) {
            PacPackQuests.LOGGER.error("Unable to read the configuration directory.", e);
        }

        PacPackQuests.LOGGER.info("Successfully loaded {} quests!", LOADED_QUESTS.size());
    }

    public static boolean saveQuest(QuestDefinition quest, RegistryWrapper.WrapperLookup registries) {
        Optional<Path> categoryDirOpt = PathSecurity.getSafeCategoryDir(quest.category());
        Optional<Path> questFileOpt = PathSecurity.getSafeQuestFile(quest.category(), quest.id());
        if (categoryDirOpt.isEmpty() || questFileOpt.isEmpty()) {
            PacPackQuests.LOGGER.warn("Blocked unsafe save quest attempt: category={}, id={}", quest.category(), quest.id());
            return false;
        }

        Path categoryDir = categoryDirOpt.get();
        try { Files.createDirectories(categoryDir); } catch (Exception ignored) {}

        Path questFile = questFileOpt.get();

        JsonObject json = new JsonObject();
        json.addProperty("title", quest.title());
        json.addProperty("icon", ItemUtils.toCommandString(quest.icon(), registries));
        json.addProperty("displayX", quest.displayX());
        json.addProperty("displayY", quest.displayY());

        if (quest.parents() != null && !quest.parents().isEmpty()) {
            json.add("parents", GSON.toJsonTree(quest.parents()));
        }

        if (quest.properties() != null && !quest.properties().isEmpty()) {
            json.add("properties", GSON.toJsonTree(quest.properties()));
        }

        // Tasks array
        JsonArray tasksArray = new JsonArray();
        for (QuestTask task : quest.tasks()) {
            JsonObject taskObj = new JsonObject();
            taskObj.addProperty("type", task.type().name());
            taskObj.addProperty("target", task.target());
            taskObj.addProperty("requiredAmount", task.requiredAmount());
            tasksArray.add(taskObj);
        }
        json.add("tasks", tasksArray);

        // Rewards array
        JsonArray rewardsArray = new JsonArray();
        for (QuestReward reward : quest.rewards()) {
            JsonObject rewObj = new JsonObject();
            rewObj.addProperty("type", reward.type().name());
            switch (reward) {
                case ItemReward ir -> {
                    rewObj.addProperty("target", ir.target());
                    rewObj.addProperty("amount", ir.amount());
                }
                case CommandReward cr -> {
                    rewObj.addProperty("command", cr.command());
                    if (cr.description() != null && !cr.description().isBlank()) {
                        rewObj.addProperty("description", cr.description());
                    }
                    rewObj.addProperty("amount", cr.amount());
                }
                case XpReward xr -> rewObj.addProperty("amount", xr.amount());
                case LevelReward lr -> rewObj.addProperty("amount", lr.amount());
            }
            rewardsArray.add(rewObj);
        }
        json.add("rewards", rewardsArray);

        try {
            Files.writeString(questFile, GSON.toJson(json));
            QuestManager.LOADED_QUESTS.put(quest.id(), quest);
        } catch (Exception e) {
            PacPackQuests.LOGGER.error("Failed to save quest", e);
        }
        return true;
    }

    private static void assertParseAndAddQuest(String filePath, JsonObject json, RegistryWrapper.WrapperLookup registries) {
        int lastSlash = filePath.lastIndexOf('/');
        String questId = filePath.substring(lastSlash + 1, filePath.length() - 5);

        StringBuilder errorMessage = new StringBuilder();

        // Check required properties
        for (String e : QUEST_DEFINITION_REQUIRED_LIST) {
            if (!json.has(e)) {
                errorMessage.append("Variable \"").append(e).append("\" is missing in the quest ").append(questId).append(".");
                PacPackQuests.LOGGER.warn(errorMessage.toString());
                return;
            }
        }

        String title = json.get("title").getAsString();
        if (title.isEmpty()) errorMessage.append(" The title cannot be empty,");

        if (lastSlash == -1) errorMessage.append(" The quest must be in a category (a subfolder),");
        String category = lastSlash == -1 ? "error" : filePath.substring(0, lastSlash);

        ItemStack icon = ItemUtils.fromCommandString(json.get("icon").getAsString(), registries);
        if (Objects.equals(icon, ItemStack.EMPTY)) errorMessage.append(" The icon ").append(json.get("icon")).append(" does not exist,");

        int displayX = json.get("displayX").getAsInt();
        int displayY = json.get("displayY").getAsInt();

        // Parse optional parents array
        List<String> parents = new ArrayList<>();
        if (json.has("parents")) {
            JsonArray parentsArray = json.getAsJsonArray("parents");
            for (JsonElement element : parentsArray) {
                if (!element.getAsString().equals(questId))
                    parents.add(element.getAsString());
                else
                    PacPackQuests.LOGGER.warn("Error in the quest \"{}\" : a quest can't have itself as parent", questId);
            }
        }

        // Parse optional properties map
        Map<String, String> properties = new HashMap<>();
        if (json.has("properties") && json.get("properties").isJsonObject()) {
            JsonObject propObj = json.getAsJsonObject("properties");
            for (Map.Entry<String, JsonElement> entry : propObj.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    properties.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        // Parse tasks array
        List<QuestTask> tasks = new ArrayList<>();
        if (json.get("tasks").isJsonArray()) {
            JsonArray tasksArr = json.getAsJsonArray("tasks");
            for (JsonElement tElem : tasksArr) {
                if (tElem.isJsonObject()) {
                    JsonObject tObj = tElem.getAsJsonObject();
                    TaskType tType = tObj.has("type") && TaskType.isValid(tObj.get("type").getAsString()) ? TaskType.valueOf(tObj.get("type").getAsString()) : null;
                    if (tType == null) errorMessage.append(" Unknown task type: ").append(tObj.get("type")).append(",");

                    String tTarget = tObj.has("target") ? tObj.get("target").getAsString() : "";
                    String rawTarget = tTarget.startsWith("#") ? tTarget.substring(1) : tTarget;
                    if (rawTarget.contains("[")) rawTarget = rawTarget.substring(0, rawTarget.indexOf('['));
                    if (Identifier.tryParse(rawTarget) == null) errorMessage.append(" The target ").append(tTarget).append(" is not a valid target,");

                    int tAmt = tObj.has("requiredAmount") ? tObj.get("requiredAmount").getAsInt() : 0;
                    if (tAmt <= 0) errorMessage.append(" Task requiredAmount must be greater than 0,");

                    tasks.add(new QuestTask(tType != null ? tType : TaskType.MINE_BLOCK, tTarget, tAmt));
                }
            }
        }
        if (tasks.isEmpty()) {
            errorMessage.append(" Quest must have at least one task in 'tasks',");
        }

        // Parse rewards array
        List<QuestReward> rewards = new ArrayList<>();
        if (json.get("rewards").isJsonArray()) {
            JsonArray rewsArr = json.getAsJsonArray("rewards");
            for (JsonElement rElem : rewsArr) {
                if (rElem.isJsonObject()) {
                    JsonObject rObj = rElem.getAsJsonObject();
                    RewardType rType = rObj.has("type") && RewardType.isValid(rObj.get("type").getAsString()) ? RewardType.valueOf(rObj.get("type").getAsString()) : null;
                    if (rType == null) {
                        errorMessage.append(" Unknown reward type: ").append(rObj.get("type")).append(",");
                        continue;
                    }

                    int rAmt = rObj.has("amount") ? rObj.get("amount").getAsInt() : 0;
                    if (rAmt <= 0) errorMessage.append(" Reward amount must be greater than 0,");

                    switch (rType) {
                        case ITEM -> {
                            String target = rObj.has("target") ? rObj.get("target").getAsString() : "";
                            String rawReward = target.contains("[") ? target.substring(0, target.indexOf('[')) : target;
                            if (rawReward.startsWith("#")) {
                                Identifier tagId = Identifier.tryParse(rawReward.substring(1));
                                if (tagId == null) errorMessage.append(" The reward tag ").append(target).append(" is invalid,");
                            } else {
                                Identifier rewardId = Identifier.tryParse(rawReward);
                                if (rewardId == null || !Registries.ITEM.containsId(rewardId)) {
                                    errorMessage.append(" The reward ").append(target).append(" does not exist,");
                                }
                            }
                            rewards.add(new ItemReward(target, rAmt));
                        }
                        case XP -> rewards.add(new XpReward(rAmt));
                        case LEVEL -> rewards.add(new LevelReward(rAmt));
                        case COMMAND -> {
                            String cmd = rObj.has("command") ? rObj.get("command").getAsString() : "";
                            String desc = rObj.has("description") ? rObj.get("description").getAsString() : "";
                            rewards.add(new CommandReward(cmd, desc, rAmt));
                        }
                    }
                }
            }
        }
        if (rewards.isEmpty()) {
            errorMessage.append(" Quest must have at least one reward in 'rewards',");
        }

        if (!errorMessage.isEmpty()) {
            errorMessage.deleteCharAt(0).deleteCharAt(errorMessage.length() - 1).insert(0, "There are errors in the json file of quest \"" + questId + "\" : ");
            PacPackQuests.LOGGER.warn(errorMessage.toString());
            return;
        }

        QuestDefinition quest = new QuestDefinition(
                questId, title, category, icon, displayX, displayY, parents, properties, tasks, rewards
        );

        LOADED_QUESTS.put(questId, quest);
    }
}
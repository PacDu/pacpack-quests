package fr.pacdu.pacpackquests.data;

import com.google.gson.*;
import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.network.QuestSyncPayload;
import fr.pacdu.pacpackquests.util.ItemUtils;
import fr.pacdu.pacpackquests.util.PathSecurity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
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
            "title", "taskType", "target", "requiredAmount", "icon", "rewardType", "reward", "rewardAmount", "displayX", "displayY"
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
        json.addProperty("taskType", quest.taskType().toString());
        json.addProperty("target", quest.target());
        json.addProperty("requiredAmount", quest.requiredAmount());
        json.addProperty("icon", ItemUtils.toCommandString(quest.icon(), registries));
        json.addProperty("rewardType", quest.rewardType().toString());

        switch (quest.rewardType()) {
            case XP -> json.addProperty("reward", "xp");
            case LEVEL -> json.addProperty("reward", "level");
            case ITEM, COMMAND -> json.addProperty("reward", quest.reward());
        }
        json.addProperty("rewardAmount", quest.rewardAmount());

        if (quest.parents() != null && !quest.parents().isEmpty()) {
            json.add("parents", GSON.toJsonTree(quest.parents()));
        }

        json.addProperty("displayX", quest.displayX());
        json.addProperty("displayY", quest.displayY());

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

        TaskType taskType = TaskType.isValid(json.get("taskType").getAsString()) ? TaskType.valueOf(json.get("taskType").getAsString()) : null;
        if (taskType == null) errorMessage.append(" The task type \"").append(json.get("taskType").getAsString()).append("\" does not exist,");

        String target = json.get("target").getAsString();
        String rawTarget = target.startsWith("#") ? target.substring(1) : target;
        if (rawTarget.contains("[")) rawTarget = rawTarget.substring(0, rawTarget.indexOf('['));
        if (Identifier.tryParse(rawTarget) == null) errorMessage.append(" The target ").append(json.get("target")).append(" is not a valid target,");

        int requiredAmount = json.get("requiredAmount").getAsInt();
        if (requiredAmount <= 0) errorMessage.append(" The required amount must be greater than 0,");

        ItemStack icon = ItemUtils.fromCommandString(json.get("icon").getAsString(), registries);
        if (Objects.equals(icon, ItemStack.EMPTY)) errorMessage.append(" The icon ").append(json.get("icon")).append(" does not exist,");

        RewardType rewardType = RewardType.isValid(json.get("rewardType").getAsString()) ? RewardType.valueOf(json.get("rewardType").getAsString()) : null;
        if (rewardType == null) errorMessage.append(" The reward type \"").append(json.get("rewardType")).append("\" does not exist,");

        String rewardStr = json.get("reward").getAsString();
        String rawReward = rewardStr.contains("[") ? rewardStr.substring(0, rewardStr.indexOf('[')) : rewardStr;
        if (rawReward.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(rawReward.substring(1));
            if (tagId == null) errorMessage.append(" The reward tag ").append(json.get("reward")).append(" is invalid,");
        } else if (rewardType == RewardType.ITEM) {
            Identifier rewardId = Identifier.tryParse(rawReward);
            if (rewardId == null || !Registries.ITEM.containsId(rewardId)) {
                errorMessage.append(" The reward ").append(json.get("reward")).append(" does not exist,");
            }
        }

        int rewardAmount = json.get("rewardAmount").getAsInt();
        if (rewardAmount <= 0) errorMessage.append(" The reward amount must be greater than 0,");

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

        int displayX = json.get("displayX").getAsInt();
        int displayY = json.get("displayY").getAsInt();

        if (!errorMessage.isEmpty()) {
            errorMessage.deleteCharAt(0).deleteCharAt(errorMessage.length() - 1).insert(0, "There are errors in the json file of quest \"" + questId + "\" : ");
            PacPackQuests.LOGGER.warn(errorMessage.toString());
            return;
        }

        QuestDefinition quest = new QuestDefinition(
                questId, title, category, taskType, target, requiredAmount, icon,
                rewardStr, rewardType, rewardAmount, parents, displayX, displayY
        );

        LOADED_QUESTS.put(questId, quest);
    }
}
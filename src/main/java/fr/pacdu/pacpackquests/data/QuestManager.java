package fr.pacdu.pacpackquests.data;

import com.google.gson.*;
import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.io.InputStreamReader;

/**
 * Handles the loading and management of quest definitions from JSON files on the server.
 * Quests are loaded from the config directory and stored in memory.
 */
public class QuestManager {

    public static final Map<String, QuestDefinition> LOADED_QUESTS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> QUEST_DEFINITION_REQUIDED_LIST = Arrays.asList("title", "taskType", "target", "requiredAmount", "icon", "rewardType", "reward", "rewardAmount", "displayX",  "displayY");

    public static void loadQuests(MinecraftServer server) {
        loadQuests();
    }

    public static void loadQuests() {
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

                            AssertParseAndAddQuest(pathStr, json);
                        } catch (Exception e) {
                            PacPackQuests.LOGGER.error("Failed to load Config quest: {}", path.getFileName(), e);
                        }
                    });
        } catch (Exception e) {
            PacPackQuests.LOGGER.error("Unable to read the configuration directory.", e);
        }

        PacPackQuests.LOGGER.info("Successfully loaded {} quests!", LOADED_QUESTS.size());
    }

    private static void AssertParseAndAddQuest(String filePath, JsonObject json) {
        int lastSlash = filePath.lastIndexOf('/');
        String questId = filePath.substring(lastSlash + 1, filePath.length() - 5);

        StringBuilder errorMessage = new StringBuilder();

        String errors = parseAndProcessDeprecatedFields(filePath, json);
        if (!errorMessage.isEmpty()) errorMessage.append(errors);

        for (String e : QUEST_DEFINITION_REQUIDED_LIST) {
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
        if (taskType == null) errorMessage.append(" The taskType \"").append(json.get("taskType").getAsString()).append("\" does not exist,");

        String target = json.get("target").getAsString();
        if (Identifier.tryParse(target) == null && Identifier.tryParse(target.substring(1)) == null) errorMessage.append(" The target ").append(json.get("target")).append(" is not a valid target,");

        int requiredAmount = json.get("requiredAmount").getAsInt();
        if (requiredAmount <= 0) errorMessage.append(" The required amount must be greater than 0,");

        Item iconItem = Registries.ITEM.get(Identifier.of(json.get("icon").getAsString()));
        if (Objects.equals(iconItem.toString(), "minecraft:air")) errorMessage.append(" The icon ").append(json.get("icon")).append(" does not exist,");

        String rewardStr = json.get("reward").getAsString();

        RewardType rewardType = RewardType.isValid(json.get("rewardType").getAsString()) ? RewardType.valueOf(json.get("rewardType").getAsString()) : null;
        if (rewardType == null) errorMessage.append(" The reward taskType \"").append(json.get("rewardType")).append("\" does not exist,");

        if (rewardStr.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(rewardStr.substring(1));
            if (tagId == null) errorMessage.append(" The reward tag ").append(json.get("reward")).append(" is invalid,");
        } else if (rewardStr.contains(":") && !rewardStr.contains(" ") && !rewardStr.startsWith("enchanted_book:") && !rewardStr.startsWith("minecraft:enchanted_book:")) {
            Item rewardItem = Registries.ITEM.get(Identifier.of(rewardStr));
            if (Objects.equals(rewardItem.toString(), "minecraft:air")) errorMessage.append(" The reward ").append(json.get("reward")).append(" does not exist,");
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
                questId, title, category, taskType, target, requiredAmount, new ItemStack(iconItem),
                rewardStr, rewardType, rewardAmount, parents, displayX, displayY
        );

        LOADED_QUESTS.put(questId, quest);
    }

    private static String parseAndProcessDeprecatedFields(String filePath, JsonObject json) {
        boolean needSave = false;
        StringBuilder errorMessage = new StringBuilder();

        if (json.has("type")) {
            json.addProperty("taskType", json.get("type").getAsString());
            json.remove("type");
            needSave = true;
            PacPackQuests.LOGGER.warn("Deprecated use of the field \"type\", use \"taskType\" instead.");
        }

        if (json.has("reward") && !json.has("rewardType")) {
            String rewardStr = json.get("reward").getAsString();
            RewardType rewardType;

            if (rewardStr.equalsIgnoreCase("xp")) {
                rewardType = RewardType.XP;
            } else if (rewardStr.equalsIgnoreCase("level") || rewardStr.equalsIgnoreCase("levels")) {
                rewardType = RewardType.LEVEL;
            } else if (rewardStr.startsWith("enchanted_book:") || rewardStr.startsWith("minecraft:enchanted_book:")) {
                rewardType = RewardType.ENCHANTED_BOOK;
            } else if (rewardStr.startsWith("#") || (rewardStr.contains(":") && !rewardStr.contains(" "))) {
                rewardType = RewardType.ITEM;
            } else {
                errorMessage.append(" The reward \"").append(json.get("reward")).append("\" is invalid,");
                return errorMessage.toString();
            }

            json.addProperty("rewardType", rewardType.name());
            needSave = true;
        }

        if (needSave) {
            Path completePath = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + filePath);
            try {
                Files.writeString(completePath, GSON.toJson(json));
            } catch (IOException e) {
                PacPackQuests.LOGGER.error("Failed to save quest", e);
            }
        }

        return errorMessage.toString();
    }
}
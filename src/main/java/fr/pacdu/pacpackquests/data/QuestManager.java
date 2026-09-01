package fr.pacdu.pacpackquests.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class QuestManager {

    // Central storage for all loaded quests
    public static final Map<String, QuestDefinition> LOADED_QUESTS = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static final List<String> QUEST_DEFINITION_REQUIDED_LIST = Arrays.asList("title", "type", "target", "requiredAmount", "icon", "reward", "rewardAmount", "displayX",  "displayY");

    public static void registerLoader() {
        SynchronousResourceReloader questReloader = manager -> {
            LOADED_QUESTS.clear();

            // 1. Native loading via Datapacks (files included in the mod or in the world folder)
            if (ModConfig.loadDefaultQuests) {
                Map<Identifier, Resource> resources = manager.findResources("quests", path -> path.getPath().endsWith(".json"));
                for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                    Identifier fileId = entry.getKey();
                    try (InputStream stream = entry.getValue().getInputStream();
                         InputStreamReader reader = new InputStreamReader(stream)) {

                        JsonObject json = GSON.fromJson(reader, JsonObject.class);

                        // Extract relative path after "quests/" (e.g., "quests/nether/blaze.json" -> "nether/blaze.json")
                        String rawPath = fileId.getPath();
                        String relativePath = rawPath.substring(7);
                        int lastSlash = relativePath.lastIndexOf('/');

                        String category = lastSlash == -1 ? "main" : relativePath.substring(0, lastSlash);
                        String questId = relativePath.substring(lastSlash + 1, relativePath.length() - 5);

                        AssertParseAndAddQuest(questId, category, json);
                    } catch (Exception e) {
                        PacPackQuests.LOGGER.error("[PacPackQuests] Failed to load Datapack quest: {}", fileId, e);
                    }
                }
            }

            // 2. Loading via the global Config folder (applies to all worlds)
            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests");

            try {
                // Automatically generate directories if they do not exist
                if (!Files.exists(configDir)) {
                    Files.createDirectories(configDir);
                }

                // Iterate through all JSON files in the config directory
                try (Stream<Path> paths = Files.walk(configDir)) {
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".json"))
                            .forEach(path -> {
                                try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path))) {
                                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                                    // Relativize path to get folders (e.g., "C:/.../config/.../nether/blaze.json" -> "nether/blaze.json")
                                    Path relativePath = configDir.relativize(path);
                                    String pathStr = relativePath.toString().replace('\\', '/');
                                    int lastSlash = pathStr.lastIndexOf('/');

                                    String category = lastSlash == -1 ? "main" : pathStr.substring(0, lastSlash);
                                    String questId = pathStr.substring(lastSlash + 1, pathStr.length() - 5);

                                    // Config quests will overwrite datapack quests if the ID is identical
                                    AssertParseAndAddQuest(questId, category, json);
                                } catch (Exception e) {
                                    PacPackQuests.LOGGER.error("Failed to load Config quest: {}", path.getFileName(), e);
                                }
                            });
                }
            } catch (Exception e) {
                PacPackQuests.LOGGER.error("Unable to read the configuration directory.", e);
            }

            PacPackQuests.LOGGER.info("Successfully loaded {} quests!", LOADED_QUESTS.size());
        };

        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(
                Identifier.of("pacpackquests", "quest_loader"),
                questReloader
        );
    }

    private static void AssertParseAndAddQuest(String questId, String category, JsonObject json) {
        StringBuilder errorMessage = new StringBuilder();

        for (String e : QUEST_DEFINITION_REQUIDED_LIST) {
            if (!json.has(e)) {
                errorMessage.append("WARN : Variable \"").append(e).append("\" is missing in the quest ").append(questId).append(".");
                PacPackQuests.LOGGER.warn(errorMessage.toString());
                return;
            }
        }

        String title = json.get("title").getAsString();
        if (title.isEmpty()) errorMessage.append(" The title cannot be empty,");

        TaskType type = TaskType.isValid(json.get("type").getAsString()) ? TaskType.valueOf(json.get("type").getAsString()) : null;
        if (type == null) errorMessage.append(" The type \"").append(json.get("type")).append("\" does not exist,");

        String target = json.get("target").getAsString();
        if (Identifier.tryParse(target) == null && Identifier.tryParse(target.substring(1)) == null) errorMessage.append(" The target ").append(json.get("target")).append(" is not a valid target,");

        int requiredAmount = json.get("requiredAmount").getAsInt();
        if (requiredAmount <= 0) errorMessage.append(" The required amount must be greater than 0,");

        Item iconItem = Registries.ITEM.get(Identifier.of(json.get("icon").getAsString()));
        if (Objects.equals(iconItem.toString(), "minecraft:air")) errorMessage.append(" The icon ").append(json.get("icon")).append(" does not exist,");

        Item rewardItem = Registries.ITEM.get(Identifier.of(json.get("reward").getAsString()));
        if (Objects.equals(rewardItem.toString(), "minecraft:air")) errorMessage.append(" The reward ").append(json.get("reward")).append(" does not exist,");

        int rewardAmount = json.get("rewardAmount").getAsInt();
        if (rewardAmount <= 0) errorMessage.append(" The reward amount must be greater than 0,");

        // Parse optional parents array
        List<String> parents = new ArrayList<>();
        if (json.has("parents")) {
            JsonArray parentsArray = json.getAsJsonArray("parents");
            for (JsonElement element : parentsArray) {
                parents.add(element.getAsString());
            }
        }

        int displayX = json.get("displayX").getAsInt();
        int displayY = json.get("displayY").getAsInt();

        if (!errorMessage.isEmpty()) {
            errorMessage.deleteCharAt(0).deleteCharAt(errorMessage.length() - 1).insert(0, "WARN : There are errors in the json file of quest \"" + questId + "\" : ");
            PacPackQuests.LOGGER.warn(errorMessage.toString());
            return;
        }

        QuestDefinition quest = new QuestDefinition(
                questId, title, category, type, target, requiredAmount,
                new ItemStack(iconItem), new ItemStack(rewardItem), rewardAmount, parents, displayX, displayY
        );

        LOADED_QUESTS.put(questId, quest);
    }
}
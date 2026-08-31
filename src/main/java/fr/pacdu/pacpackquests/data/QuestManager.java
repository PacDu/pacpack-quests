package fr.pacdu.pacpackquests.data;

import com.google.gson.Gson;
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
import java.util.stream.Stream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class QuestManager {

    // Central storage for all loaded quests
    public static final Map<String, QuestDefinition> LOADED_QUESTS = new HashMap<>();
    private static final Gson GSON = new Gson();

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
                        String fullPath = fileId.getPath();
                        String questId = fullPath.substring(fullPath.lastIndexOf('/') + 1, fullPath.length() - 5);

                        parseAndAddQuest(questId, json);
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

                                    String fileName = path.getFileName().toString();
                                    String questId = fileName.substring(0, fileName.length() - 5);

                                    // Config quests will overwrite datapack quests if the ID is identical
                                    parseAndAddQuest(questId, json);
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

    private static void parseAndAddQuest(String questId, JsonObject json) {
        String title = json.get("title").getAsString();
        TaskType type = TaskType.valueOf(json.get("type").getAsString());
        String target = json.get("target").getAsString();
        int requiredAmount = json.get("requiredAmount").getAsInt();

        Item iconItem = Registries.ITEM.get(Identifier.of(json.get("icon").getAsString()));
        Item rewardItem = Registries.ITEM.get(Identifier.of(json.get("reward").getAsString()));
        int rewardAmount = json.get("rewardAmount").getAsInt();

        QuestDefinition quest = new QuestDefinition(
                questId, title, type, target, requiredAmount,
                new ItemStack(iconItem), new ItemStack(rewardItem), rewardAmount
        );

        LOADED_QUESTS.put(questId, quest);
    }
}
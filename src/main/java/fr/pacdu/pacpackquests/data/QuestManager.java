package fr.pacdu.pacpackquests.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.TaskType;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class QuestManager {

    // Central storage for all loaded quests
    public static final Map<String, QuestDefinition> LOADED_QUESTS = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void registerLoader() {
        // 1. On utilise directement l'interface native de Minecraft pour charger les ressources
        SynchronousResourceReloader questReloader = manager -> {
            LOADED_QUESTS.clear();

            // Find all JSON files in data/<namespace>/quests/
            Map<Identifier, Resource> resources = manager.findResources("quests", path -> path.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier fileId = entry.getKey();

                try (InputStream stream = entry.getValue().getInputStream();
                     InputStreamReader reader = new InputStreamReader(stream)) {

                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    // Extract quest ID from the filename (e.g., "quests/mine_log.json" -> "mine_log")
                    String fullPath = fileId.getPath();
                    String questId = fullPath.substring(fullPath.lastIndexOf('/') + 1, fullPath.length() - 5);

                    String title = json.get("title").getAsString();
                    TaskType type = TaskType.valueOf(json.get("type").getAsString());
                    String target = json.get("target").getAsString();
                    int requiredAmount = json.get("requiredAmount").getAsInt();
                    int rewardAmount = json.get("rewardAmount").getAsInt();

                    // Parse string identifiers into actual ItemStacks
                    Item iconItem = Registries.ITEM.get(Identifier.of(json.get("icon").getAsString()));
                    Item rewardItem = Registries.ITEM.get(Identifier.of(json.get("reward").getAsString()));

                    QuestDefinition quest = new QuestDefinition(
                            questId, title, type, target, requiredAmount,
                            new ItemStack(iconItem), new ItemStack(rewardItem), rewardAmount
                    );

                    LOADED_QUESTS.put(questId, quest);

                } catch (Exception e) {
                    System.err.println("[PacPackQuests] Failed to load quest: " + fileId);
                    e.printStackTrace();
                }
            }
            System.out.println("[PacPackQuests] Successfully loaded " + LOADED_QUESTS.size() + " quests!");
        };

        // 1. Retrieve the ResourceLoader instance dedicated to server data packs
        // 2. Register the reloader using the new v1 API
        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(
                Identifier.of("pacpackquests", "quest_loader"),
                questReloader
        );
    }
}
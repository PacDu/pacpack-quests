package fr.pacdu.pacpackquests.config;

import com.google.gson.*;
import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.util.PathSecurity;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.pacdu.pacpackquests.PacPackQuests.DEFAULT_CATEGORY_NAME;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/categories.json");

    public static List<String> categoryOrder = new ArrayList<>();
    public static Map<String, CameraState> cameraPositions = new HashMap<>();

    public record CameraState(double panX, double panY, float zoom) {}

    public static void load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        if (json.has("categoryOrder") && json.get("categoryOrder").isJsonArray()) {
                            categoryOrder.clear();
                            JsonArray categoryOrderArray = json.getAsJsonArray("categoryOrder");
                            for (JsonElement element : categoryOrderArray) {
                                String cat = element.getAsString();
                                if (PathSecurity.isValidIdentifier(cat)) {
                                    categoryOrder.add(cat);
                                }
                            }
                        }

                        if (json.has("cameraPositions") && json.get("cameraPositions").isJsonObject()) {
                            cameraPositions.clear();
                            JsonObject camObj = json.getAsJsonObject("cameraPositions");
                            for (Map.Entry<String, JsonElement> entry : camObj.entrySet()) {
                                String cat = entry.getKey();
                                if (PathSecurity.isValidIdentifier(cat) && entry.getValue().isJsonObject()) {
                                    JsonObject c = entry.getValue().getAsJsonObject();
                                    double pX = c.has("panX") ? c.get("panX").getAsDouble() : 0.0;
                                    double pY = c.has("panY") ? c.get("panY").getAsDouble() : 0.0;
                                    float z = c.has("zoom") ? c.get("zoom").getAsFloat() : 1.0f;
                                    cameraPositions.put(cat, new CameraState(pX, pY, z));
                                }
                            }
                        }
                    }
                }
            } else {
                save();
            }
        } catch (Exception e) {
            PacPackQuests.LOGGER.error("Failed to load categories config.", e);
        }
    }

    public static void save() {
        try {
            if (categoryOrder.isEmpty()) categoryOrder.add(DEFAULT_CATEGORY_NAME);
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject json = new JsonObject();
            json.add("categoryOrder", GSON.toJsonTree(categoryOrder));

            if (!cameraPositions.isEmpty()) {
                JsonObject camObj = new JsonObject();
                for (Map.Entry<String, CameraState> entry : cameraPositions.entrySet()) {
                    JsonObject c = new JsonObject();
                    c.addProperty("panX", entry.getValue().panX());
                    c.addProperty("panY", entry.getValue().panY());
                    c.addProperty("zoom", entry.getValue().zoom());
                    camObj.add(entry.getKey(), c);
                }
                json.add("cameraPositions", camObj);
            }

            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            PacPackQuests.LOGGER.error("Failed to save categories config.", e);
        }
    }
}
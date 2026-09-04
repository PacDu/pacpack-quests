package fr.pacdu.pacpackquests.config;

import com.google.gson.*;
import fr.pacdu.pacpackquests.PacPackQuests;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModConfig {
    // We use GsonBuilder to make the JSON file readable (pretty printing)
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/main.json");

    // Default value
    public static boolean loadDefaultQuests = true;
    public static List<String> categoryOrder =  Arrays.asList("overworld", "nether");

    public static void load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    boolean configIsMissing = false;
                    if (json != null) {
                        if (json.has("loadDefaultQuests"))
                            loadDefaultQuests = json.get("loadDefaultQuests").getAsBoolean();
                        else configIsMissing = true;
                        if (json.has("categoryOrder")) {
                            categoryOrder.clear();
                            JsonArray categoryOrderArray = json.getAsJsonArray("categoryOrder");
                            for (JsonElement element : categoryOrderArray) {
                                categoryOrder.add(element.getAsString());
                            }
                        }
                        else configIsMissing = true;
                        if (configIsMissing) save();
                    }
                }
            } else {
                // Generate the default config file if it doesn't exist
                save();
            }
        } catch (Exception e) {
            PacPackQuests.LOGGER.error("Failed to load main config.", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("loadDefaultQuests", loadDefaultQuests);
            json.add("categoryOrder", GSON.toJsonTree(categoryOrder));

            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            PacPackQuests.LOGGER.error("Failed to save main config.", e);
        }
    }
}
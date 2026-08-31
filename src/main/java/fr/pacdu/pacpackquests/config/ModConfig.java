package fr.pacdu.pacpackquests.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.pacdu.pacpackquests.PacPackQuests;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    // We use GsonBuilder to make the JSON file readable (pretty printing)
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/main.json");

    // Default value
    public static boolean loadDefaultQuests = true;

    public static void load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null && json.has("loadDefaultQuests")) {
                        loadDefaultQuests = json.get("loadDefaultQuests").getAsBoolean();
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

            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            PacPackQuests.LOGGER.error("Failed to save main config.", e);
        }
    }
}
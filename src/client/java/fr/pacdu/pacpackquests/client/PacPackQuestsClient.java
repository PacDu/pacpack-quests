package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.client.screen.QuestScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PacPackQuestsClient implements ClientModInitializer {
	public static KeyBinding openQuestMenuKey;

	// Maps to store progress and claim status for each quest ID on the client
	public static final Map<String, QuestDefinition> CLIENT_DEFINITIONS = new HashMap<>();
	public static final Map<String, Integer> CLIENT_PROGRESS = new HashMap<>();
	public static final Map<String, Boolean> CLIENT_FINISHED = new HashMap<>();
	public static final Map<String, Boolean> CLIENT_CLAIMED = new HashMap<>();
	public static final List<String> CLIENT_CATEGORIES = new ArrayList<>();
	public static final List<String> SERVER_STRUCTURES = new ArrayList<>();
	public static final List<String> SERVER_LOOT_TABLES = new ArrayList<>();

	public static final KeyBinding.Category QUEST_CATEGORY_KEY = KeyBinding.Category.create(Identifier.of("pacpack-quests", "keys"));

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CLIENT_DEFINITIONS.clear();
            CLIENT_PROGRESS.clear();
            CLIENT_FINISHED.clear();
            CLIENT_CLAIMED.clear();
            CLIENT_CATEGORIES.clear();
            SERVER_STRUCTURES.clear();
            SERVER_LOOT_TABLES.clear();
        });

		openQuestMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.pacpack-quests.open-quest-menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				QUEST_CATEGORY_KEY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// The while loop allows you to record the action even if the game stutters
			while (openQuestMenuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new QuestScreen());
				}
			}
		});

		ClientNetworkHandler.register();
	}
}
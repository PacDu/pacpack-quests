package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.network.CreateCategoryPayload;
import fr.pacdu.pacpackquests.network.DeleteQuestPayload;
import fr.pacdu.pacpackquests.network.QuestProgressPayload;
import fr.pacdu.pacpackquests.network.QuestSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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

	public static final KeyBinding.Category QUEST_CATEGORY_KEY = KeyBinding.Category.create(Identifier.of("pacpack-quests", "keys"));

	@Override
	public void onInitializeClient() {
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

		ClientPlayNetworking.registerGlobalReceiver(QuestSyncPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				Item icon = Registries.ITEM.get(Identifier.of(payload.iconId()));
				Item reward = Registries.ITEM.get(Identifier.of(payload.rewardId()));

				// Reconstruct the quest definition with the newly received reward data
				QuestDefinition def = new QuestDefinition(
						payload.questId(), payload.title(), payload.category(), payload.type(), payload.target(),
						payload.requiredAmount(), new ItemStack(icon), new ItemStack(reward), payload.rewardType(),
						payload.rewardAmount(), payload.parents(), payload.displayX(), payload.displayY()
				);
				CLIENT_DEFINITIONS.put(payload.questId(), def);

				for (QuestDefinition quest : CLIENT_DEFINITIONS.values()) {
					if (!CLIENT_CATEGORIES.contains(quest.category())) {
						CLIENT_CATEGORIES.add(quest.category());
					}
				}

				// --- CONFIG-BASED SORTING ---
				// Replace ModConfig.categoryOrder with the actual variable from your config file
				List<String> configuredOrder = ModConfig.categoryOrder;

				CLIENT_CATEGORIES.sort((cat1, cat2) -> {
					int index1 = configuredOrder.indexOf(cat1);
					int index2 = configuredOrder.indexOf(cat2);

					// Both categories are missing from the config -> Sort them alphabetically at the end
					if (index1 == -1 && index2 == -1) return cat1.compareTo(cat2);

					// Only cat1 is missing -> Push it to the bottom
					if (index1 == -1) return 1;

					// Only cat2 is missing -> Push it to the bottom
					if (index2 == -1) return -1;

					// Both are in the config -> Sort them according to the configured order
					return Integer.compare(index1, index2);
				});

				if (context.client().currentScreen instanceof QuestScreen qs) qs.refreshUI();
			});
		});

		// Listen for progress synchronization packets from the server
		ClientPlayNetworking.registerGlobalReceiver(QuestProgressPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				CLIENT_PROGRESS.put(payload.questId(), payload.progress());
				CLIENT_FINISHED.put(payload.questId(), payload.isFinished());
				CLIENT_CLAIMED.put(payload.questId(), payload.isClaimed());

				if (context.client().currentScreen instanceof QuestScreen questScreen) {
					questScreen.updateClaimButtonState();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(DeleteQuestPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				CLIENT_DEFINITIONS.remove(payload.questId());
				CLIENT_PROGRESS.remove(payload.questId());
				CLIENT_FINISHED.remove(payload.questId());
				CLIENT_CLAIMED.remove(payload.questId());

				if (context.client().currentScreen instanceof QuestScreen questScreen) {
					questScreen.refreshUI();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(CreateCategoryPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				if (!CLIENT_CATEGORIES.contains(payload.category())) {
					CLIENT_CATEGORIES.add(payload.category());
				}
			});
		});
	}
}
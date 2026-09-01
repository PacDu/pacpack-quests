package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.TaskType;
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

import java.util.HashMap;
import java.util.Map;

public class PacPackQuestsClient implements ClientModInitializer {
	public static KeyBinding openQuestMenuKey;

	// Maps to store progress and claim status for each quest ID on the client
	public static final Map<String, QuestDefinition> CLIENT_DEFINITIONS = new HashMap<>();
	public static final Map<String, Integer> CLIENT_PROGRESS = new HashMap<>();
	public static final Map<String, Boolean> CLIENT_CLAIMED = new HashMap<>();

	public static final KeyBinding.Category QUEST_CATEGORY = KeyBinding.Category.create(Identifier.of("pacpack-quests", "keys"));

	@Override
	public void onInitializeClient() {
		openQuestMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.pacpack-quests.open-quest-menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				QUEST_CATEGORY
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
						payload.questId(), payload.title(), payload.category(), null, "",
						payload.requiredAmount(), new ItemStack(icon), new ItemStack(reward), payload.rewardAmount(),
						payload.parents(), payload.displayX(), payload.displayY()
				);
				CLIENT_DEFINITIONS.put(payload.questId(), def);
			});
		});

		// Listen for progress synchronization packets from the server
		ClientPlayNetworking.registerGlobalReceiver(QuestProgressPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				CLIENT_PROGRESS.put(payload.questId(), payload.progress());
				CLIENT_CLAIMED.put(payload.questId(), payload.isClaimed());
			});
		});
	}
}
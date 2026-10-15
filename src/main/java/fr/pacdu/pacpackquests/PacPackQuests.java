package fr.pacdu.pacpackquests;

import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.data.QuestEventHandler;
import fr.pacdu.pacpackquests.data.QuestManager;
import fr.pacdu.pacpackquests.data.QuestState;
import fr.pacdu.pacpackquests.network.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class PacPackQuests implements ModInitializer {

	public static final String MOD_ID = "pacpackquests";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing PacPack Quests...");
		ModConfig.load();
		ServerLifecycleEvents.SERVER_STARTING.register(QuestManager::loadQuests);

		// Register S2C payloads
		PayloadTypeRegistry.playS2C().register(QuestSyncPayload.ID, QuestSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(QuestProgressPayload.ID, QuestProgressPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DeleteQuestPayload.ID, DeleteQuestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CreateCategoryPayload.ID, CreateCategoryPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DeleteCategoryPayload.ID, DeleteCategoryPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ReorderCategoryPayload.ID, ReorderCategoryPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(EditCategoryPayload.ID, EditCategoryPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(QuestCompletedPayload.ID, QuestCompletedPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncRegistryPayload.ID, SyncRegistryPayload.CODEC);

		// Register C2S payloads
		PayloadTypeRegistry.playC2S().register(ClaimQuestPayload.ID, ClaimQuestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MoveQuestPayload.ID, MoveQuestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveQuestPayload.ID, SaveQuestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DeleteQuestPayload.ID, DeleteQuestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CreateCategoryPayload.ID, CreateCategoryPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DeleteCategoryPayload.ID, DeleteCategoryPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ReorderCategoryPayload.ID, ReorderCategoryPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(EditCategoryPayload.ID, EditCategoryPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestRegistryPayload.ID, RequestRegistryPayload.CODEC);

		// Connection Event: Sync all quests progress when a player joins
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;

			// Sync category order from the server config
			ServerPlayNetworking.send(player, new ReorderCategoryPayload(ModConfig.categoryOrder));

			QuestState state = QuestState.getServerState(server);
			UUID playerId = player.getUuid();

			// Send both quest definition and its current progress to the joining player
			for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
				int progress = state.getProgress(playerId, quest.id());
				boolean finished = state.isFinished(playerId, quest.id());
				boolean claimed = state.isClaimed(playerId, quest.id());

				ServerPlayNetworking.send(player, new QuestSyncPayload(
						quest.id(),
						quest.title(),
						quest.category(),
						quest.taskType(),
						quest.target(),
						quest.requiredAmount(),
						Registries.ITEM.getId(quest.icon().getItem()).toString(),
						quest.reward(),
						quest.rewardType(),
						quest.rewardAmount(),
						quest.parents(),
						quest.displayX(),
						quest.displayY()
				));

				ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), progress, finished, claimed));
			}
		});

		// Register gameplay task listeners and server network handlers
		QuestEventHandler.register();
		ServerNetworkHandler.register();
	}
}

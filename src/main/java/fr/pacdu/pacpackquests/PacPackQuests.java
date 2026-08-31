package fr.pacdu.pacpackquests;

import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.data.QuestManager;
import fr.pacdu.pacpackquests.data.QuestProgressHandler;
import fr.pacdu.pacpackquests.data.QuestState;
import fr.pacdu.pacpackquests.network.ClaimQuestPayload;
import fr.pacdu.pacpackquests.network.QuestProgressPayload;
import fr.pacdu.pacpackquests.network.QuestSyncPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class PacPackQuests implements ModInitializer {

	public static final String MOD_ID = "PacPackQuests";
	// Initialize the SLF4J Logger with your Mod ID
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing PacPack Quests...");
		ModConfig.load();
		QuestManager.registerLoader();

		// S2C (Server to Client) : The server synchronizes the list of quests with the client
		PayloadTypeRegistry.playS2C().register(QuestSyncPayload.ID, QuestSyncPayload.CODEC);
		// S2C (Server to Client) : The server updates the client's visual progress
		PayloadTypeRegistry.playS2C().register(QuestProgressPayload.ID, QuestProgressPayload.CODEC);
		// C2S (Client to Server) : The client requests to claim their reward
		PayloadTypeRegistry.playC2S().register(ClaimQuestPayload.ID, ClaimQuestPayload.CODEC);

		// 1. Connection Event: Sync all quests progress when a player joins
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			QuestState state = QuestState.getServerState(server);
			UUID playerId = player.getUuid();

			// Send both quest definition and its current progress to the joining player
			for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
				int progress = state.getProgress(playerId, quest.id());
				boolean claimed = state.isClaimed(playerId, quest.id());

				// Sync definition
				ServerPlayNetworking.send(player, new QuestSyncPayload(
						quest.id(), quest.title(), quest.category(), quest.requiredAmount(),
						Registries.ITEM.getId(quest.icon().getItem()).toString()
				));

				// Sync progress
				ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), progress, claimed));
			}
		});

		// 2. Claim Event: Listen to reward claim requests
		ServerPlayNetworking.registerGlobalReceiver(ClaimQuestPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				QuestState questState = QuestState.getServerState(context.server());
				ServerPlayerEntity player = context.player();
				UUID playerId = player.getUuid();

				String requestedQuestId = payload.questId();

				// Ensure the requested quest actually exists in our loaded Datapacks
				QuestDefinition quest = QuestManager.LOADED_QUESTS.get(requestedQuestId);

				if (quest != null) {
					int progress = questState.getProgress(playerId, quest.id());
					boolean claimed = questState.isClaimed(playerId, quest.id());

					// Anti-cheat verification
					if (progress >= quest.requiredAmount() && !claimed) {

						// Mark as claimed
						questState.setClaimed(playerId, quest.id(), true);

						// Give the reward to the player
						ItemStack stack = quest.reward().copy();
						stack.setCount(quest.rewardAmount());
						player.getInventory().offerOrDrop(stack);

						// Send visual update back to the client
						ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), progress, true));
					}
				}
			});
		});

		// The event is listened every time a block is broken
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient()) {
				// We retrieve our save manager
				QuestState questState = QuestState.getServerState(world.getServer());
				UUID playerId = player.getUuid();

				// Iterate through all dynamically loaded quests
				for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
					if (quest.type() == TaskType.MINE_BLOCK) {
						boolean isTarget = false;
						String target = quest.target();

						if (target.startsWith("#")) {
							TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, Identifier.of(target.substring(1)));
							isTarget = state.isIn(tag);
						} else {
							isTarget = Registries.BLOCK.getId(state.getBlock()).toString().equals(target);
						}

						if (isTarget) {
							int current = questState.getProgress(playerId, quest.id());
							if (current < quest.requiredAmount()) {
								questState.setProgress(playerId, quest.id(), current + 1);
								ServerPlayNetworking.send((ServerPlayerEntity) player, new QuestProgressPayload(quest.id(), current + 1, false));
							}
						}
					}
				}
			}
		});

		// Listen to combat events
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity player) {

				for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
					if (quest.type() == TaskType.KILL_MOB) {
						boolean isTarget = false;
						String target = quest.target();

						// Check tags (e.g., "#minecraft:skeletons") or direct IDs
						if (target.startsWith("#")) {
							TagKey<EntityType<?>> tag = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(target.substring(1)));
							isTarget = killedEntity.getType().isIn(tag);
						} else {
							isTarget = Registries.ENTITY_TYPE.getId(killedEntity.getType()).toString().equals(target);
						}

						if (isTarget) {
							// Always increment by 1 for a single kill
							QuestProgressHandler.incrementProgress(player, quest, 1);
						}
					}
				}
			}
		});
	}
}

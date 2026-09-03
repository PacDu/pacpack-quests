package fr.pacdu.pacpackquests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.data.QuestManager;
import fr.pacdu.pacpackquests.data.QuestProgressHandler;
import fr.pacdu.pacpackquests.data.QuestState;
import fr.pacdu.pacpackquests.network.ClaimQuestPayload;
import fr.pacdu.pacpackquests.network.MoveQuestPayload;
import fr.pacdu.pacpackquests.network.QuestProgressPayload;
import fr.pacdu.pacpackquests.network.QuestSyncPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static net.fabricmc.fabric.impl.resource.pack.ModPackResourcesUtil.GSON;

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
		PayloadTypeRegistry.playC2S().register(MoveQuestPayload.ID, MoveQuestPayload.CODEC);

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
						quest.id(),
						quest.title(),
						quest.category(),
						quest.type(),
						quest.target(),
						quest.requiredAmount(),
						Registries.ITEM.getId(quest.icon().getItem()).toString(),
						Registries.ITEM.getId(quest.reward().getItem()).toString(),
						quest.rewardType(),
						quest.rewardAmount(),
						quest.parents(),
						quest.displayX(),
						quest.displayY()
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

						// Give the reward to the player
						switch (quest.rewardType()) {
							case XP -> player.addExperience(quest.rewardAmount());

							case LEVEL -> player.addExperienceLevels(quest.rewardAmount());

							case ITEM -> {
								if (quest.rewardAmount() > 0 && !quest.reward().isEmpty()) {
									ItemStack rewardStack = new ItemStack(quest.reward().getItem(), quest.rewardAmount());
									if (!player.getInventory().insertStack(rewardStack)) {
										player.dropItem(rewardStack, false);
									}
								}
							}
						}

						// Mark as claimed
						questState.setClaimed(playerId, quest.id(), true);

						// Send visual update back to the client
						ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), progress, true));
					}
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(MoveQuestPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();

				// Security: Prevent malicious clients from modifying files
				if (!context.server().getPlayerManager().isOperator(player.getPlayerConfigEntry())) return;

				QuestDefinition quest = QuestManager.LOADED_QUESTS.get(payload.questId());
				if (quest == null) return;

				// Path logic depends on how you generate your files.
				// Example path: config/pacpackquests/quests/categoryName/questId.json
				Path questFile = FabricLoader.getInstance().getConfigDir()
						.resolve("pacpackquests/quests/" + quest.category() + "/" + payload.questId() + ".json");

				if (Files.exists(questFile)) {
					try {
						// 1. Read existing JSON
						JsonObject json = GSON.fromJson(Files.readString(questFile), JsonObject.class);

						// 2. Modify X and Y
						json.addProperty("displayX", payload.newX());
						json.addProperty("displayY", payload.newY());

						// 3. Save back to disk
						Gson gson = new GsonBuilder().setPrettyPrinting().create();
						Files.writeString(questFile, gson.toJson(json));

						// 4. Update the server's live memory (no /reload required)
						QuestDefinition newDef = new QuestDefinition(
								quest.id(), quest.title(), quest.category(), // Updated coordinates
								quest.type(), quest.target(), quest.requiredAmount(),
								quest.icon(), quest.reward(), quest.rewardType(),
								quest.rewardAmount(), quest.parents(),
								payload.newX(), payload.newY()
						);
						QuestManager.LOADED_QUESTS.put(payload.questId(), newDef);

// 5. Broadcast the updated quest to all connected players
						QuestSyncPayload syncPacket = new QuestSyncPayload(
								newDef.id(), newDef.title(), newDef.category(), newDef.type(),
								newDef.target(), newDef.requiredAmount(),
								Registries.ITEM.getId(newDef.icon().getItem()).toString(),
								Registries.ITEM.getId(newDef.reward().getItem()).toString(),
								newDef.rewardType(), newDef.rewardAmount(), newDef.parents(),
								payload.newX(), payload.newY()
						);

						for (ServerPlayerEntity onlinePlayer : context.server().getPlayerManager().getPlayerList()) {
							ServerPlayNetworking.send(onlinePlayer, syncPacket);
						}

					} catch (Exception e) {
						PacPackQuests.LOGGER.error("Failed to update quest file!", e);
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

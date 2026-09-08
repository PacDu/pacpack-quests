package fr.pacdu.pacpackquests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.data.QuestManager;
import fr.pacdu.pacpackquests.data.QuestProgressHandler;
import fr.pacdu.pacpackquests.data.QuestState;
import fr.pacdu.pacpackquests.network.*;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing PacPack Quests...");
		ModConfig.load();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> QuestManager.loadQuests());

		PayloadTypeRegistry.playS2C().register(QuestSyncPayload.ID, QuestSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(QuestProgressPayload.ID, QuestProgressPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DeleteQuestPayload.ID, DeleteQuestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CreateCategoryPayload.ID, CreateCategoryPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DeleteCategoryPayload.ID, DeleteCategoryPayload.CODEC);

		PayloadTypeRegistry.playC2S().register(ClaimQuestPayload.ID, ClaimQuestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MoveQuestPayload.ID, MoveQuestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveQuestPayload.ID, SaveQuestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DeleteQuestPayload.ID, DeleteQuestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CreateCategoryPayload.ID, CreateCategoryPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DeleteCategoryPayload.ID, DeleteCategoryPayload.CODEC);

		// Connection Event: Sync all quests progress when a player joins
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			QuestState state = QuestState.getServerState(server);
			UUID playerId = player.getUuid();

			// Send both quest definition and its current progress to the joining player
			for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
				int progress = state.getProgress(playerId, quest.id());
				boolean finished = state.isFinished(playerId, quest.id());
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
				ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), progress, finished, claimed));
			}
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient()) {
				// Iterate through all dynamically loaded quests
				for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
					if (quest.type() == TaskType.MINE_BLOCK) {
						boolean isTarget;
						String target = quest.target();

						if (target.startsWith("#")) {
							TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, Identifier.of(target.substring(1)));
							isTarget = state.isIn(tag);
						} else {
							isTarget = Registries.BLOCK.getId(state.getBlock()).toString().equals(target);
						}

						if (isTarget) {
							QuestProgressHandler.incrementProgress((ServerPlayerEntity) player, quest, 1);
						}
					}
				}
			}
		});

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

		// Claim Event: Listen to reward claim requests
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
						ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), progress, true, true));
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
						Files.writeString(questFile, GSON.toJson(json));

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

		ServerPlayNetworking.registerGlobalReceiver(SaveQuestPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

				Path categoryDir = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + payload.category());
				try { Files.createDirectories(categoryDir); } catch (Exception ignored) {}

				Path questFile = categoryDir.resolve(payload.questId() + ".json");

				JsonObject json = new JsonObject();
				json.addProperty("title", payload.title());
				json.addProperty("type", payload.type().toString());
				json.addProperty("target", payload.target());
				json.addProperty("requiredAmount", payload.requiredAmount());
				json.addProperty("icon", payload.iconId());

				switch (payload.rewardType()) {
					case XP -> json.addProperty("reward", "xp");
					case LEVEL -> json.addProperty("reward", "level");
					case ITEM -> json.addProperty("reward", payload.rewardId());
				}
				json.addProperty("rewardAmount", payload.rewardAmount());

				if (payload.parents() != null && !payload.parents().isEmpty()) {
					json.add("parents", GSON.toJsonTree(payload.parents()));
				}

				json.addProperty("displayX", payload.displayX());
				json.addProperty("displayY", payload.displayY());

				try {
					Files.writeString(questFile, GSON.toJson(json));

					// Update server memory
					QuestDefinition newDef = new QuestDefinition(
							payload.questId(), payload.title(), payload.category(), payload.type(), payload.target(),
							payload.requiredAmount(), new ItemStack(Registries.ITEM.get(Identifier.of(payload.iconId()))),
							new ItemStack(Registries.ITEM.get(Identifier.of(payload.rewardId()))), payload.rewardType(),
							payload.rewardAmount(), payload.parents(), payload.displayX(), payload.displayY()
					);
					QuestManager.LOADED_QUESTS.put(payload.questId(), newDef);

					// Sync to players
					QuestSyncPayload syncPacket = new QuestSyncPayload(
							payload.questId(), payload.title(), payload.category(), payload.type(), payload.target(),
							payload.requiredAmount(), payload.iconId(), payload.rewardId(), payload.rewardType(),
							payload.rewardAmount(), payload.parents(), payload.displayX(), payload.displayY()
					);
					context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPacket));

				} catch (Exception e) {
					PacPackQuests.LOGGER.error("Failed to save quest", e);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(DeleteQuestPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

				QuestDefinition def = QuestManager.LOADED_QUESTS.get(payload.questId());
				if (def != null) {
					Path questFile = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + def.category() + "/" + payload.questId() + ".json");
					try {
						Files.deleteIfExists(questFile);
						QuestManager.LOADED_QUESTS.remove(payload.questId());

						context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, new DeleteQuestPayload(payload.questId())));
					} catch (Exception e) {
						PacPackQuests.LOGGER.error("Failed to delete quest", e);
					}
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CreateCategoryPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

				Path categoryDir = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + payload.category());
				try {
					if (!java.nio.file.Files.exists(categoryDir)) {
						java.nio.file.Files.createDirectories(categoryDir);
					}

					context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, new CreateCategoryPayload(payload.category())));
				} catch (Exception e) {
					PacPackQuests.LOGGER.error("Failed to create category folder", e);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(DeleteCategoryPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

				String categoryToDelete = payload.category();
				Path categoryDir = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + categoryToDelete);

				try {
					// 1. Supprimer le dossier et tout son contenu (les quêtes)
					java.io.File dir = categoryDir.toFile();
					if (dir.exists()) {
						java.io.File[] files = dir.listFiles();
						if (files != null) {
							for (java.io.File file : files) file.delete();
						}
						dir.delete();
					}

					// 2. Nettoyer la mémoire vive du serveur
					QuestManager.LOADED_QUESTS.values().removeIf(quest -> quest.category().equals(categoryToDelete));

					// 3. Informer tous les joueurs connectés de la suppression
					DeleteCategoryPayload syncPayload = new DeleteCategoryPayload(categoryToDelete);
					context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPayload));

				} catch (Exception e) {
					PacPackQuests.LOGGER.error("Failed to delete category folder", e);
				}
			});
		});
	}
}

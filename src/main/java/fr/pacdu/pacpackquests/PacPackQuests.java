package fr.pacdu.pacpackquests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.data.QuestManager;
import fr.pacdu.pacpackquests.data.QuestProgressHandler;
import fr.pacdu.pacpackquests.data.QuestState;
import fr.pacdu.pacpackquests.network.*;

import fr.pacdu.pacpackquests.util.RegistryType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Main entry point for the PacPack Quests mod on the server side.
 * Handles initialization, config loading, quest loading, event registration, and synchronization.
 */
public class PacPackQuests implements ModInitializer {

	public static final String MOD_ID = "PacPackQuests";
	// Initialize the SLF4J Logger with your Mod ID
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<UUID, Map<Identifier, Integer>> previousTickInventory = new HashMap<>();

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
        PayloadTypeRegistry.playS2C().register(ReorderCategoryPayload.ID, ReorderCategoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EditCategoryPayload.ID, EditCategoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestCompletedPayload.ID, QuestCompletedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncRegistryPayload.ID, SyncRegistryPayload.CODEC);

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
			ServerPlayNetworking.send(player, new ReorderCategoryPayload(fr.pacdu.pacpackquests.config.ModConfig.categoryOrder));

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

        // Check broken blocks
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

        // Check killed entities
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity player) {

				for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
					if (quest.type() == TaskType.KILL_MOB) {
						boolean isTarget;
						String target = quest.target();

						// Check tags (e.g., "#minecraft:skeletons") or direct IDs
						if (target.startsWith("#")) {
							TagKey<EntityType<?>> tag = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(target.substring(1)));
							isTarget = killedEntity.getType().isIn(tag);
						} else {
							isTarget = Registries.ENTITY_TYPE.getId(killedEntity.getType()).toString().equals(target);
						}

						if (isTarget) {
							QuestProgressHandler.incrementProgress(player, quest, 1);
						}
					}
				}
			}
		});

        // Check opened chests
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && hand == Hand.MAIN_HAND) {
                net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(hitResult.getBlockPos());
                if (be instanceof LootableContainerBlockEntity lootable) {
                    var lootKey = lootable.getLootTable();
                    computeOpenedChest(lootKey, player, be, false);
                }
            }
            return ActionResult.PASS;
        });

        // Check opened minecarts
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && hand == Hand.MAIN_HAND && hitResult != null) {
                if (entity instanceof net.minecraft.entity.vehicle.VehicleInventory vehicle) {
                    var lootKey = vehicle.getLootTable();
                    computeOpenedChest(lootKey, player, entity, true);
                }
            }
            return ActionResult.PASS;
        });

        //Check biome, structure and dimension
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Run every 5 ticks (4 times per second).
            // Fast enough to catch players flying at extreme speeds,
            // but 5x more optimized than vanilla Minecraft's advancement checks.
            if (server.getTicks() % 5 != 0) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                BlockPos pos = player.getBlockPos();
                var world = player.getEntityWorld();
                var registryManager = world.getRegistryManager();

                Map<Identifier, Integer> currentInv = new HashMap<>();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    net.minecraft.item.ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        Identifier id = Registries.ITEM.getId(stack.getItem());
                        currentInv.put(id, currentInv.getOrDefault(id, 0) + stack.getCount());
                    }
                }

                Map<Identifier, Integer> prevInv = previousTickInventory.computeIfAbsent(player.getUuid(), k -> new HashMap<>());
                Map<Identifier, Integer> gainedItems = new HashMap<>();

                for (Map.Entry<Identifier, Integer> entry : currentInv.entrySet()) {
                    int current = entry.getValue();
                    int previous = prevInv.getOrDefault(entry.getKey(), 0);
                    if (current > previous) {
                        gainedItems.put(entry.getKey(), current - previous);
                    }
                }
                previousTickInventory.put(player.getUuid(), currentInv);

                // Loop through all active quests
                for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {

                    // Placeholder: Skip if the quest is already claimed or fully progressed
                    // if (isQuestAlreadyFinishedForPlayer(player, quest.id())) continue;

                    boolean requirementMet = false;
                    String target = quest.target();
                    boolean isTag = target.startsWith("#");
                    Identifier targetId = Identifier.tryParse(isTag ? target.substring(1) : target);

                    if (targetId == null) continue;

                    switch (quest.type()) {
                        case OBTAIN_ITEM -> {
                            if (!gainedItems.isEmpty()) {
                                int gained = 0;
                                if (isTag) {
                                    TagKey<net.minecraft.item.Item> tagKey = TagKey.of(RegistryKeys.ITEM, targetId);
                                    for (Map.Entry<Identifier, Integer> entry : gainedItems.entrySet()) {
                                        if (Registries.ITEM.get(entry.getKey()).getDefaultStack().isIn(tagKey)) {
                                            gained += entry.getValue();
                                        }
                                    }
                                } else {
                                    gained = gainedItems.getOrDefault(targetId, 0);
                                }
                                if (gained > 0) {
                                    QuestProgressHandler.incrementProgress(player, quest, gained);
                                }
                            }
                        }
                        case EXPLORE_DIMENSION -> {
                            if (world.getRegistryKey().getValue().equals(targetId)) {
                                requirementMet = true;
                            }
                        }
                        case EXPLORE_BIOME -> {
                            var biomeEntry = world.getBiome(pos);
                            if (isTag) {
                                if (biomeEntry.isIn(TagKey.of(RegistryKeys.BIOME, targetId))) requirementMet = true;
                            } else {
                                if (biomeEntry.matchesId(targetId)) requirementMet = true;
                            }
                        }
                        case EXPLORE_STRUCTURE -> {
                            var structureRegistry = registryManager.getOrThrow(RegistryKeys.STRUCTURE);
                            StructureStart structureStart = null;

                            if (isTag) {
                                TagKey<net.minecraft.world.gen.structure.Structure> tag = TagKey.of(RegistryKeys.STRUCTURE, targetId);
                                structureStart = world.getStructureAccessor().getStructureContaining(pos, tag);
                            } else {
                                var structure = structureRegistry.get(targetId);
                                if (structure != null) {
                                    structureStart = world.getStructureAccessor().getStructureContaining(pos, structure);
                                }
                            }

                            if (structureStart != null && structureStart.hasChildren()) {
                                long structureUniqueId = structureStart.getPos().toLong();

                                // 1. Load the global quest state
                                QuestState state = QuestState.getServerState(server);
                                UUID playerUuid = player.getUuid();

                                // 2. Check if the player hasn't already discovered this exact structure instance
                                if (!state.hasDiscoveredStructure(playerUuid, quest.id(), structureUniqueId)) {

                                    // 3. Register the structure in the player's save file
                                    state.addDiscoveredStructure(playerUuid, quest.id(), structureUniqueId);

                                    // 4. Increment quest progress by 1
                                    requirementMet = true;
                                }
                            }
                        }
                    }

                    if (requirementMet) {
                        QuestProgressHandler.incrementProgress(player, quest, 1);
                    }
                }
            }
        });



        // ------- NETWORK ------- \\

		// Claim Event: Listen to reward claim requests
		ServerPlayNetworking.registerGlobalReceiver(ClaimQuestPayload.ID, (payload, context) -> context.server().execute(() -> {
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
        }));

		ServerPlayNetworking.registerGlobalReceiver(MoveQuestPayload.ID, (payload, context) -> context.server().execute(() -> {
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
        }));

		ServerPlayNetworking.registerGlobalReceiver(SaveQuestPayload.ID, (payload, context) -> context.server().execute(() -> {
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
        }));

		ServerPlayNetworking.registerGlobalReceiver(DeleteQuestPayload.ID, (payload, context) -> context.server().execute(() -> {
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
        }));

		ServerPlayNetworking.registerGlobalReceiver(CreateCategoryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            Path categoryDir = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + payload.category());
            try {
                if (!Files.exists(categoryDir)) {
                    Files.createDirectories(categoryDir);

                    ModConfig.categoryOrder.add(payload.category());
                    ModConfig.save();
                }

                context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, new CreateCategoryPayload(payload.category())));
            } catch (Exception e) {
                PacPackQuests.LOGGER.error("Failed to create category folder", e);
            }
        }));

		ServerPlayNetworking.registerGlobalReceiver(DeleteCategoryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            String categoryToDelete = payload.category();
            Path categoryDir = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + categoryToDelete);

            try {
                File dir = categoryDir.toFile();
                if (dir.exists()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File file : files) file.delete();
                    }
                    dir.delete();

                    ModConfig.categoryOrder.remove(payload.category());
                    ModConfig.save();
                }

                QuestManager.LOADED_QUESTS.values().removeIf(quest -> quest.category().equals(categoryToDelete));

                DeleteCategoryPayload syncPayload = new DeleteCategoryPayload(categoryToDelete);
                context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPayload));

            } catch (Exception e) {
                PacPackQuests.LOGGER.error("Failed to delete category folder", e);
            }
        }));

        ServerPlayNetworking.registerGlobalReceiver(ReorderCategoryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            ModConfig.categoryOrder = payload.categories();
            ModConfig.save();

            for (var player : context.server().getPlayerManager().getPlayerList()) {
                if (player != context.player()) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }));

        ServerPlayNetworking.registerGlobalReceiver(RequestRegistryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            List<String> entries = new ArrayList<>();
            if (payload.type() == RegistryType.STRUCTURE) {
                var registry = context.server().getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                registry.getIds().forEach(id -> entries.add(id.toString()));
                registry.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> entries.add("#" + key.id().toString())));
            } else if (payload.type() == RegistryType.LOOT_TABLE) {
                entries.add("#pacpackquests:loot_chest");
                var opt = context.server().getReloadableRegistries().createRegistryLookup().getOptional(RegistryKeys.LOOT_TABLE);
                opt.ifPresent(lootTableImpl -> lootTableImpl.streamKeys().forEach(key -> {
                    Identifier id = key.getValue();
                    if (id.getPath().startsWith("chests/")) {
                        entries.add(id.toString());
                    }
                }));
            }
            
            if (!entries.isEmpty()) {
                ServerPlayNetworking.send(context.player(), new SyncRegistryPayload(payload.type(), entries));
            }
        }));

        ServerPlayNetworking.registerGlobalReceiver(EditCategoryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            String categoryToEdit = payload.categoryOld();
            String newCategory = payload.categoryNew();
            Path categoryDirOld = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + categoryToEdit);
            Path categoryDirNew = FabricLoader.getInstance().getConfigDir().resolve("pacpackquests/quests/" + newCategory);

            try {
                File dir = categoryDirOld.toFile();
                if (dir.exists()) {
                    File newDir = categoryDirNew.toFile();
                    dir.renameTo(newDir);
                }

                java.util.List<QuestDefinition> questsToUpdate = new java.util.ArrayList<>();
                for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
                    if (quest.category().equals(categoryToEdit)) {
                        questsToUpdate.add(quest);
                    }
                }
                for (QuestDefinition quest : questsToUpdate) {
                    QuestDefinition updatedQuest = new QuestDefinition(quest.id(), quest.title(), newCategory,
                            quest.type(), quest.target(), quest.requiredAmount(), quest.icon(), quest.reward(),
                            quest.rewardType(), quest.rewardAmount(), quest.parents(), quest.displayX(), quest.displayY());
                    QuestManager.LOADED_QUESTS.put(quest.id(), updatedQuest);
                }

                EditCategoryPayload syncPayload = new EditCategoryPayload(categoryToEdit, newCategory);
                context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPayload));

            } catch (Exception e) {
                PacPackQuests.LOGGER.error("Failed to edit category folder", e);
            }
        }));
	}

    private static void computeOpenedChest(RegistryKey<LootTable> lootKey, PlayerEntity player, Object entity, boolean isEntity) {
        if (lootKey != null) {
            Identifier lootTableId = lootKey.getValue();
            for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
                if (quest.type() == TaskType.OPEN_LOOT_CHEST) {
                    String target = quest.target();
                    if (checkLootrStatus((ServerPlayerEntity) player, entity, isEntity) && (target.equals("#pacpackquests:loot_chest") || target.equals(lootTableId.toString()))) {
                        QuestProgressHandler.incrementProgress((ServerPlayerEntity) player, quest, 1);
                    }
                }
            }
        }
    }

        private static boolean checkLootrStatus(net.minecraft.server.network.ServerPlayerEntity player, Object target, boolean isEntity) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lootr")) {
            return true;
        }
        try {
            Class<?> infoClass = Class.forName("noobanidus.mods.lootr.common.api.data.ILootrInfoProvider");
            if (infoClass.isInstance(target)) {
                java.lang.reflect.Method hasOpened = infoClass.getMethod("hasOpened", java.util.UUID.class);
                boolean opened = (boolean) hasOpened.invoke(target, player.getUuid());
                return !opened;
            }
        } catch (Exception e) {
            LOGGER.warn("You are using an outdated or incompatible version of Lootr! Loot chest quests may be exploitable.");
        }
        return true;
    }
}

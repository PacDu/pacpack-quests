package fr.pacdu.pacpackquests.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.data.QuestManager;
import fr.pacdu.pacpackquests.data.QuestState;
import fr.pacdu.pacpackquests.reward.QuestReward;
import fr.pacdu.pacpackquests.util.PathSecurity;
import fr.pacdu.pacpackquests.util.RegistryType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.pacdu.pacpackquests.PacPackQuests.DEFAULT_CATEGORY_NAME;

public class ServerNetworkHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ServerNetworkHandler() {}

    public static void register() {
        // Claim Quest Reward
        ServerPlayNetworking.registerGlobalReceiver(ClaimQuestPayload.ID, (payload, context) -> context.server().execute(() -> {
            QuestState questState = QuestState.getServerState(context.server());
            ServerPlayerEntity player = context.player();
            UUID playerId = player.getUuid();

            QuestDefinition quest = QuestManager.LOADED_QUESTS.get(payload.questId());
            if (quest != null) {
                int progress = questState.getProgress(playerId, quest.id());
                boolean claimed = questState.isClaimed(playerId, quest.id());

                if (progress >= quest.requiredAmount() && !claimed) {
                    if (quest.rewards() != null) {
                        for (QuestReward reward : quest.rewards()) {
                            reward.grant(player, context.server().getRegistryManager());
                        }
                    }
                    questState.setClaimed(playerId, quest.id(), true);
                    ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), progress, true, true));
                }
            }
        }));

        // Move Quest Node
        ServerPlayNetworking.registerGlobalReceiver(MoveQuestPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            QuestDefinition quest = QuestManager.LOADED_QUESTS.get(payload.questId());
            if (quest == null) return;

            Optional<Path> questFileOpt = PathSecurity.getSafeQuestFile(quest.category(), payload.questId());
            if (questFileOpt.isEmpty()) {
                PacPackQuests.LOGGER.warn("Blocked unsafe quest move attempt: category={}, id={}", quest.category(), payload.questId());
                return;
            }

            Path questFile = questFileOpt.get();
            if (Files.exists(questFile)) {
                try {
                    JsonObject json = GSON.fromJson(Files.readString(questFile), JsonObject.class);
                    json.addProperty("displayX", payload.newX());
                    json.addProperty("displayY", payload.newY());
                    Files.writeString(questFile, GSON.toJson(json));

                    QuestDefinition newDef = quest.withPosition(payload.newX(), payload.newY());
                    QuestManager.LOADED_QUESTS.put(payload.questId(), newDef);

                    QuestSyncPayload syncPacket = new QuestSyncPayload(newDef, context.server().getRegistryManager());

                    for (ServerPlayerEntity onlinePlayer : context.server().getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(onlinePlayer, syncPacket);
                    }
                } catch (Exception e) {
                    PacPackQuests.LOGGER.error("Failed to update quest file!", e);
                }
            }
        }));

        // Save / Edit Quest
        ServerPlayNetworking.registerGlobalReceiver(SaveQuestPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            QuestDefinition quest = payload.toDefinition(context.server().getRegistryManager());

            if (QuestManager.saveQuest(quest, context.server().getRegistryManager())) {
                QuestSyncPayload syncPacket = new QuestSyncPayload(quest, context.server().getRegistryManager());
                context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPacket));
            }
        }));

        // Delete Quest
        ServerPlayNetworking.registerGlobalReceiver(DeleteQuestPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            QuestDefinition def = QuestManager.LOADED_QUESTS.get(payload.questId());
            if (def != null) {
                Optional<Path> questFileOpt = PathSecurity.getSafeQuestFile(def.category(), payload.questId());
                if (questFileOpt.isEmpty()) {
                    PacPackQuests.LOGGER.warn("Blocked unsafe delete quest attempt: category={}, id={}", def.category(), payload.questId());
                    return;
                }
                Path questFile = questFileOpt.get();
                try {
                    Files.deleteIfExists(questFile);
                    QuestManager.LOADED_QUESTS.remove(payload.questId());
                    context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, new DeleteQuestPayload(payload.questId())));

                    // Remove it from the parents of quests that has it as a parent
                    QuestManager.LOADED_QUESTS.forEach((id, questDefinition) -> {
                        if (questDefinition.parents().remove(payload.questId()) && QuestManager.saveQuest(questDefinition, context.server().getRegistryManager())) {
                            QuestSyncPayload syncPacket = new QuestSyncPayload(questDefinition, context.server().getRegistryManager());
                            context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPacket));
                        }
                    });
                } catch (Exception e) {
                    PacPackQuests.LOGGER.error("Failed to delete quest", e);
                }
            }
        }));

        // Create Category
        ServerPlayNetworking.registerGlobalReceiver(CreateCategoryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            Optional<Path> categoryDirOpt = PathSecurity.getSafeCategoryDir(payload.category());
            if (categoryDirOpt.isEmpty()) {
                PacPackQuests.LOGGER.warn("Blocked unsafe create category attempt: {}", payload.category());
                return;
            }
            Path categoryDir = categoryDirOpt.get();
            try {
                if (!Files.exists(categoryDir)) {
                    Files.createDirectories(categoryDir);
                    if (!ModConfig.categoryOrder.contains(payload.category())) {
                        ModConfig.categoryOrder.add(payload.category());
                        ModConfig.save();
                    }
                }
                context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, new CreateCategoryPayload(payload.category())));
            } catch (Exception e) {
                PacPackQuests.LOGGER.error("Failed to create category folder", e);
            }
        }));

        // Delete Category
        ServerPlayNetworking.registerGlobalReceiver(DeleteCategoryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            String categoryToDelete = payload.category();
            Optional<Path> categoryDirOpt = PathSecurity.getSafeCategoryDir(categoryToDelete);
            if (categoryDirOpt.isEmpty()) {
                PacPackQuests.LOGGER.warn("Blocked unsafe delete category attempt: {}", categoryToDelete);
                return;
            }
            Path categoryDir = categoryDirOpt.get();

            try {
                File dir = categoryDir.toFile();
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                    if (files != null) {
                        for (File file : files) file.delete();
                    }
                    dir.delete();
                }

                ModConfig.categoryOrder.remove(categoryToDelete);
                if (ModConfig.categoryOrder.isEmpty()) ModConfig.categoryOrder.add(DEFAULT_CATEGORY_NAME);
                ModConfig.save();

                QuestManager.LOADED_QUESTS.values().removeIf(quest -> quest.category().equals(categoryToDelete));
                DeleteCategoryPayload syncPayload = new DeleteCategoryPayload(categoryToDelete);
                context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPayload));
            } catch (Exception e) {
                PacPackQuests.LOGGER.error("Failed to delete category folder", e);
            }
        }));

        // Reorder Categories
        ServerPlayNetworking.registerGlobalReceiver(ReorderCategoryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            List<String> validCategories = payload.categories().stream()
                    .filter(PathSecurity::isValidIdentifier)
                    .toList();

            ModConfig.categoryOrder = new ArrayList<>(validCategories);
            ModConfig.save();

            ReorderCategoryPayload sanitizedPayload = new ReorderCategoryPayload(validCategories);
            for (var player : context.server().getPlayerManager().getPlayerList()) {
                if (player != context.player()) {
                    ServerPlayNetworking.send(player, sanitizedPayload);
                }
            }
        }));

        // Request Registry Entries
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

        // Edit Category
        ServerPlayNetworking.registerGlobalReceiver(EditCategoryPayload.ID, (payload, context) -> context.server().execute(() -> {
            if (!context.server().getPlayerManager().isOperator(context.player().getPlayerConfigEntry())) return;

            String categoryOld = payload.categoryOld();
            String categoryNew = payload.categoryNew();

            if (categoryOld == null || categoryNew == null || categoryOld.equals(categoryNew)) return;

            Optional<Path> categoryDirOldOpt = PathSecurity.getSafeCategoryDir(categoryOld);
            Optional<Path> categoryDirNewOpt = PathSecurity.getSafeCategoryDir(categoryNew);

            if (categoryDirOldOpt.isEmpty() || categoryDirNewOpt.isEmpty()) {
                PacPackQuests.LOGGER.warn("Blocked unsafe edit category attempt: old={}, new={}", categoryOld, categoryNew);
                return;
            }

            Path categoryDirOld = categoryDirOldOpt.get();
            Path categoryDirNew = categoryDirNewOpt.get();

            try {
                File dir = categoryDirOld.toFile();
                if (dir.exists() && dir.isDirectory()) {
                    File newDir = categoryDirNew.toFile();
                    dir.renameTo(newDir);
                }

                List<QuestDefinition> questsToUpdate = new ArrayList<>();
                for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
                    if (quest.category().equals(categoryOld)) {
                        questsToUpdate.add(quest);
                    }
                }
                for (QuestDefinition quest : questsToUpdate) {
                    QuestDefinition updatedQuest = quest.withCategory(categoryNew);
                    QuestManager.LOADED_QUESTS.put(quest.id(), updatedQuest);
                }

                int index = ModConfig.categoryOrder.indexOf(categoryOld);
                if (index != -1) {
                    ModConfig.categoryOrder.set(index, categoryNew);
                    ModConfig.save();
                }

                EditCategoryPayload syncPayload = new EditCategoryPayload(categoryOld, categoryNew);
                context.server().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPayload));
            } catch (Exception e) {
                PacPackQuests.LOGGER.error("Failed to edit category folder", e);
            }
        }));
    }
}

package fr.pacdu.pacpackquests.data;

import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.TaskType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Block;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuestEventHandler {

    private static final Map<UUID, Map<Identifier, Integer>> previousTickInventory = new HashMap<>();

    private QuestEventHandler() {}

    public static void register() {
        // Check broken blocks
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClient()) {
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

        // Check biome, structure, dimension, and inventory
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 5 != 0) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                BlockPos pos = player.getBlockPos();
                var world = player.getEntityWorld();
                var registryManager = world.getRegistryManager();

                Map<Identifier, Integer> currentInv = new HashMap<>();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
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

                for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
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
                                QuestState state = QuestState.getServerState(server);
                                UUID playerUuid = player.getUuid();

                                if (!state.hasDiscoveredStructure(playerUuid, quest.id(), structureUniqueId)) {
                                    state.addDiscoveredStructure(playerUuid, quest.id(), structureUniqueId);
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

    private static boolean checkLootrStatus(ServerPlayerEntity player, Object target, boolean isEntity) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lootr")) {
            return true;
        }
        try {
            Class<?> infoClass = Class.forName("noobanidus.mods.lootr.common.api.data.ILootrInfoProvider");
            if (infoClass.isInstance(target)) {
                java.lang.reflect.Method hasOpened = infoClass.getMethod("hasOpened", UUID.class);
                boolean opened = (boolean) hasOpened.invoke(target, player.getUuid());
                return !opened;
            }
        } catch (Exception e) {
            PacPackQuests.LOGGER.warn("You are using an outdated or incompatible version of Lootr! Loot chest quests may be exploitable.");
        }
        return true;
    }
}

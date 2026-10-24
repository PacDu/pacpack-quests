package fr.pacdu.pacpackquests.client.util;

import fr.pacdu.pacpackquests.util.ItemUtils;
import fr.pacdu.pacpackquests.util.RegistryType;
import fr.pacdu.pacpackquests.util.TagUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IconUtils {

    // CACHE: Stores the result of the heavy search so we only compute it ONCE per unique target.
    // The key is a combination of target and taskType (e.g., "minecraft:zombie_MOB") to prevent collisions.
    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    /**
     * Returns a representative item for ANY quest target.
     * Guaranteed to be extremely fast and lag-free thanks to the cache.
     */
    public static ItemStack getRepresentativeItem(String target, RegistryType type) {
        String cacheKey = target + "_" + type.name();

        if (ICON_CACHE.containsKey(cacheKey)) {
            return ICON_CACHE.get(cacheKey);
        }

        ItemStack result = computeRepresentativeItem(target, type);
        ICON_CACHE.put(cacheKey, result);
        return result;
    }

    private static ItemStack computeRepresentativeItem(String target, RegistryType type) {
        World world = MinecraftClient.getInstance().world;
        RegistryWrapper.WrapperLookup registries = world != null ? world.getRegistryManager() : DynamicRegistryManager.EMPTY;

        boolean isTag = target.startsWith("#");
        String rawTarget = isTag ? target.substring(1) : target;
        if (rawTarget.contains("[")) {
            rawTarget = rawTarget.substring(0, rawTarget.indexOf('['));
        }

        Identifier id = Identifier.tryParse(rawTarget);

        if (id == null) return getDefaultFallback(type).getDefaultStack();

        // --- 1. EXPLICIT RESOLUTION FOR TANGIBLE TARGETS ---
        switch (type) {
            case ITEM -> {
                return ItemUtils.fromCommandString(target, registries);
            }
            case BLOCK -> {
                if (isTag) return TagUtils.getFirstBlockInTag(id).asItem().getDefaultStack();
                Item blockItem = Registries.BLOCK.get(id).asItem();
                return blockItem != Items.AIR ? blockItem.getDefaultStack() : Items.BARRIER.getDefaultStack();
            }
            case MOB -> {
                if (id.toString().equals("minecraft:player")) return Items.PLAYER_HEAD.getDefaultStack();

                Identifier eggId;
                if (isTag) {
                    Identifier firstEntityId = Registries.ENTITY_TYPE.getId(TagUtils.getFirstEntityTypeInTag(id));
                    eggId = Identifier.of(firstEntityId.getNamespace(), firstEntityId.getPath() + "_spawn_egg");
                } else {
                    eggId = Identifier.of(id.getNamespace(), id.getPath() + "_spawn_egg");
                }

                if (Registries.ITEM.containsId(eggId)) return Registries.ITEM.get(eggId).getDefaultStack();
                return Items.SPAWNER.getDefaultStack();
            }
            default -> {}
        }

        // --- 2. HEURISTIC & DYNAMIC RESOLUTION FOR ABSTRACT CONCEPTS ---
        String namespace = id.getNamespace();
        String path = id.getPath();

        //Treat the "c" (Common) namespace exactly like Vanilla
        boolean isVanilla = namespace.equals("minecraft") || namespace.equals("c");

        //If the tag is in "c", we must search the "minecraft" namespace for items
        String searchNamespace = namespace.equals("c") ? "minecraft" : namespace;

        if (isVanilla) {
            Item heuristic = getHeuristicIcon(path, type);
            if (heuristic != null) return heuristic.getDefaultStack();
        }

        // Filter out more generic tag words like "taskType", "is", "primary"
        Set<String> keywords = Arrays.stream(path.split("[_/]"))
                .filter(w -> w.length() > 3)
                .filter(w -> !w.equals("biome") && !w.equals("structure") && !w.equals("dimension") && !w.equals("city"))
                .filter(w -> !w.equals("type") && !w.equals("primary"))
                .collect(Collectors.toSet());

        Item bestMatch = null;

        if (!keywords.isEmpty()) {
            for (Identifier itemId : Registries.ITEM.getIds()) {
                if (itemId.getNamespace().equals(searchNamespace)) {
                    String itemPath = itemId.getPath();
                    for (String kw : keywords) {
                        if (itemPath.contains(kw)) {
                            Item item = Registries.ITEM.get(itemId);
                            if (item instanceof BlockItem) return item.getDefaultStack();
                            if (bestMatch == null) bestMatch = item;
                        }
                    }
                }
            }
        }

        if (bestMatch != null) return bestMatch.getDefaultStack();

        if (!isVanilla) {
            Item heuristic = getHeuristicIcon(path, type);
            if (heuristic != null) return heuristic.getDefaultStack();
        }

        return getDefaultFallback(type).getDefaultStack();
    }

    private static Item getDefaultFallback(RegistryType type) {
        return switch (type) {
            case DIMENSION -> Items.OBSIDIAN;
            case BIOME -> Items.GRASS_BLOCK;
            case STRUCTURE -> Items.CHISELED_SANDSTONE;
            case LOOT_TABLE -> Items.CHEST;
            default -> Items.BARRIER;
        };
    }

    private static Item getHeuristicIcon(String path, RegistryType type) {
        path = path.toLowerCase();

        // Oceans & Rivers
        if (path.contains("ocean") || path.contains("river") || path.contains("beach") || path.contains("water")) return Items.WATER_BUCKET;
        // Forests & Trees
        if (path.contains("cherry")) return Items.CHERRY_SAPLING;
        if (path.contains("dark_forest")) return Items.DARK_OAK_SAPLING;
        if (path.contains("birch")) return Items.BIRCH_SAPLING;
        if (path.contains("mangrove")) return Items.MANGROVE_PROPAGULE;
        if (path.contains("jungle")) return Items.JUNGLE_SAPLING;
        if (path.contains("taiga") || path.contains("grove") || path.contains("pine")) return Items.SPRUCE_SAPLING;
        if (path.contains("forest") || path.contains("wood") || path.contains("tree")) return Items.OAK_SAPLING;
        // Deserts & Hot places
        if (path.contains("desert") || path.contains("sand")) return Items.SAND;
        if (path.contains("badlands") || path.contains("mesa") || path.contains("terracotta")) return Items.TERRACOTTA;
        if (path.contains("savanna") || path.contains("acacia")) return Items.ACACIA_SAPLING;
        // Cold places
        if (path.contains("ice") || path.contains("frozen") || path.contains("snow")) return Items.SNOW_BLOCK;
        // Mountains & Caves
        if (path.contains("peaks") || path.contains("mountain") || path.contains("hills")) return Items.STONE;
        if (path.contains("cave")) return Items.MOSS_BLOCK;
        // Unique Biomes
        if (path.contains("mushroom")) return Items.RED_MUSHROOM_BLOCK;
        if (path.contains("swamp")) return Items.LILY_PAD;
        // Nether & End
        if (path.contains("crimson")) return Items.CRIMSON_NYLIUM;
        if (path.contains("warped")) return Items.WARPED_NYLIUM;
        if (path.contains("basalt")) return Items.BASALT;

        //Structures
        if (path.contains("village")) return Items.BELL;
        if (path.contains("mineshaft")) return Items.RAIL;
        if (path.contains("stronghold")) return Items.END_PORTAL_FRAME;
        if (path.contains("monument")) return Items.PRISMARINE;
        if (path.contains("fortress")) return Items.NETHER_BRICKS;
        if (path.contains("end_city")) return Items.PURPUR_BLOCK;
        if (path.contains("ancient_city")) return Items.SCULK_SENSOR;
        if (path.contains("mansion")) return Items.TOTEM_OF_UNDYING;
        if (path.contains("shipwreck")) return Items.OAK_BOAT;
        if (path.contains("igloo")) return Items.SNOW_BLOCK;
        if (path.contains("ruined_portal")) return Items.CRYING_OBSIDIAN;
        if (path.contains("pyramid") || path.contains("temple")) {
            return path.contains("jungle") ? Items.MOSSY_COBBLESTONE : Items.CHISELED_SANDSTONE;
        }
        if (path.contains("swamp_hut")) return Items.CAULDRON;
        if (path.contains("pillager")) return Items.CROSSBOW;
        if (path.contains("bastion")) return Items.GILDED_BLACKSTONE;
        if (path.contains("ocean_ruin")) return Items.TRIDENT;
        if (path.contains("trail_ruins")) return Items.BRUSH;

        // Dimensions
        if (path.contains("nether")) return Items.NETHERRACK;
        if (path.contains("end")) return Items.END_STONE;
        if (path.contains("overworld")) return Items.GRASS_BLOCK;

        return null;
    }
}
package fr.pacdu.pacpackquests.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class TagUtils {

    /**
     * Gets the first item associated with a tag Identifier.
     * Perfect for getting a display icon for a UI.
     * @param tagId the Identifier of the tag.
     * @return the first Item in the tag list.
     */
    public static Item getFirstItemInTag(Identifier tagId) {
        // 1. Create a TagKey specifically for the ITEM registry
        TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);

        // 2. Fetch the list of entries (items) linked to this tag using the new 1.21.2+ method
        var entryList = Registries.ITEM.getOptional(tagKey);

        // 3. If the tag exists, return the first one found
        if (entryList.isPresent()) {
            for (var entry : entryList.get()) {
                return entry.value(); // Returns the very first item and stops the loop
            }
        }

        // 4. Safe fallback if the tag is empty or doesn't exist
        // (prevents UI crashes if a mod is removed)
        return Items.BARRIER;
    }

    /**
     * Gets the first block associated with a tag Identifier.
     * Perfect for getting a display icon for a UI.
     * @param tagId the Identifier of the tag.
     * @return the first Block in the tag list.
     */
    public static Block getFirstBlockInTag(Identifier tagId) {
        TagKey<Block> tagKey = TagKey.of(RegistryKeys.BLOCK, tagId);
        var entryList = Registries.BLOCK.getOptional(tagKey);

        if (entryList.isPresent()) {
            for (var entry : entryList.get()) {
                return entry.value(); // Returns the very first item and stops the loop
            }
        }

        return Blocks.BARRIER;
    }

    /**
     * Gets the first entity associated with a tag Identifier.
     * Perfect for getting a display icon for a UI.
     * @param tagId the Identifier of the tag.
     * @return the first Entity in the tag list.
     */
    public static EntityType<?> getFirstEntityTypeInTag(Identifier tagId) {
        TagKey<EntityType<?>> tagKey = TagKey.of(RegistryKeys.ENTITY_TYPE, tagId);
        var entryList = Registries.ENTITY_TYPE.getOptional(tagKey);

        if (entryList.isPresent()) {
            for (var entry : entryList.get()) {
                return entry.value();
            }
        }

        return EntityType.PLAYER;
    }

    /**
     * Gets ALL items associated with a tag Identifier.
     * Useful if you want to make the icon cycle through all possibilities.
     * @param tagId the Identifier of the tag.
     * @return a List of all the items in the tag.
     */
    public static List<Item> getAllItemsInTag(Identifier tagId) {
        TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
        List<Item> items = new ArrayList<>();

        Registries.ITEM.getOptional(tagKey).ifPresent(entries -> {
            // Extract the actual Item object from each RegistryEntry
            entries.forEach(entry -> items.add(entry.value()));
        });

        return items;
    }
}
package fr.pacdu.pacpackquests.util;

import com.mojang.brigadier.StringReader;
import fr.pacdu.pacpackquests.PacPackQuests;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

/**
 * Utility class for converting between ItemStacks and vanilla 1.21 item command syntax strings (with Data Components),
 * as well as matching player ItemStacks against quest targets.
 */
public final class ItemUtils {

    private static RegistryWrapper.WrapperLookup STATIC_LOOKUP;

    public static RegistryWrapper.WrapperLookup getStaticLookup() {
        if (STATIC_LOOKUP == null) {
            STATIC_LOOKUP = RegistryWrapper.WrapperLookup.of(
                    Registries.REGISTRIES.stream().map(r -> (RegistryWrapper.Impl<?>) r)
            );
        }
        return STATIC_LOOKUP;
    }

    private ItemUtils() {}

    public static String toCommandString(ItemStack stack) {
        return toCommandString(stack, null);
    }

    /**
     * Converts an ItemStack to standard vanilla 1.21 item command syntax.
     * If the item has modified components (e.g. enchanted books, potions), serializes them inside brackets:
     * "minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:feather_falling':4}}]"
     * If no components are modified, returns simple "namespace:path".
     */
    public static String toCommandString(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }

        // If no components were changed from default, return standard identifier
        if (stack.getComponentChanges().isEmpty()) {
            return Registries.ITEM.getId(stack.getItem()).toString();
        }

        RegistryWrapper.WrapperLookup lookup = registries != null ? registries : getStaticLookup();
        try {
            return new ItemStackArgument(stack.getRegistryEntry(), stack.getComponentChanges()).asString(lookup);
        } catch (Exception e) {
            PacPackQuests.LOGGER.warn("Failed to serialize item to command string: {}", stack, e);
            return Registries.ITEM.getId(stack.getItem()).toString();
        }
    }

    /**
     * Reconstructs an ItemStack from a target string.
     * Supports:
     * - Modern component syntax: "namespace:path[components]"
     * - Tags: "#namespace:tag_name" (returns representative first item)
     * - Standard items: "namespace:path"
     */
    public static ItemStack fromCommandString(String itemString, RegistryWrapper.WrapperLookup registries) {
        if (itemString == null || itemString.isEmpty() || itemString.equals("minecraft:air")) {
            return ItemStack.EMPTY;
        }

        // 1. Tag (#)
        if (itemString.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(itemString.substring(1));
            return tagId != null ? new ItemStack(TagUtils.getFirstItemInTag(tagId)) : ItemStack.EMPTY;
        }

        // 2. Modern syntax with Data Components [...]
        if (itemString.contains("[")) {
            RegistryWrapper.WrapperLookup lookup = registries != null ? registries : getStaticLookup();
            try {
                ItemStringReader reader = new ItemStringReader(lookup);
                ItemStringReader.ItemResult result = reader.consume(new StringReader(itemString));
                ItemStack stack = new ItemStack(result.item());
                stack.applyChanges(result.components());
                return stack;
            } catch (Exception e) {
                PacPackQuests.LOGGER.warn("Failed to parse item with components: {}", itemString, e);
            }
            // Fallback to base item if components failed to parse
            int bracketIndex = itemString.indexOf('[');
            Identifier baseId = Identifier.tryParse(itemString.substring(0, bracketIndex));
            if (baseId != null && Registries.ITEM.containsId(baseId)) {
                return new ItemStack(Registries.ITEM.get(baseId));
            }
            return ItemStack.EMPTY;
        }

        // 3. Standard item ID
        Identifier id = Identifier.tryParse(itemString);
        if (id != null && Registries.ITEM.containsId(id)) {
            return new ItemStack(Registries.ITEM.get(id));
        }

        return ItemStack.EMPTY;
    }

    /**
     * Checks if a player's ItemStack satisfies a quest target requirement.
     * Handles tags, component matching (e.g. enchanted book level, potion contents), and standard items.
     */
    public static boolean matches(ItemStack playerStack, String target, RegistryWrapper.WrapperLookup registries) {
        if (playerStack == null || playerStack.isEmpty() || target == null || target.isEmpty()) {
            return false;
        }

        // 1. Check item tag (#)
        if (target.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(target.substring(1));
            return tagId != null && playerStack.isIn(TagKey.of(RegistryKeys.ITEM, tagId));
        }

        // 2. Target with components [...]
        if (target.contains("[")) {
            ItemStack targetStack = fromCommandString(target, registries);
            if (targetStack.isEmpty()) return false;
            return matchesComponents(playerStack, targetStack);
        }

        // 3. Standard item comparison
        Identifier targetId = Identifier.tryParse(target);
        return targetId != null && Registries.ITEM.getId(playerStack.getItem()).equals(targetId);
    }

    /**
     * Compares an item and its components against a target template stack.
     */
    private static boolean matchesComponents(ItemStack playerStack, ItemStack targetStack) {
        if (!playerStack.isOf(targetStack.getItem())) {
            return false;
        }

        ComponentChanges targetChanges = targetStack.getComponentChanges();
        for (var entry : targetChanges.entrySet()) {
            ComponentType<?> type = entry.getKey();
            Optional<?> expectedOptional = entry.getValue();

            if (expectedOptional.isPresent()) {
                Object expected = expectedOptional.get();
                Object actual = playerStack.get(type);
                if (actual == null) {
                    return false;
                }

                // If comparing stored enchantments, allow equal or higher level
                if (type == DataComponentTypes.STORED_ENCHANTMENTS && expected instanceof ItemEnchantmentsComponent expEnch && actual instanceof ItemEnchantmentsComponent actEnch) {
                    for (var enchEntry : expEnch.getEnchantmentEntries()) {
                        if (actEnch.getLevel(enchEntry.getKey()) < enchEntry.getIntValue()) {
                            return false;
                        }
                    }
                } else if (!Objects.equals(actual, expected)) {
                    return false;
                }
            } else {
                // Expected component to be explicitly absent
                if (playerStack.get(type) != null) {
                    return false;
                }
            }
        }
        return true;
    }
}

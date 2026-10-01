package fr.pacdu.pacpackquests.util;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class TranslationUtils {

    private TranslationUtils() {}

    /**
     * Translate an id (e.g. "minecraft:stone" or "#minecraft:logs") in a lisible name.
     * @param entryId a valid ID in the Minecraft Registries.
     * @param type the type of the ID that correspond to a Minecraft Registry.
     */
    public static String getTranslatedName(String entryId, RegistryType type) {
        boolean isTag = entryId.startsWith("#");
        String rawId = isTag ? entryId.substring(1) : entryId;
        Identifier id = Identifier.tryParse(rawId);

        if (id == null) return entryId;

        if (isTag) {
            String path = id.getPath();
            return "Any " + path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
        }

        return switch (type) {
            case ITEM -> Registries.ITEM.containsId(id) ? Registries.ITEM.get(id).getName().getString() : rawId;
            case BLOCK -> Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id).getName().getString() : rawId;
            case MOB -> Registries.ENTITY_TYPE.containsId(id) ? Registries.ENTITY_TYPE.get(id).getName().getString() : rawId;
            default -> {
                String path = id.getPath();
                yield path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
            }
        };
    }
}
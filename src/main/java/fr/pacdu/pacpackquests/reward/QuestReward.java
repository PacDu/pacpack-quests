package fr.pacdu.pacpackquests.reward;

import fr.pacdu.pacpackquests.RewardType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Sealed interface representing a reward that can be granted to a player upon quest completion.
 * Specific reward types (Item, XP, Level, Command) encapsulate their own data and execution logic.
 */
public sealed interface QuestReward permits ItemReward, XpReward, LevelReward, CommandReward {

    RewardType type();

    int amount();

    void grant(ServerPlayerEntity player, DynamicRegistryManager registryManager);

    ItemStack createIconStack(RegistryWrapper.WrapperLookup registries);

    void write(PacketByteBuf buf);

    static QuestReward read(PacketByteBuf buf) {
        RewardType type = buf.readEnumConstant(RewardType.class);
        return switch (type) {
            case ITEM -> ItemReward.read(buf);
            case XP -> XpReward.read(buf);
            case LEVEL -> LevelReward.read(buf);
            case COMMAND -> CommandReward.read(buf);
        };
    }
}

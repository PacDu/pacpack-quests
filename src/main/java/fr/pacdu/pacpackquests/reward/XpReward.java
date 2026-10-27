package fr.pacdu.pacpackquests.reward;

import fr.pacdu.pacpackquests.RewardType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;

public record XpReward(int amount) implements QuestReward {

    @Override
    public RewardType type() {
        return RewardType.XP;
    }

    @Override
    public void grant(ServerPlayerEntity player, DynamicRegistryManager registryManager) {
        if (player != null && amount > 0) {
            player.addExperience(amount);
        }
    }

    @Override
    public ItemStack createIconStack(RegistryWrapper.WrapperLookup registries) {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(RewardType.XP);
        buf.writeInt(amount);
    }

    public static XpReward read(PacketByteBuf buf) {
        return new XpReward(buf.readInt());
    }
}

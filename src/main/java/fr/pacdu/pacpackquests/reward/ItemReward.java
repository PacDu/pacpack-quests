package fr.pacdu.pacpackquests.reward;

import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.util.ItemUtils;
import fr.pacdu.pacpackquests.util.TagUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public record ItemReward(String target, int amount) implements QuestReward {

    @Override
    public RewardType type() {
        return RewardType.ITEM;
    }

    @Override
    public void grant(ServerPlayerEntity player, DynamicRegistryManager registryManager) {
        if (target == null || target.isEmpty() || amount <= 0) return;

        // Random reward from a tag (e.g. "#minecraft:logs")
        if (target.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(target.substring(1));
            if (tagId != null) {
                List<Item> items = TagUtils.getAllItemsInTag(tagId);
                if (!items.isEmpty()) {
                    Item randomItem = items.get(player.getRandom().nextInt(items.size()));
                    giveItemStack(player, new ItemStack(randomItem, amount));
                    return;
                }
            }
        }

        // Item with components or standard item
        ItemStack rewardStack = ItemUtils.fromCommandString(target, registryManager);
        if (!rewardStack.isEmpty()) {
            rewardStack.setCount(amount);
            giveItemStack(player, rewardStack);
        }
    }

    /**
     * Safely gives an ItemStack to a player, dropping it at their feet if the inventory is full.
     */
    private void giveItemStack(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    @Override
    public ItemStack createIconStack(RegistryWrapper.WrapperLookup registries) {
        if (target == null || target.isEmpty()) return ItemStack.EMPTY;
        return ItemUtils.fromCommandString(target, registries);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(RewardType.ITEM);
        buf.writeString(target != null ? target : "");
        buf.writeInt(amount);
    }

    public static ItemReward read(PacketByteBuf buf) {
        return new ItemReward(buf.readString(), buf.readInt());
    }
}

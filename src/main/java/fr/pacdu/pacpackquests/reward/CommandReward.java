package fr.pacdu.pacpackquests.reward;

import fr.pacdu.pacpackquests.PacPackQuests;
import fr.pacdu.pacpackquests.RewardType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public record CommandReward(String command, String description, int amount) implements QuestReward {

    @Override
    public RewardType type() {
        return RewardType.COMMAND;
    }

    @Override
    public void grant(ServerPlayerEntity player, DynamicRegistryManager registryManager) {
        if (player == null || command == null || command.isEmpty() || amount <= 0) return;

        MinecraftServer server = player.getCommandSource().getServer();
        String cleanCommand = command.startsWith("/") ? command.substring(1) : command;

        ServerCommandSource source = server.getCommandSource()
                .withEntity(player)
                .withPosition(player.getEntityPos())
                .withRotation(player.getRotationClient())
                .withWorld(player.getEntityWorld())
                .withSilent();

        try {
            for (int i = 0; i < amount; i++) {
                server.getCommandManager().getDispatcher().execute(cleanCommand, source);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            PacPackQuests.LOGGER.error("Failed to execute quest reward command: {}", cleanCommand, e);
        }
    }

    @Override
    public ItemStack createIconStack(RegistryWrapper.WrapperLookup registries) {
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(RewardType.COMMAND);
        buf.writeString(command != null ? command : "");
        buf.writeString(description != null ? description : "");
        buf.writeInt(amount);
    }

    public static CommandReward read(PacketByteBuf buf) {
        String command = buf.readString();
        String description = buf.readString();
        int amount = buf.readInt();
        return new CommandReward(command, description, amount);
    }
}

package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record QuestSyncPayload(String questId, String title, int requiredAmount, String iconId) implements CustomPayload {
    public static final Id<QuestSyncPayload> ID = new Id<>(Identifier.of("pacpack-quests", "quest_sync"));

    public static final PacketCodec<RegistryByteBuf, QuestSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QuestSyncPayload::questId,
            PacketCodecs.STRING, QuestSyncPayload::title,
            PacketCodecs.INTEGER, QuestSyncPayload::requiredAmount,
            PacketCodecs.STRING, QuestSyncPayload::iconId,
            QuestSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
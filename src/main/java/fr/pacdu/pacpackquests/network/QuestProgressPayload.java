package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record QuestProgressPayload(String questId, int progress, boolean isFinished, boolean isClaimed) implements CustomPayload {
    public static final Id<QuestProgressPayload> ID = new Id<>(Identifier.of("pacpack-quests", "quest_progress"));

    // Add PacketCodecs.STRING for the questId at the start of the tuple
    public static final PacketCodec<RegistryByteBuf, QuestProgressPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QuestProgressPayload::questId,
            PacketCodecs.INTEGER, QuestProgressPayload::progress,
            PacketCodecs.BOOLEAN, QuestProgressPayload::isFinished,
            PacketCodecs.BOOLEAN, QuestProgressPayload::isClaimed,
            QuestProgressPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
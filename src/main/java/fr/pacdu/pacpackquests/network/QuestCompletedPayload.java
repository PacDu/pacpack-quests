package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// A lightweight payload to tell the client "Hey, show the toast for this quest ID!"
public record QuestCompletedPayload(String questId) implements CustomPayload {

    public static final CustomPayload.Id<QuestCompletedPayload> ID = new CustomPayload.Id<>(Identifier.of("pacpackquests", "quest_completed"));

    // 1.21 Packet Codec structure
    public static final PacketCodec<RegistryByteBuf, QuestCompletedPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QuestCompletedPayload::questId,
            QuestCompletedPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
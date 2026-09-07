package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DeleteQuestPayload(String questId) implements CustomPayload {
    public static final Id<DeleteQuestPayload> ID = new Id<>(Identifier.of("pacpackquests", "delete_quest"));

    public static final PacketCodec<RegistryByteBuf, DeleteQuestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, DeleteQuestPayload::questId,
            DeleteQuestPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
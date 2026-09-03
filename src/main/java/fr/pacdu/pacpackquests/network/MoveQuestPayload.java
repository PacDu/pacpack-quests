package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MoveQuestPayload(String questId, int newX, int newY) implements CustomPayload {
    public static final Id<MoveQuestPayload> ID = new Id<>(Identifier.of("pacpackquests", "move_quest"));

    public static final PacketCodec<RegistryByteBuf, MoveQuestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, MoveQuestPayload::questId,
            PacketCodecs.INTEGER, MoveQuestPayload::newX,
            PacketCodecs.INTEGER, MoveQuestPayload::newY,
            MoveQuestPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
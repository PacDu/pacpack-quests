package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ClaimQuestPayload(String questId) implements CustomPayload {
    public static final Id<ClaimQuestPayload> ID = new Id<>(Identifier.of("pacpack-quests", "claim_quest"));

    public static final PacketCodec<RegistryByteBuf, ClaimQuestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ClaimQuestPayload::questId,
            ClaimQuestPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
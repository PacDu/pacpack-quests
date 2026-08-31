package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record QuestSyncPayload(String questId, String title, String category, int requiredAmount, String iconId, String rewardId, int rewardAmount) implements CustomPayload {

    public static final Id<QuestSyncPayload> ID = new Id<>(Identifier.of("pacpackquests", "quest_sync"));

    // Manual codec to safely handle 7 variables
    public static final PacketCodec<RegistryByteBuf, QuestSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.questId());
                buf.writeString(value.title());
                buf.writeString(value.category());
                buf.writeInt(value.requiredAmount());
                buf.writeString(value.iconId());
                buf.writeString(value.rewardId());
                buf.writeInt(value.rewardAmount());
            },
            buf -> new QuestSyncPayload(
                    buf.readString(),
                    buf.readString(),
                    buf.readString(),
                    buf.readInt(),
                    buf.readString(),
                    buf.readString(),
                    buf.readInt()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
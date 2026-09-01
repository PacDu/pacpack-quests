package fr.pacdu.pacpackquests.network;

import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record QuestSyncPayload(
        String questId, String title, String category, TaskType type, String target, int requiredAmount,
        String iconId, String rewardId, RewardType rewardType, int rewardAmount, List<String> parents,
        int displayX, int displayY
) implements CustomPayload {

    public static final Id<QuestSyncPayload> ID = new Id<>(Identifier.of("pacpackquests", "quest_sync"));

    public static final PacketCodec<RegistryByteBuf, QuestSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.questId());
                buf.writeString(value.title());
                buf.writeString(value.category());
                buf.writeEnumConstant(value.type());
                buf.writeString(value.target());
                buf.writeInt(value.requiredAmount());
                buf.writeString(value.iconId());
                buf.writeString(value.rewardId());
                buf.writeEnumConstant(value.rewardType());
                buf.writeInt(value.rewardAmount());
                buf.writeCollection(value.parents(), PacketByteBuf::writeString);
                buf.writeInt(value.displayX());
                buf.writeInt(value.displayY());
            },
            buf -> new QuestSyncPayload(
                    buf.readString(),
                    buf.readString(),
                    buf.readString(),
                    buf.readEnumConstant(TaskType.class),
                    buf.readString(),
                    buf.readInt(),
                    buf.readString(),
                    buf.readString(),
                    buf.readEnumConstant(RewardType.class),
                    buf.readInt(),
                    buf.readCollection(ArrayList::new, PacketByteBuf::readString),
                    buf.readInt(),
                    buf.readInt()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
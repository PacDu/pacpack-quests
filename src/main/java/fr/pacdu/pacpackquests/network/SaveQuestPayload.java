package fr.pacdu.pacpackquests.network;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.QuestTask;
import fr.pacdu.pacpackquests.reward.QuestReward;
import fr.pacdu.pacpackquests.util.ItemUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SaveQuestPayload(
        String questId,
        String title,
        String category,
        String iconId,
        int displayX,
        int displayY,
        List<String> parents,
        Map<String, String> properties,
        List<QuestTask> tasks,
        List<QuestReward> rewards
) implements CustomPayload {

    public static final Id<SaveQuestPayload> ID = new Id<>(Identifier.of("pacpackquests", "save_quest"));

    public SaveQuestPayload(QuestDefinition quest, RegistryWrapper.WrapperLookup registries) {
        this(
                quest.id(),
                quest.title(),
                quest.category(),
                ItemUtils.toCommandString(quest.icon(), registries),
                quest.displayX(),
                quest.displayY(),
                quest.parents(),
                quest.properties(),
                quest.tasks(),
                quest.rewards()
        );
    }

    public static final PacketCodec<RegistryByteBuf, SaveQuestPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.questId());
                buf.writeString(value.title());
                buf.writeString(value.category());
                buf.writeString(value.iconId());
                buf.writeInt(value.displayX());
                buf.writeInt(value.displayY());
                buf.writeCollection(value.parents(), PacketByteBuf::writeString);
                buf.writeMap(value.properties(), PacketByteBuf::writeString, PacketByteBuf::writeString);
                buf.writeCollection(value.tasks(), (b, task) -> task.write(b));
                buf.writeCollection(value.rewards(), (b, reward) -> reward.write(b));
            },
            buf -> new SaveQuestPayload(
                    buf.readString(),
                    buf.readString(),
                    buf.readString(),
                    buf.readString(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readCollection(ArrayList::new, PacketByteBuf::readString),
                    buf.readMap(HashMap::new, PacketByteBuf::readString, PacketByteBuf::readString),
                    buf.readCollection(ArrayList::new, QuestTask::read),
                    buf.readCollection(ArrayList::new, QuestReward::read)
            )
    );

    public QuestDefinition toDefinition(RegistryWrapper.WrapperLookup registries) {
        ItemStack icon = ItemUtils.fromCommandString(iconId, registries);
        return new QuestDefinition(questId, title, category, icon, displayX, displayY, parents, properties, tasks, rewards);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

package fr.pacdu.pacpackquests;

import net.minecraft.network.PacketByteBuf;

/**
 * Represents an individual task or objective within a quest (e.g. mine a block, craft an item, visit a biome).
 * A quest can contain one or multiple tasks.
 */
public record QuestTask(TaskType type, String target, int requiredAmount) {

    public static QuestTask of(TaskType type, String target, int requiredAmount) {
        return new QuestTask(type, target, requiredAmount);
    }

    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(this.type);
        buf.writeString(this.target != null ? this.target : "");
        buf.writeInt(this.requiredAmount);
    }

    public static QuestTask read(PacketByteBuf buf) {
        TaskType type = buf.readEnumConstant(TaskType.class);
        String target = buf.readString();
        int requiredAmount = buf.readInt();
        return new QuestTask(type, target, requiredAmount);
    }
}

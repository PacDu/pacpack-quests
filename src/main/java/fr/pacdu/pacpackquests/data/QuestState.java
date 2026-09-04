package fr.pacdu.pacpackquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.pacdu.pacpackquests.QuestDefinition;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class QuestState extends PersistentState {

    // Structure: Player UUID -> (Quest ID -> Value)
    public final Map<UUID, Map<String, Integer>> progress = new HashMap<>();
    public final Map<UUID, Map<String, Boolean>> claimed = new HashMap<>();

    public int getProgress(UUID player, String questId) {
        return progress.getOrDefault(player, new HashMap<>()).getOrDefault(questId, 0);
    }

    public void setProgress(UUID player, String questId, int amount) {
        progress.computeIfAbsent(player, k -> new HashMap<>()).put(questId, amount);
        this.markDirty();
    }

    public boolean isFinished(UUID player, String questId) {
        QuestDefinition quest = QuestManager.LOADED_QUESTS.get(questId);
        int progress = getProgress(player, quest.id());
        return progress >= quest.requiredAmount();
    }

    public boolean isClaimed(UUID player, String questId) {
        return claimed.getOrDefault(player, new HashMap<>()).getOrDefault(questId, false);
    }

    public void setClaimed(UUID player, String questId, boolean isClaimed) {
        claimed.computeIfAbsent(player, k -> new HashMap<>()).put(questId, isClaimed);
        this.markDirty();
    }

    // 1. Define the Codec (Mojang's new serialization system replacing NBT methods)
    public static final Codec<QuestState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Serialize the progress map
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .fieldOf("progress").forGetter(QuestState::getProgressMapAsString),

            // Serialize the claimed rewards map
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.BOOL))
                    .fieldOf("claimed").forGetter(QuestState::getClaimedMapAsString)
    ).apply(instance, QuestState::createFromMaps));

    // 2. Helper methods to convert UUIDs to Strings for the Codec
    private Map<String, Map<String, Integer>> getProgressMapAsString() {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        this.progress.forEach((uuid, map) -> result.put(uuid.toString(), map));
        return result;
    }

    private Map<String, Map<String, Boolean>> getClaimedMapAsString() {
        Map<String, Map<String, Boolean>> result = new HashMap<>();
        this.claimed.forEach((uuid, map) -> result.put(uuid.toString(), map));
        return result;
    }

    private static QuestState createFromMaps(Map<String, Map<String, Integer>> progressMap, Map<String, Map<String, Boolean>> claimedMap) {
        QuestState state = new QuestState();
        progressMap.forEach((uuid, map) -> state.progress.put(UUID.fromString(uuid), new HashMap<>(map)));
        claimedMap.forEach((uuid, map) -> state.claimed.put(UUID.fromString(uuid), new HashMap<>(map)));
        return state;
    }

    // 3. Global accessor for server state, using the new Codec system
    private static final PersistentStateType<QuestState> TYPE = new PersistentStateType<>(
            "pacpackquests_data", // ID of the save file
            QuestState::new,      // Factory method
            QuestState.CODEC,     // Our custom Codec
            DataFixTypes.ADVANCEMENTS
    );

    public static QuestState getServerState(MinecraftServer server) {
        // getOrCreate now only takes the TYPE as a single argument
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE);
    }
}
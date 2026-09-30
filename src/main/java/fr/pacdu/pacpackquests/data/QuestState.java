package fr.pacdu.pacpackquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.pacdu.pacpackquests.QuestDefinition;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.*;

public class QuestState extends PersistentState {

    // Structure: Player UUID -> (Quest ID -> Value)
    public final Map<UUID, Map<String, Integer>> progress = new HashMap<>();
    public final Map<UUID, Map<String, Boolean>> claimed = new HashMap<>();

    // Structure: Player UUID -> (Quest ID -> Set of discovered ChunkPos as Longs)
    public final Map<UUID, Map<String, Set<Long>>> discoveredStructures = new HashMap<>();

    public int getProgress(UUID player, String questId) {
        return progress.getOrDefault(player, new HashMap<>()).getOrDefault(questId, 0);
    }

    public void setProgress(UUID player, String questId, int amount) {
        progress.computeIfAbsent(player, k -> new HashMap<>()).put(questId, amount);
        this.markDirty();
    }

    public boolean isFinished(UUID player, String questId) {
        QuestDefinition quest = QuestManager.LOADED_QUESTS.get(questId);
        int currentProgress = getProgress(player, quest.id());
        return currentProgress >= quest.requiredAmount();
    }

    public boolean isClaimed(UUID player, String questId) {
        return claimed.getOrDefault(player, new HashMap<>()).getOrDefault(questId, false);
    }

    public void setClaimed(UUID player, String questId, boolean isClaimed) {
        claimed.computeIfAbsent(player, k -> new HashMap<>()).put(questId, isClaimed);
        this.markDirty();
    }

    // --- New Methods for Structure Tracking ---

    public boolean hasDiscoveredStructure(UUID player, String questId, long chunkPosLong) {
        return discoveredStructures.getOrDefault(player, new HashMap<>())
                .getOrDefault(questId, new HashSet<>())
                .contains(chunkPosLong);
    }

    public void addDiscoveredStructure(UUID player, String questId, long chunkPosLong) {
        discoveredStructures
                .computeIfAbsent(player, k -> new HashMap<>())
                .computeIfAbsent(questId, k -> new HashSet<>())
                .add(chunkPosLong);
        this.markDirty();
    }

    // 1. Define the Codec
    public static final Codec<QuestState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Serialize the progress map
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .fieldOf("progress").forGetter(QuestState::getProgressMapAsString),

            // Serialize the claimed rewards map
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.BOOL))
                    .fieldOf("claimed").forGetter(QuestState::getClaimedMapAsString),

            // Serialize the discovered structures map
            // We use optionalFieldOf to prevent crashing when loading old saves that don't have this data yet
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.list(Codec.LONG)))
                    .optionalFieldOf("discovered_structures", new HashMap<>()).forGetter(QuestState::getDiscoveredStructuresMapAsString)

    ).apply(instance, QuestState::createFromMaps));


    // 2. Helper methods to convert UUIDs and Sets to Strings/Lists for the Codec
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

    private Map<String, Map<String, List<Long>>> getDiscoveredStructuresMapAsString() {
        Map<String, Map<String, List<Long>>> result = new HashMap<>();
        this.discoveredStructures.forEach((uuid, map) -> {
            Map<String, List<Long>> listMap = new HashMap<>();
            // Codec handles Lists better than Sets, so we convert them for saving
            map.forEach((questId, set) -> listMap.put(questId, new ArrayList<>(set)));
            result.put(uuid.toString(), listMap);
        });
        return result;
    }

    // Updated factory method to include the third map
    private static QuestState createFromMaps(
            Map<String, Map<String, Integer>> progressMap,
            Map<String, Map<String, Boolean>> claimedMap,
            Map<String, Map<String, List<Long>>> discoveredMap) {

        QuestState state = new QuestState();
        progressMap.forEach((uuid, map) -> state.progress.put(UUID.fromString(uuid), new HashMap<>(map)));
        claimedMap.forEach((uuid, map) -> state.claimed.put(UUID.fromString(uuid), new HashMap<>(map)));

        // Convert the Lists back to Sets for runtime memory efficiency
        discoveredMap.forEach((uuid, map) -> {
            Map<String, Set<Long>> setMap = new HashMap<>();
            map.forEach((questId, list) -> setMap.put(questId, new HashSet<>(list)));
            state.discoveredStructures.put(UUID.fromString(uuid), setMap);
        });

        return state;
    }

    // 3. Global accessor for server state
    private static final PersistentStateType<QuestState> TYPE = new PersistentStateType<>(
            "pacpackquests_data", // ID of the save file
            QuestState::new,      // Factory method
            QuestState.CODEC,     // Our custom Codec
            DataFixTypes.ADVANCEMENTS
    );

    public static QuestState getServerState(MinecraftServer server) {
        return Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getPersistentStateManager().getOrCreate(TYPE);
    }
}
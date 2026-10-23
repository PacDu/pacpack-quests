package fr.pacdu.pacpackquests.data;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.network.QuestProgressPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;
import java.util.UUID;

public class QuestProgressHandler {

    public static void incrementProgress(ServerPlayerEntity player, QuestDefinition quest, int amountToAdd) {
        setProgress(player, quest, amountToAdd, true);
    }

    public static void setProgress(ServerPlayerEntity player, QuestDefinition quest, int amountToSet) {
        setProgress(player, quest, amountToSet, false);
    }

    // Generic method to safely set progress and sync it with the client
    private static void setProgress(ServerPlayerEntity player, QuestDefinition quest, int amount, boolean isIncrementation) {
        MinecraftServer server = Objects.requireNonNull(player.getEntityWorld().getServer());
        QuestState state = QuestState.getServerState(server);
        UUID playerId = player.getUuid();

        // Dependency Check: Block progression if any parent is not finished yet
        for (String parentId : quest.parents()) {
            if (!state.isFinished(playerId, parentId)) {
                return;
            }
        }

        int current = state.getProgress(playerId, quest.id());

        if (current < quest.requiredAmount()) {
            if (isIncrementation) amount = current + amount;
            int newProgress = Math.clamp(amount, current, quest.requiredAmount());
            boolean isFinished = newProgress >= quest.requiredAmount();
            state.setProgress(player, quest.id(), newProgress);

            ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), newProgress, isFinished, false));
        }
    }
}
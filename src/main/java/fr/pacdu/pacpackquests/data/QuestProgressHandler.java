package fr.pacdu.pacpackquests.data;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.network.QuestProgressPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;
import java.util.UUID;

public class QuestProgressHandler {

    // Generic method to safely increment progress and sync it with the client
    public static void incrementProgress(ServerPlayerEntity player, QuestDefinition quest, int amountToAdd) {
        MinecraftServer server = Objects.requireNonNull(player.getEntityWorld().getServer());
        QuestState state = QuestState.getServerState(server);
        UUID playerId = player.getUuid();

        // Dependency Check: Block progression if any parent is not claimed yet
        for (String parentId : quest.parents()) {
            if (!state.isClaimed(playerId, parentId)) {
                return;
            }
        }

        int current = state.getProgress(playerId, quest.id());

        if (current < quest.requiredAmount()) {
            int newProgress = Math.min(current + amountToAdd, quest.requiredAmount());
            state.setProgress(playerId, quest.id(), newProgress);

            ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), newProgress, false));
        }
    }
}
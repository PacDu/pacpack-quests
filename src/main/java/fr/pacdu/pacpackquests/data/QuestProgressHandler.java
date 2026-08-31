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
        // Objects.requireNonNull garantit à l'IDE que le résultat ne sera jamais null
        MinecraftServer server = Objects.requireNonNull(player.getEntityWorld().getServer());
        QuestState state = QuestState.getServerState(server);
        UUID playerId = player.getUuid();

        int current = state.getProgress(playerId, quest.id());

        if (current < quest.requiredAmount()) {
            // Prevent progression from exceeding the required amount
            int newProgress = Math.min(current + amountToAdd, quest.requiredAmount());

            state.setProgress(playerId, quest.id(), newProgress);

            // Send visual update back to the client
            ServerPlayNetworking.send(player, new QuestProgressPayload(quest.id(), newProgress, false));
        }
    }
}
package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.client.screen.QuestScreen;
import fr.pacdu.pacpackquests.client.screen.SelectionScreen;
import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.network.*;
import fr.pacdu.pacpackquests.util.RegistryType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

public class ClientNetworkHandler {

    private ClientNetworkHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(QuestSyncPayload.ID, (payload, context) -> context.client().execute(() -> {
            Item icon = Registries.ITEM.get(Identifier.of(payload.iconId()));
            QuestDefinition def = new QuestDefinition(
                    payload.questId(), payload.title(), payload.category(), payload.type(), payload.target(),
                    payload.requiredAmount(), new ItemStack(icon), payload.reward(), payload.rewardType(),
                    payload.rewardAmount(), payload.parents(), payload.displayX(), payload.displayY()
            );
            PacPackQuestsClient.CLIENT_DEFINITIONS.put(payload.questId(), def);

            for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
                if (!PacPackQuestsClient.CLIENT_CATEGORIES.contains(quest.category())) {
                    PacPackQuestsClient.CLIENT_CATEGORIES.add(quest.category());
                }
            }

            List<String> configuredOrder = ModConfig.categoryOrder;
            for (String category : configuredOrder) {
                if (!PacPackQuestsClient.CLIENT_CATEGORIES.contains(category)) {
                    PacPackQuestsClient.CLIENT_CATEGORIES.add(category);
                }
            }

            // Config-based sorting
            PacPackQuestsClient.CLIENT_CATEGORIES.sort((cat1, cat2) -> {
                int index1 = configuredOrder.indexOf(cat1);
                int index2 = configuredOrder.indexOf(cat2);

                if (index1 == -1 && index2 == -1) return cat1.compareTo(cat2);
                if (index1 == -1) return 1;
                if (index2 == -1) return -1;
                return Integer.compare(index1, index2);
            });

            if (context.client().currentScreen instanceof QuestScreen qs) qs.refreshUI();
        }));

        ClientPlayNetworking.registerGlobalReceiver(QuestProgressPayload.ID, (payload, context) -> context.client().execute(() -> {
            PacPackQuestsClient.CLIENT_PROGRESS.put(payload.questId(), payload.progress());
            PacPackQuestsClient.CLIENT_FINISHED.put(payload.questId(), payload.isFinished());
            PacPackQuestsClient.CLIENT_CLAIMED.put(payload.questId(), payload.isClaimed());

            if (context.client().currentScreen instanceof QuestScreen questScreen) {
                questScreen.updateClaimButtonState();
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(DeleteQuestPayload.ID, (payload, context) -> context.client().execute(() -> {
            PacPackQuestsClient.CLIENT_DEFINITIONS.remove(payload.questId());
            PacPackQuestsClient.CLIENT_PROGRESS.remove(payload.questId());
            PacPackQuestsClient.CLIENT_FINISHED.remove(payload.questId());
            PacPackQuestsClient.CLIENT_CLAIMED.remove(payload.questId());

            if (context.client().currentScreen instanceof QuestScreen questScreen) {
                questScreen.refreshUI();
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(CreateCategoryPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (!PacPackQuestsClient.CLIENT_CATEGORIES.contains(payload.category())) {
                PacPackQuestsClient.CLIENT_CATEGORIES.add(payload.category());
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(DeleteCategoryPayload.ID, (payload, context) -> context.client().execute(() -> {
            PacPackQuestsClient.CLIENT_DEFINITIONS.entrySet().removeIf(entry -> entry.getValue().category().equals(payload.category()));

            if (context.client().currentScreen instanceof QuestScreen questScreen) {
                questScreen.removeCategory(payload.category());
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(ReorderCategoryPayload.ID, (payload, context) -> context.client().execute(() -> {
            PacPackQuestsClient.CLIENT_CATEGORIES.clear();
            PacPackQuestsClient.SERVER_STRUCTURES.clear();
            PacPackQuestsClient.SERVER_LOOT_TABLES.clear();
            PacPackQuestsClient.CLIENT_CATEGORIES.addAll(payload.categories());
            if (context.client().currentScreen instanceof QuestScreen questScreen) {
                questScreen.refreshUI();
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(EditCategoryPayload.ID, (payload, context) -> context.client().execute(() -> {
            for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
                if (quest.category().equals(payload.categoryOld())) {
                    QuestDefinition updatedQuest = new QuestDefinition(quest.id(), quest.title(), payload.categoryNew(),
                            quest.taskType(), quest.target(), quest.requiredAmount(), quest.icon(), quest.reward(),
                            quest.rewardType(), quest.rewardAmount(), quest.parents(), quest.displayX(), quest.displayY());
                    PacPackQuestsClient.CLIENT_DEFINITIONS.put(quest.id(), updatedQuest);
                }
            }

            if (context.client().currentScreen instanceof QuestScreen questScreen) {
                questScreen.renameCategory(payload.categoryOld(), payload.categoryNew());
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(SyncRegistryPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (payload.type() == RegistryType.STRUCTURE) {
                PacPackQuestsClient.SERVER_STRUCTURES.clear();
                PacPackQuestsClient.SERVER_STRUCTURES.addAll(payload.entries());
                if (context.client().currentScreen instanceof SelectionScreen screen && screen.getRegistryType() == RegistryType.STRUCTURE) {
                    screen.refreshEntries(PacPackQuestsClient.SERVER_STRUCTURES);
                }
            } else if (payload.type() == RegistryType.LOOT_TABLE) {
                PacPackQuestsClient.SERVER_LOOT_TABLES.clear();
                PacPackQuestsClient.SERVER_LOOT_TABLES.addAll(payload.entries());
                if (context.client().currentScreen instanceof SelectionScreen screen && screen.getRegistryType() == RegistryType.LOOT_TABLE) {
                    screen.refreshEntries(PacPackQuestsClient.SERVER_LOOT_TABLES);
                }
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(QuestCompletedPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                QuestDefinition quest = PacPackQuestsClient.CLIENT_DEFINITIONS.get(payload.questId());
                if (quest != null) {
                    context.client().getToastManager().add(new QuestCompletedToast(quest));
                }
            });
        });
    }
}

package fr.pacdu.pacpackquests.mixin;

import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.data.QuestManager;
import fr.pacdu.pacpackquests.data.QuestProgressHandler;
import fr.pacdu.pacpackquests.QuestDefinition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public class CraftingResultSlotMixin {

    // The @Shadow annotation allows access to the private variables of the original class
    @Shadow @Final private PlayerEntity player;
    @Shadow private int amount;

    // We inject into the game's internal statistics method
    @Inject(method = "onCrafted(Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"))
    private void onCraftedItem(ItemStack stack, CallbackInfo ci) {
        if (!this.player.getEntityWorld().isClient() && this.player instanceof ServerPlayerEntity serverPlayer) {

            // Anti-air security
            if (stack.isEmpty()) return;

            for (QuestDefinition quest : QuestManager.LOADED_QUESTS.values()) {
                if (quest.type() == TaskType.CRAFT_ITEM) {
                    String target = quest.target().trim();
                    boolean isTarget = false;

                    if (target.startsWith("#")) {
                        TagKey<Item> tag = TagKey.of(RegistryKeys.ITEM, Identifier.of(target.substring(1)));
                        isTarget = stack.isIn(tag);
                    } else {
                        isTarget = Registries.ITEM.getId(stack.getItem()).toString().equals(target);
                    }

                    if (isTarget) {
                        // “this.amount” contains the exact number of items selected (required for Shift-click)
                        QuestProgressHandler.incrementProgress(serverPlayer, quest, this.amount);
                    }
                }
            }
        }
    }
}
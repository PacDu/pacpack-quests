package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.network.DeleteQuestPayload;
import fr.pacdu.pacpackquests.network.SaveQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;

public class EditQuestScreen extends Screen {

    private final QuestScreen parent;
    private final String questId;
    private final String category;
    private final int gridX, gridY;

    private TextFieldWidget idField, titleField, targetField, reqAmountField, iconField, rewardField, rewardAmountField, parentsField;
    private TaskType currentTaskType = TaskType.MINE_BLOCK;
    private RewardType currentRewardType = RewardType.ITEM;

    private String errorMessage = null;

    public EditQuestScreen(QuestScreen parent, String questId, String category, int gridX, int gridY) {
        super(Text.literal(questId == null ? I18n.translate("gui.pacpack-quests.create_new_quest") : I18n.translate("gui.pacpack-quests.edit_quest")));
        this.parent = parent;
        this.questId = questId;
        this.category = category;
        this.gridX = gridX;
        this.gridY = gridY;
    }

    @Override
    protected void init() {
        int col1 = this.width / 2 - 155;
        int col2 = this.width / 2 + 5;
        int fieldWidth = 150;

        int yStep = 34; // 20px for the field + 14px for the text above
        int totalFormHeight = (yStep * 5) + 25; // 5 rows + the buttons row

        int y = Math.max(35, (this.height - totalFormHeight) / 2);

        // ROW 1: ID & Title
        this.idField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.quest_id"));
        if (questId != null) {
            this.idField.setText(questId);
            this.idField.setEditable(false);
        } else {
            this.idField.setTextPredicate(text -> text.matches("^[a-z0-9_]*$"));
            this.idField.setPlaceholder(Text.translatable("form.pacpack-quests.quest_id_placeholder").formatted(Formatting.DARK_GRAY));
        }
        this.addDrawableChild(this.idField);

        this.iconField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.icon_item_id"));
        this.iconField.setPlaceholder(Text.translatable("form.pacpack-quests.icon_item_id_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.iconField);

        // ROW 2: Task Type & Target
        y += yStep;
        this.titleField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.title"));
        this.titleField.setPlaceholder(Text.translatable("form.pacpack-quests.title_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.titleField);

        this.parentsField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.parents"));
        this.parentsField.setPlaceholder(Text.translatable("form.pacpack-quests.parents_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.parentsField);

        // ROW 3: Req Amount & Icon
        y += yStep - 10;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(I18n.translate("form.pacpack-quests.task_type") + ": " + currentTaskType.name()), button -> {
            int nextOrdinal = (currentTaskType.ordinal() + 1) % TaskType.values().length;
            currentTaskType = TaskType.values()[nextOrdinal];
            button.setMessage(Text.literal(I18n.translate("form.pacpack-quests.task_type") + ": " + currentTaskType.name()));
            switch(currentTaskType) {
                case MINE_BLOCK -> this.targetField.setPlaceholder(Text.translatable("form.pacpack-quests.task_target_placeholder_mine").formatted(Formatting.DARK_GRAY));
                case CRAFT_ITEM -> this.targetField.setPlaceholder(Text.translatable("form.pacpack-quests.task_target_placeholder_craft").formatted(Formatting.DARK_GRAY));
                case KILL_MOB -> this.targetField.setPlaceholder(Text.translatable("form.pacpack-quests.task_target_placeholder_kill").formatted(Formatting.DARK_GRAY));
            }
        }).dimensions(col1, y, fieldWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal(I18n.translate("form.pacpack-quests.reward_type") + ": " + currentRewardType.name()), button -> {
            int nextOrdinal = (currentRewardType.ordinal() + 1) % RewardType.values().length;
            currentRewardType = RewardType.values()[nextOrdinal];
            button.setMessage(Text.literal(I18n.translate("form.pacpack-quests.reward_type") + ": " + currentRewardType.name()));
            this.rewardField.setVisible(currentRewardType == RewardType.ITEM);
        }).dimensions(col2, y, fieldWidth, 20).build());

        // ROW 4: Reward Type & Target
        y += yStep;
        this.targetField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.task_target"));
        this.targetField.setPlaceholder(Text.translatable("form.pacpack-quests.task_target_placeholder_mine").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.targetField);

        this.rewardField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.reward_item"));
        this.rewardField.setPlaceholder(Text.translatable("form.pacpack-quests.reward_item_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.rewardField);

        // ROW 5: Reward Amount & Parents
        y += yStep;
        this.reqAmountField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.required_amount"));
        this.reqAmountField.setTextPredicate(text -> text.matches("^[0-9]*$"));
        this.reqAmountField.setPlaceholder(Text.translatable("form.pacpack-quests.required_amount_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.reqAmountField);

        this.rewardAmountField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.reward_amount"));
        this.rewardAmountField.setTextPredicate(text -> text.matches("^[0-9]*$"));
        this.rewardAmountField.setPlaceholder(Text.translatable("form.pacpack-quests.reward_amount_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.rewardAmountField);

        // --- PREFILL DATA IF EDITING ---
        if (questId != null && PacPackQuestsClient.CLIENT_DEFINITIONS.containsKey(questId)) {
            QuestDefinition def = PacPackQuestsClient.CLIENT_DEFINITIONS.get(questId);
            this.titleField.setText(def.title());
            this.currentTaskType = def.type();
            this.targetField.setText(def.target());
            this.reqAmountField.setText(String.valueOf(def.requiredAmount()));
            this.iconField.setText(Registries.ITEM.getId(def.icon().getItem()).toString());
            this.currentRewardType = def.rewardType();
            this.rewardField.setText(Registries.ITEM.getId(def.reward().getItem()).toString());
            this.rewardAmountField.setText(String.valueOf(def.rewardAmount()));
            if (def.parents() != null) {
                this.parentsField.setText(String.join(",", def.parents()));
            }
        }

        // --- BOTTOM ACTION BUTTONS ---
        y += yStep + 8;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.pacpack-quests.cancel"), button -> this.client.setScreen(parent))
                .dimensions(this.width / 2 - 105, y, 60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.pacpack-quests.save").formatted(Formatting.GREEN), button -> saveQuest())
                .dimensions(this.width / 2 - 35, y, 80, 20).build());

        if (questId != null) {
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.pacpack-quests.delete").formatted(Formatting.RED), button -> {
                ClientPlayNetworking.send(new DeleteQuestPayload(questId));
                this.client.setScreen(parent);
            }).dimensions(this.width / 2 + 55, y, 50, 20).build());
        }
    }

    // Helper to check if a target is valid in the Registries or is a valid Tag syntax
    private boolean isValidTarget(String target, TaskType type) {
        if (target.startsWith("#")) {
            Identifier id = Identifier.tryParse(target.substring(1));
            if (id == null) return false;

            // Check if the tag actually exists and is populated in the game's registries
            return switch (type) {
                case MINE_BLOCK -> Registries.BLOCK.getOptional(TagKey.of(RegistryKeys.BLOCK, id)).isPresent();
                case CRAFT_ITEM -> Registries.ITEM.getOptional(TagKey.of(RegistryKeys.ITEM, id)).isPresent();
                case KILL_MOB -> Registries.ENTITY_TYPE.getOptional(TagKey.of(RegistryKeys.ENTITY_TYPE, id)).isPresent();
            };
        }

        Identifier id = Identifier.tryParse(target);
        if (id == null) return false;

        // Check if the specific ID exists
        return switch (type) {
            case MINE_BLOCK -> Registries.BLOCK.containsId(id);
            case CRAFT_ITEM -> Registries.ITEM.containsId(id);
            case KILL_MOB -> Registries.ENTITY_TYPE.containsId(id);
        };
    }

    private void saveQuest() {
        // 1. Reset all fields to normal color (0xE0E0E0 is default text color)
        this.errorMessage = null;
        this.idField.setEditableColor(0xFFE0E0E0);
        this.titleField.setEditableColor(0xFFE0E0E0);
        this.iconField.setEditableColor(0xFFE0E0E0);
        this.targetField.setEditableColor(0xFFE0E0E0);
        this.reqAmountField.setEditableColor(0xFFE0E0E0);
        this.rewardField.setEditableColor(0xFFE0E0E0);
        this.rewardAmountField.setEditableColor(0xFFE0E0E0);
        this.parentsField.setEditableColor(0xFFE0E0E0);

        // 2. Validate ID
        String finalId = this.idField.getText().trim();
        if (finalId.isEmpty() || !finalId.matches("^[a-z0-9_]+$")) {
            this.errorMessage = "error.pacpack-quests.invalid_id";
            this.idField.setEditableColor(0xFFFF5555); // Red
            return;
        }
        // Prevent overwriting an existing ID if creating a new quest
        if (this.questId == null && PacPackQuestsClient.CLIENT_DEFINITIONS.containsKey(finalId)) {
            this.errorMessage = "error.pacpack-quests.id_already_exists";
            this.idField.setEditableColor(0xFFFF5555);
            return;
        }

        // 3. Validate Title
        if (this.titleField.getText().trim().isEmpty()) {
            this.errorMessage = "error.pacpack-quests.missing_title";
            this.titleField.setEditableColor(0xFFFF5555);
            return;
        }

        // 4. Validate Icon (Must be a valid Item Registry ID)
        String iconIdStr = this.iconField.getText().trim();
        Identifier iconId = Identifier.tryParse(iconIdStr);
        if (iconIdStr.isEmpty() || iconId == null || !Registries.ITEM.containsId(iconId)) {
            this.errorMessage = "error.pacpack-quests.invalid_icon";
            this.iconField.setEditableColor(0xFFFF5555);
            return;
        }

        // 5. Validate Task Target
        String targetStr = this.targetField.getText().trim();
        if (targetStr.isEmpty() || !isValidTarget(targetStr, currentTaskType)) {
            this.errorMessage = "error.pacpack-quests.invalid_target";
            this.targetField.setEditableColor(0xFFFF5555);
            return;
        }

        // 6. Validate Required Amount
        int reqAmt;
        try {
            reqAmt = Integer.parseInt(this.reqAmountField.getText().trim());
            if (reqAmt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            this.errorMessage = "error.pacpack-quests.invalid_amount";
            this.reqAmountField.setEditableColor(0xFFFF5555);
            return;
        }

        // 7. Validate Reward Item (Only if type is ITEM)
        String finalRewardStr = this.rewardField.getText().trim();
        if (currentRewardType == RewardType.ITEM) {
            Identifier rewId = Identifier.tryParse(finalRewardStr);
            if (finalRewardStr.isEmpty() || rewId == null || !Registries.ITEM.containsId(rewId)) {
                this.errorMessage = "error.pacpack-quests.invalid_reward_item";
                this.rewardField.setEditableColor(0xFFFF5555);
                return;
            }
        } else {
            finalRewardStr = "minecraft:air"; // Fallback placeholder for XP/Levels
        }

        // 8. Validate Reward Amount
        int rewAmt;
        try {
            rewAmt = Integer.parseInt(this.rewardAmountField.getText().trim());
            if (rewAmt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            this.errorMessage = "error.pacpack-quests.invalid_amount";
            this.rewardAmountField.setEditableColor(0xFFFF5555);
            return;
        }

        // 9. Validate Parents (Must exist in memory and not be itself)
        ArrayList<String> parentsList = new ArrayList<>();
        String parentsStr = this.parentsField.getText().trim();
        if (!parentsStr.isEmpty()) {
            for (String p : parentsStr.split(",")) {
                String parentId = p.trim();
                if (parentId.equals(finalId)) {
                    this.errorMessage = "error.pacpack-quests.self_parent";
                    this.parentsField.setEditableColor(0xFFFF5555);
                    return;
                }
                if (!PacPackQuestsClient.CLIENT_DEFINITIONS.containsKey(parentId)) {
                    this.errorMessage = "error.pacpack-quests.unknown_parent";
                    this.parentsField.setEditableColor(0xFFFF5555);
                    return;
                }
                parentsList.add(parentId);
            }
        }

        // 10. All checks passed -> Send to server
        SaveQuestPayload payload = new SaveQuestPayload(
                finalId, this.titleField.getText().trim(), this.category,
                this.currentTaskType, targetStr, reqAmt,
                iconIdStr, finalRewardStr,
                this.currentRewardType, rewAmt, parentsList, this.gridX, this.gridY
        );

        ClientPlayNetworking.send(payload);
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.quest_id"), this.idField.getX(), this.idField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.title"), this.titleField.getX(), this.titleField.getY() - 10, 0xFFFFFFFF, true);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.task_target"), this.targetField.getX(), this.targetField.getY() - 10, 0xFFFFFFFF, true);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.required_amount"), this.reqAmountField.getX(), this.reqAmountField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.icon_item_id"), this.iconField.getX(), this.iconField.getY() - 10, 0xFFFFFFFF, true);

        if (this.currentRewardType == RewardType.ITEM)
            context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.reward_item"), this.rewardField.getX(), this.rewardField.getY() - 10, 0xFFFFFFFF, true);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.reward_amount"), this.rewardAmountField.getX(), this.rewardAmountField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.parents"), this.parentsField.getX(), this.parentsField.getY() - 10, 0xFFFFFFFF, true);

        // --- DRAW ERROR MESSAGE ---
        if (this.errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(this.errorMessage).formatted(Formatting.RED, Formatting.BOLD), this.width / 2, this.height - 20, 0xFFFFFFFF);
        }
    }
}
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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;

public class EditQuestScreen extends Screen {

    private final QuestScreen parent;
    private final String questId;
    private final String category;
    private final int gridX, gridY;

    private TextFieldWidget idField, titleField, targetField, reqAmountField, iconField, rewardField, rewardAmountField, parentsField;
    private TaskType currentTaskType = TaskType.MINE_BLOCK;
    private RewardType currentRewardType = RewardType.ITEM;

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
            this.idField.setPlaceholder(Text.translatable("form.pacpack-quests.quest_id_placeholder"));
        }
        this.addDrawableChild(this.idField);

        this.iconField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.icon_item_id"));
        this.iconField.setPlaceholder(Text.translatable("form.pacpack-quests.icon_item_id_placeholder"));
        this.addDrawableChild(this.iconField);

        // ROW 2: Task Type & Target
        y += yStep;
        this.titleField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.title"));
        this.titleField.setPlaceholder(Text.translatable("form.pacpack-quests.title_placeholder"));
        this.addDrawableChild(this.titleField);

        this.parentsField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.parents"));
        this.parentsField.setPlaceholder(Text.translatable("form.pacpack-quests.parents_placeholder"));
        this.addDrawableChild(this.parentsField);

        // ROW 3: Req Amount & Icon
        y += yStep - 10;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(I18n.translate("form.pacpack-quests.task_type") + ": " + currentTaskType.name()), button -> {
            int nextOrdinal = (currentTaskType.ordinal() + 1) % TaskType.values().length;
            currentTaskType = TaskType.values()[nextOrdinal];
            button.setMessage(Text.literal(I18n.translate("form.pacpack-quests.task_type") + ": " + currentTaskType.name()));
            switch(currentTaskType) {
                case MINE_BLOCK -> this.targetField.setPlaceholder(Text.translatable("form.pacpack-quests.task_target_placeholder_mine"));
                case CRAFT_ITEM -> this.targetField.setPlaceholder(Text.translatable("form.pacpack-quests.task_target_placeholder_craft"));
                case KILL_MOB -> this.targetField.setPlaceholder(Text.translatable("form.pacpack-quests.task_target_placeholder_kill"));
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
        this.targetField.setPlaceholder(Text.translatable("form.pacpack-quests.task_target_placeholder_mine"));
        this.addDrawableChild(this.targetField);

        this.rewardField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.reward_item"));
        this.rewardField.setPlaceholder(Text.translatable("form.pacpack-quests.reward_item_placeholder"));
        this.addDrawableChild(this.rewardField);

        // ROW 5: Reward Amount & Parents
        y += yStep;
        this.reqAmountField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.required_amount"));
        this.reqAmountField.setPlaceholder(Text.translatable("form.pacpack-quests.required_amount_placeholder"));
        this.addDrawableChild(this.reqAmountField);

        this.rewardAmountField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.reward_amount"));
        this.rewardAmountField.setPlaceholder(Text.translatable("form.pacpack-quests.reward_amount_placeholder"));
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

    private void saveQuest() {
        try {
            String finalId = this.idField.getText().trim();
            if (finalId.isEmpty()) return;

            int reqAmt = Integer.parseInt(this.reqAmountField.getText().trim());
            int rewAmt = Integer.parseInt(this.rewardAmountField.getText().trim());

            ArrayList<String> parentsList = new ArrayList<>();
            if (!this.parentsField.getText().trim().isEmpty()) {
                for (String p : this.parentsField.getText().split(",")) {
                    parentsList.add(p.trim());
                }
            }

            SaveQuestPayload payload = new SaveQuestPayload(
                    finalId, this.titleField.getText().trim(), this.category,
                    this.currentTaskType, this.targetField.getText().trim(), reqAmt,
                    this.iconField.getText().trim(), this.rewardField.getText().trim(),
                    this.currentRewardType, rewAmt, parentsList, this.gridX, this.gridY
            );

            ClientPlayNetworking.send(payload);
            this.client.setScreen(parent);
        } catch (NumberFormatException e) {
            // Ignore format errors for now
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // --- DRAW LABELS DYNAMICALLY ---
        // By using field.getX() and field.getY() - 10, the text will ALWAYS be perfectly aligned
        // right above the field, no matter the screen size or GUI scale.

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.quest_id"), this.idField.getX(), this.idField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.title"), this.titleField.getX(), this.titleField.getY() - 10, 0xFFFFFFFF, true);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.task_target"), this.targetField.getX(), this.targetField.getY() - 10, 0xFFFFFFFF, true);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.required_amount"), this.reqAmountField.getX(), this.reqAmountField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.icon_item_id"), this.iconField.getX(), this.iconField.getY() - 10, 0xFFFFFFFF, true);

        if (this.currentRewardType == RewardType.ITEM)
            context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.reward_item"), this.rewardField.getX(), this.rewardField.getY() - 10, 0xFFFFFFFF, true);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.reward_amount"), this.rewardAmountField.getX(), this.rewardAmountField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.parents"), this.parentsField.getX(), this.parentsField.getY() - 10, 0xFFFFFFFF, true);
    }
}
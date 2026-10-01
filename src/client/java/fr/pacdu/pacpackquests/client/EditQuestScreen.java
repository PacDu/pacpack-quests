package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.network.DeleteQuestPayload;
import fr.pacdu.pacpackquests.network.SaveQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
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

    // Standard Text Fields
    private TextFieldWidget idField, titleField, reqAmountField, rewardAmountField, parentsField;

    // Custom Selection Buttons (Replaced the TextFields)
    private ButtonWidget targetButton, iconButton, rewardButton, taskTypeButton;

    private boolean isTaskDropdownOpen = false;
    private int taskDropdownScroll = 0;
    private boolean isDraggingDropdownScrollbar = false;
    private static final int MAX_DROPDOWN_VISIBLE = 4;

    // State variables holding the currently selected Strings
    private String selectedTarget = "minecraft:stone";
    private String selectedIcon = "minecraft:stone";
    private String selectedReward = "minecraft:diamond";

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

        int yStep = 34;
        int totalFormHeight = (yStep * 5) + 25;
        int y = Math.max(35, (this.height - totalFormHeight) / 2);

        // --- PREFILL DATA IF EDITING ---
        String initialTitle = "";
        String initialReqAmt = "1";
        String initialRewAmt = "1";
        String initialParents = "";

        // 1. Load data from memory if we are editing a quest
        if (questId != null && PacPackQuestsClient.CLIENT_DEFINITIONS.containsKey(questId)) {
            QuestDefinition def = PacPackQuestsClient.CLIENT_DEFINITIONS.get(questId);
            initialTitle = def.title();
            this.currentTaskType = def.type();

            // Only update these variables if they are empty (to avoid overwriting SelectionScreen callbacks)
            if (this.selectedTarget == null || this.selectedTarget.equals("minecraft:stone")) this.selectedTarget = def.target();
            if (this.selectedIcon == null || this.selectedIcon.equals("minecraft:stone")) this.selectedIcon = Registries.ITEM.getId(def.icon().getItem()).toString();

            this.currentRewardType = def.rewardType();

            if (this.selectedReward == null || this.selectedReward.equals("minecraft:diamond")) this.selectedReward = Registries.ITEM.getId(def.reward().getItem()).toString();

            initialReqAmt = String.valueOf(def.requiredAmount());
            initialRewAmt = String.valueOf(def.rewardAmount());
            if (def.parents() != null) initialParents = String.join(",", def.parents());
        }

        // 2. Override initial values with current field contents (if they exist)
        String currentId = this.idField != null ? this.idField.getText() : "";
        if (this.titleField != null) initialTitle = this.titleField.getText();
        if (this.reqAmountField != null) initialReqAmt = this.reqAmountField.getText();
        if (this.rewardAmountField != null) initialRewAmt = this.rewardAmountField.getText();
        if (this.parentsField != null) initialParents = this.parentsField.getText();

        // ROW 1: ID & Icon Button
        this.idField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.quest_id"));
        if (questId != null) {
            this.idField.setText(questId);
            this.idField.setEditable(false);
        } else {
            // Restore the ID typed during the creation of a new quest
            this.idField.setText(currentId);
            this.idField.setTextPredicate(text -> text.matches("^[a-z0-9_]*$"));
            this.idField.setPlaceholder(Text.translatable("form.pacpack-quests.quest_id_placeholder").formatted(Formatting.DARK_GRAY));
        }
        this.addDrawableChild(this.idField);

        this.iconButton = ButtonWidget.builder(Text.literal(formatDisplayString(selectedIcon)), button -> {
            this.client.setScreen(new SelectionScreen(this, SelectionScreen.SelectionContext.ITEM, result -> {
                this.selectedIcon = result;
                this.iconButton.setMessage(Text.literal(formatDisplayString(result)));
            }));
        }).dimensions(col2, y, fieldWidth, 20).build();
        this.addDrawableChild(this.iconButton);

        // ROW 2: Title & Parents
        y += yStep;
        this.titleField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.title"));
        this.titleField.setText(initialTitle);
        this.titleField.setPlaceholder(Text.translatable("form.pacpack-quests.title_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.titleField);

        this.parentsField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.parents"));
        this.parentsField.setText(initialParents);
        this.parentsField.setPlaceholder(Text.translatable("form.pacpack-quests.parents_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.parentsField);

        // ROW 3: Task Type & Reward Type Toggles
        y += yStep - 10;
        this.taskTypeButton = ButtonWidget.builder(Text.literal(I18n.translate("form.pacpack-quests.task_type") + ": " + I18n.translate("task.pacpack-quests." + currentTaskType.name().toLowerCase())), button -> {
            this.isTaskDropdownOpen = !this.isTaskDropdownOpen;
        }).dimensions(col1, y, fieldWidth, 20).build();
        this.addDrawableChild(this.taskTypeButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal(I18n.translate("form.pacpack-quests.reward_type") + ": " + currentRewardType.name()), button -> {
            int nextOrdinal = (currentRewardType.ordinal() + 1) % RewardType.values().length;
            currentRewardType = RewardType.values()[nextOrdinal];
            button.setMessage(Text.literal(I18n.translate("form.pacpack-quests.reward_type") + ": " + currentRewardType.name()));
            this.rewardButton.active = (currentRewardType == RewardType.ITEM); // Disable selection if XP/Levels
        }).dimensions(col2, y, fieldWidth, 20).build());

        // ROW 4: Target Button & Reward Button
        y += yStep;
        this.targetButton = ButtonWidget.builder(Text.literal(formatDisplayString(selectedTarget)), button -> {
            SelectionScreen.SelectionContext ctx = switch (currentTaskType) {
                case MINE_BLOCK -> SelectionScreen.SelectionContext.BLOCK;
                case CRAFT_ITEM, OBTAIN_ITEM -> SelectionScreen.SelectionContext.ITEM;
                case KILL_MOB -> SelectionScreen.SelectionContext.MOB;
                case EXPLORE_BIOME -> SelectionScreen.SelectionContext.BIOME;
                case EXPLORE_STRUCTURE -> SelectionScreen.SelectionContext.STRUCTURE;
                case EXPLORE_DIMENSION -> SelectionScreen.SelectionContext.DIMENSION;
            };
            this.client.setScreen(new SelectionScreen(this, ctx, result -> {
                this.selectedTarget = result;
                this.targetButton.setMessage(Text.literal(formatDisplayString(result)));
            }));
        }).dimensions(col1, y, fieldWidth, 20).build();
        this.addDrawableChild(this.targetButton);

        this.rewardButton = ButtonWidget.builder(Text.literal(formatDisplayString(selectedReward)), button -> {
            this.client.setScreen(new SelectionScreen(this, SelectionScreen.SelectionContext.ITEM, result -> {
                this.selectedReward = result;
                this.rewardButton.setMessage(Text.literal(formatDisplayString(result)));
            }));
        }).dimensions(col2, y, fieldWidth, 20).build();
        this.rewardButton.active = (currentRewardType == RewardType.ITEM);
        this.addDrawableChild(this.rewardButton);

        // ROW 5: Amounts
        y += yStep;
        this.reqAmountField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.required_amount"));
        this.reqAmountField.setTextPredicate(text -> text.matches("^[0-9]*$"));
        this.reqAmountField.setText(initialReqAmt);
        this.reqAmountField.setEditable(this.currentTaskType != TaskType.EXPLORE_DIMENSION && this.currentTaskType != TaskType.EXPLORE_BIOME);
        this.addDrawableChild(this.reqAmountField);

        this.rewardAmountField = new TextFieldWidget(this.textRenderer, col2, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.reward_amount"));
        this.rewardAmountField.setTextPredicate(text -> text.matches("^[0-9]*$"));
        this.rewardAmountField.setText(initialRewAmt);
        this.addDrawableChild(this.rewardAmountField);

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

    // Helper to format the long IDs into short readable button texts (e.g., "minecraft:stone" -> "Stone")
    private String formatDisplayString(String raw) {
        if (raw == null || raw.isEmpty()) return "...";
        boolean isTag = raw.startsWith("#");
        String clean = isTag ? raw.substring(1) : raw;
        Identifier id = Identifier.tryParse(clean);
        if (id == null) return raw;

        String path = id.getPath();
        String formatted = path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
        if (formatted.length() > 18) formatted = formatted.substring(0, 15) + "..."; // Truncate long names
        return isTag ? "# " + formatted : formatted;
    }

    // Validity checks are mostly handled by the selection screen now, but we keep this as a safeguard
    private boolean isValidTarget(String target, TaskType type) {
        if (target.startsWith("#")) {
            Identifier id = Identifier.tryParse(target.substring(1));
            if (id == null) return false;
            return switch (type) {
                case MINE_BLOCK -> Registries.BLOCK.getOptional(TagKey.of(RegistryKeys.BLOCK, id)).isPresent();
                case CRAFT_ITEM, OBTAIN_ITEM -> Registries.ITEM.getOptional(TagKey.of(RegistryKeys.ITEM, id)).isPresent();
                case KILL_MOB -> Registries.ENTITY_TYPE.getOptional(TagKey.of(RegistryKeys.ENTITY_TYPE, id)).isPresent();
                case EXPLORE_BIOME -> MinecraftClient.getInstance().world.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getOptional(TagKey.of(RegistryKeys.BIOME, id)).isPresent();
                case EXPLORE_STRUCTURE -> true;
                case EXPLORE_DIMENSION -> false;
            };
        }
        Identifier id = Identifier.tryParse(target);
        if (id == null) return false;
        return switch (type) {
            case MINE_BLOCK -> Registries.BLOCK.containsId(id);
            case CRAFT_ITEM, OBTAIN_ITEM -> Registries.ITEM.containsId(id);
            case KILL_MOB -> Registries.ENTITY_TYPE.containsId(id);
            case EXPLORE_BIOME -> MinecraftClient.getInstance().world.getRegistryManager().getOrThrow(RegistryKeys.BIOME).containsId(id);
            case EXPLORE_STRUCTURE -> true;
            case EXPLORE_DIMENSION -> {
                var handler = MinecraftClient.getInstance().getNetworkHandler();
                yield handler != null && handler.getWorldKeys().stream().anyMatch(key -> key.getValue().equals(id));
            }
        };
    }

    private void saveQuest() {
        this.errorMessage = null;
        this.idField.setEditableColor(0xFFE0E0E0);
        this.titleField.setEditableColor(0xFFE0E0E0);
        this.reqAmountField.setEditableColor(0xFFE0E0E0);
        this.rewardAmountField.setEditableColor(0xFFE0E0E0);
        this.parentsField.setEditableColor(0xFFE0E0E0);

        String finalId = this.idField.getText().trim();
        if (finalId.isEmpty() || !finalId.matches("^[a-z0-9_]+$")) {
            this.errorMessage = "error.pacpack-quests.invalid_id";
            this.idField.setEditableColor(0xFFFF5555);
            return;
        }
        if (this.questId == null && PacPackQuestsClient.CLIENT_DEFINITIONS.containsKey(finalId)) {
            this.errorMessage = "error.pacpack-quests.id_already_exists";
            this.idField.setEditableColor(0xFFFF5555);
            return;
        }

        if (this.titleField.getText().trim().isEmpty()) {
            this.errorMessage = "error.pacpack-quests.missing_title";
            this.titleField.setEditableColor(0xFFFF5555);
            return;
        }

        if (this.selectedIcon.isEmpty() || Identifier.tryParse(this.selectedIcon) == null) {
            this.errorMessage = "error.pacpack-quests.invalid_icon";
            return;
        }

        if (this.selectedTarget.isEmpty() || !isValidTarget(this.selectedTarget, currentTaskType)) {
            this.errorMessage = "error.pacpack-quests.invalid_target";
            return;
        }

        int reqAmt;
        try {
            reqAmt = Integer.parseInt(this.reqAmountField.getText().trim());
            if (reqAmt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            this.errorMessage = "error.pacpack-quests.invalid_amount";
            this.reqAmountField.setEditableColor(0xFFFF5555);
            return;
        }

        String finalRewardStr = (currentRewardType == RewardType.ITEM) ? this.selectedReward : "minecraft:air";

        int rewAmt;
        try {
            rewAmt = Integer.parseInt(this.rewardAmountField.getText().trim());
            if (rewAmt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            this.errorMessage = "error.pacpack-quests.invalid_amount";
            this.rewardAmountField.setEditableColor(0xFFFF5555);
            return;
        }

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

        SaveQuestPayload payload = new SaveQuestPayload(
                finalId, this.titleField.getText().trim(), this.category,
                this.currentTaskType, this.selectedTarget, reqAmt,
                this.selectedIcon, finalRewardStr,
                this.currentRewardType, rewAmt, parentsList, this.gridX, this.gridY
        );

        ClientPlayNetworking.send(payload);
        this.client.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.isTaskDropdownOpen) {
            int maxScroll = Math.max(0, TaskType.values().length - MAX_DROPDOWN_VISIBLE);
            if (maxScroll > 0) {
                this.taskDropdownScroll -= (int) Math.signum(verticalAmount);
                this.taskDropdownScroll = Math.clamp(this.taskDropdownScroll, 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.isDraggingDropdownScrollbar && this.taskTypeButton != null) {
            int dropY = this.taskTypeButton.getY() + this.taskTypeButton.getHeight();
            int maxScroll = Math.max(0, TaskType.values().length - MAX_DROPDOWN_VISIBLE);
            int visibleOptions = Math.min(TaskType.values().length, MAX_DROPDOWN_VISIBLE);
            int dropHeight = 20 * visibleOptions;
            
            this.updateDropdownScroll(click.y(), dropY, dropHeight, maxScroll);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && this.isDraggingDropdownScrollbar) {
            this.isDraggingDropdownScrollbar = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void updateDropdownScroll(double mouseY, int startY, int scrollHeight, int maxScroll) {
        int thumbHeight = 15;
        double adjustedY = mouseY - startY - (thumbHeight / 2.0);
        double ratio = adjustedY / (scrollHeight - thumbHeight);
        ratio = Math.clamp(ratio, 0.0, 1.0);
        this.taskDropdownScroll = (int) Math.round(ratio * maxScroll);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.isTaskDropdownOpen && this.taskTypeButton != null) {
            int dropX = this.taskTypeButton.getX();
            int dropY = this.taskTypeButton.getY() + this.taskTypeButton.getHeight();
            int dropWidth = this.taskTypeButton.getWidth();
            
            int numOptions = TaskType.values().length;
            int maxScroll = Math.max(0, numOptions - MAX_DROPDOWN_VISIBLE);
            int visibleOptions = Math.min(numOptions, MAX_DROPDOWN_VISIBLE);
            int dropHeight = 20 * visibleOptions;

            if (maxScroll > 0 && click.x() >= dropX + dropWidth && click.x() <= dropX + dropWidth + 6 && click.y() >= dropY && click.y() <= dropY + dropHeight) {
                this.isDraggingDropdownScrollbar = true;
                this.updateDropdownScroll(click.y(), dropY, dropHeight, maxScroll);
                return true;
            }

            if (click.x() >= dropX && click.x() < dropX + dropWidth && click.y() >= dropY && click.y() < dropY + dropHeight) {
                int index = this.taskDropdownScroll + (int) ((click.y() - dropY) / 20);
                if (index >= 0 && index < numOptions) {
                    this.currentTaskType = TaskType.values()[index];
                    this.taskTypeButton.setMessage(Text.literal(I18n.translate("form.pacpack-quests.task_type") + ": " + I18n.translate("task.pacpack-quests." + this.currentTaskType.name().toLowerCase())));
                    this.isTaskDropdownOpen = false;
                    this.reqAmountField.setEditable(this.currentTaskType != TaskType.EXPLORE_DIMENSION && this.currentTaskType != TaskType.EXPLORE_BIOME);

                    if (currentTaskType == TaskType.EXPLORE_DIMENSION || currentTaskType == TaskType.EXPLORE_BIOME) {
                        this.reqAmountField.setText("1");
                    }

                    return true;
                }
            } else if (!this.taskTypeButton.isMouseOver(click.x(), click.y())) {
                this.isTaskDropdownOpen = false;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.quest_id"), this.idField.getX(), this.idField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.title"), this.titleField.getX(), this.titleField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.icon_item_id"), this.iconButton.getX(), this.iconButton.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.task_target"), this.targetButton.getX(), this.targetButton.getY() - 10, 0xFFFFFFFF, true);

        if (this.currentRewardType == RewardType.ITEM)
            context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.reward_item"), this.rewardButton.getX(), this.rewardButton.getY() - 10, 0xFFFFFFFF, true);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.required_amount"), this.reqAmountField.getX(), this.reqAmountField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.reward_amount"), this.rewardAmountField.getX(), this.rewardAmountField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.parents"), this.parentsField.getX(), this.parentsField.getY() - 10, 0xFFFFFFFF, true);

        if (this.errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(this.errorMessage).formatted(Formatting.RED, Formatting.BOLD), this.width / 2, this.height - 20, 0xFFFFFFFF);
        }

        // Draw TaskType dropdown menu on top
        if (this.isTaskDropdownOpen && this.taskTypeButton != null) {
            int dropX = this.taskTypeButton.getX();
            int dropY = this.taskTypeButton.getY() + this.taskTypeButton.getHeight();
            int dropWidth = this.taskTypeButton.getWidth();
            int numOptions = TaskType.values().length;
            
            int maxScroll = Math.max(0, numOptions - MAX_DROPDOWN_VISIBLE);
            int visibleOptions = Math.min(numOptions, MAX_DROPDOWN_VISIBLE);
            int dropHeight = 20 * visibleOptions;

            // overall background
            context.fill(dropX, dropY, dropX + dropWidth, dropY + dropHeight, 0xFF000000);
            
            // outline
            int totalWidth = maxScroll > 0 ? dropWidth + 6 : dropWidth;
            context.fill(dropX - 1, dropY - 1, dropX + totalWidth + 1, dropY, 0xFFAAAAAA);
            context.fill(dropX - 1, dropY + dropHeight, dropX + totalWidth + 1, dropY + dropHeight + 1, 0xFFAAAAAA);
            context.fill(dropX - 1, dropY, dropX, dropY + dropHeight, 0xFFAAAAAA);
            context.fill(dropX + totalWidth, dropY, dropX + totalWidth + 1, dropY + dropHeight, 0xFFAAAAAA);

            for (int i = 0; i < visibleOptions; i++) {
                int dataIndex = this.taskDropdownScroll + i;
                if (dataIndex >= numOptions) break;

                int optY = dropY + (i * 20);
                boolean hovered = mouseX >= dropX && mouseX < dropX + dropWidth && mouseY >= optY && mouseY < optY + 20;
                
                if (hovered) {
                    context.fill(dropX, optY, dropX + dropWidth, optY + 20, 0xFF666666);
                }
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(I18n.translate("task.pacpack-quests." + TaskType.values()[dataIndex].name().toLowerCase())), dropX + dropWidth / 2, optY + 6, 0xFFFFFFFF);
            }

            // Draw scrollbar if needed
            if (maxScroll > 0) {
                int scrollX = dropX + dropWidth;
                int thumbHeight = 15;
                
                // Track background
                context.fill(scrollX, dropY, scrollX + 6, dropY + dropHeight, 0xFF222222);
                
                float scrollRatio = (float) this.taskDropdownScroll / maxScroll;
                int thumbY = dropY + (int) (scrollRatio * (dropHeight - thumbHeight));
                
                boolean isHoveringThumb = mouseX >= scrollX && mouseX <= scrollX + 6 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
                int thumbColor = (this.isDraggingDropdownScrollbar || isHoveringThumb) ? 0xFFFFFFFF : 0xFFAAAAAA;
                
                // Thumb
                context.fill(scrollX, thumbY, scrollX + 6, thumbY + thumbHeight, thumbColor);
            }
        }
    }
}
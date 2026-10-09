package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.client.gui.ScrollbarWidget;
import fr.pacdu.pacpackquests.network.DeleteQuestPayload;
import fr.pacdu.pacpackquests.network.SaveQuestPayload;
import fr.pacdu.pacpackquests.util.RegistryType;
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
import java.util.Locale;

import static fr.pacdu.pacpackquests.util.TranslationUtils.getTranslatedName;

public class EditQuestScreen extends Screen {

    private final QuestScreen parent;
    private final String questId;
    private final String category;
    private final int gridX, gridY;

    // Standard Text Fields
    private TextFieldWidget idField, titleField, reqAmountField, rewardAmountField;
    private ButtonWidget parentsButton;

    // Custom Selection Buttons (Replaced the TextFields)
    private ButtonWidget targetButton, iconButton, rewardButton, taskTypeButton;

    private boolean isTaskDropdownOpen = false;
    private final ScrollbarWidget dropdownScrollbar = new ScrollbarWidget(0, 0, 6, 0, 1, true);
    private static final int MAX_DROPDOWN_VISIBLE = 4;

    // State variables holding the currently selected Strings
    private String selectedIcon = "minecraft:stone";
    private String selectedTarget = "minecraft:stone";
    private String selectedReward = "minecraft:diamond";

    private TaskType currentTaskType = TaskType.MINE_BLOCK;
    private RewardType currentRewardType = RewardType.ITEM;

    private String errorMessage = null;
    private java.util.List<String> selectedParents = new java.util.ArrayList<>();

    public void updateParents(java.util.List<String> parents) {
        this.selectedParents = new java.util.ArrayList<>(parents);
    }
    private boolean isInitialized = false;

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
            
            if (!this.isInitialized) {
                this.currentTaskType = def.type();
                this.currentRewardType = def.rewardType();
            }

            // Only update these variables if they are empty (to avoid overwriting SelectionScreen callbacks)
            if (this.selectedIcon == null || this.selectedIcon.equals("minecraft:stone")) this.selectedIcon = Registries.ITEM.getId(def.icon().getItem()).toString();
            if (this.selectedTarget == null || this.selectedTarget.equals("minecraft:stone")) this.selectedTarget = def.target();
            if (this.selectedReward == null || this.selectedReward.equals("minecraft:diamond")) this.selectedReward = Registries.ITEM.getId(def.reward().getItem()).toString();

            initialReqAmt = String.valueOf(def.requiredAmount());
            initialRewAmt = String.valueOf(def.rewardAmount());
            if (def.parents() != null) { initialParents = String.join(",", def.parents()); if (!this.isInitialized) { this.selectedParents = new java.util.ArrayList<>(def.parents()); } }
        }
        
        this.isInitialized = true;

        // 2. Override initial values with current field contents (if they exist)
        String currentId = this.idField != null ? this.idField.getText() : "";
        if (this.titleField != null) initialTitle = this.titleField.getText();
        if (this.reqAmountField != null) initialReqAmt = this.reqAmountField.getText();
        if (this.rewardAmountField != null) initialRewAmt = this.rewardAmountField.getText();
        if (this.selectedParents != null) initialParents = String.join(",", this.selectedParents);

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

        this.iconButton = ButtonWidget.builder(Text.literal(formatDisplayString(getTranslatedName(selectedIcon, RegistryType.ITEM))), button -> {
            this.client.setScreen(new SelectionScreen(this, RegistryType.ITEM, result -> {
                this.selectedIcon = result;
                this.iconButton.setMessage(Text.literal(formatDisplayString(getTranslatedName(result, RegistryType.ITEM))));
            }, false));
        }).dimensions(col2, y, fieldWidth, 20).build();
        this.addDrawableChild(this.iconButton);

        // ROW 2: Title & Parents
        y += yStep;
        this.titleField = new TextFieldWidget(this.textRenderer, col1, y, fieldWidth, 20, Text.translatable("form.pacpack-quests.title"));
        this.titleField.setText(initialTitle);
        this.titleField.setPlaceholder(Text.translatable("form.pacpack-quests.title_placeholder").formatted(Formatting.DARK_GRAY));
        this.addDrawableChild(this.titleField);

        this.parentsButton = ButtonWidget.builder(Text.literal(initialParents.isEmpty() ? "None" : initialParents.split(",").length + " parents"), button -> {
            this.client.setScreen(new ParentSelectionScreen(this, this.idField.getText(), this.category, this.selectedParents));
        }).dimensions(col2, y, fieldWidth, 20).build();
        this.addDrawableChild(this.parentsButton);

        // ROW 3: Task Type & Reward Type Toggles
        y += yStep - 10;
        this.taskTypeButton = ButtonWidget.builder(
                Text.literal(I18n.translate("form.pacpack-quests.task_type") + ": " + I18n.translate("task.pacpack-quests." + currentTaskType.name().toLowerCase())),
                button -> this.isTaskDropdownOpen = !this.isTaskDropdownOpen
        ).dimensions(col1, y, fieldWidth, 20).build();
        this.addDrawableChild(this.taskTypeButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal(I18n.translate("form.pacpack-quests.reward_type") + ": " + I18n.translate("reward.pacpack-quests." + currentRewardType.name().toLowerCase())), button -> {
            int nextOrdinal = (currentRewardType.ordinal() + 1) % RewardType.values().length;
            currentRewardType = RewardType.values()[nextOrdinal];
            button.setMessage(Text.literal(I18n.translate("form.pacpack-quests.reward_type") + ": " + I18n.translate("reward.pacpack-quests." + currentRewardType.name().toLowerCase())));
            this.rewardButton.active = currentRewardType == RewardType.ITEM; // Disable selection if XP/Levels
            switch (currentRewardType) {
                case ITEM, LEVEL -> this.rewardAmountField.setText("1");
                case XP -> this.rewardAmountField.setText("100");
            }
        }).dimensions(col2, y, fieldWidth, 20).build());

        // ROW 4: Target Button & Reward Button
        y += yStep;
        this.targetButton = ButtonWidget.builder(Text.literal(formatDisplayString(getTranslatedName(selectedTarget, RegistryType.ITEM))), button -> {
            RegistryType ctx = switch (currentTaskType) {
                case MINE_BLOCK -> RegistryType.BLOCK;
                case CRAFT_ITEM, OBTAIN_ITEM -> RegistryType.ITEM;
                case KILL_MOB -> RegistryType.MOB;
                case EXPLORE_BIOME -> RegistryType.BIOME;
                case EXPLORE_STRUCTURE -> RegistryType.STRUCTURE;
                case EXPLORE_DIMENSION -> RegistryType.DIMENSION;
                case OPEN_LOOT_CHEST -> RegistryType.LOOT_TABLE;
            };
            this.client.setScreen(new SelectionScreen(this, ctx, result -> {
                this.selectedTarget = result;
                this.targetButton.setMessage(Text.literal(formatDisplayString(getTranslatedName(result, RegistryType.ITEM))));
            }));
        }).dimensions(col1, y, fieldWidth, 20).build();
        this.addDrawableChild(this.targetButton);

        this.rewardButton = ButtonWidget.builder(Text.literal(formatDisplayString(getTranslatedName(selectedReward, RegistryType.ITEM))), button -> {
            this.client.setScreen(new SelectionScreen(this, RegistryType.ITEM, result -> {
                this.selectedReward = result;
                this.rewardButton.setMessage(Text.literal(formatDisplayString(getTranslatedName(result, RegistryType.ITEM))));
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
                case OPEN_LOOT_CHEST -> target.equals("#pacpackquests:loot_chest");
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
            case OPEN_LOOT_CHEST -> {
                var server = MinecraftClient.getInstance().getServer();
                if (server == null) yield true; var opt = server.getReloadableRegistries().createRegistryLookup().getOptional(RegistryKeys.LOOT_TABLE); if (opt.isPresent()) { var key = net.minecraft.registry.RegistryKey.of(RegistryKeys.LOOT_TABLE, id); yield opt.get().getOptional(key).isPresent(); } yield false;
            }
        };
    }

    private void saveQuest() {
        this.errorMessage = null;
        this.idField.setEditableColor(0xFFE0E0E0);
        this.titleField.setEditableColor(0xFFE0E0E0);
        this.reqAmountField.setEditableColor(0xFFE0E0E0);
        this.rewardAmountField.setEditableColor(0xFFE0E0E0);
        

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
        String parentsStr = String.join(",", this.selectedParents);
        if (!parentsStr.isEmpty()) {
            for (String p : parentsStr.split(",")) {
                String parentId = p.trim();
                if (parentId.equals(finalId)) {
                    this.errorMessage = "error.pacpack-quests.self_parent";
                    
                    return;
                }
                if (!PacPackQuestsClient.CLIENT_DEFINITIONS.containsKey(parentId)) {
                    this.errorMessage = "error.pacpack-quests.unknown_parent";
                    
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
        if (this.isTaskDropdownOpen && dropdownScrollbar.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.isTaskDropdownOpen && dropdownScrollbar.mouseDragged(click.x(), click.y(), click.button(), offsetX, offsetY)) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.isTaskDropdownOpen && dropdownScrollbar.mouseReleased(click.x(), click.y(), click.button())) return true;
        return super.mouseReleased(click);
    }



    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.isTaskDropdownOpen && this.taskTypeButton != null) {
            int dropX = this.taskTypeButton.getX();
            int dropY = this.taskTypeButton.getY() + this.taskTypeButton.getHeight();
            int dropWidth = this.taskTypeButton.getWidth();
            
            int numOptions = TaskType.values().length;
            int visibleOptions = Math.min(numOptions, MAX_DROPDOWN_VISIBLE);
            int dropHeight = 20 * visibleOptions;

            if (dropdownScrollbar.mouseClicked(click.x(), click.y(), click.button())) {
                return true;
            }

            if (click.x() >= dropX && click.x() < dropX + dropWidth && click.y() >= dropY && click.y() < dropY + dropHeight) {
                int taskDropdownScroll = dropdownScrollbar.getStepOffset();
                int index = (int) ((taskDropdownScroll + click.y() - dropY) / 20);
                if (index >= 0 && index < numOptions) {
                    this.currentTaskType = TaskType.values()[index];
                    this.taskTypeButton.setMessage(Text.literal(I18n.translate("form.pacpack-quests.task_type") + ": " + I18n.translate("task.pacpack-quests." + this.currentTaskType.name().toLowerCase())));
                    this.isTaskDropdownOpen = false;
                    this.reqAmountField.setEditable(this.currentTaskType != TaskType.EXPLORE_DIMENSION && this.currentTaskType != TaskType.EXPLORE_BIOME);

                    if (this.currentTaskType == TaskType.EXPLORE_DIMENSION || this.currentTaskType == TaskType.EXPLORE_BIOME) {
                        this.reqAmountField.setText("1");
                    }

                    this.selectedTarget = switch (this.currentTaskType) {
                        case MINE_BLOCK -> "minecraft:stone";
                        case KILL_MOB -> "minecraft:zombie";
                        case CRAFT_ITEM, OBTAIN_ITEM -> "minecraft:iron_pickaxe";
                        case EXPLORE_BIOME -> "minecraft:desert";
                        case EXPLORE_STRUCTURE -> "minecraft:ruined_portal";
                        case EXPLORE_DIMENSION -> "minecraft:the_nether";
                        case OPEN_LOOT_CHEST -> "#pacpackquests:loot_chests";
                    };

                    this.targetButton.setMessage(Text.literal(formatDisplayString(getTranslatedName(selectedTarget, RegistryType.ITEM))));

                    return true;
                }
            } else if (!this.taskTypeButton.isMouseOver(click.x(), click.y())) {
                this.isTaskDropdownOpen = false;
            }
            return true;
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
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.parents"), this.parentsButton.getX(), this.parentsButton.getY() - 10, 0xFFFFFFFF, true);

        if (this.errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(this.errorMessage).formatted(Formatting.RED, Formatting.BOLD), this.width / 2, this.height - 52, 0xFFFFFFFF);
        }

        // Draw TaskType dropdown menu on top
        if (this.isTaskDropdownOpen && this.taskTypeButton != null) {
            int dropX = this.taskTypeButton.getX();
            int dropY = this.taskTypeButton.getY() + this.taskTypeButton.getHeight();
            int dropWidth = this.taskTypeButton.getWidth();
            int numOptions = TaskType.values().length;
            
            int visibleOptions = Math.min(numOptions, MAX_DROPDOWN_VISIBLE);
            int dropHeight = 20 * visibleOptions;
            int maxScroll = Math.max(0, (numOptions * 20) - dropHeight);

            // overall background
            context.fill(dropX, dropY, dropX + dropWidth, dropY + dropHeight, 0xFF000000);
            
            // outline
            int totalWidth = maxScroll > 0 ? dropWidth + 6 : dropWidth;
            context.fill(dropX - 1, dropY - 1, dropX + totalWidth + 1, dropY, 0xFFAAAAAA);
            context.fill(dropX - 1, dropY + dropHeight, dropX + totalWidth + 1, dropY + dropHeight + 1, 0xFFAAAAAA);
            context.fill(dropX - 1, dropY, dropX, dropY + dropHeight, 0xFFAAAAAA);
            context.fill(dropX + totalWidth, dropY, dropX + totalWidth + 1, dropY + dropHeight, 0xFFAAAAAA);

            context.enableScissor(dropX, dropY, dropX + dropWidth, dropY + dropHeight);
            context.getMatrices().translate(0f, (float) -dropdownScrollbar.getScrollAmount());

            for (int i = 0; i < numOptions; i++) {
                int optY = dropY + (i * 20);
                boolean hovered = mouseX >= dropX && mouseX < dropX + dropWidth && mouseY >= dropY && mouseY < dropY + dropHeight && 
                                  (mouseY + dropdownScrollbar.getScrollAmount()) >= optY && (mouseY + dropdownScrollbar.getScrollAmount()) < optY + 20;
                
                if (hovered) {
                    context.fill(dropX, optY, dropX + dropWidth, optY + 20, 0xFF666666);
                }
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(I18n.translate("task.pacpack-quests." + TaskType.values()[i].name().toLowerCase())), dropX + dropWidth / 2, optY + 6, 0xFFFFFFFF);
            }

            context.getMatrices().translate(0f, (float) dropdownScrollbar.getScrollAmount());
            context.disableScissor();

            // Draw scrollbar if needed
            if (maxScroll > 0) {
                dropdownScrollbar.setBounds(dropX + dropWidth, dropY, 6, dropHeight);
                dropdownScrollbar.setMaxScroll(maxScroll, dropHeight);
                dropdownScrollbar.render(context, mouseX, mouseY);
            }
        
        }
    }
}
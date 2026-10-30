package fr.pacdu.pacpackquests.client.screen;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.config.ModConfig;
import fr.pacdu.pacpackquests.network.*;
import fr.pacdu.pacpackquests.client.util.TranslationUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import fr.pacdu.pacpackquests.client.gui.ScrollbarWidget;
import fr.pacdu.pacpackquests.util.PathSecurity;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.pacdu.pacpackquests.PacPackQuests.DEFAULT_CATEGORY_NAME;
import static fr.pacdu.pacpackquests.client.PacPackQuestsClient.*;

public class QuestScreen extends AbstractQuestGridScreen {

	private final List<QuestNode> questList = new ArrayList<>();

	private static String selectedCategory = DEFAULT_CATEGORY_NAME;

	private ButtonWidget claimAllButton;
	private TextFieldWidget inlineCategoryField;

	// --- Edit Mode System ---
	private boolean isEditMode = false;
	private boolean cameraInitialized = false;
	private String categoryClickTarget = null;
	private String categoryEditTarget = null;
	private String draggedCategory = null;
	private QuestDefinition duplicatedQuest = null;
	private final ScrollbarWidget categoryScrollbar = new ScrollbarWidget(0, 0, 6, 0, 25, true);

	record QuestNode(QuestDefinition definition, int x, int y, boolean isLocked) implements GridNode {
		public String id() { return definition.id(); }
		public String title() { return definition.title(); }
		public int displayX() { return definition.displayX(); }
		public int displayY() { return definition.displayY(); }
		public ItemStack icon() { return definition.icon(); }
		public List<String> parents() { return definition.parents(); }
		public TaskType taskType() { return definition.taskType(); }
		public String target() { return definition.target(); }
		public int requiredAmount() { return definition.requiredAmount(); }
	}
	record GridPos(int x, int y) {}

	public QuestScreen() {
		super(Text.translatable("gui.pacpack-quests.quest_menu"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		super.init();

		int buttonWidth = 100;
		int buttonHeight = 20;

		// "Done" Button (Shifted left)
		this.addDrawableChild(
				ButtonWidget.builder(Text.translatable("gui.done"), button -> this.close())
						.dimensions((this.width / 2) - buttonWidth - 5, this.height - 35, buttonWidth, buttonHeight)
						.build()
		);

		// "Claim All" Button (Shifted right)
		this.claimAllButton = ButtonWidget.builder(Text.translatable("gui.pacpack-quests.claim_all"), button -> claimAllQuests())
				.dimensions((this.width / 2) + 5, this.height - 35, buttonWidth, buttonHeight)
				.build();

		// Set initial state
		this.claimAllButton.active = hasClaimableQuests();
		this.addDrawableChild(this.claimAllButton);

		// "Edit Mode" Toggle Button (Only visible if player is OP)
		if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)) {
			this.addDrawableChild(
					ButtonWidget.builder(Text.literal(I18n.translate("gui.pacpack-quests.edit") + ": " + (isEditMode ? "ON" : "OFF")).formatted(isEditMode ? Formatting.GREEN : Formatting.RED), button -> {
								this.isEditMode = !this.isEditMode;
								this.clearAndInit(); // Refresh screen to update button text and visuals
								this.clampPanning();
							})
							.dimensions(startX + windowWidth - buttonWidth, startY - 25, buttonWidth, buttonHeight)
							.build()
			);
		}

		// --- Categories Setup ---
		this.inlineCategoryField = new TextFieldWidget(this.textRenderer, startX - tabWidth, 0, tabWidth, tabHeight, Text.empty()) {
			@Override
			public void write(String text) {
				super.write(PathSecurity.formatStringToValidPath(text));
			}
		};
		this.inlineCategoryField.setMaxLength(categoryNameMaxChars);
		this.inlineCategoryField.setVisible(false);
		this.addDrawableChild(this.inlineCategoryField);

		this.categoryEditTarget = null;

		if (CLIENT_CATEGORIES.isEmpty()) {
			CLIENT_CATEGORIES.add(DEFAULT_CATEGORY_NAME);
		}

		// Update selectedCategory if the previous one no longer exists
		if (!CLIENT_CATEGORIES.contains(selectedCategory)) {
			selectedCategory = CLIENT_CATEGORIES.getFirst();
		}

		refreshQuests();
		if (!this.cameraInitialized) {
			restoreCameraState(selectedCategory);
			this.cameraInitialized = true;
		} else {
			clampPanning();
		}
	}

	@Override
	public void removed() {
		saveCurrentCameraState();
		super.removed();
	}

	private void saveCurrentCameraState() {
		if (selectedCategory != null) {
			ModConfig.cameraPositions.put(selectedCategory, new ModConfig.CameraState(this.panX, this.panY, this.zoom));
		}
	}

	private void restoreCameraState(String category) {
		ModConfig.CameraState state = ModConfig.cameraPositions.get(category);
		if (state != null) {
			this.panX = state.panX();
			this.panY = state.panY();
			this.zoom = state.zoom();
			clampPanning();
		} else {
			resetCamera();
			saveCurrentCameraState();
		}
	}

	private void refreshQuests() {
		questList.clear();

		for (QuestDefinition quest : CLIENT_DEFINITIONS.values()) {
			if (quest.category().equals(selectedCategory)) {

				int localX = canvasOffsetX + (quest.displayX() * gridSpacing);
				int localY = canvasOffsetY + (quest.displayY() * gridSpacing);

				boolean locked = false;
				if (quest.parents() != null) {
					for (String parentId : quest.parents()) {
						if (!CLIENT_FINISHED.getOrDefault(parentId, false)) {
							locked = true;
							break;
						}
					}
				}

				questList.add(new QuestNode(quest, localX, localY, locked));
			}
		}

		this.clampPanning();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		super.drawBackgroundFrame(context, this.title);

		// --- DRAW CATEGORIES ---
		int categMarginTop = 20;
		int categMarginBottom = 10;
		int categMarginY = categMarginTop + categMarginBottom;

		int tabX = startX - tabWidth;
		int currentTabY = startY + categMarginTop;

		int totalTabs = CLIENT_CATEGORIES.size() + (isEditMode ? 1 : 0);
		double totalContentHeight = totalTabs * (tabHeight + 5) - 5;
		double maxScroll = Math.max(0, totalContentHeight - (windowHeight - categMarginY));
		
		int scrollX = isEditMode ? tabX - 35 : tabX - 10;
		categoryScrollbar.setBounds(scrollX, startY + categMarginTop, 6, windowHeight - categMarginY);
		categoryScrollbar.setMaxScroll(maxScroll, this.height - categMarginY);

		double scrollAmount = categoryScrollbar.getScrollAmount();
		int dropIndex = -1;
		if (draggedCategory != null) {
			dropIndex = Math.clamp((int) Math.round((mouseY + scrollAmount - (startY + categMarginTop)) / (tabHeight + 5.0)), 0, CLIENT_CATEGORIES.size());
		}

		context.enableScissor(tabX - 40, startY + categMarginTop, startX, startY + windowHeight - categMarginBottom);
		
		context.getMatrices().translate(0f, (float)-scrollAmount);

		String hoveredTruncatedCategory = null;
		int maxTextWidth = tabWidth - 8;

		int catIndex = 0;
		for (String category : CLIENT_CATEGORIES) {
			if (currentTabY + tabHeight < startY + categMarginTop + scrollAmount || currentTabY > startY + windowHeight + scrollAmount - categMarginBottom) {
				currentTabY += tabHeight + 5;
				catIndex++;
				continue;
			}

			// Draw the green insertion line
			if (isEditMode && draggedCategory != null && dropIndex == catIndex) {
				context.fill(tabX, currentTabY - 3, tabX + tabWidth, currentTabY - 1, 0xFF55FF55);
			}

			// The original tab is not drawn if it is being moved
			if (!category.equals(draggedCategory)) {
				if (!this.inlineCategoryField.isVisible() || !category.equals(this.categoryEditTarget)) {
					boolean isTabHovered = isHovering(tabX, currentTabY, tabWidth, tabHeight, mouseX, (int)(mouseY + scrollAmount));
					boolean isSelected = category.equals(selectedCategory) || isTabHovered;
					context.fill(tabX, currentTabY, tabX + tabWidth, currentTabY + tabHeight, isSelected ? 0xFF666666 : 0xFF333333);

					String fullCategory = category.toUpperCase().replace("_", " ");
					String displayCategory = fullCategory;
					boolean isTruncated = false;
					if (this.textRenderer.getWidth(fullCategory) > maxTextWidth) {
						int ellipsisWidth = this.textRenderer.getWidth("...");
						displayCategory = this.textRenderer.trimToWidth(fullCategory, maxTextWidth - ellipsisWidth) + "...";
						isTruncated = true;
					}

					context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(displayCategory), tabX + tabWidth / 2, currentTabY + 6, isSelected ? 0xFFFFFFFF : 0xFFAAAAAA);

					if (isTabHovered && isTruncated) {
						hoveredTruncatedCategory = fullCategory;
					}
				}

				if (isEditMode) {
					int delBtnX = tabX - 14;
					int delBtnY = currentTabY + 4;
					boolean isHoverDelBtn = isHovering(delBtnX, delBtnY, 10, 10, mouseX, (int)(mouseY + scrollAmount));
					context.fill(delBtnX, delBtnY, delBtnX + 10, delBtnY + 10, isHoverDelBtn ? 0xFFCC1111 : 0xFF991111);
					context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("x").formatted(Formatting.BOLD), delBtnX + 5, delBtnY + 1, isHoverDelBtn ? 0xFFFFFFFF : 0xFFAAAAAA);

					int editBtnX = delBtnX - 14;
					int editBtnY = currentTabY + 4;
					boolean isHoverEditBtn = isHovering(editBtnX, editBtnY, 10, 10, mouseX, (int)(mouseY + scrollAmount));
					context.fill(editBtnX, editBtnY, editBtnX + 10, editBtnY + 10, isHoverEditBtn ? 0xFF666666 : 0xFF333333);
					context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("\u270E").formatted(Formatting.BOLD), editBtnX + 5, editBtnY + 1, isHoverEditBtn ? 0xFFFFFFFF : 0xFFAAAAAA);
				}
			}
			currentTabY += tabHeight + 5;
			catIndex++;
		}

		// Insertion line if you let go all the way at the bottom
		if (isEditMode && draggedCategory != null && dropIndex == CLIENT_CATEGORIES.size()) {
			context.fill(tabX, currentTabY - 3, tabX + tabWidth, currentTabY - 1, 0xFF55FF55);
		}

		// Draw the floating tab attached to the mouse
		if (isEditMode && draggedCategory != null) {
			int floatY = (int)(mouseY + scrollAmount) - tabHeight / 2;
			context.fill(tabX, floatY, tabX + tabWidth, floatY + tabHeight, 0xFF888888);
			String fullCategory = draggedCategory.toUpperCase();
			String displayCategory = fullCategory;
			if (this.textRenderer.getWidth(fullCategory) > maxTextWidth) {
				int ellipsisWidth = this.textRenderer.getWidth("...");
				displayCategory = this.textRenderer.trimToWidth(fullCategory, maxTextWidth - ellipsisWidth) + "...";
			}
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(displayCategory), tabX + tabWidth / 2, floatY + 6, 0xFFFFFFFF);
		}

		// --- ADD CATEGORY BUTTON / INLINE FIELD ---
		if (isEditMode) {
			if (!this.inlineCategoryField.isVisible() || this.categoryEditTarget != null) {
				boolean isHover = isHovering(tabX, currentTabY, tabWidth, tabHeight, mouseX, (int)(mouseY + scrollAmount));
				context.fill(tabX, currentTabY, tabX + tabWidth, currentTabY + tabHeight, isHover ? 0xFF666666 : 0xFF444444);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("+").formatted(Formatting.BOLD), tabX + tabWidth / 2, currentTabY + 6, isHover ? 0xFFFFFFFF : 0xFFBBBBBB);
			}
		}

		context.getMatrices().translate(0f, (float)scrollAmount);
		context.disableScissor();

		// --- CATEGORY SCROLLBAR ---
		categoryScrollbar.render(context, mouseX, mouseY);

		double localMouseX = (mouseX - startX - panX) / zoom;
		double localMouseY = (mouseY - startY - panY) / zoom;
		boolean isMouseInWindow = isMouseInWindow(mouseX, mouseY);

		QuestNode hoveredNode = null;
		QuestNode nodeBeingDragged = null;
		int hoveredDotX = -1;
		int hoveredDotY = -1;

		context.enableScissor(startX, startY, startX + windowWidth, startY + windowHeight);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate((float) (startX + panX), (float) (startY + panY));
		context.getMatrices().scale(zoom, zoom);

		// Draw Helper Grid in Edit Mode
		if (isEditMode) {
			for (int gx = 0; gx < maxGridColumns; gx++) {
				for (int gy = 0; gy < maxGridRows; gy++) {
					int bgColor = 0x11FFFFFF;
					int bgX = canvasOffsetX + (gx * gridSpacing);
					int bgY = canvasOffsetY + (gy * gridSpacing);

					if (isEditMode && isMouseInWindow && isHoveringNode(bgX, bgY, localMouseX, localMouseY))
						bgColor = 0xFF666666;
					context.fill(bgX - 4, bgY - 4, bgX + 20, bgY + 20, bgColor);

					int dotX = canvasOffsetX + (gx * gridSpacing) + 7;
					int dotY = canvasOffsetY + (gy * gridSpacing) + 7;
					context.fill(dotX, dotY, dotX + 2, dotY + 2, 0x55FFFFFF);

					if (isMouseInWindow && isHoveringNode(bgX, bgY, localMouseX, localMouseY)) {
						hoveredDotX = dotX;
						hoveredDotY = dotY;
					}
				}
			}
		}

		// --- DRAW LINES ---
		for (QuestNode quest : questList) {
			if (quest.parents() == null) continue;
			for (String parentId : quest.parents()) {
				QuestNode parentNode = getQuestById(parentId);
				if (parentNode != null) {
					int lineColor = quest.isLocked() ? 0xFF444444 : 0xFF88AA88; int arrowColor = quest.isLocked() ? 0xFF888888 : 0xFFCCFFCC; boolean isDragging = parentNode.id().equals(draggedQuestId) || quest.id().equals(draggedQuestId); drawConnectionLine(context, parentNode, quest, lineColor, arrowColor, isDragging);
				}
			}
		}

		// --- DRAW QUEST NODES ---
		for (QuestNode quest : questList) {
			if (quest.id().equals(draggedQuestId)) {
				nodeBeingDragged = quest;
				continue; // Skip rendering the dragged node here; we draw it at the mouse position later
			}
			renderNode(context, quest, localMouseX, localMouseY, isMouseInWindow);
			if (isMouseInWindow && isHoveringNode(quest.x(), quest.y(), localMouseX, localMouseY)) {
				hoveredNode = quest;
			}
		}

		// --- LOAD NODE FOR QUEST BEING DUPLICATED ---
		if (duplicatedQuest != null) {
			int localX = canvasOffsetX + (duplicatedQuest.displayX() * gridSpacing);
			int localY = canvasOffsetY + (duplicatedQuest.displayY() * gridSpacing);
			nodeBeingDragged = new QuestNode(duplicatedQuest, localX, localY, false);
		}

		// --- DRAW DRAGGED NODE AT CURSOR ---
		if (nodeBeingDragged != null) {
			// Draw a semi-transparent drop shadow snapped to the grid to show where it will land
			int dropGridX = nodeBeingDragged.displayX();
			int dropGridY = nodeBeingDragged.displayY();
			if (isMouseInWindow || duplicatedQuest != null) {
				dropGridX = Math.round(((float) localMouseX - canvasOffsetX) / gridSpacing);
				dropGridY = Math.round(((float) localMouseY - canvasOffsetY) / gridSpacing);
				GridPos pos = searchNearestEmptySlot(dropGridX, dropGridY);
				if (pos != null) {
					dropGridX = pos.x();
					dropGridY = pos.y();
				}
			}
			int snapX = canvasOffsetX + (dropGridX * gridSpacing);
			int snapY = canvasOffsetY + (dropGridY * gridSpacing);
			context.fill(snapX - 4, snapY - 4, snapX + 20, snapY + 20, 0x5500FF00); // Green drop highlight
		}

		context.disableScissor();

		if (nodeBeingDragged != null) {
			// Temporarily replace X/Y with mouse coordinates for rendering
			QuestNode movingNode = new QuestNode(nodeBeingDragged.definition(), (int) localMouseX - 12, (int) localMouseY - 12, nodeBeingDragged.isLocked());
			renderNode(context, movingNode, localMouseX, localMouseY, false);
		}

		context.getMatrices().popMatrix();

		// --- DRAW TOOLTIP ---
		if (hoveredNode != null && !isEditMode) { // Tooltips can get in the way during editing
			drawQuestTooltip(context, hoveredNode, mouseX, mouseY);
		} else if (hoveredNode != null) {
			List<Text> editTooltip = new ArrayList<>();
			editTooltip.add(Text.translatable("gui.pacpack-quests.edit_action").formatted(Formatting.YELLOW, Formatting.BOLD));
			editTooltip.add(Text.translatable("gui.pacpack-quests.click_drag").formatted(Formatting.GRAY).append(" ").append(Text.translatable("gui.pacpack-quests.to_move").formatted(Formatting.WHITE)));
			editTooltip.add(Text.translatable("gui.pacpack-quests.right_click").formatted(Formatting.GRAY).append(" ").append(Text.translatable("gui.pacpack-quests.to_edit_delete").formatted(Formatting.WHITE)));
			editTooltip.add(Text.translatable("gui.pacpack-quests.middle_click").formatted(Formatting.GRAY).append(" ").append(Text.translatable("gui.pacpack-quests.to_duplicate").formatted(Formatting.WHITE)));
			context.drawTooltip(this.textRenderer, editTooltip, mouseX, mouseY);
		} else if (hoveredDotX >= 0 && hoveredDotY >= 0 && isEditMode) {
			List<Text> editTooltip = new ArrayList<>();
			editTooltip.add(Text.translatable("gui.pacpack-quests.edit_action").formatted(Formatting.YELLOW, Formatting.BOLD));
			editTooltip.add(Text.translatable("gui.pacpack-quests.right_click").formatted(Formatting.GRAY).append(" ").append(Text.translatable("gui.pacpack-quests.to_add").formatted(Formatting.WHITE)));
			context.drawTooltip(this.textRenderer, editTooltip, mouseX, mouseY);
		}

		if (hoveredTruncatedCategory != null && draggedQuestId == null && draggedCategory == null) {
			context.drawTooltip(this.textRenderer, Text.literal(hoveredTruncatedCategory), mouseX, mouseY);
		}
	}

	private void renderNode(DrawContext context, QuestNode quest, double localMouseX, double localMouseY, boolean checkHover) {
		int progress = CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
		boolean claimed = CLIENT_CLAIMED.getOrDefault(quest.id(), false);

		int bgColor = 0xFF333333;
		if (quest.isLocked()) bgColor = 0xFF221111;
		else if (claimed) bgColor = 0xFF225522;
		else if (progress >= quest.requiredAmount()) bgColor = !isEditMode && checkHover && isHoveringNode(quest.x(), quest.y(), localMouseX, localMouseY) ? 0xFF808033 : 0xFF555522;

		// Highlight hovered node in edit mode
		if (isEditMode && checkHover && isHoveringNode(quest.x(), quest.y(), localMouseX, localMouseY)) {
			bgColor = 0xFF666666;
		}
		super.renderNodeBase(context, quest, bgColor);

		Text textToDraw;
		int textColor;
		if (quest.isLocked()) {
			textToDraw = Text.literal("???");
			textColor = 0xFFFF5555;
		} else if (claimed) {
			textToDraw = Text.literal(progress + "/" + quest.requiredAmount());
			textColor = 0xFF55FF55;
		} else {
			textToDraw = Text.literal(progress + "/" + quest.requiredAmount());
			textColor = progress >= quest.requiredAmount() ? 0xFFFFFF55 : 0xFFAAAAAA;
		}

		context.drawCenteredTextWithShadow(this.textRenderer, textToDraw, quest.x() + 8, quest.y() + 22, textColor);
	}

	private void drawQuestTooltip(DrawContext context, QuestNode quest, int mouseX, int mouseY) {
		int progress = CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
		int requiredAmount = CLIENT_DEFINITIONS.get(quest.id()).requiredAmount();
		boolean claimed = CLIENT_CLAIMED.getOrDefault(quest.id(), false);

		List<Text> tooltip = new ArrayList<>();

		// 1. Title
		tooltip.add(Text.translatable(quest.title()).formatted(Formatting.GOLD, Formatting.BOLD));

		// 2. Status and Progress
		MutableText statusLabel = Text.translatable("gui.pacpack-quests.status").append(": ").formatted(Formatting.GRAY);

		if (claimed) {
			tooltip.add(statusLabel.append(Text.translatable("gui.pacpack-quests.claimed").formatted(Formatting.GREEN)));
		} else if (progress >= requiredAmount) {
			tooltip.add(statusLabel.append(Text.translatable("gui.pacpack-quests.ready_to_claim").formatted(Formatting.YELLOW)));
		} else if (quest.isLocked()) {
			tooltip.add(statusLabel.append(Text.translatable("gui.pacpack-quests.locked").formatted(Formatting.RED)));
		} else {
			// Task target (e.g., Craft: Shield)
			tooltip.add(Text.translatable("gui.pacpack-quests." + quest.taskType().toString().toLowerCase())
					.append(": ")
					.append(Text.literal(TranslationUtils.getTranslatedName(quest.target(), TaskType.getRegistryType(quest.taskType()))))
					.formatted(Formatting.GRAY));

			// Progress (e.g., Progress: 0 / 1)
			tooltip.add(Text.translatable("gui.pacpack-quests.progress")
					.append(": " + progress + " / " + requiredAmount)
					.formatted(Formatting.GRAY));
		}

		// 3. Reward
		MutableText rewardLabel = Text.translatable("gui.pacpack-quests.reward").append(": ").formatted(Formatting.GRAY);
		tooltip.add(rewardLabel.append(TranslationUtils.getRewardText(quest.definition().primaryReward(), claimed)));

		context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
	}

	private QuestNode getQuestById(String id) {
		for (QuestNode node : questList) {
			if (node.id().equals(id)) return node;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (draggedQuestId != null) return true;

		double mouseX = click.x();
		double mouseY = click.y();
		double localMouseX = (mouseX - startX - panX) / zoom;
		double localMouseY = (mouseY - startY - panY) / zoom;
		boolean isMouseInWindow = isMouseInWindow(click.x(), click.y());

		if (click.button() == 0) { // Left Click
			if (categoryScrollbar.mouseClicked(mouseX, mouseY, 0)) {
				if (this.inlineCategoryField.isVisible()) {
					this.inlineCategoryField.setVisible(false);
					this.categoryEditTarget = null;
				}
				return true;
			}

			// Category tabs selection
			int currentTabY = startY + 20;
			double scrollAmount = categoryScrollbar.getScrollAmount();
			double adjustedMouseY = mouseY + scrollAmount;
			
			if (mouseY >= startY + 20 && mouseY <= startY + windowHeight) {
				for (String category : CLIENT_CATEGORIES) {
					int tabX = startX - tabWidth;

					// 1. Intercept the click on the delete button (red cross) or edit button (pen symbol)
					if (isEditMode) {
						int delBtnX = tabX - 14;
						int delBtnY = currentTabY + 4;
						if (mouseX >= delBtnX && mouseX <= delBtnX + 10 && adjustedMouseY >= delBtnY && adjustedMouseY <= delBtnY + 10) {
							this.client.setScreen(new ConfirmScreen(
									confirmed -> {
										if (confirmed) {
											ClientPlayNetworking.send(new DeleteCategoryPayload(category));
										}
										this.client.setScreen(this);
									},
									Text.translatable("gui.pacpack-quests.delete_category_title"),
									Text.translatable("gui.pacpack-quests.delete_category_desc").append(" " + category.toUpperCase() + " ?")
							));
							return true;
						}

						int editBtnX = delBtnX - 14;
						int editBtnY = currentTabY + 4;
						if (mouseX >= editBtnX && mouseX <= editBtnX + 10 && adjustedMouseY >= editBtnY && adjustedMouseY <= editBtnY + 10) {
							categoryEditTarget = category;
							this.inlineCategoryField.setX(tabX);
							this.inlineCategoryField.setWidth(tabWidth);
							this.inlineCategoryField.setY((int)(currentTabY - scrollAmount));
							this.inlineCategoryField.setText(category);
							this.inlineCategoryField.setVisible(true);
							this.setFocused(this.inlineCategoryField);
							this.inlineCategoryField.setFocused(true);
							return true;
						}
					}

					// 2. Normal click to change category OR prepare drag
					if (mouseX >= tabX && mouseX <= tabX + tabWidth && adjustedMouseY >= currentTabY && adjustedMouseY <= currentTabY + tabHeight
							&& (!this.inlineCategoryField.isVisible() || !category.equals(this.categoryEditTarget))) {
						if (!category.equals(selectedCategory)) {
							saveCurrentCameraState();
							selectedCategory = category;
							refreshQuests();
							restoreCameraState(selectedCategory);
						}
						if (isEditMode) {
							categoryClickTarget = category;
						}
						return true;
					}
					currentTabY += tabHeight + 5;
				}

				// + category tab button
				if (isEditMode && (!this.inlineCategoryField.isVisible() || this.categoryEditTarget != null)) {
					int tabX = startX - tabWidth;

					if (mouseX >= tabX && mouseX <= tabX + tabWidth && adjustedMouseY >= currentTabY && adjustedMouseY <= currentTabY + tabHeight) {
						this.categoryEditTarget = null;
						this.inlineCategoryField.setX(tabX);
						this.inlineCategoryField.setWidth(tabWidth);
						this.inlineCategoryField.setY((int)(currentTabY - scrollAmount));
						this.inlineCategoryField.setText("");
						this.inlineCategoryField.setVisible(true);
						this.setFocused(this.inlineCategoryField);
						this.inlineCategoryField.setFocused(true);
						return true;
					}
				}
			}
		}

		// Edit Mode Logic (Node interaction)
		if (isEditMode && isMouseInWindow) {
			if (click.button() == 0 && draggedQuestId == null) { // Left Click: Pick up node to drag
				for (QuestNode quest : questList) {
					if (isHoveringNode(quest.x(), quest.y(), localMouseX, localMouseY)) {
						draggedQuestId = quest.id();
						return true; // Consume event to prevent panning
					}
				}
			} else if (click.button() == 1) { // Right Click: Open Edit/Delete Menu
				for (QuestNode quest : questList) {
					if (isHoveringNode(quest.x(), quest.y(), localMouseX, localMouseY)) {
						// Edit existing quest
						MinecraftClient.getInstance().setScreen(new EditQuestScreen(this, quest.id(), selectedCategory, quest.displayX(), quest.displayY()));
						return true;
					}
				}

				if ((localMouseX - PADDING) % 48 < 24 && (localMouseY - PADDING) % 48 < 24
						&& localMouseX >= PADDING && localMouseY >= PADDING
						&& localMouseX <= PADDING + 48 * maxGridColumns
						&& localMouseY <= PADDING + 48 * maxGridRows) {
					// Create new quest on empty nodes
					int gridX = Math.round(((float) localMouseX - canvasOffsetX) / gridSpacing);
					int gridY = Math.round(((float) localMouseY - canvasOffsetY) / gridSpacing);
					MinecraftClient.getInstance().setScreen(new EditQuestScreen(this, null, selectedCategory, gridX, gridY));
					return true;
				}
			} else if (click.button() == 2) { // Middle Click: Duplicate Quest
				for (QuestNode quest : questList) {
					if (isHoveringNode(quest.x(), quest.y(), localMouseX, localMouseY)) {
						int suffix = 0;
						Pattern pattern = Pattern.compile("(" + quest.id() + "_copy)([0-9]+)$");
						for (QuestDefinition def : CLIENT_DEFINITIONS.values()) {
							Matcher matcher = pattern.matcher(def.id());
							if (matcher.find()) {
								suffix = Math.max(suffix, Integer.parseInt(matcher.group(2)));
							}
						}
						String newId = quest.id() + "_copy" + (suffix + 1);

						QuestDefinition originalDef = CLIENT_DEFINITIONS.get(quest.id());
						if (originalDef == null) return true;
						QuestDefinition newQuest = originalDef.withIdAndTitle(newId, originalDef.title() + " Copy");

						draggedQuestId = newQuest.id();
						duplicatedQuest = newQuest;
						return true;
					}
				}
			}
		}

		// Normal Interaction logic (Claiming)
		if (!isEditMode && click.button() == 0) {
			if (isMouseInWindow) {
				for (QuestNode quest : questList) {
					if (isHoveringNode(quest.x(), quest.y(), localMouseX, localMouseY)) {
						if (quest.isLocked()) return true;

						int currentProg = CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
						int requiredAmount = CLIENT_DEFINITIONS.get(quest.id()).requiredAmount();
						boolean isClaimed = CLIENT_CLAIMED.getOrDefault(quest.id(), false);

						if (currentProg >= requiredAmount && !isClaimed) {
							ClientPlayNetworking.send(new ClaimQuestPayload(quest.id()));
						}
						return true;
					}
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (categoryScrollbar.mouseDragged(click.x(), click.y(), click.button(), offsetX, offsetY)) {
			if (this.inlineCategoryField != null && this.inlineCategoryField.isVisible()) {
				this.inlineCategoryField.setVisible(false);
				this.categoryEditTarget = null;
			}
			return true;
		}

		if (isEditMode && categoryClickTarget != null) {
			draggedCategory = categoryClickTarget;
			return true;
		}

		// If holding a node, do NOT pan the camera
		if (isEditMode && draggedQuestId != null && click.button() == 0) {
			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (categoryScrollbar.mouseReleased(click.x(), click.y(), click.button())) {
			return true;
		}
		
		categoryClickTarget = null; // Reset click target

		if (isEditMode && draggedCategory != null && click.button() == 0) {
			int dropIndex = Math.clamp((int) Math.round((click.y() + categoryScrollbar.getScrollAmount() - (startY + 20.0)) / (tabHeight + 5.0)), 0, CLIENT_CATEGORIES.size());
			CLIENT_CATEGORIES.remove(draggedCategory);

			// Using Math.clamp after subtraction prevents OutOfBounds errors
			dropIndex = Math.clamp(dropIndex, 0, CLIENT_CATEGORIES.size());
			CLIENT_CATEGORIES.add(dropIndex, draggedCategory);

			ClientPlayNetworking.send(new ReorderCategoryPayload(CLIENT_CATEGORIES));

			draggedCategory = null;
			refreshUI();
			return true;
		}

		if (isEditMode && draggedQuestId != null && click.button() == 0) {
			double localMouseX = (click.x() - startX - panX) / zoom;
			double localMouseY = (click.y() - startY - panY) / zoom;

			// Reverse math to find the closest grid X and Y
			int dropGridX = Math.round(((float) localMouseX - canvasOffsetX) / gridSpacing);
			int dropGridY = Math.round(((float) localMouseY - canvasOffsetY) / gridSpacing);

			QuestDefinition oldDef = duplicatedQuest != null ? duplicatedQuest : CLIENT_DEFINITIONS.get(draggedQuestId);
			if (oldDef != null) {
				int finalX = dropGridX;
				int finalY = dropGridY;
				boolean foundSpot = false;

				GridPos spot = searchNearestEmptySlot(dropGridX, dropGridY);
				if (spot != null) {
					finalX = spot.x;
					finalY = spot.y;
					foundSpot = true;
				}

				if (!foundSpot && duplicatedQuest != null) {
					duplicatedQuest = null;
					draggedQuestId = null;
					return true;
				}

				// Fallback to the original coordinates if the area is completely full or mouse is out of window
				if (!foundSpot || !isMouseInWindow(click.x(), click.y())) {
					if (duplicatedQuest != null) return true;
					finalX = oldDef.displayX();
					finalY = oldDef.displayY();
				}

				// Open edit screen after releasing the duplicated quest
				if (duplicatedQuest != null) {
					duplicatedQuest = null;
					draggedQuestId = null;
					MinecraftClient.getInstance().setScreen(new EditQuestScreen(this, null, selectedCategory, finalX, finalY, oldDef));
				}

				// Only send packet and update if the position actually changed
				else if (finalX != oldDef.displayX() || finalY != oldDef.displayY()) {
					ClientPlayNetworking.send(new MoveQuestPayload(draggedQuestId, finalX, finalY));

					QuestDefinition newDef = oldDef.withPosition(finalX, finalY);
					CLIENT_DEFINITIONS.put(draggedQuestId, newDef);
				}
			}

			draggedQuestId = null;
			refreshQuests();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX < startX) {
			if (categoryScrollbar.mouseScrolled(mouseX, mouseY, verticalAmount)) {
				if (this.inlineCategoryField != null && this.inlineCategoryField.isVisible()) {
					this.inlineCategoryField.setVisible(false);
					this.categoryEditTarget = null;
				}
				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (this.inlineCategoryField.isVisible() && this.inlineCategoryField.isFocused()) {
			if (input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
				String newName = this.inlineCategoryField.getText().trim().toLowerCase(Locale.ROOT).replace(" ", "_");
				if (PathSecurity.isValidIdentifier(newName) && categoryEditTarget == null) {
					if (!CLIENT_CATEGORIES.contains(newName)) {
						ClientPlayNetworking.send(new CreateCategoryPayload(newName));
						addNewCategory(newName);
					}
				} else if (PathSecurity.isValidIdentifier(newName) && !categoryEditTarget.equals(newName)) {
					ClientPlayNetworking.send(new EditCategoryPayload(categoryEditTarget, newName));
				}
				this.inlineCategoryField.setVisible(false);
				this.inlineCategoryField.setText("");
				categoryEditTarget = null;
				return true;
			}
			else if (input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				this.inlineCategoryField.setVisible(false);
				this.inlineCategoryField.setText("");
				categoryEditTarget = null;
				return true;
			}
			return super.keyPressed(input);
		}

		if (duplicatedQuest != null) {
			if (input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)  {
				duplicatedQuest = null;
				draggedQuestId = null;
				return true;
			}
		}

		if (openQuestMenuKey.matchesKey(input)) {
			this.close();
			return true;
		}
		return super.keyPressed(input);
	}

	private GridPos searchNearestEmptySlot(int dropGridX, int dropGridY) {
		// Clamp target coordinates inside the grid first
		int targetX = Math.clamp(dropGridX, 0, maxGridColumns - 1);
		int targetY = Math.clamp(dropGridY, 0, maxGridRows - 1);

		// Fast path: if the target slot is already free, return it immediately
		if (!isGridOccupied(targetX, targetY, draggedQuestId)) {
			return new GridPos(targetX, targetY);
		}

		GridPos bestPos = null;
		int minDistanceSq = Integer.MAX_VALUE;

		// Scan the grid and find the empty slot with the shortest Euclidean distance
		for (int x = 0; x < maxGridColumns; x++) {
			for (int y = 0; y < maxGridRows; y++) {
				int dx = x - targetX;
				int dy = y - targetY;
				int distSq = dx * dx + dy * dy;

				// Only check occupation if this slot is strictly closer than our current best
				if (distSq < minDistanceSq && !isGridOccupied(x, y, draggedQuestId)) {
					minDistanceSq = distSq;
					bestPos = new GridPos(x, y);
				}
			}
		}

		return bestPos;
	}

	@Override
	protected boolean forceFullGridBounds() {
		return this.isEditMode;
	}

	@Override
	protected java.util.List<? extends GridNode> getNodes() {
		return this.questList;
	}

	private void claimAllQuests() {
		for (QuestDefinition quest : CLIENT_DEFINITIONS.values()) {
			int currentProg = CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
			int requiredAmount = quest.requiredAmount();
			boolean isClaimed = CLIENT_CLAIMED.getOrDefault(quest.id(), false);

			// Check if quest is ready to be claimed
			if (currentProg >= requiredAmount && !isClaimed) {

				// Verify lock status to prevent sending useless packets
				boolean isLocked = false;
				if (quest.parents() != null) {
					for (String parentId : quest.parents()) {
						if (!CLIENT_FINISHED.getOrDefault(parentId, false)) {
							isLocked = true;
							break;
						}
					}
				}

				// Send standard claim payload if unlocked
				if (!isLocked) {
					ClientPlayNetworking.send(new ClaimQuestPayload(quest.id()));
				}
			}
		}
		this.claimAllButton.active = false;
	}

	private boolean hasClaimableQuests() {
		for (QuestDefinition quest : CLIENT_DEFINITIONS.values()) {
			int currentProg = CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
			int requiredAmount = quest.requiredAmount();
			boolean isClaimed = CLIENT_CLAIMED.getOrDefault(quest.id(), false);

			if (currentProg >= requiredAmount && !isClaimed) {
				boolean isLocked = false;
				if (quest.parents() != null) {
					for (String parentId : quest.parents()) {
						if (!CLIENT_CLAIMED.getOrDefault(parentId, false)) {
							isLocked = true;
							break;
						}
					}
				}
				if (!isLocked) {
					return true; // Found at least one quest ready to be claimed
				}
			}
		}
		return false; // No quests ready
	}

	private boolean isGridOccupied(int gridX, int gridY, String ignoreQuestId) {
		for (QuestDefinition quest : CLIENT_DEFINITIONS.values()) {
			if (quest.category().equals(selectedCategory)
					&& !quest.id().equals(ignoreQuestId)
					&& quest.displayX() == gridX
					&& quest.displayY() == gridY) {
				return true;
			}
		}
		return false;
	}

	private boolean isMouseInWindow(double mouseX, double mouseY) {
		return mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight;
	}

	public void updateClaimButtonState() {
		if (this.claimAllButton != null) {
			this.claimAllButton.active = hasClaimableQuests();
		}
	}

	public void addNewCategory(String categoryName) {
		if (!CLIENT_CATEGORIES.contains(categoryName)) {
			CLIENT_CATEGORIES.add(categoryName);
		}
		saveCurrentCameraState();
		selectedCategory = categoryName;
		refreshQuests();
		resetCamera();
		saveCurrentCameraState();
	}

	public void removeCategory(String categoryName) {
		ModConfig.cameraPositions.remove(categoryName);
		// If the deleted category was the one open, we switch to another one
		if (selectedCategory.equals(categoryName)) {
			selectedCategory = CLIENT_CATEGORIES.isEmpty() ? DEFAULT_CATEGORY_NAME : CLIENT_CATEGORIES.getFirst();
			refreshQuests();
			restoreCameraState(selectedCategory);
		}
		this.refreshUI();
	}

	public void renameCategory(String oldName, String newName) {
		int index = CLIENT_CATEGORIES.indexOf(oldName);
		if (index >= 0) {
			ModConfig.CameraState state = ModConfig.cameraPositions.remove(oldName);
			if (state != null) {
				ModConfig.cameraPositions.put(newName, state);
			}

			List<String> newCategories = new ArrayList<>();

			for (int i = 0; i < CLIENT_CATEGORIES.size(); i++) {
				if (i == index) {
					newCategories.add(newName);
				} else {
					newCategories.add(CLIENT_CATEGORIES.get(i));
				}
			}

			CLIENT_CATEGORIES.clear();
			CLIENT_CATEGORIES.addAll(newCategories);

			if (selectedCategory.equals(oldName))
				selectedCategory = newName;
		}
	}

	public void refreshUI() {
		this.clearAndInit();
	}
}

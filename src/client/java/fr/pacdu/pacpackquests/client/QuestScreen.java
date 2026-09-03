package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.network.ClaimQuestPayload;
import fr.pacdu.pacpackquests.network.MoveQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class QuestScreen extends Screen {

	private int windowWidth, windowHeight, startX, startY;
	private final List<QuestNode> questList = new ArrayList<>();

	private final List<String> categories = new ArrayList<>();
	private static String selectedCategory = "main";
	private final int tabWidth = 70;
	private final int tabHeight = 20;

	// --- Camera & Canvas System ---
	private double panX = 0;
	private double panY = 0;
	private float zoom = 1.0f;
	private final int gridSpacing = 48;
	private final int canvasOffsetX = 20;
	private final int canvasOffsetY = 20;

	// --- Edit Mode System ---
	private boolean isEditMode = false;
	private String draggedQuestId = null;

	record QuestNode(String id, String title, int displayX, int displayY, int x, int y, TaskType type, String target, ItemStack icon, ItemStack reward, RewardType rewardType, int rewardAmount, List<String> parents, boolean isLocked) {}

	public QuestScreen() {
		super(Text.translatable("gui.pacpack-quests.title"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		super.init();

		windowWidth = this.width - 140;
		windowHeight = this.height - 80;
		startX = (this.width - windowWidth) / 2 + 40;
		startY = (this.height - windowHeight) / 2 - 10;

		int buttonWidth = 100;
		int buttonHeight = 20;

		// "Done" Button
		this.addDrawableChild(
				ButtonWidget.builder(Text.translatable("gui.done"), button -> this.close())
						.dimensions((this.width - buttonWidth) / 2, this.height - 35, buttonWidth, buttonHeight)
						.build()
		);

		// "Edit Mode" Toggle Button (Only visible if player is OP)
		if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getServer().getPlayerManager().isOperator(MinecraftClient.getInstance().player.getPlayerConfigEntry())) {
			this.addDrawableChild(
					ButtonWidget.builder(Text.literal("Edit: " + (isEditMode ? "ON" : "OFF")).formatted(isEditMode ? Formatting.GREEN : Formatting.RED), button -> {
								this.isEditMode = !this.isEditMode;
								this.clearAndInit(); // Refresh screen to update button text and visuals
							})
							.dimensions(startX + windowWidth - 80, startY - 25, 80, 20)
							.build()
			);
		}

		// --- Categories Setup ---
		categories.clear();
		for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
			if (!categories.contains(quest.category())) {
				categories.add(quest.category());
			}
		}

		if (categories.contains("main")) {
			categories.remove("main");
			categories.addFirst("main");
		} else if (categories.isEmpty()) {
			categories.add("main");
		}

		if (!categories.contains(selectedCategory)) {
			selectedCategory = categories.getFirst();
		}

		refreshQuests();
	}

	private void refreshQuests() {
		questList.clear();

		// Reset camera when refreshing or switching tabs
		panX = 0;
		panY = 0;
		zoom = 1.0f;

		for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
			if (quest.category().equals(selectedCategory)) {

				int localX = canvasOffsetX + (quest.displayX() * gridSpacing);
				int localY = canvasOffsetY + (quest.displayY() * gridSpacing);

				boolean locked = false;
				if (quest.parents() != null) {
					for (String parentId : quest.parents()) {
						if (!PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(parentId, false)) {
							locked = true;
							break;
						}
					}
				}

				questList.add(new QuestNode(
						quest.id(), quest.title(), quest.displayX(), quest.displayY(), localX, localY,
						quest.type(), quest.target(), quest.icon(), quest.reward(),
						quest.rewardType(), quest.rewardAmount(), quest.parents(), locked
				));
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.fill(startX, startY, startX + windowWidth, startY + windowHeight, 0xAA000000);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, startY - 18, -1);

		int currentTabY = startY + 20;
		for (String category : categories) {
			boolean isSelected = category.equals(selectedCategory);
			int color = isSelected ? 0xFF666666 : 0xFF333333;
			int tabX = startX - tabWidth;

			context.fill(tabX, currentTabY, tabX + tabWidth, currentTabY + tabHeight, color);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(category.toUpperCase()), tabX + tabWidth / 2, currentTabY + 6, isSelected ? 0xFFFFFFFF : 0xFFAAAAAA);

			currentTabY += tabHeight + 5;
		}

		double localMouseX = (mouseX - startX - panX) / zoom;
		double localMouseY = (mouseY - startY - panY) / zoom;
		boolean isMouseInWindow = mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight;

		QuestNode hoveredNode = null;
		QuestNode nodeBeingDragged = null;

		context.enableScissor(startX, startY, startX + windowWidth, startY + windowHeight);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate((float) (startX + panX), (float) (startY + panY));
		context.getMatrices().scale(zoom, zoom);

		// Draw Helper Grid in Edit Mode
		if (isEditMode) {
			for (int gx = 0; gx < 20; gx++) {
				for (int gy = 0; gy < 20; gy++) {
					int dotX = canvasOffsetX + (gx * gridSpacing) + 8;
					int dotY = canvasOffsetY + (gy * gridSpacing) + 8;
					context.fill(dotX, dotY, dotX + 2, dotY + 2, 0x55FFFFFF);
				}
			}
		}

		// --- DRAW LINES ---
		for (QuestNode quest : questList) {
			if (quest.parents() == null) continue;
			for (String parentId : quest.parents()) {
				QuestNode parentNode = getQuestById(parentId);
				if (parentNode != null) {
					drawConnectionLine(context, parentNode, quest);
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
			if (isMouseInWindow && isHovering(quest, localMouseX, localMouseY)) {
				hoveredNode = quest;
			}
		}

		// --- DRAW DRAGGED NODE AT CURSOR ---
		if (nodeBeingDragged != null && isMouseInWindow) {
			// Temporarily replace X/Y with mouse coordinates for rendering
			QuestNode movingNode = new QuestNode(
					nodeBeingDragged.id(), nodeBeingDragged.title(), nodeBeingDragged.displayX(), nodeBeingDragged.displayY(),
					(int) localMouseX - 12, (int) localMouseY - 12, nodeBeingDragged.type(), nodeBeingDragged.target(),
					nodeBeingDragged.icon(), nodeBeingDragged.reward(), nodeBeingDragged.rewardType(),
					nodeBeingDragged.rewardAmount(), nodeBeingDragged.parents(), nodeBeingDragged.isLocked()
			);
			renderNode(context, movingNode, localMouseX, localMouseY, false);
			// Draw a semi-transparent drop shadow snapped to the grid to show where it will land
			int dropGridX = Math.round(((float) localMouseX - canvasOffsetX) / gridSpacing);
			int dropGridY = Math.round(((float) localMouseY - canvasOffsetY) / gridSpacing);
			int snapX = canvasOffsetX + (dropGridX * gridSpacing);
			int snapY = canvasOffsetY + (dropGridY * gridSpacing);
			context.fill(snapX - 4, snapY - 4, snapX + 20, snapY + 20, 0x5500FF00); // Green drop highlight
		}

		context.getMatrices().popMatrix();
		context.disableScissor();

		if (hoveredNode != null && !isEditMode) { // Tooltips can get in the way during editing
			drawQuestTooltip(context, hoveredNode, mouseX, mouseY);
		} else if (hoveredNode != null && isEditMode) {
			List<Text> editTooltip = new ArrayList<>();
			editTooltip.add(Text.literal("Edit Mode Actions:").formatted(Formatting.YELLOW, Formatting.BOLD));
			editTooltip.add(Text.literal("Left Click & Drag").formatted(Formatting.GRAY).append(Text.literal(" to Move").formatted(Formatting.WHITE)));
			editTooltip.add(Text.literal("Right Click").formatted(Formatting.GRAY).append(Text.literal(" to Edit/Delete").formatted(Formatting.WHITE)));
			context.drawTooltip(this.textRenderer, editTooltip, mouseX, mouseY);
		}
	}

	private void renderNode(DrawContext context, QuestNode quest, double localMouseX, double localMouseY, boolean checkHover) {
		int progress = PacPackQuestsClient.CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
		int requiredAmount = PacPackQuestsClient.CLIENT_DEFINITIONS.get(quest.id()).requiredAmount();
		boolean claimed = PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(quest.id(), false);

		int bgColor = 0xFF333333;
		if (quest.isLocked()) bgColor = 0xFF221111;
		else if (claimed) bgColor = 0xFF225522;
		else if (progress >= requiredAmount) bgColor = 0xFF555522;

		// Highlight hovered node in edit mode
		if (isEditMode && checkHover && isHovering(quest, localMouseX, localMouseY)) {
			bgColor = 0xFF666666;
		}

		context.fill(quest.x() - 4, quest.y() - 4, quest.x() + 20, quest.y() + 20, bgColor);
		context.drawItem(quest.icon(), quest.x(), quest.y());

		Text textToDraw;
		int textColor;
		if (quest.isLocked()) {
			textToDraw = Text.translatable("gui.pacpack-quests.locked");
			textColor = 0xFFFF5555;
		} else if (claimed) {
			textToDraw = Text.translatable("gui.done");
			textColor = 0xFF55FF55;
		} else {
			textToDraw = Text.literal(progress + "/" + requiredAmount);
			textColor = progress >= requiredAmount ? 0xFFFFFF55 : 0xFFAAAAAA;
		}

		context.drawText(this.textRenderer, textToDraw, quest.x() - 2, quest.y() + 22, textColor, true);
	}

	private void drawQuestTooltip(DrawContext context, QuestNode quest, int mouseX, int mouseY) {
		int progress = PacPackQuestsClient.CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
		int requiredAmount = PacPackQuestsClient.CLIENT_DEFINITIONS.get(quest.id()).requiredAmount();
		boolean claimed = PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(quest.id(), false);

		List<Text> tooltip = new ArrayList<>();
		tooltip.add(Text.translatable(quest.title()).formatted(Formatting.GOLD, Formatting.BOLD));

		if (claimed) {
			tooltip.add(Text.literal(I18n.translate("gui.pacpack-quests.status") + ": " + I18n.translate("gui.pacpack-quests.claimed")).formatted(Formatting.GREEN));
		} else if (progress >= requiredAmount) {
			tooltip.add(Text.literal(I18n.translate("gui.pacpack-quests.status") + ": " + I18n.translate("gui.pacpack-quests.ready_to_claim")).formatted(Formatting.YELLOW));
		} else if (quest.isLocked()) {
			tooltip.add(Text.literal(I18n.translate("gui.pacpack-quests.status") + ": " + I18n.translate("gui.pacpack-quests.locked")).formatted(Formatting.RED));
		} else {
			tooltip.add(Text.literal(I18n.translate("gui.pacpack-quests." + quest.type().toString().toLowerCase()) + ": " + getTranslatedTargetName(quest.target())).formatted(Formatting.GRAY));
			tooltip.add(Text.literal(I18n.translate("gui.pacpack-quests.progress") + ": " + progress + " / " + requiredAmount).formatted(Formatting.GRAY));
		}

		switch (quest.rewardType()) {
			case XP -> tooltip.add(Text.translatable("gui.pacpack-quests.reward")
					.append(": " + quest.rewardAmount() + " XP")
					.formatted(claimed ? Formatting.GREEN : Formatting.LIGHT_PURPLE));
			case LEVEL -> tooltip.add(Text.translatable("gui.pacpack-quests.reward")
					.append(": " + quest.rewardAmount() + " ")
					.append(Text.translatable("gui.pacpack-quests.levels"))
					.formatted(claimed ? Formatting.GREEN : Formatting.LIGHT_PURPLE));
			case ITEM -> tooltip.add(Text.translatable("gui.pacpack-quests.reward")
					.append(": " + quest.rewardAmount() + "x ").append(quest.reward().getName())
					.formatted(claimed ? Formatting.GREEN : Formatting.AQUA));
		}

		context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
	}

	private void drawConnectionLine(DrawContext context, QuestNode parent, QuestNode child) {
		int px = parent.x() + 8;
		int py = parent.y() + 8;
		int cx = child.x() + 8;
		int cy = child.y() + 8;

		// If either node is being dragged, visually disconnect the lines to avoid messy rendering, or draw to mouse
		if (parent.id().equals(draggedQuestId) || child.id().equals(draggedQuestId)) return;

		int lineColor = child.isLocked() ? 0xFF444444 : 0xFF88AA88;
		int thickness = 2;
		context.fill(Math.min(px, cx), py - thickness/2, Math.max(px, cx) + thickness/2, py + thickness/2, lineColor);
		context.fill(cx - thickness/2, Math.min(py, cy), cx + thickness/2, Math.max(py, cy) + thickness/2, lineColor);
	}

	private QuestNode getQuestById(String id) {
		for (QuestNode node : questList) {
			if (node.id().equals(id)) return node;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();
		double localMouseX = (mouseX - startX - panX) / zoom;
		double localMouseY = (mouseY - startY - panY) / zoom;
		boolean isMouseInWindow = mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight;

		if (isEditMode && isMouseInWindow) {
			if (click.button() == 0) { // Left Click: Pick up node to drag
				for (QuestNode quest : questList) {
					if (isHovering(quest, localMouseX, localMouseY)) {
						draggedQuestId = quest.id();
						return true; // Consume event to prevent panning
					}
				}
			} else if (click.button() == 1) { // Right Click: Open Edit/Delete Menu
				for (QuestNode quest : questList) {
					if (isHovering(quest, localMouseX, localMouseY)) {
						// TODO: Implement Edit/Delete screen opening here
						// MinecraftClient.getInstance().setScreen(new EditQuestScreen(this, quest.id()));
						return true;
					}
				}
				// If right-clicked empty space, add a new quest
				// ClientPlayNetworking.send(new CreateQuestPayload(selectedCategory, localMouseX, localMouseY));
				return true;
			}
		}

		// Normal Interaction logic (Claiming & Tabs)
		if (!isEditMode && click.button() == 0) {
			int currentTabY = startY + 20;
			for (String category : categories) {
				int tabX = startX - tabWidth;
				if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= currentTabY && mouseY <= currentTabY + tabHeight) {
					selectedCategory = category;
					refreshQuests();
					return true;
				}
				currentTabY += tabHeight + 5;
			}

			if (isMouseInWindow) {
				for (QuestNode quest : questList) {
					if (isHovering(quest, localMouseX, localMouseY)) {
						if (quest.isLocked()) return true;

						int currentProg = PacPackQuestsClient.CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
						int requiredAmount = PacPackQuestsClient.CLIENT_DEFINITIONS.get(quest.id()).requiredAmount();
						boolean isClaimed = PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(quest.id(), false);

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
		// If holding a node, do NOT pan the camera
		if (isEditMode && draggedQuestId != null) {
			return true;
		}

		if (click.x() >= startX && click.x() <= startX + windowWidth && click.y() >= startY && click.y() <= startY + windowHeight) {
			panX += offsetX;
			panY += offsetY;

			clampPanning();

			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	// New Native Method: Triggered when the mouse button is released
	@Override
	public boolean mouseReleased(Click click) {
		if (isEditMode && draggedQuestId != null && click.button() == 0) {
			double localMouseX = (click.x() - startX - panX) / zoom;
			double localMouseY = (click.y() - startY - panY) / zoom;

			// Reverse math to find the closest grid X and Y
			int dropGridX = Math.round(((float) localMouseX - canvasOffsetX) / gridSpacing);
			int dropGridY = Math.round(((float) localMouseY - canvasOffsetY) / gridSpacing);

			// Send packet to server to permanently save new position
			// ClientPlayNetworking.send(new MoveQuestPayload(draggedQuestId, dropGridX, dropGridY));

			// 1. Send the new coordinates to the server
			ClientPlayNetworking.send(new MoveQuestPayload(draggedQuestId, dropGridX, dropGridY));

			// 2. Recreate the definition locally with the new X and Y to prevent UI lag
			QuestDefinition oldDef = PacPackQuestsClient.CLIENT_DEFINITIONS.get(draggedQuestId);
			if (oldDef != null) {
				QuestDefinition newDef = new QuestDefinition(
						oldDef.id(), oldDef.title(), oldDef.category(),
						oldDef.type(), oldDef.target(), oldDef.requiredAmount(),
						oldDef.icon(), oldDef.reward(), oldDef.rewardType(),
						oldDef.rewardAmount(), oldDef.parents(),
						dropGridX, dropGridY
				);

				// 3. Overwrite the old quest in the client's memory
				PacPackQuestsClient.CLIENT_DEFINITIONS.put(draggedQuestId, newDef);
			}

			draggedQuestId = null;
			refreshQuests();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight) {
			double oldZoom = zoom;
			zoom += (float) (verticalAmount * 0.15f);
			zoom = Math.clamp(zoom, 0.3f, 2f);

			double zoomRatio = zoom / oldZoom;
			double relX = mouseX - startX;
			double relY = mouseY - startY;

			panX = relX - (relX - panX) * zoomRatio;
			panY = relY - (relY - panY) * zoomRatio;

			clampPanning();

			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (PacPackQuestsClient.openQuestMenuKey.matchesKey(input)) {
			this.close();
			return true;
		}
		return super.keyPressed(input);
	}

	private boolean isHovering(QuestNode quest, double localMouseX, double localMouseY) {
		return localMouseX >= quest.x() - 4 && localMouseX <= quest.x() + 20 &&
				localMouseY >= quest.y() - 4 && localMouseY <= quest.y() + 20;
	}

	private String getTranslatedTargetName(String target) {
		if (target.startsWith("#")) {
			String tagKey = "tag." + target.substring(1).replace(":", ".");
			if (I18n.hasTranslation(tagKey)) {
				return I18n.translate(tagKey);
			} else {
				String path = Identifier.of(target.substring(1)).getPath();
				String formatted = path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
				return "Any " + formatted;
			}
		} else {
			Item item = Registries.ITEM.get(Identifier.of(target));
			return item.getName().getString();
		}
	}

	// Keeps the camera within the bounds of the 20x20 grid
	private void clampPanning() {
		// 20 grid units * 48 pixels per unit = 960 total pixels
		double canvasPixelWidth = 20 * gridSpacing;
		double canvasPixelHeight = 20 * gridSpacing;

		double maxPanX = 0;
		double maxPanY = 0;

		// We multiply by zoom because the physical size of the canvas changes as we zoom in/out.
		double minPanX = -(canvasPixelWidth * zoom);
		double minPanY = -(canvasPixelHeight * zoom);

		// Apply the limits safely
		panX = Math.clamp(panX, minPanX, maxPanX);
		panY = Math.clamp(panY, minPanY, maxPanY);
	}
}
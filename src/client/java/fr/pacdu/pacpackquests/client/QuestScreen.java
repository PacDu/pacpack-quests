package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.network.ClaimQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

	// x and y are now LOCAL canvas coordinates, not raw screen coordinates
	record QuestNode(String id, String title, int x, int y, TaskType type, String target, ItemStack icon, ItemStack reward, RewardType rewardType, int rewardAmount, List<String> parents, boolean isLocked) {}

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
		this.addDrawableChild(
				ButtonWidget.builder(Text.translatable("gui.done"), button -> this.close())
						.dimensions((this.width - buttonWidth) / 2, this.height - 35, buttonWidth, buttonHeight)
						.build()
		);

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

		int gridSpacing = 48;

		// Offset so 0,0 is perfectly in the top-left corner (with a 20px padding)
		int canvasOffsetX = 20;
		int canvasOffsetY = 20;

		// Reset camera when refreshing or switching tabs
		panX = 0;
		panY = 0;
		zoom = 1.0f;

		for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
			if (quest.category().equals(selectedCategory)) {

				// Calculate local layout coordinates (0,0 maps to top-left)
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
						quest.id(), quest.title(), localX, localY, quest.type(), quest.target(),
						quest.icon(), quest.reward(), quest.rewardType(), quest.rewardAmount(),
						quest.parents(), locked
				));
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		// 1. Draw UI Background
		context.fill(startX, startY, startX + windowWidth, startY + windowHeight, 0xAA000000);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, startY - 18, -1);

		// 2. Draw Left Navigation Tabs
		int currentTabY = startY + 20;
		for (String category : categories) {
			boolean isSelected = category.equals(selectedCategory);
			int color = isSelected ? 0xFF666666 : 0xFF333333;
			int tabX = startX - tabWidth;

			context.fill(tabX, currentTabY, tabX + tabWidth, currentTabY + tabHeight, color);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(category.toUpperCase()), tabX + tabWidth / 2, currentTabY + 6, isSelected ? 0xFFFFFFFF : 0xFFAAAAAA);

			currentTabY += tabHeight + 5;
		}

		// --- MATH: Convert Global Mouse to Local Canvas Mouse ---
		double localMouseX = (mouseX - startX - panX) / zoom;
		double localMouseY = (mouseY - startY - panY) / zoom;
		boolean isMouseInWindow = mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight;

		QuestNode hoveredNode = null;

		// 3. ENABLE SCISSOR (Clips everything drawn outside the main window bounds)
		context.enableScissor(startX, startY, startX + windowWidth, startY + windowHeight);

		// 4. PUSH MATRIX (Apply Pan and Zoom)
		context.getMatrices().pushMatrix();
		context.getMatrices().translate((float) (startX + panX), (float) (startY + panY));
		context.getMatrices().scale(zoom, zoom);

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
			int progress = PacPackQuestsClient.CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
			int requiredAmount = PacPackQuestsClient.CLIENT_DEFINITIONS.get(quest.id()).requiredAmount();
			boolean claimed = PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(quest.id(), false);

			int bgColor = 0xFF333333;
			if (quest.isLocked()) {
				bgColor = 0xFF221111;
			} else if (claimed) {
				bgColor = 0xFF225522;
			} else if (progress >= requiredAmount) {
				bgColor = 0xFF555522;
			}

			context.fill(quest.x() - 4, quest.y() - 4, quest.x() + 20, quest.y() + 20, bgColor);
			context.drawItem(quest.icon(), quest.x(), quest.y());

			// Corrected Text formatting block (No more translatable exception)
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

			// Notice we draw text at quest.x, quest.y (local coordinates)
			context.drawText(this.textRenderer, textToDraw, quest.x() - 2, quest.y() + 22, textColor, true);

			// Check hover using the converted local mouse coordinates
			if (isMouseInWindow && isHovering(quest, localMouseX, localMouseY)) {
				hoveredNode = quest;
			}
		}

		// 5. POP MATRIX & DISABLE SCISSOR
		context.getMatrices().popMatrix();
		context.disableScissor();

		// 6. DRAW TOOLTIP (Drawn outside the matrix so it isn't clipped or zoomed!)
		if (hoveredNode != null) {
			drawQuestTooltip(context, hoveredNode, mouseX, mouseY);
		}
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
		if (click.button() == 0) {
			double mouseX = click.x();
			double mouseY = click.y();

			// 1. Tab Clicks
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

			// 2. Quest Clicks
			double localMouseX = (mouseX - startX - panX) / zoom;
			double localMouseY = (mouseY - startY - panY) / zoom;
			boolean isMouseInWindow = mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight;

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

	// --- DRAG TO MOVE ---
	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		// Only allow dragging if clicking inside the canvas window bounds
		if (click.x() >= startX && click.x() <= startX + windowWidth && click.y() >= startY && click.y() <= startY + windowHeight) {
			panX += offsetX;
			panY += offsetY;
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	// --- SCROLL TO ZOOM ---
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight) {
			double oldZoom = zoom;

			// Adjust zoom sensitivity
			zoom += (float) (verticalAmount * 0.15f);

			// Clamp zoom between 0.3x (zoomed out) and 2.5x (zoomed in)
			zoom = Math.clamp(zoom, 0.3f, 2f);

			// Adjust Pan so it dynamically zooms directly into the mouse cursor's location
			double zoomRatio = zoom / oldZoom;
			double relX = mouseX - startX;
			double relY = mouseY - startY;

			panX = relX - (relX - panX) * zoomRatio;
			panY = relY - (relY - panY) * zoomRatio;

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

	// Accepts local coordinates now
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
}
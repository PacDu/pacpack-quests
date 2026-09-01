package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.RewardType;
import fr.pacdu.pacpackquests.TaskType;
import fr.pacdu.pacpackquests.network.ClaimQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
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

	// Node now includes parents and lock status
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

		// Define the center of the quest window to place the (0,0) coordinate
		int screenCenterX = startX + (windowWidth / 2);
		int screenCenterY = startY + (windowHeight / 2);
		int gridSpacing = 48; // Pixel distance between grid units

		for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
			if (quest.category().equals(selectedCategory)) {

				// Convert JSON grid coordinates to actual screen pixels
				// Assuming you added displayX() and displayY() to QuestDefinition
				int screenX = screenCenterX + (quest.displayX() * gridSpacing);
				int screenY = screenCenterY + (quest.displayY() * gridSpacing);

				// Determine if quest is locked based on parent claim status
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
						quest.id(), quest.title(), screenX, screenY, quest.type(), quest.target(),
						quest.icon(), quest.reward(), quest.rewardType(), quest.rewardAmount(),
						quest.parents(), locked
				));
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.fill(startX, startY, startX + windowWidth, startY + windowHeight, 0xAA000000);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, startY + 10, -1);

		// Draw Left Navigation Tabs
		int currentTabY = startY + 20;
		for (String category : categories) {
			boolean isSelected = category.equals(selectedCategory);
			int color = isSelected ? 0xFF666666 : 0xFF333333;
			int tabX = startX - tabWidth;

			context.fill(tabX, currentTabY, tabX + tabWidth, currentTabY + tabHeight, color);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(category.toUpperCase()), tabX + tabWidth / 2, currentTabY + 6, isSelected ? 0xFFFFFFFF : 0xFFAAAAAA);

			currentTabY += tabHeight + 5;
		}

		// --- DRAW LINES (UNDERNEATH NODES) ---
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

			int bgColor = 0xFF333333; // Default dark grey
			if (quest.isLocked()) {
				bgColor = 0xFF221111; // Very dark red for locked
			} else if (claimed) {
				bgColor = 0xFF225522; // Muted green
			} else if (progress >= requiredAmount) {
				bgColor = 0xFF555522; // Muted gold
			}

			context.fill(quest.x() - 4, quest.y() - 4, quest.x() + 20, quest.y() + 20, bgColor);

			// Draw objective icon
			context.drawItem(quest.icon(), quest.x(), quest.y());

			String progressText;
			int textColor;
			if (quest.isLocked()) {
				progressText = "gui.pacpack-quests.locked";
				textColor = 0xFFFF5555;
			} else if (claimed) {
				progressText = "gui.done";
				textColor = 0xFF55FF55;
			} else {
				progressText = progress + "/" + requiredAmount;
				textColor = progress >= requiredAmount ? 0xFFFFFF55 : 0xFFAAAAAA;
			}

			context.drawText(this.textRenderer, Text.translatable(progressText), quest.x() - 2, quest.y() + 22, textColor, true);

			// Rich tooltip
			if (isHovering(quest, mouseX, mouseY)) {
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
		}
	}

	// Draws an orthogonal "elbow" line between two quests using basic fill
	private void drawConnectionLine(DrawContext context, QuestNode parent, QuestNode child) {
		// Center of the 24x24 box is x+8, y+8
		int px = parent.x() + 8;
		int py = parent.y() + 8;
		int cx = child.x() + 8;
		int cy = child.y() + 8;

		int lineColor = child.isLocked() ? 0xFF444444 : 0xFF88AA88; // Greenish if unlocked, grey if locked
		int thickness = 2;

		// Draw horizontal line from Parent to Child's X
		context.fill(Math.min(px, cx), py - thickness/2, Math.max(px, cx) + thickness/2, py + thickness/2, lineColor);

		// Draw vertical line from Parent's Y to Child's Y
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
		if (click.button() == 0) { // Left click
			double mouseX = click.x();
			double mouseY = click.y();

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

			for (QuestNode quest : questList) {
				if (isHovering(quest, (int) mouseX, (int) mouseY)) {

					if (quest.isLocked()) return true; // Do nothing if locked

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
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (PacPackQuestsClient.openQuestMenuKey.matchesKey(input)) {
			this.close();
			return true;
		}
		return super.keyPressed(input);
	}

	private boolean isHovering(QuestNode quest, int mouseX, int mouseY) {
		return mouseX >= quest.x() - 4 && mouseX <= quest.x() + 20 &&
				mouseY >= quest.y() - 4 && mouseY <= quest.y() + 20;
	}

	private String getTranslatedTargetName(String target) {
		if (target.startsWith("#")) {
			// It's a Tag (e.g., #minecraft:logs)
			// 1. We create a custom translation key: "tag.minecraft.logs"
			String tagKey = "tag." + target.substring(1).replace(":", ".");

			// 2. We check if you provided a translation for it in your mod's lang file
			if (I18n.hasTranslation(tagKey)) {
				return I18n.translate(tagKey);
			} else {
				// 3. Fallback: Format the raw name nicely (e.g., "logs" -> "Any Logs")
				String path = Identifier.of(target.substring(1)).getPath();
				String formatted = path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
				return "Any " + formatted;
			}
		} else {
			// It's a specific Item (e.g., minecraft:oak_planks)
			// We fetch the Item from the registry and ask for its native translated name
			Item item = Registries.ITEM.get(Identifier.of(target));
			return item.getName().getString(); // Returns "Oak Planks" or "Planches de chêne"
		}
	}
}
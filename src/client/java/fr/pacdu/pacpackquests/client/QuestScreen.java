package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import fr.pacdu.pacpackquests.network.ClaimQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class QuestScreen extends Screen {

	private int windowWidth, windowHeight, startX, startY;
	private final List<QuestNode> questList = new ArrayList<>();

	private final List<String> categories = new ArrayList<>();
	private static String selectedCategory = "main";
	private final int tabWidth = 70;
	private final int tabHeight = 20;

	// Streamlined record for minimal node UI
	record QuestNode(String id, String title, int x, int y, ItemStack icon, ItemStack reward, int rewardAmount) {}

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

		// Build unique categories list
		categories.clear();
		for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
			if (!categories.contains(quest.category())) {
				categories.add(quest.category());
			}
		}

		// Force "main" category to always be at the top
		if (categories.contains("main")) {
			categories.remove("main");
			categories.addFirst("main");
		} else if (categories.isEmpty()) {
			categories.add("main");
		}

		// Fallback if the previously selected category was deleted/no longer exists
		if (!categories.contains(selectedCategory)) {
			selectedCategory = categories.getFirst();
		}

		refreshQuests();
	}

	private void refreshQuests() {
		questList.clear();
		int offsetX = 0;
		int offsetY = 0;
		int maxPerRow = 6; // Grid layout configuration to prevent overflowing horizontally
		int spacing = 36;

		int index = 0;
		for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
			if (quest.category().equals(selectedCategory)) {

				int x = startX + 30 + (index % maxPerRow) * spacing;
				int y = startY + 40 + (index / maxPerRow) * spacing;

				questList.add(new QuestNode(
						quest.id(), quest.title(), x, y,
						quest.icon(), quest.reward(), quest.rewardAmount()
				));
				index++;
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		// Draw main background and window title
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

		// Draw minimal quest nodes
		for (QuestNode quest : questList) {
			int current = PacPackQuestsClient.CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
			int requiredAmount = PacPackQuestsClient.CLIENT_DEFINITIONS.get(quest.id()).requiredAmount();
			boolean claimed = PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(quest.id(), false);

			// Determine border/background color based on quest state
			int bgColor = 0xFF333333; // Default dark grey
			if (claimed) {
				bgColor = 0xFF225522; // Muted green for completed/claimed
			} else if (current >= requiredAmount) {
				bgColor = 0xFF555522; // Muted gold/yellow if ready to claim
			}

			// Draw clean 24x24 slot background box
			context.fill(quest.x() - 4, quest.y() - 4, quest.x() + 20, quest.y() + 20, bgColor);

			// Draw objective icon
			context.drawItem(quest.icon(), quest.x(), quest.y());

			// Render mini progress label underneath the node slot
			String progressText;
			int textColor;
			if (claimed) {
				progressText = "Done";
				textColor = 0xFF55FF55;
			} else {
				progressText = current + "/" + requiredAmount;
				textColor = current >= requiredAmount ? 0xFFFFFF55 : 0xFFAAAAAA;
			}

			context.drawText(this.textRenderer, Text.literal(progressText), quest.x() - 2, quest.y() + 22, textColor, true);

			// Render rich tooltip on mouse hover
			if (isHovering(quest, mouseX, mouseY)) {
				List<Text> tooltip = new ArrayList<>();
				tooltip.add(Text.literal(quest.title()).formatted(Formatting.GOLD, Formatting.BOLD));

				if (claimed) {
					tooltip.add(Text.literal("Status: Completed").formatted(Formatting.GREEN));
				} else if (current >= requiredAmount) {
					tooltip.add(Text.literal("Status: Ready to Claim!").formatted(Formatting.YELLOW));
				} else {
					tooltip.add(Text.literal("Progress: " + current + " / " + requiredAmount).formatted(Formatting.GRAY));
				}

				// Append reward description cleanly inside the tooltip
				tooltip.add(Text.literal("Reward: " + quest.rewardAmount() + "x ").append(quest.reward().getName()).formatted(claimed ? Formatting.GREEN : Formatting.AQUA));

				context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0) { // Left click
			double mouseX = click.x();
			double mouseY = click.y();

			// Check tab selection clicks
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

			// Check quest node clicks to claim rewards
			for (QuestNode quest : questList) {
				if (isHovering(quest, (int) mouseX, (int) mouseY)) {
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

	// Precise hitbox matching the 24x24 icon slot
	private boolean isHovering(QuestNode quest, int mouseX, int mouseY) {
		return mouseX >= quest.x() - 4 && mouseX <= quest.x() + 20 &&
				mouseY >= quest.y() - 4 && mouseY <= quest.y() + 20;
	}
}
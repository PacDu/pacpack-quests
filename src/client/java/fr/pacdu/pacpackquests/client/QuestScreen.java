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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class QuestScreen extends Screen {

	private int windowWidth, windowHeight, startX, startY;
	private final List<QuestNode> QuestList = new ArrayList<>();

	// Tab system variables
	private final List<String> categories = new ArrayList<>();
	private final int tabWidth = 70;
	private final int tabHeight = 20;
	private static String selectedCategory = "main";

	record QuestNode(String id, String title, int x, int y, ItemStack icon) {}

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

		// Shift main window right to leave space for tabs on the left
		windowWidth = this.width - 140;
		windowHeight = this.height - 80;
		startX = (this.width - windowWidth) / 2 + 40;
		startY = (this.height - windowHeight) / 2 - 10;

		// Close button
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
		if (categories.contains("main")) {
			categories.remove("main");
			categories.addFirst("main"); // L'insère tout en haut
		} else if (categories.isEmpty()) {
			categories.add("main");
		}
		if (!categories.contains(selectedCategory)) {
			selectedCategory = categories.getFirst();
		}

		refreshQuests();
	}

	// Separated from init() so switching tabs doesn't rebuild the entire screen
	private void refreshQuests() {
		QuestList.clear();
		int offsetX = 0;
		for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
			if (quest.category().equals(selectedCategory)) {
				QuestList.add(new QuestNode(quest.id(), quest.title(), startX + 30 + offsetX, startY + 40, quest.icon()));
				offsetX += 50;
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		// Background and title
		context.fill(startX, startY, startX + windowWidth, startY + windowHeight, 0xAA000000);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, startY + 10, -1);

		// Draw Tabs
		int currentTabY = startY + 20;
		for (String category : categories) {
			boolean isSelected = category.equals(selectedCategory);
			int color = isSelected ? 0xFF666666 : 0xFF333333;
			int tabX = startX - tabWidth;

			context.fill(tabX, currentTabY, tabX + tabWidth, currentTabY + tabHeight, color);
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(category.toUpperCase()), tabX + tabWidth / 2, currentTabY + 6, isSelected ? 0xFFFFFFFF : 0xFFAAAAAA);

			currentTabY += tabHeight + 5;
		}

		// Draw quest nodes
		for (QuestNode quest : QuestList) {
			context.fill(quest.x() - 4, quest.y() - 4, quest.x() + 20, quest.y() + 20, 0xFF444444);
			context.drawItem(quest.icon(), quest.x(), quest.y());

			int current = PacPackQuestsClient.CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
			int requiredAmount = PacPackQuestsClient.CLIENT_DEFINITIONS.get(quest.id()).requiredAmount();
			boolean claimed = PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(quest.id(), false);

			String progressText;
			int color;

			if (claimed) {
				progressText = "Claimed!";
				color = 0xFF55FF55;
			} else {
				progressText = current + " / " + requiredAmount;
				color = current >= requiredAmount ? 0xFFFFFF55 : 0xFFFF5555;
			}

			context.drawText(this.textRenderer, Text.literal(progressText), quest.x() - 10, quest.y() + 25, color, true);

			if (isHovering(quest, mouseX, mouseY)) {
				context.drawTooltip(this.textRenderer, Text.literal(quest.title()), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0) { // Clic gauche
			double mouseX = click.x();
			double mouseY = click.y();

			// 1. Vérifier si un onglet a été cliqué
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

			// 2. Vérifier si une quête a été cliquée
			for (QuestNode quest : QuestList) {
				if (isHovering(quest, (int) mouseX, (int) mouseY)) {
					int currentProg = PacPackQuestsClient.CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
					boolean isClaimed = PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(quest.id(), false);

					if (currentProg >= 1 && !isClaimed) {
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
		// Check if the pressed key matches our custom keybinding
		if (PacPackQuestsClient.openQuestMenuKey.matchesKey(input)) {
			this.close(); // Close the screen
			return true;  // Tell the game we handled this input
		}

		// Fallback to default behavior (allows closing with the ESC key)
		return super.keyPressed(input);
	}

	private boolean isHovering(QuestNode quest, int mouseX, int mouseY) {
		return mouseX >= quest.x() - 4 && mouseX <= quest.x() + 20 &&
				mouseY >= quest.y() - 4 && mouseY <= quest.y() + 20;
	}
}
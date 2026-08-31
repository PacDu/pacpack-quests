package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.PacPackQuests;
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

import java.util.ArrayList;
import java.util.List;

public class QuestScreen extends Screen {

	// Variables globales pour la fenêtre
	private int windowWidth, windowHeight, startX, startY;

	// Notre liste de quêtes de test
	private final List<QuestNode> QuestList = new ArrayList<>();

	// Un "record" Java est parfait pour stocker des données simples immuables
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

		// Dimensions of the Background Window
		windowWidth = this.width - 80;
		windowHeight = this.height - 80;
		startX = (this.width - windowWidth) / 2;
		startY = (this.height - windowHeight) / 2 - 10;

		// Close button
		int buttonWidth = 100;
		int buttonHeight = 20;
		this.addDrawableChild(
				ButtonWidget.builder(Text.translatable("spectatorMenu.close"), button -> this.close())
						.dimensions((this.width - buttonWidth) / 2, this.height - 35, buttonWidth, buttonHeight)
						.build()
		);

		// The list is cleared first when the screen is resized
		QuestList.clear();

		int offsetX = 0;
		for (QuestDefinition quest : PacPackQuestsClient.CLIENT_DEFINITIONS.values()) {
			// Dynamically add quest nodes based on server-synced definitions
			QuestList.add(new QuestNode(quest.id(), quest.title(), startX + 30 + offsetX, startY + 40, quest.icon()));
			offsetX += 50;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		// Drawing of the background and title
		context.fill(startX, startY, startX + windowWidth, startY + windowHeight, 0xAA000000);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, startY + 10, -1);

		// Drawing quest nodes
		for (QuestNode quest : QuestList) {
			context.fill(quest.x() - 4, quest.y() - 4, quest.x() + 20, quest.y() + 20, 0xFF444444);
			context.drawItem(quest.icon(), quest.x(), quest.y());

			// We retrieve the progress received from the network
			int current = PacPackQuestsClient.CLIENT_PROGRESS.getOrDefault(quest.id(), 0);
			int requiredAmount = PacPackQuestsClient.CLIENT_DEFINITIONS.getOrDefault(quest.id(), null).requiredAmount();
			boolean claimed = PacPackQuestsClient.CLIENT_CLAIMED.getOrDefault(quest.id(), false);

			String progressText;
			int color;

			if (claimed) {
				progressText = "Claimed !";
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
		if (click.button() == 0) { // Left click
			for (QuestNode quest : QuestList) {
				if (isHovering(quest, (int) click.x(), (int) click.y())) {
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

	// Utility method to check if the mouse is over the quest area (24x24 pixels)
	private boolean isHovering(QuestNode quest, int mouseX, int mouseY) {
		return mouseX >= quest.x - 4 && mouseX <= quest.x + 20 &&
				mouseY >= quest.y - 4 && mouseY <= quest.y + 20;
	}
}
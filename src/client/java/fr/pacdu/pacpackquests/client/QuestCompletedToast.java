package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class QuestCompletedToast implements Toast {

    // The vanilla texture used for advancements
    private static final Identifier BACKGROUND_TEXTURE = Identifier.of("minecraft", "toast/advancement");

    private final QuestDefinition quest;
    private boolean soundPlayed = false;

    private Visibility visibility = Visibility.SHOW;
    private long startTime = -1;

    public QuestCompletedToast(QuestDefinition quest) {
        this.quest = quest;
    }

    @Override
    public Visibility getVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        // Initialize start time on the first frame
        if (this.startTime == -1) {
            this.startTime = time;
        }

        // Hide the toast after 5 seconds (5000 ms)
        if (time - this.startTime >= 5000L) {
            this.visibility = Visibility.HIDE;
        }
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        // Play the satisfying achievement sound only once
        if (!this.soundPlayed) {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            }
            this.soundPlayed = true;
        }

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, 0, 0, this.getWidth(), this.getHeight());

        // Draw Title ("Quest Completed!" in yellow)
        Text title = Text.translatable("toast.pacpack-quests.completed").formatted(Formatting.YELLOW, Formatting.BOLD);
        context.drawText(textRenderer, title, 30, 7, 0xFFFFFF00, false);

        // Draw Subtitle (The actual name of the quest in white)
        context.drawText(textRenderer, Text.literal(quest.title()), 30, 18, 0xFFFFFFFF, false);

        // Draw the quest icon on the left side
        context.drawItemWithoutEntity(quest.icon(), 8, 8);
    }
}
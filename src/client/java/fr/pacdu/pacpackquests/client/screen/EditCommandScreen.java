package fr.pacdu.pacpackquests.client.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jspecify.annotations.Nullable;

import java.awt.*;
import java.util.function.BiConsumer;

public class EditCommandScreen extends Screen {

    private final Screen parent;
    private final String previousCommand;
    private final String previousDescription;
    private final BiConsumer<String, String> onEdited;

    private TextFieldWidget commandField, commandDescriptionField;
    private ChatInputSuggestor commandSuggestor;

    protected EditCommandScreen(Screen parent, String previousCommand, String previousDescription, BiConsumer<String, String> onEdited) {
        super(Text.translatable("gui.pacpack-quests.edit_command"));
        this.parent = parent;
        this.previousCommand = previousCommand;
        this.previousDescription = previousDescription;
        this.onEdited = onEdited;
    }

    @Override
    protected void init() {
        super.init();

        int commandFieldWidth = (int) Math.clamp(this.width * 0.8, 200, 2000);

        this.commandField = new TextFieldWidget(this.textRenderer, (this.width - commandFieldWidth) / 2, 50, commandFieldWidth, 20, Text.empty());
        this.commandField.setMaxLength(16384);
        this.commandField.setText(this.previousCommand);
        this.commandField.setPlaceholder(Text.translatable("form.pacpack-quests.reward_command_placeholder").formatted(Formatting.DARK_GRAY));
        this.commandField.setChangedListener(text -> this.commandSuggestor.refresh());
        this.addDrawableChild(this.commandField);

        this.commandSuggestor = new ChatInputSuggestor(this.client, this, this.commandField, this.textRenderer, true, true, 0, 7, false, Integer.MIN_VALUE);
        this.commandSuggestor.setWindowActive(true);
        this.commandSuggestor.refresh();
        this.setFocused(this.commandField);

        this.commandDescriptionField = new TextFieldWidget(this.textRenderer, (this.width - commandFieldWidth) / 2, this.commandField.getY() + 120, commandFieldWidth, 20, Text.empty());
        this.commandDescriptionField.setMaxLength(256);
        this.commandDescriptionField.setText(this.previousDescription != null ? this.previousDescription : "");
        this.addDrawableChild(this.commandDescriptionField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.pacpack-quests.cancel"), button -> this.client.setScreen(this.parent))
                .dimensions((this.width - 100) / 2 - 60, this.height - 30, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.pacpack-quests.validate"), button -> {
            this.onEdited.accept(this.commandField.getText(), this.commandDescriptionField.getText());
            this.client.setScreen(this.parent);
        }).dimensions((this.width - 100) / 2 + 60, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);

        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.command").append(Text.literal(" *").formatted(Formatting.RED)), this.commandField.getX(), this.commandField.getY() - 10, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("form.pacpack-quests.command_description"), this.commandDescriptionField.getX(), this.commandDescriptionField.getY() - 10, 0xFFFFFFFF, true);

        if (this.commandField.isFocused()) {
            this.commandSuggestor.render(context, mouseX, mouseY);
        } else {
            this.commandField.setSuggestion(null);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (this.commandField.isFocused() && this.commandSuggestor.keyPressed(input)) {
            return true;
        }
        return super.keyPressed(input);
    }
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.commandField.isFocused() && this.commandSuggestor.mouseClicked(click)) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.commandField.isFocused() && this.commandSuggestor.mouseScrolled(verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void setFocused(Element focused) {
        super.setFocused(focused);
        if (this.commandSuggestor != null) {
            boolean isCommandFocused = (focused == this.commandField);
            this.commandSuggestor.setWindowActive(isCommandFocused);
            if (isCommandFocused) {
                this.commandSuggestor.refresh();
            } else {
                this.commandField.setSuggestion(null);
            }
        }
    }
}

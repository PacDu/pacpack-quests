package fr.pacdu.pacpackquests.client.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.awt.*;
import java.util.function.Consumer;

public class EditCommandScreen extends Screen {

    private final Screen parent;
    private final String previousCommand;
    private final Consumer<String> onEdited;

    private TextFieldWidget commandField;
    private ChatInputSuggestor commandSuggestor;

    protected EditCommandScreen(Screen parent, String previousCommand, Consumer<String> onEdited) {
        super(Text.translatable("gui.pacpack-quests.edit_command"));
        this.parent = parent;
        this.previousCommand = previousCommand;
        this.onEdited = onEdited;
    }

    @Override
    protected void init() {
        super.init();

        int commandFieldWidth = (int) Math.clamp(this.width * 0.8, 200, 2000);

        this.commandField = new TextFieldWidget(this.textRenderer, (this.width - commandFieldWidth) / 2, 50, commandFieldWidth, 20, Text.empty());
        this.commandField.setMaxLength(512);
        this.commandField.setText(this.previousCommand);
        this.commandField.setPlaceholder(Text.translatable("form.pacpack-quests.reward_command_placeholder").formatted(Formatting.DARK_GRAY));
        this.commandField.setChangedListener(text -> this.commandSuggestor.refresh());
        this.addDrawableChild(this.commandField);
        this.setFocused(this.commandField);

        this.commandSuggestor = new ChatInputSuggestor(this.client, this, this.commandField, this.textRenderer, true, true, 0, 7, false, Integer.MIN_VALUE);
        this.commandSuggestor.setWindowActive(true);
        this.commandSuggestor.refresh();

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.pacpack-quests.cancel"), button -> this.client.setScreen(this.parent))
                .dimensions((this.width - 100) / 2 - 60, this.height - 30, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.pacpack-quests.validate"), button -> {
            this.onEdited.accept(this.commandField.getText());
            this.client.setScreen(this.parent);
        }).dimensions((this.width - 100) / 2 + 60, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 0xFFFFFFFF);

        this.commandSuggestor.render(context, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return this.commandSuggestor.keyPressed(input) || super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return this.commandSuggestor.mouseClicked(click) ||  super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.commandSuggestor.mouseScrolled(verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}

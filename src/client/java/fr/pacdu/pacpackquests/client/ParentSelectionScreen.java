package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.QuestDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

import static fr.pacdu.pacpackquests.client.PacPackQuestsClient.CLIENT_DEFINITIONS;

public class ParentSelectionScreen extends AbstractQuestGridScreen {

    private final EditQuestScreen parentScreen;
    private final String questId; // The quest we are editing (so we can't select it as a parent)
    private final String currentCategory;
    private final List<String> selectedParents;

    // Use the GridNode interface defined in AbstractQuestGridScreen
    record SelectionNode(String id, String title, int x, int y, List<String> parents, ItemStack icon) implements GridNode {
        @Override
        public boolean isLocked() {
            return false;
        }
    }
    
    private final List<SelectionNode> nodes = new ArrayList<>();

    private final String tempTitle;
    private final int tempX;
    private final int tempY;
    private final String tempIcon;

    public ParentSelectionScreen(EditQuestScreen parentScreen, String questId, String currentCategory, List<String> initialParents, String tempTitle, int tempX, int tempY, String tempIcon) {
        super(Text.translatable("gui.pacpack-quests.select_parents"));
        this.parentScreen = parentScreen;
        this.questId = questId;
        this.currentCategory = currentCategory;
        this.selectedParents = new ArrayList<>(initialParents);
        this.tempTitle = tempTitle;
        this.tempX = tempX;
        this.tempY = tempY;
        this.tempIcon = tempIcon;
    }

    @Override
    protected void init() {
        super.init(); // This now identical to QuestScreen bounds
        
        nodes.clear();
        boolean foundSelf = false;
        for (QuestDefinition def : CLIENT_DEFINITIONS.values()) {
            if (def.category().equals(this.currentCategory)) {
                if (def.id().equals(this.questId)) foundSelf = true;
                int nodeX = canvasOffsetX + (def.displayX() * gridSpacing) + 8;
                int nodeY = canvasOffsetY + (def.displayY() * gridSpacing) + 8;
                nodes.add(new SelectionNode(def.id(), def.title(), nodeX, nodeY, def.parents(), def.icon().getItem().getDefaultStack()));
            }
        }

        if (!foundSelf && this.questId != null) {
            int nodeX = canvasOffsetX + (this.tempX * gridSpacing) + 8;
            int nodeY = canvasOffsetY + (this.tempY * gridSpacing) + 8;
            ItemStack iconStack = Items.STONE.getDefaultStack();
            if (this.tempIcon != null) {
                Identifier id = Identifier.tryParse(this.tempIcon);
                if (id != null && Registries.ITEM.containsId(id)) {
                    iconStack = Registries.ITEM.get(id).getDefaultStack();
                }
            }
            nodes.add(new SelectionNode(this.questId, this.tempTitle != null && !this.tempTitle.trim().isEmpty() ? this.tempTitle : this.questId, nodeX, nodeY, this.selectedParents, iconStack));
        }

        int buttonWidth = 100;
        int buttonHeight = 20;

        // "Done" Button positioned exactly where "Claim All" button is in QuestScreen for symmetry
        this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.done"), button -> {
                this.parentScreen.updateParents(this.selectedParents);
                this.client.setScreen(this.parentScreen);
            })
            .dimensions((this.width / 2) - buttonWidth / 2, this.height - 35, buttonWidth, buttonHeight)
            .build()
        );
    }

    @Override
    protected List<? extends GridNode> getNodes() {
        return this.nodes;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (click.button() == 0) {
            double mouseX = click.x(); double mouseY = click.y(); double localMouseX = (mouseX - startX - panX) / zoom;
            double localMouseY = (mouseY - startY - panY) / zoom;
            boolean isMouseInWindow = mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight;

            if (isMouseInWindow) {
                for (SelectionNode node : nodes) {
                    if (isHoveringNode(node.x(), node.y(), localMouseX, localMouseY)) {
                        // Prevent selecting the quest itself or its descendants as a parent
                        if (node.id().equals(questId) || isDescendant(node.id(), questId, new java.util.HashSet<>())) return true;

                        if (selectedParents.contains(node.id())) {
                            selectedParents.remove(node.id());
                        } else {
                            selectedParents.add(node.id());
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Share the background framing logic with QuestScreen
        drawBackgroundFrame(context, Text.translatable("gui.pacpack-quests.select_parents"));
        
        // Category tab logic
        int tabX = startX - tabWidth;
        int tabY = startY + 20;
        context.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, 0xFF666666);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(currentCategory.toUpperCase()), tabX + tabWidth / 2, tabY + 6, 0xFFFFFFFF);

        context.enableScissor(startX, startY, startX + windowWidth, startY + windowHeight);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) (startX + panX), (float) (startY + panY));
        context.getMatrices().scale(zoom, zoom);

        // Draw connections between nodes using the abstract class logic
        for (SelectionNode node : nodes) {
            if (node.parents() != null && !node.id().equals(questId)) {
                for (String parentId : node.parents()) {
                    SelectionNode parentNode = getNodeById(parentId);
                    if (parentNode != null) {
                        drawConnectionLine(context, parentNode, node, 0xFF444444, 0xFF888888, false);
                    }
                }
            }
        }

        // Draw dynamically selected parents connections in bright green
        SelectionNode currentQuestNode = getNodeById(this.questId);
        if (currentQuestNode != null) {
            for (String parentId : this.selectedParents) {
                SelectionNode parentNode = getNodeById(parentId);
                if (parentNode != null) {
                    drawConnectionLine(context, parentNode, currentQuestNode, 0xFF44AA44, 0xFF88FF88, false);
                }
            }
        }

        // Render nodes
        double localMouseX = (mouseX - startX - panX) / zoom;
        double localMouseY = (mouseY - startY - panY) / zoom;
        boolean isMouseInWindow = mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight;

        SelectionNode hoveredNode = null;
        for (SelectionNode node : nodes) {
            renderNode(context, node, localMouseX, localMouseY);
            if (isMouseInWindow && isHoveringNode(node.x(), node.y(), localMouseX, localMouseY)) {
                hoveredNode = node;
            }
        }

        context.getMatrices().popMatrix();
        context.disableScissor();

        // Tooltip for hovering
        if (hoveredNode != null) {
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(Text.literal(hoveredNode.title()).formatted(Formatting.GOLD, Formatting.BOLD));
            tooltip.add(Text.literal(hoveredNode.id()).formatted(Formatting.GRAY));
            if (hoveredNode.id().equals(questId)) {
                tooltip.add(Text.translatable("gui.pacpack-quests.cannot_select_self").formatted(Formatting.RED));
            } else if (isDescendant(hoveredNode.id(), questId, new java.util.HashSet<>())) {
                tooltip.add(Text.translatable("gui.pacpack-quests.cannot_select_child").formatted(Formatting.RED));
            }
            context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private void renderNode(DrawContext context, SelectionNode node, double localMouseX, double localMouseY) { //
        boolean isSelected = selectedParents.contains(node.id());
        boolean isDescendant = isDescendant(node.id(), questId, new java.util.HashSet<>());
        int bgColor = 0xFF555555;
        
        if (isHoveringNode(node.x(), node.y(), localMouseX, localMouseY)) {
            bgColor = 0xFF777777;
        }

        if (node.id().equals(questId)) {
            bgColor = 0xFF333333;
            // Draw a subtle gold highlight border behind the node we are editing
            context.fill(node.x() - 6, node.y() - 6, node.x() + 22, node.y() + 22, 0xFFFFAA00);
            context.fill(node.x() - 5, node.y() - 5, node.x() + 21, node.y() + 21, 0xFF000000);
        } else if (isDescendant) {
            bgColor = 0xFF331111;
        }

        super.renderNodeBase(context, node, bgColor);

        if (isSelected) {
            context.fill(node.x() + 10, node.y() - 6, node.x() + 22, node.y() + 6, 0xFF000000); // outline
            context.fill(node.x() + 11, node.y() - 5, node.x() + 21, node.y() + 5, 0xFF00AA00); // background
            
            // Simple 90-degree checkmark
            context.fill(node.x() + 13, node.y() + 1, node.x() + 14, node.y() + 3, 0xFFFFFFFF);
            context.fill(node.x() + 14, node.y() + 2, node.x() + 15, node.y() + 4, 0xFFFFFFFF);
            context.fill(node.x() + 15, node.y() + 3, node.x() + 16, node.y() + 5, 0xFFFFFFFF);
            context.fill(node.x() + 16, node.y() + 2, node.x() + 17, node.y() + 4, 0xFFFFFFFF);
            context.fill(node.x() + 17, node.y() + 1, node.x() + 18, node.y() + 3, 0xFFFFFFFF);
            context.fill(node.x() + 18, node.y() + 0, node.x() + 19, node.y() + 2, 0xFFFFFFFF);
            context.fill(node.x() + 19, node.y() - 1, node.x() + 20, node.y() + 1, 0xFFFFFFFF);
        }
    }

    private boolean isDescendant(String potentialParentId, String targetAncestorId, java.util.Set<String> visited) {
        if (potentialParentId.equals(targetAncestorId)) return true;
        if (!visited.add(potentialParentId)) return false;

        fr.pacdu.pacpackquests.QuestDefinition def = fr.pacdu.pacpackquests.client.PacPackQuestsClient.CLIENT_DEFINITIONS.get(potentialParentId);
        if (def != null && def.parents() != null) {
            for (String parentId : def.parents()) {
                if (isDescendant(parentId, targetAncestorId, visited)) return true;
            }
        }
        return false;
    }

    private SelectionNode getNodeById(String id) {
        for (SelectionNode node : nodes) {
            if (node.id().equals(id)) return node;
        }
        return null;
    }
}

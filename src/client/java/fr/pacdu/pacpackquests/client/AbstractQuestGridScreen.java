package fr.pacdu.pacpackquests.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

public abstract class AbstractQuestGridScreen extends Screen {

    protected int windowWidth, windowHeight, startX, startY;
    
    // --- Camera & Canvas System ---
    protected static double panX = 0;
    protected static double panY = 0;
    protected static float zoom = 1.0f;
    protected final int gridSpacing = 48;
    protected final int canvasOffsetX = 20;
    protected final int canvasOffsetY = 20;
    
    protected final int tabWidth = 70;
    protected final int tabHeight = 20;

    protected AbstractQuestGridScreen(Text title) {
        super(title);
    }

    public interface GridNode {
        String id();
        String title();
        int x();
        int y();
        List<String> parents();
        ItemStack icon();
        boolean isLocked(); // Usually true if requirements not met
    }

    @Override
    protected void init() {
        super.init();
        // Exact layout from the original QuestScreen to ensure perfect match
        windowWidth = this.width - 2 * this.tabWidth;
        windowHeight = this.height - 80;
        startX = (this.width - windowWidth) / 2 + 40;
        startY = (this.height - windowHeight) / 2 - 10;
    }

    // --- INPUT HANDLING FOR PAN & ZOOM ---
    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        // If left click and we are hovering the canvas, we pan.
        // Child classes can intercept this beforehand if they want dragging nodes instead.
        if (click.x() >= startX && click.x() <= startX + windowWidth && click.y() >= startY && click.y() <= startY + windowHeight) {
            panX += deltaX;
            panY += deltaY;
            clampPanning();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight) {
            double oldZoom = zoom;
            zoom += (float) (verticalAmount * 0.15f);
            zoom = (float) Math.clamp(zoom, 0.3f, 2f);

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

    protected abstract List<? extends GridNode> getNodes();

    protected void clampPanning() {
        double currentMinPanX, currentMaxPanX;
        double currentMinPanY, currentMaxPanY;

        List<? extends GridNode> nodes = getNodes();

        if (nodes == null || nodes.isEmpty()) {
            currentMinPanX = currentMaxPanX = currentMinPanY = currentMaxPanY = 0;
        } else {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (GridNode node : nodes) {
                minX = Math.min(minX, node.x() - 4);
                maxX = Math.max(maxX, node.x() + 20);
                minY = Math.min(minY, node.y() - 4);
                maxY = Math.max(maxY, node.y() + 20);
            }

            int padding = 20;
            minX -= padding;
            maxX += padding;
            minY -= padding;
            maxY += padding;

            double boundX1 = -minX * zoom;
            double boundX2 = windowWidth - maxX * zoom;
            currentMinPanX = Math.min(boundX1, boundX2);
            currentMaxPanX = Math.max(boundX1, boundX2);

            double boundY1 = -minY * zoom;
            double boundY2 = windowHeight - maxY * zoom;
            currentMinPanY = Math.min(boundY1, boundY2);
            currentMaxPanY = Math.max(boundY1, boundY2);
        }

        panX = Math.clamp(panX, currentMinPanX, currentMaxPanX);
        panY = Math.clamp(panY, currentMinPanY, currentMaxPanY);
    }

    // --- RENDER HELPERS ---

    protected void drawBackgroundFrame(DrawContext context, Text titleText) {
        // Dark translucent background
        context.fill(startX, startY, startX + windowWidth, startY + windowHeight, 0xAA000000);
        // Title at the top
        if (titleText != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, titleText, this.width / 2, startY - 18, -1);
        }
    }

    protected void renderNodeBase(DrawContext context, GridNode node, int bgColor) {
        context.fill(node.x() - 4, node.y() - 4, node.x() + 20, node.y() + 20, bgColor);
        if (node.icon() != null) {
            context.drawItem(node.icon(), node.x(), node.y());
        }
    }

    protected void drawConnectionLine(DrawContext context, GridNode parent, GridNode child, int lineColor, int arrowColor, boolean isDragging) {
        int px = parent.x() + 8;
        int py = parent.y() + 8;
        int cx = child.x() + 8;
        int cy = child.y() + 8;

        if (isDragging) return;

        int thickness = 2;

        float dx = cx - px;
        float dy = cy - py;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.atan2(dy, dx);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) px, (float) py);
        context.getMatrices().rotate(angle);

        context.fill(0, -thickness / 2, (int) length, thickness / 2, lineColor);
        drawArrowChevron(context, (int) (length / 2) + 4, arrowColor);

        context.getMatrices().popMatrix();
    }

    private void drawArrowChevron(DrawContext context, int x, int color) {
        context.fill(x, -1, x + 2, 1, color);
        context.fill(x - 2, -3, x, -1, color);
        context.fill(x - 2, 1, x, 3, color);
        context.fill(x - 4, -5, x - 2, -3, color);
        context.fill(x - 4, 3, x - 2, 5, color);
    }

    protected boolean isHovering(int x, int y, int width, int height, double localMouseX, double localMouseY) {
        return localMouseX >= x && localMouseX <= x + width && localMouseY >= y && localMouseY <= y + height;
    }

    protected boolean isHoveringNode(int nodeX, int nodeY, double localMouseX, double localMouseY) {
        return isHovering(nodeX - 4, nodeY - 4, 24, 24, localMouseX, localMouseY);
    }
}

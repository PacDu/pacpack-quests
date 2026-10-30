package fr.pacdu.pacpackquests.client.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

public abstract class AbstractQuestGridScreen extends Screen {

    protected int windowWidth, windowHeight, startX, startY;

    protected static final int PADDING = 16;
    
    // --- Camera & Canvas System ---
    protected double panX = 0;
    protected double panY = 0;
    protected float zoom = 1.0f;
    protected final int gridSpacing = 48;
    protected final int canvasOffsetX = 20;
    protected final int canvasOffsetY = 20;

    protected int tabWidth = 90;
    protected final int tabHeight = 20;
    protected final int categoryNameMaxChars = 32;

    // Grid size for edit mode constraints
    protected int maxGridColumns = 20;
    protected int maxGridRows = 20;

    protected String draggedQuestId = null;

    private record Bounds(int minX, int maxX, int minY, int maxY) {}

    protected abstract List<? extends GridNode> getNodes();

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

        int maxTabWidth = this.textRenderer.getWidth("M".repeat(categoryNameMaxChars)) + 12;
        int winWidthPx = (this.client != null && this.client.getWindow() != null) ? this.client.getWindow().getFramebufferWidth() : 1920;
        float factor = Math.clamp((winWidthPx - 854f) / (1920f - 854f), 0.0f, 1.0f);
        this.tabWidth = Math.round(90 + factor * (maxTabWidth - 90));

        startX = this.tabWidth + 40;
        windowWidth = Math.max(100, this.width - startX - 30);
        windowHeight = this.height - 80;
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

    protected boolean forceFullGridBounds() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= startX && mouseX <= startX + windowWidth && mouseY >= startY && mouseY <= startY + windowHeight) {
            Bounds bounds = getActiveBounds();
            double oldZoom = zoom;
            zoom = Math.clamp(zoom + (float) (verticalAmount * 0.15f), getMinZoom(bounds), 2.0f);

            double zoomRatio = zoom / oldZoom;
            double relX = mouseX - startX;
            double relY = mouseY - startY;

            panX = relX - (relX - panX) * zoomRatio;
            panY = relY - (relY - panY) * zoomRatio;

            clampPanning(bounds);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    protected void clampPanning() {
        clampPanning(getActiveBounds());
    }

    private void clampPanning(Bounds bounds) {
        zoom = Math.clamp(zoom, getMinZoom(bounds), 2.0f);

        int gridMaxX = getGridMax(canvasOffsetX, maxGridColumns);
        int gridMaxY = getGridMax(canvasOffsetY, maxGridRows);

        if (bounds == null) {
            // Empty nodes in normal mode: center the pan on the grid
            panX = (windowWidth - gridMaxX * zoom) / 2.0;
            panY = (windowHeight - gridMaxY * zoom) / 2.0;
        } else {
            panX = clampAxis(panX, windowWidth, bounds.minX, bounds.maxX);
            panY = clampAxis(panY, windowHeight, bounds.minY, bounds.maxY);
        }
    }

    /**
     * Centers the camera on specific canvas coordinates (canvasX, canvasY).
     */
    public void centerOn(int canvasX, int canvasY) {
        zoom = 1.0f;
        panX = (windowWidth / 2.0) - canvasX * zoom;
        panY = (windowHeight / 2.0) - canvasY * zoom;
        clampPanning();
    }

    /**
     * Resets zoom to 1.0f and adaptively positions the camera:
     * - Horizontal: Centers if content fits; otherwise aligns leftmost quest to the left border with padding.
     * - Vertical: Centers on the leftmost quest(s) (or the whole tree if it fits), clamped within bounds so the starting quest never disappears.
     */
    public void resetCamera() {
        zoom = 1.0f;
        Bounds bounds = getNodeBounds();
        int gridMaxX = getGridMax(canvasOffsetX, maxGridColumns);
        int gridMaxY = getGridMax(canvasOffsetY, maxGridRows);

        if (bounds == null) {
            panX = (windowWidth - gridMaxX * zoom) / 2.0;
            panY = (windowHeight - gridMaxY * zoom) / 2.0;
        } else {
            List<? extends GridNode> nodes = getNodes();
            double contentWidth = (bounds.maxX - bounds.minX) * zoom;
            double contentHeight = (bounds.maxY - bounds.minY) * zoom;

            // 1. Horizontal axis (X)
            if (contentWidth <= windowWidth) {
                panX = (windowWidth - (bounds.minX + bounds.maxX) * zoom) / 2.0;
            } else {
                panX = -bounds.minX * zoom;
            }

            // 2. Vertical axis (Y)
            if (contentHeight <= windowHeight) {
                panY = (windowHeight - (bounds.minY + bounds.maxY) * zoom) / 2.0;
            } else if (nodes != null && !nodes.isEmpty()) {
                // When height exceeds window: align to the topmost quest among the leftmost starting quests
                int minX = Integer.MAX_VALUE;
                for (GridNode node : nodes) {
                    minX = Math.min(minX, node.x());
                }

                int minLeftY = Integer.MAX_VALUE;
                for (GridNode node : nodes) {
                    if (node.x() == minX) {
                        minLeftY = Math.min(minLeftY, node.y());
                    }
                }

                int topGutter = Math.max(0, minLeftY - 4 - PADDING);
                panY = -topGutter * zoom;
            } else {
                panY = -bounds.minY * zoom;
            }
        }
        clampPanning();
    }

    /**
     * Computes the bounding box of current nodes, including padding.
     * Returns null if no nodes exist.
     */
    protected Bounds getNodeBounds() {
        List<? extends GridNode> nodes = getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (GridNode node : nodes) {
            minX = Math.min(minX, node.x());
            maxX = Math.max(maxX, node.x());
            minY = Math.min(minY, node.y());
            maxY = Math.max(maxY, node.y());
        }

        int gridMaxX = getGridMax(canvasOffsetX, maxGridColumns);
        int gridMaxY = getGridMax(canvasOffsetY, maxGridRows);

        return new Bounds(
                Math.max(0, minX - 4 - PADDING),
                Math.min(gridMaxX, maxX + 20 + PADDING),
                Math.max(0, minY - 4 - PADDING),
                Math.min(gridMaxY, maxY + 20 + PADDING)
        );
    }

    /**
     * Computes the active bounding box once:
     * - Full grid bounds if forceFullGridBounds() is true
     * - Clamped node bounds in normal mode
     * - null if there are no visible nodes in normal mode
     */
    private Bounds getActiveBounds() {
        if (forceFullGridBounds()) {
            int gridMaxX = getGridMax(canvasOffsetX, maxGridColumns);
            int gridMaxY = getGridMax(canvasOffsetY, maxGridRows);
            return new Bounds(0, gridMaxX, 0, gridMaxY);
        }
        return getNodeBounds();
    }

    private int getGridMax(int offset, int count) {
        return offset + (count - 1) * gridSpacing + 28 + PADDING;
    }

    /**
     * Calculates the minimum allowed zoom so the user can zoom out to see the entire active area,
     * while always allowing at least 1.0f scale.
     */
    private float getMinZoom(Bounds b) {
        int width = b != null ? b.maxX - b.minX : getGridMax(canvasOffsetX, maxGridColumns);
        int height = b != null ? b.maxY - b.minY : getGridMax(canvasOffsetY, maxGridRows);

        float fitZoom = Math.min((float) windowWidth / Math.max(1, width), (float) windowHeight / Math.max(1, height));
        return Math.clamp(fitZoom, 0.05f, 1.0f);
    }

    /**
     * Computes and clamps the pan value for a single axis (X or Y),
     * restricting it to the active bounds without exceeding the grid bounds.
     */
    private double clampAxis(double pan, int windowSize, int nodeMin, int nodeMax) {
        double contentSize = (nodeMax - nodeMin) * zoom;
        if (contentSize <= windowSize) {
            return (windowSize - (nodeMin + nodeMax) * zoom) / 2.0;
        }

        double minPan = windowSize - nodeMax * zoom;
        double maxPan = -nodeMin * zoom;

        return Math.clamp(pan, minPan, maxPan);
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
        return localMouseX >= x && localMouseX <= x + width && localMouseY >= y && localMouseY <= y + height && draggedQuestId == null;
    }

    protected boolean isHoveringNode(int nodeX, int nodeY, double localMouseX, double localMouseY) {
        return isHovering(nodeX - 4, nodeY - 4, 24, 24, localMouseX, localMouseY);
    }
}

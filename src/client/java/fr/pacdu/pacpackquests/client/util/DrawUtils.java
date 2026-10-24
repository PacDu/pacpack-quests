package fr.pacdu.pacpackquests.client.util;

import net.minecraft.client.gui.DrawContext;

/**
 * A small utility class for common custom drawings like checkmarks.
 */
public class DrawUtils {
    /**
     * Draw a green 90-degree checkmark with a clean cardinal (non-diagonal) white outline.
     * Sizes below 7 may not render correctly.
     *
     * @param context The context where to draw.
     * @param x       The x start position.
     * @param y       The y start position.
     * @param size    The total size of the checkmark (width and height).
     */
    public static void drawCheckmark(DrawContext context, int x, int y, int size) {
        if (size <= 0) return;

        // 1. Compute dimensions and bottom vertex coordinates (xv, yv)
        int margin = size <= 6 ? 0 : 1;
        int totalW = Math.max(2, size - 1 - 2 * margin);
        int l1 = Math.max(1, (int) Math.round(totalW * 0.3));
        int l2 = Math.max(1, totalW - l1);

        int xv = margin + l1;
        int thickness = size >= 14 ? 3 : 2;
        int topOffset = Math.max(0, (size - (l2 + thickness + 1)) / 2);
        int yv = Math.min(size - 2, topOffset + l2 + thickness);

        // 2. Render column by column from the left outline cap (-l1 - 1) to the right cap (l2 + 1)
        for (int dx = -l1 - 1; dx <= l2 + 1; dx++) {
            boolean isSideCap = dx < -l1 || dx > l2;
            int px = xv + dx;
            int py = yv - (isSideCap ? (dx < 0 ? l1 : l2) : Math.abs(dx));

            if (px >= 0 && px < size && py >= 0 && py < size) {
                int yTop = Math.max(0, py - thickness + 1);

                // Draw vertical white strip (1px higher/lower on main columns, exact height on side caps)
                int whiteTop = isSideCap ? yTop : Math.max(0, yTop - 1);
                int whiteBot = isSideCap ? py + 1 : Math.min(size, py + 2);
                context.fill(x + px, y + whiteTop, x + px + 1, y + whiteBot, 0xFFFFFFFF);

                // Draw vertical green strip on top for the checkmark body
                if (!isSideCap) {
                    context.fill(x + px, y + yTop, x + px + 1, y + py + 1, 0xFF00AA00);
                }
            }
        }
    }
}
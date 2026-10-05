package fr.pacdu.pacpackquests.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public class ScrollbarWidget {
    private int x, y, width, height;
    private double scrollAmount;
    private double maxScroll;
    private double visibleContent;
    private final boolean smoothScrolling;
    private final double stepSize;
    private boolean isDragging;
    
    public ScrollbarWidget(int x, int y, int width, int height, double stepSize, boolean smoothScrolling) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.stepSize = stepSize;
        this.smoothScrolling = smoothScrolling;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public void setMaxScroll(double maxScroll, double visibleContent) {
        this.visibleContent = visibleContent;
        this.maxScroll = Math.max(0, maxScroll);
        this.scrollAmount = MathHelper.clamp(this.scrollAmount, 0, this.maxScroll);
    }
    
    public int getStepOffset() {
        return (int) Math.round(this.scrollAmount / this.stepSize);
    }

    public double getScrollAmount() {
        return this.scrollAmount;
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (maxScroll <= 0) return;
        
        context.fill(x, y, x + width, y + height, 0xFF222222);
        
        int thumbHeight = getThumbHeight();
        int thumbY = y + (int) ((this.scrollAmount / this.maxScroll) * (height - thumbHeight));
        
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
        int color = (isDragging || hovered) ? 0xFFFFFFFF : 0xFFAAAAAA;
        
        context.fill(x, thumbY, x + width, thumbY + thumbHeight, color);
    }
    
    private int getThumbHeight() {
        if (visibleContent <= 0) return 15;
        double ratio = visibleContent / (maxScroll + visibleContent);
        int proportional = (int) (height * ratio);
        return MathHelper.clamp(proportional, 15, height);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScroll > 0 && button == 0) {
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                this.isDragging = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isDragging) {
            this.isDragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll > 0) {
            double scrollDelta = smoothScrolling ? 15.0 : stepSize;
            this.scrollAmount -= amount * scrollDelta;
            this.scrollAmount = MathHelper.clamp(this.scrollAmount, 0, this.maxScroll);
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double mouseY) {
        int thumbHeight = getThumbHeight();
        double adjustedY = mouseY - y - (thumbHeight / 2.0);
        double ratio = adjustedY / (height - thumbHeight);
        ratio = MathHelper.clamp(ratio, 0.0, 1.0);
        this.scrollAmount = ratio * maxScroll;
        if (!smoothScrolling) {
            this.scrollAmount = Math.round(this.scrollAmount / stepSize) * stepSize;
        }
    }
}

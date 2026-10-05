package cn.blockforge.generated.sogcarpet.client;

import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 假人背包左侧的竖向滑条（对应截图：渲染距离红条、模拟距离绿条）。
 *
 * <p>26.2 的控件不再走 {@code renderWidget(GuiGraphics,...)}，而是“提取渲染状态”：
 * 覆写 {@link #renderWidget(GuiGraphics, int, int, float)}
 * 往 {@code GuiGraphics} 里写填充矩形与文字。鼠标事件也换成了
 * {@link MouseButtonEvent} 记录类。</p>
 *
 * <p>左键拖动/点击取值 min..max，数值变化通过回调发往服务端。滑条自身的最大值/最小值
 * 由调用方按原版游戏设置的范围传入，手柄旁会直接标出当前数字。</p>
 *
 * <p><b>不再有“跟随玩家设置”选项</b>：右键恢复跟随已取消。假人只在被召唤的那一刻
 * 对齐一次玩家设置（由服务端完成），之后这块面板上的数字就是这个假人自己保存的值，
 * 想改就直接拖滑条。</p>
 */
public final class VerticalDistanceSlider extends AbstractWidget {
    private final int min;
    private final int max;
    private final int color;
    private final IntConsumer onChange;
    private int value;

    public VerticalDistanceSlider(int x, int y, int width, int height, int min, int max,
            int initialValue, Component label, int color, IntConsumer onChange) {
        super(x, y, width, height, label);
        this.min = min;
        this.max = max;
        this.color = color;
        this.onChange = onChange;
        this.value = Math.min(max, Math.max(min, initialValue));
    }

    public int sog$getValue() {
        return value;
    }

    /** 手柄当前停留的数字（用于画数字）。 */
    public int sog$getDisplayValue() {
        return this.value;
    }

    public int sog$getMin() {
        return this.min;
    }

    public int sog$getMax() {
        return this.max;
    }

    /** 滑条自身的颜色（渲染红 / 模拟绿），说明文字沿用同一颜色方便对应。 */
    public int sog$getColor() {
        return this.color;
    }

    @Override
    protected void renderWidget(GuiGraphics extractor, int mouseX, int mouseY,
            float partialTick) {
        int centerX = this.getX() + this.width / 2;
        int trackTop = this.getY();
        int trackBottom = this.getY() + this.height;
        int trackColor = (this.color & 0x00FFFFFF) | 0x90000000;

        // 轨道：2 像素宽的竖线
        extractor.fill(centerX - 1, trackTop, centerX + 1, trackBottom, trackColor);
        // 轨道端头
        extractor.fill(centerX - 4, trackTop - 1, centerX + 4, trackTop + 1, this.color);
        extractor.fill(centerX - 4, trackBottom - 1, centerX + 4, trackBottom + 1, this.color);
        // 手柄：按数值在轨道上定位（顶端为 max，底端为 min）
        int handleY = valueToHandleY(this.value);
        extractor.fill(centerX - 7, handleY - 2, centerX + 7, handleY + 2, this.color);

        // 数字显示：手柄正下方直接标出当前值。
        // 两根滑条间距 22 像素，两位数字不会互相压住。
        extractor.drawCenteredString(Minecraft.getInstance().font, Integer.toString(this.value), centerX,
                handleY + 5, this.color);
    }

    private int valueToHandleY(int v) {
        double t = (double) (max - v) / (double) (max - min);
        return (int) Math.round(this.getY() + t * (this.height - 4)) + 2;
    }

    private int yToValue(double mouseY) {
        double t = (mouseY - this.getY() - 2) / (double) Math.max(1, this.height - 4);
        t = Math.max(0.0D, Math.min(1.0D, t));
        return max - (int) Math.round(t * (max - min));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || !this.visible) {
            return false;
        }
        if (!this.isMouseOver(event.x(), event.y())) {
            return false;
        }
        // 只认左键；右键不再代表“跟随玩家”，直接不处理。
        if (event.button() != 0) {
            return false;
        }
        applyValue(yToValue(event.y()));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() != 0 || !this.active || !this.visible) {
            return false;
        }
        applyValue(yToValue(event.y()));
        return true;
    }

    private void applyValue(int newValue) {
        int clamped = Math.min(max, Math.max(min, newValue));
        if (clamped != this.value) {
            this.value = clamped;
            this.onChange.accept(clamped);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // 横向只放宽 3 像素：两条滑条间距很小，放宽太多会互相抢点击。
        return mouseX >= this.getX() - 3 && mouseX <= this.getX() + this.width + 3
                && mouseY >= this.getY() - 4 && mouseY <= this.getY() + this.height + 16;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}

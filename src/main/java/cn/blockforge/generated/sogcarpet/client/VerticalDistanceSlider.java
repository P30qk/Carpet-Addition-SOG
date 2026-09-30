package cn.blockforge.generated.sogcarpet.client;

import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 假人背包左侧的竖向滑条（对应截图：渲染距离红条、模拟距离绿条）。
 *
 * <p>26.2 的控件不再走 {@code renderWidget(GuiGraphics,...)}，而是“提取渲染状态”：
 * 覆写 {@link #extractWidgetRenderState(GuiGraphicsExtractor, int, int, float)}
 * 往 {@code GuiGraphicsExtractor} 里写填充矩形与文字。鼠标事件也换成了
 * {@link MouseButtonEvent} 记录类。</p>
 *
 * <p>左键拖动/点击取值 min..max；右键恢复“跟随玩家设置”（-1）。
 * 数值变化通过回调发往服务端。滑条自身的最大值/最小值由调用方按原版游戏
 * 设置的范围传入，手柄旁会直接标出当前数字，跟随状态下数字前带 {@code ≈}。</p>
 */
public final class VerticalDistanceSlider extends AbstractWidget {
    /** 右键“跟随玩家设置”的哨兵值。 */
    public static final int FOLLOW = -1;

    private final int min;
    private final int max;
    private final int color;
    private final IntConsumer onChange;
    private int value;
    private boolean follow;

    public VerticalDistanceSlider(int x, int y, int width, int height, int min, int max,
            int initialValue, boolean follow, Component label, int color, IntConsumer onChange) {
        super(x, y, width, height, label);
        this.min = min;
        this.max = max;
        this.color = color;
        this.onChange = onChange;
        this.follow = follow;
        this.value = Math.min(max, Math.max(min, initialValue));
    }

    public int sog$getValue() {
        return follow ? FOLLOW : value;
    }

    /** 手柄当前停留的数字（跟随状态下返回跟随用的那个值，用于画数字）。 */
    public int sog$getDisplayValue() {
        return this.value;
    }

    public int sog$getMin() {
        return this.min;
    }

    public int sog$getMax() {
        return this.max;
    }

    public boolean sog$isFollow() {
        return this.follow;
    }

    /** 滑条自身的颜色（渲染红 / 模拟绿），说明文字沿用同一颜色方便对应。 */
    public int sog$getColor() {
        return this.color;
    }

    public void sog$setValue(int newValue, boolean nowFollow) {
        this.follow = nowFollow;
        if (!nowFollow) {
            this.value = Math.min(max, Math.max(min, newValue));
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
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

        // 数字显示：手柄正下方直接标出当前值，跟随玩家时带 ≈。
        // 两根滑条间距 22 像素，两位数字（含 ≈ 最多约 18 像素）不会互相压住。
        String valueText = (this.follow ? "≈" : "") + this.value;
        extractor.centeredText(Minecraft.getInstance().font, valueText, centerX, handleY + 5,
                this.color);
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
        if (event.button() == 1) {
            // 右键：恢复跟随玩家设置
            this.follow = true;
            this.onChange.accept(FOLLOW);
            return true;
        }
        this.follow = false;
        applyValue(yToValue(event.y()));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() != 0 || !this.active || !this.visible) {
            return false;
        }
        this.follow = false;
        applyValue(yToValue(event.y()));
        return true;
    }

    private void applyValue(int newValue) {
        int clamped = Math.min(max, Math.max(min, newValue));
        if (clamped != this.value || this.follow) {
            this.value = clamped;
            this.follow = false;
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

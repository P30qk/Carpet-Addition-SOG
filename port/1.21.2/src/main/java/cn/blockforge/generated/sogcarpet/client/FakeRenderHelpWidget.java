package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 假人背包左侧滑条下方的文字说明面板。
 *
 * <p>纯展示控件：不接收鼠标（{@link #isMouseOver} 恒为 false），只负责在滑条
 * 底下画出这块假人的渲染设置数字与说明。数字每帧从两根滑条实时读取，所以
 * 拖动时读数会立刻跟着变。</p>
 *
 * <p>内容分为三部分：</p>
 * <ol>
 *   <li>当前值：渲染/模拟距离各自的数字与它跟随的游戏范围（最大值、最小值
 *       取自原版「渲染距离 / 模拟距离」选项范围，由滑条自己带过来）；</li>
 *   <li>服务器默认：服务器当前生效的视距 / 模拟距离，也就是新假人召唤时对齐的那组值；</li>
 *   <li>操作说明与独立性说明（已去掉右键跟随，新假人只在召唤时对齐一次）。</li>
 * </ol>
 *
 * <p>文字块整体右对齐到滑条左侧，尽量待在界面左边的空白里；屏幕太窄放不下时
 * 退化为从屏幕最左边开始画，宁可压到界面也不跑到屏幕外。</p>
 */
public final class FakeRenderHelpWidget extends AbstractWidget {
    private static final int TITLE_COLOR = 0xFFF0F0F0;
    private static final int GAME_COLOR = 0xFFD8C080;
    private static final int HINT_COLOR = 0xFFA8B0B8;
    private static final int LINE_SPACING = 10;

    /** 文字块右边界（两根滑条左侧再往左 6 像素）。 */
    private final int rightEdge;
    private final String fakeName;
    private final VerticalDistanceSlider renderSlider;
    private final VerticalDistanceSlider simulationSlider;
    private final int gameRender;
    private final int gameSimulation;

    public FakeRenderHelpWidget(int rightEdge, int y, String fakeName,
            VerticalDistanceSlider renderSlider, VerticalDistanceSlider simulationSlider,
            int gameRender, int gameSimulation) {
        super(0, y, 0, 0, Component.empty());
        this.rightEdge = rightEdge;
        this.fakeName = fakeName;
        this.renderSlider = renderSlider;
        this.simulationSlider = simulationSlider;
        this.gameRender = gameRender;
        this.gameSimulation = gameSimulation;
    }

    @Override
    protected void renderWidget(GuiGraphics extractor, int mouseX, int mouseY,
            float partialTick) {
        Font font = Minecraft.getInstance().font;
        List<Component> lines = buildLines();
        List<Integer> colors = lineColors();

        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        int blockX = Math.max(2, this.rightEdge - maxWidth);

        // 半透明底衬：文字落在世界画面上也能看清（屏幕太窄时会压到界面，可接受）。
        int totalHeight = lines.size() * LINE_SPACING;
        extractor.fill(blockX - 2, this.getY() - 2, blockX + maxWidth + 2,
                this.getY() + totalHeight, 0x80000000);

        int y = this.getY();
        for (int i = 0; i < lines.size(); i++) {
            extractor.drawString(font, lines.get(i), blockX, y, colors.get(i), true);
            y += LINE_SPACING;
        }
    }

    private List<Component> buildLines() {
        List<Component> lines = new ArrayList<>(6);
        lines.add(Component.translatable("sog_carpet.ui.fake_render.title", this.fakeName));
        lines.add(distanceLine("sog_carpet.ui.render_distance", this.renderSlider));
        lines.add(distanceLine("sog_carpet.ui.simulation_distance", this.simulationSlider));
        lines.add(Component.translatable("sog_carpet.ui.fake_render.game",
                this.gameRender, this.gameSimulation));
        lines.add(Component.translatable("sog_carpet.ui.fake_render.hint_controls"));
        lines.add(Component.translatable("sog_carpet.ui.fake_render.hint_independent"));
        return lines;
    }

    private static Component distanceLine(String labelKey, VerticalDistanceSlider slider) {
        return Component.translatable("sog_carpet.ui.fake_render.line",
                Component.translatable(labelKey), slider.sog$getDisplayValue(),
                slider.sog$getMin(), slider.sog$getMax(), Component.empty());
    }

    private List<Integer> lineColors() {
        List<Integer> colors = new ArrayList<>(6);
        colors.add(TITLE_COLOR);
        colors.add(this.renderSlider.sog$getColor());
        colors.add(this.simulationSlider.sog$getColor());
        colors.add(GAME_COLOR);
        colors.add(HINT_COLOR);
        colors.add(HINT_COLOR);
        return colors;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // 只要不抢滑条的点击即可，本身不参与交互。
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // 纯说明文字，不参与朗读导航。
    }
}

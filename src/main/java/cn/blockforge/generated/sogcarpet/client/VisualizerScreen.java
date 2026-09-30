package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 可视化渲染总控界面（Ctrl+V 打开）。
 *
 * <p>一屏列出全部可视化规则，每行三样东西：</p>
 * <ul>
 *   <li>规则名；</li>
 *   <li>规则开关状态（carpet 规则，服务端，只读显示）；</li>
 *   <li>“渲染”按钮：本客户端是否真的把它画出来；</li>
 *   <li>带独立配置界面的规则（世吞小助手）多一个“配置”按钮，直接进它的方块清单界面。</li>
 * </ul>
 *
 * <p>布局要点：按钮区（顶部）、提示行、列头、数据行自上而下各占各的一行，
 * 任何两个控件之间都留有空隙，不会出现文字叠在一起。所有这些渲染开关都只存在本地，
 * 所以同一台服务器上每个玩家看到的设置互不影响。</p>
 */
public final class VisualizerScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_WIDTH = 380;
    /** 顶部按钮那一行。 */
    private static final int BUTTON_Y = 22;
    /** 提示文字那一行。 */
    private static final int HINT_Y = 46;
    /** 列头那一行。 */
    private static final int HEADER_Y = 60;
    /** 第一条数据行。 */
    private static final int ROWS_Y = 72;

    private Button masterButton;
    private int left;
    private int top;

    public VisualizerScreen() {
        super(Component.translatable("sog_carpet.ui.visualizer.title"));
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = ROWS_Y;

        this.masterButton = addRenderableWidget(Button.builder(masterLabel(), button -> {
            VisualizerState.toggleMaster();
            button.setMessage(masterLabel());
        }).bounds(this.left, BUTTON_Y, 150, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.visualizer.done"), button -> onClose())
                .bounds(this.left + PANEL_WIDTH - 80, BUTTON_Y, 80, 20)
                .build());

        List<String> rules = VisualizerState.rules();
        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i);
            int y = ROWS_Y + i * ROW_HEIGHT + 1;
            addRenderableWidget(Button.builder(renderLabel(rule), button -> {
                VisualizerState.setRenderEnabled(rule, !VisualizerState.isRenderEnabled(rule));
                button.setMessage(renderLabel(rule));
            }).bounds(this.left + PANEL_WIDTH - 72, y, 68, 18).build());
            if (VisualizerState.hasConfigScreen(rule)) {
                // 配置按钮永远可用：界面只改客户端白名单，不需要先开服务端规则。
                addRenderableWidget(Button.builder(
                                Component.translatable("sog_carpet.ui.visualizer.config"),
                                button -> this.minecraft.setScreenAndShow(new WorldEaterConfigScreen()))
                        .bounds(this.left + PANEL_WIDTH - 146, y, 68, 18)
                        .build());
            }
        }
    }

    private static Component renderLabel(String rule) {
        return Component.translatable(VisualizerState.isRenderEnabled(rule)
                ? "sog_carpet.ui.visualizer.on"
                : "sog_carpet.ui.visualizer.off");
    }

    private static Component masterLabel() {
        return Component.translatable(VisualizerState.masterEnabled()
                ? "sog_carpet.ui.visualizer.master_on_short"
                : "sog_carpet.ui.visualizer.master_off_short");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
            float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xC0101014);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        extractor.text(this.font, Component.translatable("sog_carpet.ui.visualizer.hint"),
                this.left, HINT_Y, 0xFFA8B0B8, false);

        extractor.text(this.font, Component.translatable("sog_carpet.ui.visualizer.column_rule"),
                this.left + 4, HEADER_Y, 0xFFB8C0C8, false);
        extractor.text(this.font, Component.translatable("sog_carpet.ui.visualizer.column_state"),
                this.left + 168, HEADER_Y, 0xFFB8C0C8, false);

        List<String> rules = VisualizerState.rules();
        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i);
            int rowTop = ROWS_Y + i * ROW_HEIGHT;
            if ((i & 1) == 1) {
                extractor.fill(this.left, rowTop, this.left + PANEL_WIDTH, rowTop + ROW_HEIGHT - 1,
                        0x22FFFFFF);
            }
            int y = rowTop + 6;
            extractor.text(this.font, VisualizerState.ruleName(rule), this.left + 4, y,
                    0xFFF0F0F0, false);
            boolean ruleOn = VisualizerState.isRuleEnabled(rule);
            extractor.text(this.font, Component.translatable(ruleOn
                    ? "sog_carpet.ui.visualizer.rule_on"
                    : "sog_carpet.ui.visualizer.rule_off"), this.left + 168, y,
                    ruleOn ? 0xFF7FD67F : 0xFFE08080, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停世界：勾选渲染开关时能立刻看到世界里的变化。
        return false;
    }
}

package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
 *   <li>带独立配置界面的规则（世吞小助手、速度/动量可视化）多一个“配置”按钮。</li>
 * </ul>
 *
 * <p>规则变多以后一屏放不下，右侧加了一条可<b>左键拖动</b>的滚动条，滚轮也能滚动，
 * 数据行会随滚动整体上下移动（按钮跟着一起移动，滚出视野的会被隐藏）。所有这些渲染开关都
 * 只存在本地，所以同一台服务器上每个玩家看到的设置互不影响。</p>
 */
public final class VisualizerScreen extends Screen {
    private static final int ROW_HEIGHT = 18;
    /** 行内按钮高度；比行距小 2 像素，行与行之间留出空隙。 */
    private static final int ROW_BUTTON_HEIGHT = 16;
    private static final int PANEL_WIDTH = 380;
    /** 顶部按钮那一行。 */
    private static final int BUTTON_Y = 22;
    /** 提示文字那一行。 */
    private static final int HINT_Y = 44;
    /** 搜索框那一行。 */
    private static final int SEARCH_Y = 56;
    /** 列头那一行。 */
    private static final int HEADER_Y = 78;
    /**
     * 规则状态列与三个按钮的横向布局。
     *
     * <p>面板宽 {@value #PANEL_WIDTH}，右侧三个按钮必须互不重叠：状态（OP 时是三态按钮）
     * → 配置 → 渲染。之前状态按钮宽 92，从 168 一直伸到 260，而“配置”按钮从 234 开始，
     * 两者叠了 26 像素——这就是“开启规则与设置重叠”的原因。现在按下面的常量排：
     * 状态 168..232、配置 234..302、渲染 308..376，各自留 2 像素缝。</p>
     */
    private static final int RULE_COLUMN_X = 168;
    private static final int RULE_BUTTON_WIDTH = 64;
    private static final int CONFIG_BUTTON_X = RULE_COLUMN_X + RULE_BUTTON_WIDTH + 2;
    private static final int CONFIG_BUTTON_WIDTH = 68;
    private static final int RENDER_BUTTON_X = CONFIG_BUTTON_X + CONFIG_BUTTON_WIDTH + 6;
    private static final int RENDER_BUTTON_WIDTH = PANEL_WIDTH - RENDER_BUTTON_X;
    /** 第一条数据行。 */
    private static final int ROWS_Y = 89;
    /** 滚动条宽度与它和面板之间的空隙。 */
    private static final int SCROLL_WIDTH = 6;
    private static final int SCROLL_GAP = 4;

    private Button masterButton;
    private EditBox searchBox;
    private int left;
    private int panelWidth = PANEL_WIDTH;
    /** 动态列位置：面板变窄时按比例收缩，保证状态/配置/渲染三个按钮永不重叠。 */
    private int ruleColumnX = RULE_COLUMN_X;
    private int ruleButtonWidth = RULE_BUTTON_WIDTH;
    private int configButtonX = CONFIG_BUTTON_X;
    private int configButtonWidth = CONFIG_BUTTON_WIDTH;
    private int renderButtonX = RENDER_BUTTON_X;
    private int renderButtonWidth = RENDER_BUTTON_WIDTH;
    private int scroll;
    private boolean draggingBar;
    private double dragOffset;
    private String filter = "";

    private final List<Button> renderButtons = new ArrayList<>();
    private final List<Button> configButtons = new ArrayList<>();
    /** OP 才有的三态规则按钮（关闭 / 暂时开启 / 永久开启）；非 OP 时为 null。 */
    private final List<Button> ruleButtons = new ArrayList<>();
    private final List<String> rowRules = new ArrayList<>();

    public VisualizerScreen() {
        super(Component.translatable("sog_carpet.ui.visualizer.title"));
    }

    @Override
    protected void init() {
        this.panelWidth = Math.max(180, Math.min(PANEL_WIDTH, this.width - 16));
        this.left = Math.max(8, (this.width - this.panelWidth) / 2);
        // 从右往左排：渲染、配置、状态三列固定间距；剩余宽度全给规则名。
        this.renderButtonWidth = Math.min(72, Math.max(48, this.panelWidth / 4));
        this.configButtonWidth = Math.min(68, Math.max(44, this.panelWidth / 5));
        this.ruleButtonWidth = Math.min(64, Math.max(40, this.panelWidth / 5));
        this.renderButtonX = this.panelWidth - this.renderButtonWidth;
        this.configButtonX = this.renderButtonX - 6 - this.configButtonWidth;
        this.ruleColumnX = this.configButtonX - 2 - this.ruleButtonWidth;

        int masterWidth = Math.max(90, this.panelWidth - 90);
        this.masterButton = addRenderableWidget(Button.builder(masterLabel(), button -> {
            VisualizerState.toggleMaster();
            button.setMessage(masterLabel());
        }).bounds(this.left, BUTTON_Y, masterWidth, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.visualizer.done"), button -> onClose())
                .bounds(this.left + this.panelWidth - 80, BUTTON_Y, 80, 20)
                .build());

        this.searchBox = new EditBox(this.font, this.left, SEARCH_Y, this.panelWidth, 18,
                Component.translatable("sog_carpet.ui.visualizer.search"));
        this.searchBox.setHint(Component.translatable("sog_carpet.ui.visualizer.search_hint"));
        this.searchBox.setValue(this.filter);
        this.searchBox.setResponder(value -> {
            this.filter = value == null ? "" : value;
            rebuildRows();
        });
        addRenderableWidget(this.searchBox);

        rebuildRows();
    }

    /** 重新按当前搜索词构建规则行（先移除旧行控件，避免筛选后残留）。 */
    private void rebuildRows() {
        for (Button button : this.renderButtons) {
            removeWidget(button);
        }
        for (Button button : this.configButtons) {
            if (button != null) {
                removeWidget(button);
            }
        }
        for (Button button : this.ruleButtons) {
            if (button != null) {
                removeWidget(button);
            }
        }
        this.renderButtons.clear();
        this.configButtons.clear();
        this.ruleButtons.clear();
        this.rowRules.clear();

        // 自己是 OP（且对面也装了本模组）时，规则状态列变成可点的三态按钮。
        boolean canControl = VisualizerState.canControlRules();
        for (String rule : filteredRules()) {
            this.rowRules.add(rule);
            this.renderButtons.add(addRenderableWidget(Button.builder(renderLabel(rule), button -> {
                VisualizerState.setRenderEnabled(rule, !VisualizerState.isRenderEnabled(rule));
                button.setMessage(renderLabel(rule));
            }).bounds(this.left + this.renderButtonX, 0, this.renderButtonWidth,
                    ROW_BUTTON_HEIGHT).build()));
            if (canControl) {
                this.ruleButtons.add(addRenderableWidget(Button.builder(
                                VisualizerState.ruleModeName(rule), button -> {
                                    VisualizerState.cycleRuleMode(rule);
                                    button.setMessage(VisualizerState.ruleModeName(rule));
                                })
                        .bounds(this.left + this.ruleColumnX, 0, this.ruleButtonWidth,
                                ROW_BUTTON_HEIGHT)
                        .build()));
            } else {
                this.ruleButtons.add(null);
            }
            if (VisualizerState.hasConfigScreen(rule)) {
                this.configButtons.add(addRenderableWidget(Button.builder(
                                Component.translatable("sog_carpet.ui.visualizer.config"),
                                button -> this.minecraft.setScreen(VisualizerState.configScreen(rule, this)))
                        .bounds(this.left + this.configButtonX, 0, this.configButtonWidth,
                                ROW_BUTTON_HEIGHT)
                        .build()));
            } else {
                this.configButtons.add(null);
            }
        }
        clampScroll();
        updateRows();
    }

    /** 按搜索词过滤规则：匹配规则名（语言文案）或规则 ID 即可。 */
    private List<String> filteredRules() {
        String needle = this.filter == null ? "" : this.filter.trim().toLowerCase();
        if (needle.isEmpty()) {
            return VisualizerState.rules();
        }
        List<String> result = new ArrayList<>();
        for (String rule : VisualizerState.rules()) {
            String name = VisualizerState.ruleName(rule).getString().toLowerCase();
            if (name.contains(needle) || rule.toLowerCase().contains(needle)) {
                result.add(rule);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 滚动

    private int viewportTop() {
        return ROWS_Y;
    }

    private int viewportBottom() {
        return Math.max(ROWS_Y + ROW_HEIGHT, this.height - 12);
    }

    private int viewportHeight() {
        return viewportBottom() - viewportTop();
    }

    private int contentHeight() {
        return this.rowRules.size() * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewportHeight());
    }

    private void clampScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
    }

    /** 把每行的按钮挪到滚动后的纵向位置，滚出视野的行隐藏掉。 */
    private void updateRows() {
        for (int i = 0; i < this.rowRules.size(); i++) {
            int rowTop = ROWS_Y + i * ROW_HEIGHT - this.scroll;
            boolean visible = rowTop + ROW_HEIGHT > viewportTop() && rowTop < viewportBottom();
            Button render = this.renderButtons.get(i);
            render.setY(rowTop + 1);
            render.visible = visible;
            render.active = visible;
            Button mode = this.ruleButtons.get(i);
            if (mode != null) {
                mode.setY(rowTop + 1);
                mode.visible = visible;
                mode.active = visible;
            }
            Button config = this.configButtons.get(i);
            if (config != null) {
                config.setY(rowTop + 1);
                config.visible = visible;
                config.active = visible;
            }
        }
    }

    private int scrollbarLeft() {
        return Math.max(2, Math.min(this.left + this.panelWidth + SCROLL_GAP,
                this.width - SCROLL_WIDTH - 2));
    }

    private int trackHeight() {
        return viewportHeight();
    }

    private int barHeight() {
        int content = contentHeight();
        if (content <= 0) {
            return trackHeight();
        }
        return Math.max(18, trackHeight() * viewportHeight() / content);
    }

    private void dragBarTo(double mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int usable = trackHeight() - barHeight();
        if (usable <= 0) {
            return;
        }
        double ratio = (mouseY - viewportTop() - this.dragOffset) / usable;
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        this.scroll = (int) Math.round(ratio * max);
        clampScroll();
        updateRows();
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0
                && mouseX >= scrollbarLeft() - 2 && mouseX <= scrollbarLeft() + SCROLL_WIDTH + 2
                && mouseY >= viewportTop() && mouseY <= viewportBottom()) {
            int barTop = viewportTop() + (int) Math.round(
                    (double) this.scroll / maxScroll() * (trackHeight() - barHeight()));
            if (mouseY >= barTop && mouseY <= barTop + barHeight()) {
                this.dragOffset = mouseY - barTop;
            } else {
                this.dragOffset = barHeight() / 2.0D;
                dragBarTo(mouseY);
            }
            this.draggingBar = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingBar) {
            dragBarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingBar) {
            this.draggingBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.left && mouseX <= this.left + this.panelWidth
                && mouseY >= viewportTop() && mouseY <= viewportBottom() && maxScroll() > 0) {
            this.scroll -= (int) Math.round(scrollY * ROW_HEIGHT);
            clampScroll();
            updateRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ 绘制

    /** 按像素宽度截断文字，防止长规则名/提示压到右侧按钮。 */
    private Component clip(Component text, int maxWidth) {
        String raw = text.getString();
        if (this.font == null || this.font.width(raw) <= maxWidth) {
            return text;
        }
        return Component.literal(this.font.plainSubstrByWidth(raw,
                Math.max(0, maxWidth - 6)) + "…");
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
    public void render(GuiGraphics extractor, int mouseX, int mouseY,
            float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xC0101014);
        super.render(extractor, mouseX, mouseY, partialTick);
        extractor.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        extractor.drawString(this.font, clip(Component.translatable("sog_carpet.ui.visualizer.hint"),
                        this.panelWidth), this.left, HINT_Y, 0xFFA8B0B8, false);

        extractor.drawString(this.font, clip(Component.translatable(
                        "sog_carpet.ui.visualizer.column_rule"), this.ruleColumnX - 8),
                this.left + 4, HEADER_Y, 0xFFB8C0C8, false);
        extractor.drawString(this.font, clip(Component.translatable(
                        "sog_carpet.ui.visualizer.column_state"), this.ruleButtonWidth),
                this.left + this.ruleColumnX + 4, HEADER_Y, 0xFFB8C0C8, false);

        extractor.enableScissor(this.left, viewportTop(), this.left + this.panelWidth,
                viewportBottom());
        List<String> rules = this.rowRules;
        for (int i = 0; i < rules.size(); i++) {
            int rowTop = ROWS_Y + i * ROW_HEIGHT - this.scroll;
            if (rowTop + ROW_HEIGHT <= viewportTop() || rowTop >= viewportBottom()) {
                continue;
            }
            String rule = rules.get(i);
            if ((i & 1) == 1) {
                extractor.fill(this.left, rowTop, this.left + this.panelWidth,
                        rowTop + ROW_HEIGHT - 2, 0x22FFFFFF);
            }
            int y = rowTop + 4;
            extractor.drawString(this.font, clip(VisualizerState.ruleName(rule),
                            this.ruleColumnX - 8), this.left + 4, y, 0xFFF0F0F0, false);
            if (this.ruleButtons.get(i) == null) {
                // 非 OP：规则状态仍然只读显示。
                boolean ruleOn = VisualizerState.isRuleEnabled(rule);
                extractor.drawString(this.font, clip(Component.translatable(ruleOn
                                ? "sog_carpet.ui.visualizer.rule_on"
                                : "sog_carpet.ui.visualizer.rule_off"), this.ruleButtonWidth),
                        this.left + this.ruleColumnX + 4, y,
                        ruleOn ? 0xFF7FD67F : 0xFFE08080, false);
            }
        }
        extractor.disableScissor();

        drawScrollbar(extractor);
    }

    private void drawScrollbar(GuiGraphics extractor) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int x = scrollbarLeft();
        int trackTop = viewportTop();
        int track = trackHeight();
        extractor.fill(x, trackTop, x + SCROLL_WIDTH, trackTop + track, 0x60000000);
        int bar = barHeight();
        int barTop = trackTop + (int) Math.round((double) this.scroll / max * (track - bar));
        extractor.fill(x, barTop, x + SCROLL_WIDTH, barTop + bar, 0xFFB0B0B0);
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停世界：勾选渲染开关时能立刻看到世界里的变化。
        return false;
    }
}

package cn.blockforge.generated.sogcarpet.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 一条可视化的“颜色 / 选项”配置界面（Ctrl+V 里点对应规则的“配置”进入）。
 *
 * <p>顶部是这条可视化的布尔选项（例如刷怪可视化的“统计面板”“候选路径”），下面是它的颜色
 * 部件列表：每个部件一行，左边名字、中间色块，右边一排按钮调整色相（H±）、饱和度（S±）、
 * 明度（V±），以及一个 ↺ 恢复默认。改动即时生效并写入
 * {@code config/sog_carpet-visualizer-config.txt}，不暂停世界。</p>
 */
public final class VisualizerConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 40;
    private static final int SWATCH_X = 150;
    private static final int SWATCH_SIZE = 14;
    /** 颜色行右侧一排小按钮：H- H+ S- S+ V- V+ ↺。 */
    private static final int BTN_W = 20;
    private static final int BTN_GAP = 2;
    private static final int BTN_COUNT = 7;

    private final String rule;
    /** 上级面板；非空时 Done/Esc 返回它。 */
    private final Screen parent;
    private int left;
    private int panelWidth = PANEL_WIDTH;
    private int swatchX = SWATCH_X;
    private int scroll;

    public VisualizerConfigScreen(String rule) {
        this(rule, null);
    }

    public VisualizerConfigScreen(String rule, Screen parent) {
        super(Component.translatable("sog_carpet.ui.color.title",
                VisualizerState.ruleName(rule)));
        this.rule = rule;
        this.parent = parent;
    }

    @Override
    protected void init() {
        VisualizerConfig.parts(this.rule);
        this.panelWidth = Math.max(260, Math.min(PANEL_WIDTH, this.width - 16));
        this.left = Math.max(8, (this.width - this.panelWidth) / 2);
        int buttonStartX = this.left + this.panelWidth - BTN_COUNT * (BTN_W + BTN_GAP);
        this.swatchX = Math.max(this.left + 60,
                Math.min(this.left + SWATCH_X, buttonStartX - 4 - SWATCH_SIZE));
        // Done 放右上角；提示文字在左侧并限制在 Done 左边，避免标题/提示/按钮互相压住。
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.color.done"), button -> onClose())
                .bounds(this.left + this.panelWidth - 60, 18, 60, 18)
                .build());
    }

    /** 按像素宽度截断文字，防止长语言文案压到色块或右侧开关文字。 */
    private Component clip(Component text, int maxWidth) {
        String raw = text.getString();
        if (this.font == null || this.font.width(raw) <= maxWidth) {
            return text;
        }
        return Component.literal(this.font.plainSubstrByWidth(raw,
                Math.max(0, maxWidth - 6)) + "…");
    }

    private int viewportTop() {
        return TOP;
    }

    private int viewportBottom() {
        return this.height - 8;
    }

    private List<VisualizerConfig.Part> parts() {
        return VisualizerConfig.parts(this.rule);
    }

    private List<VisualizerConfig.Option> options() {
        return VisualizerConfig.options(this.rule);
    }

    private int contentHeight() {
        return options().size() * ROW_HEIGHT + parts().size() * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewportBottom() - viewportTop()));
    }

    private void clampScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.left && mouseX <= this.left + this.panelWidth
                && mouseY >= viewportTop() && mouseY <= viewportBottom() && maxScroll() > 0) {
            this.scroll -= (int) Math.round(scrollY * ROW_HEIGHT);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (x < this.left || x > this.left + this.panelWidth) {
            return false;
        }
        List<VisualizerConfig.Option> options = options();
        List<VisualizerConfig.Part> parts = parts();
        int rowTop = viewportTop() - this.scroll;
        // 选项行：整行一个开关。
        for (VisualizerConfig.Option option : options) {
            if (y >= rowTop && y < rowTop + ROW_HEIGHT) {
                boolean value = VisualizerConfig.option(this.rule, option.key(), option.defaultValue());
                VisualizerConfig.setOption(this.rule, option.key(), !value);
                return true;
            }
            rowTop += ROW_HEIGHT;
        }
        // 颜色行：命中右边某个小按钮。
        for (VisualizerConfig.Part part : parts) {
            if (y >= rowTop && y < rowTop + ROW_HEIGHT) {
                int index = buttonIndexAt(x, rowTop);
                if (index >= 0) {
                    apply(part, index);
                }
                return true;
            }
            rowTop += ROW_HEIGHT;
        }
        return false;
    }

    private int buttonIndexAt(int mouseX, int rowTop) {
        int startX = this.left + this.panelWidth - BTN_COUNT * (BTN_W + BTN_GAP);
        int buttonY = rowTop + (ROW_HEIGHT - 16) / 2;
        for (int i = 0; i < BTN_COUNT; i++) {
            int bx = startX + i * (BTN_W + BTN_GAP);
            if (mouseX >= bx && mouseX <= bx + BTN_W) {
                return i;
            }
        }
        return -1;
    }

    private void apply(VisualizerConfig.Part part, int index) {
        int argb = VisualizerConfig.color(this.rule, part.key(), part.defaultArgb());
        int updated = switch (index) {
            case 0 -> VisualizerConfig.adjust(argb, -10.0F / 360.0F, 0.0F, 0.0F);
            case 1 -> VisualizerConfig.adjust(argb, 10.0F / 360.0F, 0.0F, 0.0F);
            case 2 -> VisualizerConfig.adjust(argb, 0.0F, -0.08F, 0.0F);
            case 3 -> VisualizerConfig.adjust(argb, 0.0F, 0.08F, 0.0F);
            case 4 -> VisualizerConfig.adjust(argb, 0.0F, 0.0F, -0.08F);
            case 5 -> VisualizerConfig.adjust(argb, 0.0F, 0.0F, 0.08F);
            default -> part.defaultArgb();
        };
        if (index == 6) {
            VisualizerConfig.resetColor(this.rule, part.key());
        } else {
            VisualizerConfig.setColor(this.rule, part.key(), updated);
        }
    }

    // ------------------------------------------------------------------ 绘制

    private static final String[] BTN_LABELS = {"H-", "H+", "S-", "S+", "V-", "V+", "↺"};

    @Override
    public void render(GuiGraphics extractor, int mouseX, int mouseY,
            float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xB0101014);
        super.render(extractor, mouseX, mouseY, partialTick);
        extractor.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
        extractor.drawString(this.font, clip(Component.translatable("sog_carpet.ui.color.hint"),
                        this.panelWidth - 74),
                this.left + 4, 23, 0xFFA8B0B8, false);

        Font font = this.font;
        extractor.enableScissor(this.left, viewportTop(), this.left + this.panelWidth, viewportBottom());
        List<VisualizerConfig.Option> options = options();
        List<VisualizerConfig.Part> parts = parts();
        int rowTop = viewportTop() - this.scroll;

        for (VisualizerConfig.Option option : options) {
            if (rowTop + ROW_HEIGHT > viewportTop() && rowTop < viewportBottom()) {
                boolean value = VisualizerConfig.option(this.rule, option.key(), option.defaultValue());
                boolean hovered = mouseX >= this.left && mouseX <= this.left + this.panelWidth
                        && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
                int bg = hovered ? 0xFF303840 : 0xFF202830;
                extractor.fill(this.left, rowTop + 1, this.left + this.panelWidth,
                        rowTop + ROW_HEIGHT - 1, bg);
                extractor.drawString(font, clip(Component.translatable(option.lang()),
                                this.panelWidth - 54), this.left + 4, rowTop + 6,
                        0xFFE0E6EC, false);
                Component state = Component.translatable(value
                        ? "sog_carpet.ui.color.on" : "sog_carpet.ui.color.off");
                extractor.drawString(font, state, this.left + this.panelWidth - 30, rowTop + 6,
                        value ? 0xFF7FD67F : 0xFFE08080, false);
            }
            rowTop += ROW_HEIGHT;
        }

        for (VisualizerConfig.Part part : parts) {
            if (rowTop + ROW_HEIGHT > viewportTop() && rowTop < viewportBottom()) {
                int argb = VisualizerConfig.color(this.rule, part.key(), part.defaultArgb());
                extractor.fill(this.left, rowTop + 1, this.left + this.panelWidth,
                        rowTop + ROW_HEIGHT - 1, 0x22FFFFFF);
                extractor.drawString(font, clip(Component.translatable(part.lang()),
                                this.swatchX - this.left - 8), this.left + 4, rowTop + 6,
                        0xFFE0E6EC, false);
                extractor.fill(this.swatchX, rowTop + 4, this.swatchX + SWATCH_SIZE,
                        rowTop + 4 + SWATCH_SIZE, argb);
                extractor.renderOutline(this.swatchX, rowTop + 4, SWATCH_SIZE, SWATCH_SIZE, 0xFF808080);

                int startX = this.left + this.panelWidth - BTN_COUNT * (BTN_W + BTN_GAP);
                int buttonY = rowTop + (ROW_HEIGHT - 16) / 2;
                boolean hoverRow = mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
                for (int i = 0; i < BTN_COUNT; i++) {
                    int bx = startX + i * (BTN_W + BTN_GAP);
                    boolean hovered = hoverRow && mouseX >= bx && mouseX <= bx + BTN_W;
                    extractor.fill(bx, buttonY, bx + BTN_W, buttonY + 16,
                            hovered ? 0xFF4A5560 : 0xFF333B44);
                    extractor.drawCenteredString(font, BTN_LABELS[i], bx + BTN_W / 2, buttonY + 4, 0xFFE0E6EC);
                }
            }
            rowTop += ROW_HEIGHT;
        }
        extractor.disableScissor();

        if (maxScroll() > 0) {
            int barX = Math.max(2, Math.min(this.left + this.panelWidth + 3, this.width - 4));
            int track = viewportBottom() - viewportTop();
            extractor.fill(barX, viewportTop(), barX + 4, viewportBottom(), 0x60000000);
            int barHeight = Math.max(16, track * track / Math.max(1, contentHeight()));
            int barTop = viewportTop() + (int) ((long) this.scroll * (track - barHeight) / maxScroll());
            extractor.fill(barX, barTop, barX + 4, barTop + barHeight, 0xFFB0B0B0);
        }
    }

    @Override
    public void onClose() {
        if (this.parent != null && this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停世界：调颜色时能立刻看到世界里的变化。
        return false;
    }
}

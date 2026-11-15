package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 敌对生物仇恨范围配置界面里的逐生物开关列表。
 *
 * <p>自绘的可滚动列表（每行 {@value #ROW_HEIGHT} 像素），不依赖原版的
 * {@code AbstractSelectionList}，因为本版本渲染管线改用 {@code GuiGraphics}
 * 提取渲染状态，自绘更可控。每行左边一个勾选框、右边是生物名字；点行内任意处即可开关这个
 * 生物是否绘制仇恨范围球。右侧有可左键拖动的滚动条，滚轮也照常可用。</p>
 */
public final class AggroRangeMobList extends AbstractWidget {
    private static final int ROW_HEIGHT = 20;
    private static final int CHECK_SIZE = 12;
    private static final int SCROLL_WIDTH = 5;

    private final Runnable onChanged;
    private final List<AggroRangeConfig.Entry> rows = new ArrayList<>();
    private String filter = "";
    private double scroll;

    private boolean draggingBar;
    private double dragOffset;

    public AggroRangeMobList(int x, int y, int width, int height, Runnable onChanged) {
        super(x, y, width, height, Component.empty());
        this.onChanged = onChanged;
        refresh();
    }

    /** 重新按当前过滤词生成行。 */
    public void refresh() {
        String query = this.filter.trim().toLowerCase(Locale.ROOT);
        this.rows.clear();
        for (AggroRangeConfig.Entry entry : AggroRangeConfig.entries()) {
            if (query.isEmpty() || matches(entry, query)) {
                this.rows.add(entry);
            }
        }
        clampScroll();
    }

    private static boolean matches(AggroRangeConfig.Entry entry, String query) {
        if (entry.key().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return entry.name().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    public void setFilter(String value) {
        this.filter = value == null ? "" : value;
        this.scroll = 0.0D;
        refresh();
    }

    private double maxScroll() {
        return Math.max(0, this.rows.size() * ROW_HEIGHT - (this.height - 2));
    }

    private void clampScroll() {
        this.scroll = Math.max(0.0D, Math.min(this.scroll, maxScroll()));
    }

    private int rowAt(double mouseY) {
        int relative = (int) mouseY - (getY() + 1) + (int) this.scroll;
        int index = relative / ROW_HEIGHT;
        return index >= 0 && index < this.rows.size() ? index : -1;
    }

    @Override
    protected void renderWidget(GuiGraphics extractor, int mouseX, int mouseY,
            float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;
        extractor.fill(x, y, x + w, y + h, 0x90000000);
        extractor.submitOutline(x, y, w, h, 0xFF8A8A8A);
        extractor.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        if (this.rows.isEmpty()) {
            extractor.drawString(font, Component.translatable("sog_carpet.ui.aggro.empty"),
                    x + 6, y + 6, 0xFF909090, false);
        } else {
            int offset = (int) this.scroll;
            int first = Math.max(0, offset / ROW_HEIGHT);
            int index = first;
            int top = y + 1 + first * ROW_HEIGHT - offset;
            while (top < y + h - 1 && index < this.rows.size()) {
                boolean hovered = mouseX >= x + 1 && mouseX < x + w - 1
                        && mouseY >= top && mouseY < top + ROW_HEIGHT;
                drawRow(extractor, font, this.rows.get(index), top, hovered);
                index++;
                top += ROW_HEIGHT;
            }
        }
        extractor.disableScissor();
        drawScrollbar(extractor);
    }

    private void drawRow(GuiGraphics extractor, Font font, AggroRangeConfig.Entry entry,
            int top, boolean hovered) {
        int x = getX();
        int w = width;
        if (hovered) {
            extractor.fill(x + 1, top, x + w - 1, top + ROW_HEIGHT - 1, 0x30FFFFFF);
        }
        extractor.fill(x + 1, top + ROW_HEIGHT - 1, x + w - 1, top + ROW_HEIGHT, 0x30FFFFFF);

        int boxTop = top + (ROW_HEIGHT - CHECK_SIZE) / 2;
        int boxLeft = x + 4;
        extractor.fill(boxLeft, boxTop, boxLeft + CHECK_SIZE, boxTop + CHECK_SIZE, 0xFF101010);
        extractor.fill(boxLeft + 1, boxTop + 1, boxLeft + CHECK_SIZE - 1, boxTop + CHECK_SIZE - 1,
                entry.enabled() ? 0xFF3A9A36 : 0xFF555555);
        if (entry.enabled()) {
            extractor.fill(boxLeft + 3, boxTop + 6, boxLeft + 5, boxTop + 9, 0xFF0A2A0A);
            extractor.fill(boxLeft + 5, boxTop + 4, boxLeft + 8, boxTop + 7, 0xFF0A2A0A);
        }

        int nameLeft = boxLeft + CHECK_SIZE + 6;
        int maxName = Math.max(10, x + w - 18 - nameLeft);
        String shown = font.plainSubstrByWidth(entry.name().getString(), maxName);
        extractor.drawString(font, shown, nameLeft, top + 6,
                entry.enabled() ? 0xFFE8E8E8 : 0xFF8C8C8C, false);
    }

    private void drawScrollbar(GuiGraphics extractor) {
        double max = maxScroll();
        if (max <= 0.0D) {
            return;
        }
        int scrollLeft = getX() + width - 2 - SCROLL_WIDTH;
        int scrollRight = getX() + width - 2;
        int trackTop = getY() + 1;
        int trackHeight = this.height - 2;
        extractor.fill(scrollLeft, trackTop, scrollRight, trackTop + trackHeight, 0x60000000);
        int barHeight = barHeight(trackHeight);
        int barTop = trackTop + (int) Math.round(this.scroll / max * (trackHeight - barHeight));
        extractor.fill(scrollLeft, barTop, scrollRight, barTop + barHeight, 0xFFB0B0B0);
    }

    private int barHeight(int trackHeight) {
        int content = this.rows.size() * ROW_HEIGHT;
        if (content <= 0) {
            return trackHeight;
        }
        return Math.max(14, trackHeight * (this.height - 2) / content);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        if (maxScroll() <= 0.0D) {
            return false;
        }
        int scrollLeft = getX() + width - 2 - SCROLL_WIDTH;
        return mouseX >= scrollLeft - 2 && mouseX <= getX() + width
                && mouseY >= getY() + 1 && mouseY <= getY() + this.height - 1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || !this.visible || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (event.button() == 0 && overScrollbar(event.x(), event.y())) {
            int trackTop = getY() + 1;
            int trackHeight = this.height - 2;
            int barHeight = barHeight(trackHeight);
            int barTop = trackTop
                    + (int) Math.round(this.scroll / maxScroll() * (trackHeight - barHeight));
            if (event.y() >= barTop && event.y() <= barTop + barHeight) {
                this.dragOffset = event.y() - barTop;
            } else {
                this.dragOffset = barHeight / 2.0D;
                dragBarTo(event.y());
            }
            this.draggingBar = true;
            return true;
        }
        int index = rowAt(event.y());
        if (index < 0) {
            return false;
        }
        AggroRangeConfig.Entry entry = this.rows.get(index);
        AggroRangeConfig.setEnabled(entry.key(), !entry.enabled());
        refresh();
        this.onChanged.run();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!this.draggingBar) {
            return false;
        }
        dragBarTo(event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingBar) {
            this.draggingBar = false;
            return true;
        }
        return false;
    }

    private void dragBarTo(double mouseY) {
        double max = maxScroll();
        if (max <= 0.0D) {
            return;
        }
        int trackTop = getY() + 1;
        int trackHeight = this.height - 2;
        int barHeight = barHeight(trackHeight);
        double usable = trackHeight - barHeight;
        if (usable <= 0.0D) {
            return;
        }
        double ratio = (mouseY - trackTop - this.dragOffset) / usable;
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        this.scroll = ratio * max;
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.scroll -= scrollY * ROW_HEIGHT;
        clampScroll();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

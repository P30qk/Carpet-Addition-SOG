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
import net.minecraft.resources.ResourceLocation;

/**
 * 世吞小助手配置界面里的方块白名单列表。
 *
 * <p>这是一段自绘的可滚动列表控件（每行 {@value #ROW_HEIGHT} 像素），不依赖原版的
 * {@code AbstractSelectionList}，因为本版本渲染管线改用 {@code GuiGraphics}
 * 提取渲染状态，自绘更可控。每行从左到右是：</p>
 * <ul>
 *   <li>渲染勾选框：是否画出这个方块；</li>
 *   <li>方块名字（过长时按宽度裁切，绝不会压到右边列）；</li>
 *   <li>颜色分类色块（点一下切换到下一个分类，用来给手动添加的方块配色）；</li>
 *   <li>“含水”开关：可含水方块才有，决定它的含水状态标不标；</li>
 *   <li>“×”：把方块从白名单里移除。</li>
 * </ul>
 *
 * <p>列位置全部从右边往左固定推出来，彼此留有空隙，任何宽度下都不会文字叠加。
 * 右侧有一条可<b>左键拖动</b>的滚动条，滚轮也照常可用。</p>
 */
public final class WorldEaterBlockList extends AbstractWidget {
    private static final int ROW_HEIGHT = 20;
    private static final int CHECK_SIZE = 12;
    private static final int SWATCH_SIZE = 8;
    private static final int SCROLL_WIDTH = 5;

    private final Runnable onChanged;
    private final List<WorldEaterConfig.Entry> rows = new ArrayList<>();
    private String filter = "";
    private double scroll;
    private ResourceLocation highlight;

    private boolean draggingBar;
    private double dragOffset;

    public WorldEaterBlockList(int x, int y, int width, int height, Runnable onChanged) {
        super(x, y, width, height, Component.empty());
        this.onChanged = onChanged;
        refresh();
    }

    // ------------------------------------------------------------------ 列位置（界面画表头时也用它们）

    public static int checkColumnX(int listX) {
        return listX + 3;
    }

    public static int nameColumnX(int listX) {
        return listX + 20;
    }

    public static int catColumnX(int listX, int listWidth) {
        return resolve(listX, listWidth).catLeft;
    }

    public static int waterColumnX(int listX, int listWidth) {
        return resolve(listX, listWidth).waterLeft;
    }

    private static Columns resolve(int listX, int listWidth) {
        Columns c = new Columns();
        c.scrollRight = listX + listWidth - 2;
        c.scrollLeft = c.scrollRight - SCROLL_WIDTH;
        c.removeRight = c.scrollLeft - 6;
        c.removeLeft = c.removeRight - 12;
        c.waterRight = c.removeLeft - 6;
        c.waterLeft = c.waterRight - 28;
        c.catRight = c.waterLeft - 6;
        c.catLeft = c.catRight - 58;
        c.nameRight = c.catLeft - 4;
        return c;
    }

    private Columns columns() {
        return resolve(getX(), width);
    }

    private static final class Columns {
        int scrollLeft;
        int scrollRight;
        int removeLeft;
        int removeRight;
        int waterLeft;
        int waterRight;
        int catLeft;
        int catRight;
        int nameRight;
    }

    // ------------------------------------------------------------------ 数据

    /** 重新按当前过滤词生成行；保留滚动位置（尽量）。 */
    public void refresh() {
        List<WorldEaterConfig.Entry> all = WorldEaterConfig.entries();
        String query = this.filter.trim().toLowerCase(Locale.ROOT);
        this.rows.clear();
        for (WorldEaterConfig.Entry entry : all) {
            if (query.isEmpty() || matches(entry, query)) {
                this.rows.add(entry);
            }
        }
        clampScroll();
    }

    private static boolean matches(WorldEaterConfig.Entry entry, String query) {
        if (entry.path().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (entry.idString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return WorldEaterConfig.displayName(entry.id()).toLowerCase(Locale.ROOT).contains(query);
    }

    public void setFilter(String value) {
        this.filter = value == null ? "" : value;
        this.scroll = 0.0D;
        refresh();
    }

    /** 添加方块后把它滚到可见位置并闪一下。 */
    public void highlight(ResourceLocation id) {
        this.highlight = id;
        for (int i = 0; i < this.rows.size(); i++) {
            if (this.rows.get(i).id().equals(id)) {
                this.scroll = Math.max(0.0D, i * ROW_HEIGHT - (this.height - ROW_HEIGHT) / 2.0D);
                clampScroll();
                return;
            }
        }
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

    // ------------------------------------------------------------------ 绘制

    @Override
    protected void renderWidget(GuiGraphics extractor, int mouseX, int mouseY,
            float partialTick) {
        Font font = Minecraft.getInstance().font;
        Columns c = columns();
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;
        extractor.fill(x, y, x + w, y + h, 0x90000000);
        extractor.submitOutline(x, y, w, h, 0xFF8A8A8A);
        extractor.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        if (this.rows.isEmpty()) {
            extractor.drawString(font, Component.translatable("sog_carpet.ui.world_eater.empty"),
                    x + 6, y + 6, 0xFF909090, false);
        } else {
            int offset = (int) this.scroll;
            int first = Math.max(0, offset / ROW_HEIGHT);
            int index = first;
            int top = y + 1 + first * ROW_HEIGHT - offset;
            while (top < y + h - 1 && index < this.rows.size()) {
                boolean hovered = mouseX >= x + 1 && mouseX < x + w - 1
                        && mouseY >= top && mouseY < top + ROW_HEIGHT;
                drawRow(extractor, font, this.rows.get(index), top, hovered, c);
                index++;
                top += ROW_HEIGHT;
            }
        }
        extractor.disableScissor();
        drawScrollbar(extractor, c);
    }

    private void drawRow(GuiGraphics extractor, Font font, WorldEaterConfig.Entry entry,
            int top, boolean hovered, Columns c) {
        int x = getX();
        int w = width;
        if (hovered) {
            extractor.fill(x + 1, top, x + w - 1, top + ROW_HEIGHT - 1, 0x30FFFFFF);
        }
        if (this.highlight != null && this.highlight.equals(entry.id())) {
            extractor.fill(x + 1, top, x + w - 1, top + ROW_HEIGHT - 1, 0x40FFD050);
        }
        // 行分隔线，让密集的清单更好读。
        extractor.fill(x + 1, top + ROW_HEIGHT - 1, x + w - 1, top + ROW_HEIGHT, 0x30FFFFFF);

        int boxTop = top + (ROW_HEIGHT - CHECK_SIZE) / 2;
        int boxLeft = checkColumnX(x);
        extractor.fill(boxLeft, boxTop, boxLeft + CHECK_SIZE, boxTop + CHECK_SIZE, 0xFF101010);
        extractor.fill(boxLeft + 1, boxTop + 1, boxLeft + CHECK_SIZE - 1, boxTop + CHECK_SIZE - 1,
                entry.enabled() ? 0xFF3A9A36 : 0xFF555555);
        if (entry.enabled()) {
            extractor.fill(boxLeft + 3, boxTop + 6, boxLeft + 5, boxTop + 9, 0xFF0A2A0A);
            extractor.fill(boxLeft + 5, boxTop + 4, boxLeft + 8, boxTop + 7, 0xFF0A2A0A);
        }

        String name = WorldEaterConfig.displayName(entry.id());
        int maxName = Math.max(10, c.nameRight - nameColumnX(x));
        String shown = font.plainSubstrByWidth(name, maxName);
        extractor.drawString(font, shown, nameColumnX(x), top + 6,
                entry.enabled() ? 0xFFE8E8E8 : 0xFF8C8C8C, false);

        // 颜色分类：色块 + 分类名。
        int color = WorldEaterConfig.categoryColor(entry.category());
        extractor.fill(c.catLeft, top + 6, c.catLeft + SWATCH_SIZE, top + 6 + SWATCH_SIZE, color);
        extractor.submitOutline(c.catLeft, top + 6, SWATCH_SIZE, SWATCH_SIZE, 0xFF202020);
        Component category = WorldEaterConfig.categoryName(entry.category());
        extractor.drawString(font, category, c.catLeft + SWATCH_SIZE + 4, top + 6, 0xFFB8B8B8, false);

        // 含水样式开关：可含水方块才有。
        if (WorldEaterConfig.isWaterloggable(entry.id())) {
            int waterColor = entry.waterlog() ? 0xFF56A0FF : 0xFF8A8A8A;
            extractor.fill(c.waterLeft, top + 4, c.waterRight, top + 16, 0x40000000);
            extractor.submitOutline(c.waterLeft, top + 4, c.waterRight - c.waterLeft, 12, waterColor);
            extractor.drawCenteredString(font, Component.translatable("sog_carpet.ui.world_eater.water"),
                    (c.waterLeft + c.waterRight) / 2, top + 6, waterColor);
        }

        extractor.drawCenteredString(font, "×", (c.removeLeft + c.removeRight) / 2 + 1, top + 5,
                0xFFFF7A7A);
    }

    private void drawScrollbar(GuiGraphics extractor, Columns c) {
        double max = maxScroll();
        if (max <= 0.0D) {
            return;
        }
        int trackTop = getY() + 1;
        int trackHeight = this.height - 2;
        extractor.fill(c.scrollLeft, trackTop, c.scrollRight, trackTop + trackHeight, 0x60000000);
        int barHeight = barHeight(trackHeight);
        int barTop = trackTop + (int) Math.round(this.scroll / max * (trackHeight - barHeight));
        extractor.fill(c.scrollLeft, barTop, c.scrollRight, barTop + barHeight, 0xFFB0B0B0);
    }

    // ------------------------------------------------------------------ 命中区

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
        Columns c = columns();
        return mouseX >= c.scrollLeft - 2 && mouseX <= c.scrollRight + 2
                && mouseY >= getY() + 1 && mouseY <= getY() + this.height - 1;
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || !this.visible || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (event.button() == 0 && overScrollbar(event.x(), event.y())) {
            Columns c = columns();
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
        WorldEaterConfig.Entry entry = this.rows.get(index);
        Columns c = columns();
        int mouseX = (int) event.x();
        if (mouseX >= c.removeLeft && mouseX <= c.removeRight) {
            WorldEaterConfig.removeEntry(entry.id());
            refresh();
            this.onChanged.run();
            return true;
        }
        if (mouseX >= c.waterLeft && mouseX <= c.waterRight
                && WorldEaterConfig.isWaterloggable(entry.id())) {
            WorldEaterConfig.setWaterlog(entry.id(), !entry.waterlog());
            refresh();
            this.onChanged.run();
            return true;
        }
        if (mouseX >= c.catLeft && mouseX <= c.catRight) {
            WorldEaterConfig.setCategory(entry.id(), WorldEaterConfig.nextCategory(entry.category()));
            refresh();
            this.onChanged.run();
            return true;
        }
        if (mouseX <= c.nameRight) {
            WorldEaterConfig.setEnabled(entry.id(), !entry.enabled());
            refresh();
            this.onChanged.run();
            return true;
        }
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

    /** 按鼠标所在的纵向比例把滚动条拖过去（左键按住即可拖动）。 */
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

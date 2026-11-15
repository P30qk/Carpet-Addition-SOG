package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogBeaconFilter;
import cn.blockforge.generated.sogcarpet.SogBeaconHelper;
import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.net.BeaconFilterPayload;
import cn.blockforge.generated.sogcarpet.net.PortableBeaconUsePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * 效果选择界面：既给「自定义信标效果」用，也给「随身信标」用。
 *
 * <p>两种打开方式：</p>
 * <ul>
 *   <li><b>信标界面里的效果槽</b>（{@link BeaconEffectSlot}）：选一个，写回信标界面，
 *       再由原版确认按钮发包（等级/时长/扣物品全走原版）；</li>
 *   <li><b>随身信标</b>（手持信标 Ctrl+右键）：最多选<b>两个</b>——选两个各 I 级，只选一个
 *       则该效果 II 级；确认后把两个 id 一起发给服务端（{@link PortableBeaconUsePayload}），
 *       服务端校验后维持效果（只要信标还在背包里就一直有效）。</li>
 * </ul>
 *
 * <h2>布局（本版重排，消除文字/按钮重叠）</h2>
 *
 * <p>上一版把模式按钮放在 y=6、提示文字放在 y=22、标题居中放在 y=10，三者互相压住：模式按钮
 * （y 6-26）与提示行（y 22 起）在竖直方向重叠，Op 提示文字又横着穿过居中标题。现在每一行各占
 * 一条互不相交的横带：</p>
 *
 * <pre>
 *   y=6    标题（居中）
 *   y=20   说明文字（单行，超出面板宽度自动截断）
 *   y=32   工具栏：只有 Op 才有模式按钮，Op 提示紧随其右（同一行、不同的 x）
 *   y=54   搜索框
 *   y=78   效果网格（到 height-58）
 *   ...    已选效果那一行（height-48）
 *   ...    清空 / 确定 / 取消（height-31）
 * </pre>
 *
 * <p>所有文字都经过 {@link #fit(String, int)} 截断，不会越出面板压到旁边的控件。</p>
 *
 * <p><b>黑/白名单</b>（服务端规则，加入时同步下来）：只作用于<b>信标方块</b>里的自定义效果槽位——
 * 被禁用的效果标上 {@code [x]} 并且点不动。Op 可以<b>右键</b>任意效果把它加入/移出
 * <b>当前模式</b>那一份名单（黑名单与白名单各自独立），也可以点工具栏的模式按钮切换生效哪一份。
 * 改动走 {@link BeaconFilterPayload}，服务端校验权限后用 carpet 指令落盘并广播。</p>
 *
 * <p><b>右键必须能点中“被锁定”的格子</b>：“点不动”只是把按钮设成 {@code active = false}，
 * 而 {@code AbstractWidget#isMouseOver} 内部是 {@code isActive() && 矩形判定}，所以右键命中测试
 * 绝不能复用它——否则黑名单里的效果右键删不掉、白名单外的效果右键加不进来（本类曾经就是这个 bug）。
 * 见 {@link #effectAt}。</p>
 *
 * <p><b>服务端没装本模组时</b>：没有任何人能接收 {@link BeaconFilterPayload}，此时右键与模式按钮
 * 改的是<b>本客户端</b>的那一份名单（界面上的锁定状态立刻跟着变），并给一条“仅本客户端生效”的提示。
 * 服务端装了本模组时行为完全不变：仍由服务端校验 Op 权限后写 carpet 规则并广播给所有人。</p>
 *
 * <p><b>随身信标不受名单影响</b>：它的可选范围只由「自定义信标效果」规则决定，所以随身信标界面
 * 不显示锁定标记，也不提供名单编辑按钮。</p>
 */
public final class BeaconEffectPickerScreen extends Screen {
    /** 网格列数与单元格尺寸。 */
    private static final int COLUMNS = 4;
    private static final int CELL_WIDTH = 104;
    private static final int CELL_HEIGHT = 18;
    private static final int CELL_GAP = 2;
    /** 格子再窄就放不下效果名，低于这个值时允许面板比窗口略宽。 */
    private static final int MIN_CELL_WIDTH = 48;
    /** 面板左右内边距。 */
    private static final int PANEL_PAD = 6;
    /** 滚动条宽度与它到网格的间距。 */
    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_GAP = 4;
    /** 滑块最短高度（内容很多时也要抓得住）。 */
    private static final int MIN_THUMB = 12;

    /** 各横带的 y（互不相交，见类注释里的布局表）。 */
    private static final int TITLE_Y = 6;
    private static final int HINT_Y = 20;
    private static final int TOOLBAR_Y = 32;
    private static final int TOOLBAR_H = 18;
    private static final int SEARCH_Y = 54;
    private static final int SEARCH_H = 18;
    private static final int GRID_TOP = 78;
    /** 网格底部距离窗口底部的距离：给“已选效果”那一行与三个按钮留位置。 */
    private static final int GRID_BOTTOM_MARGIN = 58;
    /** 已选效果那一行。 */
    private static final int SELECTED_MARGIN = 48;
    /** 三个底部按钮。 */
    private static final int BUTTON_MARGIN = 31;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_W = 76;

    /** Op 禁用效果的前缀（纯 ASCII，保证任何字体下都能显示）。 */
    private static final String LOCKED_MARK = "[x]";
    /** 随身信标里“已选第 1 / 第 2 个”的标记。 */
    private static final String SLOT1_MARK = "1.";
    private static final String SLOT2_MARK = "2.";
    private static final String CHOSEN_MARK = "> ";
    /** 随身信标最多选几个效果。 */
    private static final int MAX_PORTABLE_CHOICES = 2;

    /** 滚动条配色：轨道半透明黑，滑块灰，hover/拖动时变白。 */
    private static final int COLOR_TRACK = 0x60000000;
    private static final int COLOR_TRACK_EDGE = 0xFF5A5F66;
    private static final int COLOR_THUMB = 0xFFA8B0B8;
    private static final int COLOR_THUMB_EDGE = 0xFF3A3F45;
    private static final int COLOR_THUMB_HOVER = 0xFFFFFFFF;

    private final Screen parent;
    /** 信标界面写回口；随身信标模式为 {@code null}。 */
    private final SogBeaconScreenAccess access;
    private final boolean primarySlot;
    /** 是否随身信标模式（确认即发包给服务端，最多选两个）。 */
    private final boolean portable;

    private final List<Holder<MobEffect>> allEffects = new ArrayList<>();
    private final List<Holder<MobEffect>> shown = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    /** 随身信标里已选的效果（按选择顺序，最多 {@value #MAX_PORTABLE_CHOICES} 个）。 */
    private final List<Holder<MobEffect>> chosen = new ArrayList<>();

    private EditBox searchBox;
    private String filter = "";
    private Holder<MobEffect> selected;
    private int scroll;
    private int left;
    private int panelWidth;
    private int cellWidth = CELL_WIDTH;
    private int gridTop;
    private int gridBottom;
    private int scrollbarX;
    /** 是否正按住滑块拖动。 */
    private boolean draggingThumb;
    /** 按下时鼠标相对滑块顶端的偏移，拖动时保持相对位置。 */
    private int thumbGrabOffset;
    /** 上一次构建时看到的「模式 + 名单」，用于在 Op 改动后自动重建。 */
    private String filterSnapshot = "";
    private Button modeButton;
    private Button confirmButton;

    /** 信标界面里的效果槽用这个构造（写回信标界面）。 */
    public BeaconEffectPickerScreen(Screen parent, SogBeaconScreenAccess access, boolean primarySlot) {
        this(parent, access, primarySlot, false);
    }

    /** 随身信标用这个构造：确认后把（最多两个）效果发给服务端。 */
    public static BeaconEffectPickerScreen portable(Screen parent) {
        return new BeaconEffectPickerScreen(parent, null, true, true);
    }

    private BeaconEffectPickerScreen(Screen parent, SogBeaconScreenAccess access,
            boolean primarySlot, boolean portable) {
        super(Component.translatable(portable
                ? "sog_carpet.ui.beacon.portable_title"
                : primarySlot ? "sog_carpet.ui.beacon.pick_primary"
                : "sog_carpet.ui.beacon.pick_secondary"));
        this.parent = parent;
        this.access = access;
        this.primarySlot = primarySlot;
        this.portable = portable;
        this.selected = access == null ? null
                : primarySlot ? access.sog$getPendingPrimary() : access.sog$getPendingSecondary();
    }

    /** 是否是随身信标打开的界面（规则中途被关掉时用它决定要不要自动关闭）。 */
    public boolean sog$isPortable() {
        return this.portable;
    }

    @Override
    protected void init() {
        if (this.allEffects.isEmpty()) {
            collectEffects();
        }
        // 先按窗口算出能用的格子宽度，再由它反推面板宽度，保证「4 列 + 滚动条」一定放得下。
        int maxGrid = Math.min(this.width - 40 - 2 * PANEL_PAD - SCROLLBAR_W - SCROLLBAR_GAP,
                COLUMNS * (CELL_WIDTH + CELL_GAP) - CELL_GAP);
        this.cellWidth = Math.max(MIN_CELL_WIDTH, (maxGrid - (COLUMNS - 1) * CELL_GAP) / COLUMNS);
        int gridWidth = COLUMNS * this.cellWidth + (COLUMNS - 1) * CELL_GAP;
        this.panelWidth = gridWidth + 2 * PANEL_PAD + SCROLLBAR_W + SCROLLBAR_GAP;
        this.left = (this.width - this.panelWidth) / 2;
        this.scrollbarX = this.left + PANEL_PAD + gridWidth + SCROLLBAR_GAP;
        this.gridTop = GRID_TOP;
        this.gridBottom = this.height - GRID_BOTTOM_MARGIN;

        this.searchBox = new EditBox(this.font, this.left + PANEL_PAD, SEARCH_Y, gridWidth,
                SEARCH_H, Component.translatable("sog_carpet.ui.beacon.search"));
        this.searchBox.setHint(Component.translatable("sog_carpet.ui.beacon.search_hint"));
        this.searchBox.setValue(this.filter);
        this.searchBox.setResponder(value -> {
            this.filter = value == null ? "" : value;
            // 换了搜索词就回到列表顶部，否则停在原来的滚动位置会看不到搜索结果。
            rebuild(true);
        });
        addRenderableWidget(this.searchBox);

        // Op：黑名单 / 白名单切换（独占工具栏左半边，不再压住任何文字）。
        // 随身信标不受名单影响，所以那里不显示模式按钮。
        // 服务端没装本模组时也显示：此时按钮改的是本客户端那一份名单（见 #switchMode）。
        this.modeButton = null;
        if (!portable && canEditFilter()) {
            this.modeButton = addRenderableWidget(Button.builder(modeLabel(), button -> switchMode())
                    .tooltip(Tooltip.create(Component.translatable(
                            "sog_carpet.ui.beacon.mode_hint")))
                    .bounds(this.left + PANEL_PAD, TOOLBAR_Y, 132, TOOLBAR_H).build());
        }

        int buttonY = this.height - BUTTON_MARGIN;
        addRenderableWidget(Button.builder(
                        Component.translatable(portable
                                ? "sog_carpet.ui.beacon.clear_portable"
                                : "sog_carpet.ui.beacon.clear"), button -> clear())
                .tooltip(Tooltip.create(Component.translatable(portable
                        ? "sog_carpet.ui.beacon.clear_portable_hint"
                        : "sog_carpet.ui.beacon.clear_hint")))
                .bounds(this.width / 2 - 118, buttonY, BUTTON_W, BUTTON_H).build());
        this.confirmButton = addRenderableWidget(Button.builder(
                        Component.translatable(portable
                                ? "sog_carpet.ui.beacon.apply" : "sog_carpet.ui.beacon.confirm"),
                        button -> confirm())
                .bounds(this.width / 2 - 38, buttonY, BUTTON_W, BUTTON_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.beacon.cancel"), button -> back())
                .bounds(this.width / 2 + 42, buttonY, BUTTON_W, BUTTON_H).build());

        this.filterSnapshot = currentFilterSnapshot();
        rebuild();
    }

    /** 收集全部效果并按显示名排序（只做一次）。 */
    private void collectEffects() {
        for (Identifier id : BuiltInRegistries.MOB_EFFECT.keySet()) {
            BuiltInRegistries.MOB_EFFECT.get(id).ifPresent(holder -> {
                if (holder.value() != null) {
                    this.allEffects.add(holder);
                }
            });
        }
        this.allEffects.sort(Comparator.comparing(
                holder -> holder.value().getDisplayName().getString(),
                String.CASE_INSENSITIVE_ORDER));
    }

    /** 名单 / 模式 / 相关规则的当前快照：一变就说明服务端同步下来了，需要重建列表。 */
    private String currentFilterSnapshot() {
        if (this.portable) {
            // 随身信标不受名单影响，只需要盯「自定义信标效果」这一条。
            return "portable|" + cn.blockforge.generated.sogcarpet.SogSettings.customBeaconEffects;
        }
        return SogBeaconFilter.mode().name()
                + "|" + SogBeaconFilter.entriesFor(
                        cn.blockforge.generated.sogcarpet.SogSettings.BeaconEffectFilterMode.BLACKLIST)
                + "|" + SogBeaconFilter.entriesFor(
                        cn.blockforge.generated.sogcarpet.SogSettings.BeaconEffectFilterMode.WHITELIST)
                + "|" + cn.blockforge.generated.sogcarpet.SogSettings.customBeaconEffects;
    }

    /** 这个格子是否被名单锁定（随身信标永不锁定）。 */
    private boolean locked(Holder<MobEffect> holder) {
        return !this.portable && SogBeaconFilter.locked(holder);
    }

    @Override
    public void tick() {
        super.tick();
        if (!currentFilterSnapshot().equals(this.filterSnapshot)) {
            this.filterSnapshot = currentFilterSnapshot();
            if (this.modeButton != null) {
                this.modeButton.setMessage(modeLabel());
            }
            rebuild();
        }
    }

    private Component modeLabel() {
        return Component.translatable("sog_carpet.ui.beacon.mode",
                Component.translatable("sog_carpet.ui.beacon.mode."
                        + SogBeaconFilter.mode().name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** 按搜索词与黑白名单重建按钮网格（保留当前滚动位置）。 */
    private void rebuild() {
        rebuild(false);
    }

    /**
     * 按搜索词与黑白名单重建按钮网格。
     *
     * <p>{@code resetScroll=false} 时保留滚动位置：右键把某个效果加进/移出名单后服务端会广播，
     * 界面随之重建，如果每次都跳回顶部，正在列表下方连点几个效果就会点到别的东西去。
     * 越界由 {@link #updateRows()} 负责夹回合法范围。</p>
     */
    private void rebuild(boolean resetScroll) {
        for (Button button : this.buttons) {
            removeWidget(button);
        }
        this.buttons.clear();
        this.shown.clear();

        String needle = this.filter.trim().toLowerCase();
        for (Holder<MobEffect> holder : this.allEffects) {
            boolean locked = locked(holder);
            // 列表里被锁定的效果仍然显示（标 [x] 且点不动），方便 Op 右键解锁。
            if (!needle.isEmpty()) {
                String name = holder.value().getDisplayName().getString().toLowerCase();
                String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
                if (!name.contains(needle) && !id.contains(needle)) {
                    continue;
                }
            }
            this.shown.add(holder);
        }
        if (resetScroll) {
            this.scroll = 0;
        }
        for (int i = 0; i < this.shown.size(); i++) {
            Holder<MobEffect> holder = this.shown.get(i);
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            Button button = Button.builder(label(holder), pressed -> onPick(holder))
                    .bounds(this.left + PANEL_PAD + column * (this.cellWidth + CELL_GAP),
                            row * (CELL_HEIGHT + CELL_GAP), this.cellWidth, CELL_HEIGHT).build();
            this.buttons.add(addRenderableWidget(button));
        }
        updateRows();
        refreshLabels();
    }

    /** 点一个格子：信标槽位是单选替换；随身信标是最多选两个（再点一次取消）。 */
    private void onPick(Holder<MobEffect> holder) {
        if (locked(holder)) {
            return;
        }
        if (!this.portable) {
            this.selected = holder;
            refreshLabels();
            return;
        }
        // 已经选过：再点一次就取消它（方便改成单选 → II 级）。
        for (int i = 0; i < this.chosen.size(); i++) {
            if (this.chosen.get(i).equals(holder)) {
                this.chosen.remove(i);
                refreshLabels();
                return;
            }
        }
        if (this.chosen.size() >= MAX_PORTABLE_CHOICES) {
            // 选满两个后再点新的：顶掉最早选的那个，玩家总能改主意，不需要先取消。
            this.chosen.remove(0);
        }
        this.chosen.add(holder);
        this.selected = holder;
        refreshLabels();
    }

    /**
     * 格子上的文字：选中的加标记，被 Op 禁用的加锁。
     *
     * <p>只用 ASCII 标记：原版字体没有 emoji 字形，画成 🔒 会显示成缺字的方框。</p>
     */
    private Component label(Holder<MobEffect> holder) {
        Component name = holder.value().getDisplayName();
        if (locked(holder)) {
            return Component.literal(LOCKED_MARK + " ").append(name);
        }
        if (this.portable) {
            int index = indexOfChosen(holder);
            if (index == 0) {
                return Component.literal(SLOT1_MARK + " ").append(name);
            }
            if (index == 1) {
                return Component.literal(SLOT2_MARK + " ").append(name);
            }
            return name;
        }
        return holder.equals(this.selected)
                ? Component.literal(CHOSEN_MARK).append(name)
                : name;
    }

    /** 这个效果在随身信标的选择里排第几个（-1 表示没选）。 */
    private int indexOfChosen(Holder<MobEffect> holder) {
        for (int i = 0; i < this.chosen.size(); i++) {
            if (this.chosen.get(i).equals(holder)) {
                return i;
            }
        }
        return -1;
    }

    private void refreshLabels() {
        for (int i = 0; i < this.buttons.size(); i++) {
            Holder<MobEffect> holder = this.shown.get(i);
            Button button = this.buttons.get(i);
            button.setMessage(label(holder));
            // 被禁用的效果不可点（清空/确定仍然可用）。
            button.active = button.visible && !locked(holder);
        }
        if (this.confirmButton != null) {
            boolean ready = this.portable
                    ? !this.chosen.isEmpty()
                    : this.selected != null && !locked(this.selected);
            this.confirmButton.active = ready;
        }
    }

    /** 按滚动位置摆放按钮，超出可视区域的隐藏。 */
    private void updateRows() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
        for (int i = 0; i < this.buttons.size(); i++) {
            int row = i / COLUMNS;
            int y = this.gridTop + row * (CELL_HEIGHT + CELL_GAP) - this.scroll;
            Button button = this.buttons.get(i);
            boolean visible = y + CELL_HEIGHT > this.gridTop && y < this.gridBottom;
            button.setY(y);
            button.visible = visible;
        }
        refreshLabels();
    }

    private int contentHeight() {
        int rows = (this.shown.size() + COLUMNS - 1) / COLUMNS;
        return rows * (CELL_HEIGHT + CELL_GAP);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (this.gridBottom - this.gridTop));
    }

    // ------------------------------------------------------------------ 滚动条

    private int trackTop() {
        return this.gridTop;
    }

    private int trackHeight() {
        return Math.max(1, this.gridBottom - this.gridTop);
    }

    /** 滑块高度：按「可视高度 / 内容高度」的比例，夹在 [MIN_THUMB, 轨道高]。 */
    private int thumbHeight() {
        int track = trackHeight();
        int content = contentHeight();
        if (content <= track) {
            return track;
        }
        int height = (int) Math.round(track * (track / (double) content));
        return Math.max(Math.min(MIN_THUMB, track), Math.min(track, height));
    }

    /** 滑块顶端 y：滚动量线性映射到轨道剩余行程。 */
    private int thumbTop() {
        int max = maxScroll();
        int travel = trackHeight() - thumbHeight();
        if (max <= 0 || travel <= 0) {
            return trackTop();
        }
        return trackTop() + (int) Math.round(travel * (this.scroll / (double) max));
    }

    /** 鼠标是否落在滚动条那一列（左右各放宽 2 像素，方便点中）。 */
    private boolean overScrollbar(double mouseX, double mouseY) {
        return maxScroll() > 0
                && mouseX >= this.scrollbarX - 2
                && mouseX <= this.scrollbarX + SCROLLBAR_W + 2
                && mouseY >= trackTop()
                && mouseY <= trackTop() + trackHeight();
    }

    /** 按滑块顶端位置反算滚动量（点轨道跳转与拖动共用）。 */
    private void scrollToThumbTop(int thumbTop) {
        int travel = trackHeight() - thumbHeight();
        int max = maxScroll();
        if (travel <= 0 || max <= 0) {
            return;
        }
        int local = Math.max(0, Math.min(travel, thumbTop - trackTop()));
        this.scroll = (int) Math.round(max * (local / (double) travel));
        updateRows();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 右键某个效果：在「当前模式」那一份名单里加入 / 移除它（26.3 的右键是 SDL 编号 3）。
        // 随身信标不受名单影响，所以那里不提供这个操作。
        if (!this.portable && SogMouseButtons.isRight(event)) {
            Holder<MobEffect> hit = effectAt(event.x(), event.y());
            if (hit != null) {
                onFilterRightClick(hit);
                return true;
            }
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (!SogMouseButtons.isLeft(event) || !overScrollbar(event.x(), event.y())) {
            return false;
        }
        int height = thumbHeight();
        int top = thumbTop();
        if (event.y() >= top && event.y() <= top + height) {
            // 按在滑块上：记住相对偏移，拖动时不跳。
            this.thumbGrabOffset = (int) (event.y() - top);
        } else {
            // 按在轨道空白：先把滑块中心移到鼠标处，再继续拖动。
            this.thumbGrabOffset = height / 2;
            scrollToThumbTop((int) (event.y() - this.thumbGrabOffset));
        }
        this.draggingThumb = true;
        return true;
    }

    /**
     * 右键一个效果格子：在当前模式对应的那一份名单里加入 / 移出它。
     *
     * <p>方向由“这个效果是否已经列在当前模式那一份名单里”决定，不能看 {@link #locked}：
     * 黑名单模式下 locked 表示“已列在黑名单”，白名单模式正好相反。已列 → 移出，未列 → 加入，
     * 两种模式语义一致，右键永远是“加入/移出”的开关。</p>
     */
    private void onFilterRightClick(Holder<MobEffect> hit) {
        String id = SogBeaconFilter.idOf(hit);
        if (id == null) {
            return;
        }
        SogSettings.BeaconEffectFilterMode mode = SogBeaconFilter.mode();
        boolean listed = SogBeaconFilter.listed(mode, id);
        if (editingLocalFilter()) {
            // 服务端没装本模组：没有能与我们对齐的规则，直接改客户端本地那一份并立刻重画。
            if (mode == SogSettings.BeaconEffectFilterMode.WHITELIST) {
                SogSettings.beaconEffectWhitelist = SogBeaconFilter.withMember(mode, id, !listed);
            } else {
                SogSettings.beaconEffectBlacklist = SogBeaconFilter.withMember(mode, id, !listed);
            }
            this.filterSnapshot = currentFilterSnapshot();
            rebuild();
            showMessage(Component.translatable(listed
                    ? "sog_carpet.ui.beacon.filter_removed_client"
                    : "sog_carpet.ui.beacon.filter_added_client", hit.value().getDisplayName()));
            return;
        }
        if (!VisualizerState.canControlRules()) {
            // 服务端装了本模组，但不是 OP：明确告诉玩家为什么点了没反应。
            showMessage(Component.translatable("sog_carpet.ui.beacon.filter_denied"));
            return;
        }
        if (!ClientPlayNetworking.canSend(BeaconFilterPayload.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(new BeaconFilterPayload(
                listed ? BeaconFilterPayload.ACTION_REMOVE : BeaconFilterPayload.ACTION_ADD,
                mode.name() + BeaconFilterPayload.SEPARATOR + id));
        // 给一条即时反馈：即使服务端拒绝（比如权限不够）也不会“点了没反应”。
        showMessage(Component.translatable(listed ? "sog_carpet.ui.beacon.filter_removed"
                : "sog_carpet.ui.beacon.filter_added", hit.value().getDisplayName()));
    }

    /**
     * 切换黑名单 / 白名单。
     *
     * <p>服务端装了本模组时发给服务端（由它校验 Op 后改 carpet 规则、必要时给空白名单补上原版六种效果，
     * 再广播回来）；服务端没装时直接翻本客户端的那一份，逻辑与服务端
     * {@code SogBeaconFilterControl#setMode} 保持一致。</p>
     */
    private void switchMode() {
        if (!editingLocalFilter()) {
            if (ClientPlayNetworking.canSend(BeaconFilterPayload.TYPE)) {
                ClientPlayNetworking.send(new BeaconFilterPayload(
                        BeaconFilterPayload.ACTION_MODE, ""));
            }
            return;
        }
        SogSettings.BeaconEffectFilterMode next =
                SogBeaconFilter.mode() == SogSettings.BeaconEffectFilterMode.BLACKLIST
                        ? SogSettings.BeaconEffectFilterMode.WHITELIST
                        : SogSettings.BeaconEffectFilterMode.BLACKLIST;
        SogSettings.beaconEffectFilterMode = next;
        // 切到白名单时白名单若是空的，先填上原版信标那六种：否则界面上一项可选都没有，看起来像坏了。
        if (next == SogSettings.BeaconEffectFilterMode.WHITELIST
                && SogBeaconFilter.entriesFor(next).isEmpty()) {
            SogSettings.beaconEffectWhitelist = SogBeaconFilter.normalize(
                    String.join(",", SogBeaconHelper.vanillaBeaconEffectIds()));
        }
        if (this.modeButton != null) {
            this.modeButton.setMessage(modeLabel());
        }
        this.filterSnapshot = currentFilterSnapshot();
        rebuild();
    }

    /** 在物品栏上方提示一行文字；没有玩家（比如脱离世界）时静默跳过。 */
    private static void showMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(message);
        }
    }

    /**
     * 现在能不能改名单：服务端装了本模组时要 Op；服务端根本没装时改的是本客户端那一份。
     *
     * <p>两种情况都不是时（服务端装了但自己不是 Op）保持只读，并给出 {@code filter_denied} 提示。</p>
     */
    private boolean canEditFilter() {
        return VisualizerState.canControlRules() || editingLocalFilter();
    }

    /**
     * 名单是否只在客户端本地生效（服务端没装本模组，改动不会同步给任何人）。
     *
     * <p>单人 / 局域网里“服务端”和客户端是同一个进程、同一份静态字段，绝不能把它当成
     * “仅本客户端的副本”去改（那样会绕过 carpet 规则、也不会落盘），所以这种情况一律返回
     * {@code false}：规则同步到达之前右键会提示需要 Op 权限，同步到达后立刻恢复正常。</p>
     */
    private boolean editingLocalFilter() {
        if (this.minecraft != null && this.minecraft.hasSingleplayerServer()) {
            return false;
        }
        return !VisualizerState.serverHasSogRules();
    }

    /**
     * 鼠标位置下的效果格子（右键用）；没命中返回 null。
     *
     * <p><b>不能直接调 {@code AbstractWidget#isMouseOver}：</b>本版本里它的实现是
     * {@code isActive() && 矩形判定}，而“被名单锁定”的效果正是 {@code active = false}，
     * 于是锁定格永远命中不到——黑名单里的效果右键删不掉、白名单外的效果右键加不进来。
     * 右键编辑恰恰要求锁定格也能点中，所以这里只看可见性与矩形范围。</p>
     */
    private Holder<MobEffect> effectAt(double mouseX, double mouseY) {
        for (int i = 0; i < this.buttons.size(); i++) {
            Button button = this.buttons.get(i);
            if (!button.visible) {
                continue;
            }
            int x = button.getX();
            int y = button.getY();
            if (mouseX >= x && mouseX < x + button.getWidth()
                    && mouseY >= y && mouseY < y + button.getHeight()) {
                return this.shown.get(i);
            }
        }
        return null;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingThumb) {
            scrollToThumbTop((int) (event.y() - this.thumbGrabOffset));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingThumb) {
            this.draggingThumb = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= this.gridTop && mouseY <= this.gridBottom && maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(maxScroll(),
                    this.scroll - (int) Math.round(scrollY * (CELL_HEIGHT + CELL_GAP))));
            updateRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void confirm() {
        if (this.portable) {
            if (this.chosen.isEmpty()) {
                return;
            }
            String primary = SogBeaconFilter.idOf(this.chosen.get(0));
            String secondary = this.chosen.size() > 1 ? SogBeaconFilter.idOf(this.chosen.get(1)) : "";
            // 对面没声明这条通道（例如连到没装本模组的服务器）时不发包，避免协议错误。
            if (primary != null && ClientPlayNetworking.canSend(PortableBeaconUsePayload.TYPE)) {
                ClientPlayNetworking.send(new PortableBeaconUsePayload(primary,
                        secondary == null ? "" : secondary));
            }
            back();
            return;
        }
        if (this.selected == null || locked(this.selected)) {
            return;
        }
        this.access.sog$setPendingEffect(this.selected, this.primarySlot);
        back();
    }

    /**
     * 清空。
     *
     * <p>信标槽位：把这个槽位清空（回到「+」）。原版没有“取消单个效果”的操作，这里补一个。</p>
     * <p>随身信标：等于“关闭效果”——把空效果发给服务端，服务端撤掉已经发出去的那两条并忘掉
     * 这次选择（之后即使背包里还有信标也不会再自动恢复）。</p>
     */
    private void clear() {
        this.selected = null;
        this.chosen.clear();
        refreshLabels();
        if (!this.portable) {
            this.access.sog$setPendingEffect(null, this.primarySlot);
            back();
            return;
        }
        if (ClientPlayNetworking.canSend(PortableBeaconUsePayload.TYPE)) {
            ClientPlayNetworking.send(new PortableBeaconUsePayload("", ""));
        }
        back();
    }

    private void back() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
            float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xC0101014);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        // 标题：单独一条横带，居中，不与任何控件相邻。
        extractor.centeredText(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFFFF);
        // 说明：单行，按面板宽度截断。
        Component hint = Component.translatable(this.portable
                ? "sog_carpet.ui.beacon.hint_portable" : "sog_carpet.ui.beacon.hint");
        extractor.text(this.font, Component.literal(
                        fit(hint.getString(), this.panelWidth - 2 * PANEL_PAD)),
                this.left + PANEL_PAD, HINT_Y, 0xFFA8B0B8, false);
        // Op / 本地提示：与模式按钮同一行、按钮右侧，宽度也被面板右边界限制。
        if (!this.portable && canEditFilter()) {
            int hintX = this.left + PANEL_PAD + 138;
            int hintWidth = this.left + this.panelWidth - PANEL_PAD - hintX;
            Component op = Component.translatable(editingLocalFilter()
                    ? "sog_carpet.ui.beacon.op_hint_client"
                    : "sog_carpet.ui.beacon.op_hint",
                    Component.translatable("sog_carpet.ui.beacon.mode."
                            + SogBeaconFilter.mode().name().toLowerCase(java.util.Locale.ROOT)),
                    SogBeaconFilter.entries().size());
            extractor.text(this.font, Component.literal(fit(op.getString(), hintWidth)),
                    hintX, TOOLBAR_Y + 5, 0xFF9BE29B, false);
        }
        // 白名单为空时给出可操作提示，否则“所有效果都锁着”看起来像坏了。
        if (!this.portable && SogBeaconFilter.mode() == SogSettings.BeaconEffectFilterMode.WHITELIST
                && SogBeaconFilter.entries().isEmpty()) {
            extractor.text(this.font, Component.literal(fit(
                            Component.translatable("sog_carpet.ui.beacon.whitelist_empty").getString(),
                            this.panelWidth - 2 * PANEL_PAD)),
                    this.left + PANEL_PAD, this.height - SELECTED_MARGIN - 12, 0xFFFF9C6B, false);
        }
        // 已选效果：单独一行，位于网格与按钮之间。
        extractor.text(this.font, Component.literal(
                        fit(selectionText().getString(), this.panelWidth - 2 * PANEL_PAD)),
                this.left + PANEL_PAD, this.height - SELECTED_MARGIN, 0xFFFFE082, false);
        drawScrollbar(extractor, mouseX, mouseY);
    }

    /** 已选效果那一行的文字。 */
    private Component selectionText() {
        if (this.portable) {
            if (this.chosen.isEmpty()) {
                return Component.translatable("sog_carpet.ui.beacon.selected_none");
            }
            if (this.chosen.size() == 1) {
                return Component.translatable("sog_carpet.ui.beacon.selected_one",
                        this.chosen.get(0).value().getDisplayName());
            }
            return Component.translatable("sog_carpet.ui.beacon.selected_two",
                    this.chosen.get(0).value().getDisplayName(),
                    this.chosen.get(1).value().getDisplayName());
        }
        Component current = this.selected == null
                ? Component.translatable("sog_carpet.ui.beacon.none")
                : this.selected.value().getDisplayName();
        return Component.translatable("sog_carpet.ui.beacon.current", current);
    }

    /** 把文字截断到指定像素宽度以内（原版字体没有省略号宽度保证，超长直接截断）。 */
    private String fit(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (maxWidth <= 0 || this.font.width(text) <= maxWidth) {
            return text;
        }
        String clipped = this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - 3));
        return clipped + "…";
    }

    /** 画滚动条：半透明轨道 + 滑块，hover 或拖动时滑块变白。内容装得下时不画。 */
    private void drawScrollbar(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        if (maxScroll() <= 0) {
            return;
        }
        int top = trackTop();
        int height = trackHeight();
        int right = this.scrollbarX + SCROLLBAR_W;
        extractor.fill(this.scrollbarX, top, right, top + height, COLOR_TRACK);
        extractor.fill(this.scrollbarX, top, right, top + 1, COLOR_TRACK_EDGE);
        extractor.fill(this.scrollbarX, top + height - 1, right, top + height, COLOR_TRACK_EDGE);

        int thumbHeight = thumbHeight();
        int thumbY = thumbTop();
        boolean highlight = this.draggingThumb || overScrollbar(mouseX, mouseY);
        int color = highlight ? COLOR_THUMB_HOVER : COLOR_THUMB;
        extractor.fill(this.scrollbarX, thumbY, right, thumbY + thumbHeight, color);
        extractor.fill(this.scrollbarX, thumbY, this.scrollbarX + 1, thumbY + thumbHeight,
                COLOR_THUMB_EDGE);
        extractor.fill(right - 1, thumbY, right, thumbY + thumbHeight, COLOR_THUMB_EDGE);
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停世界：信标光柱与效果还在后面动着，方便边看边挑。
        return false;
    }
}

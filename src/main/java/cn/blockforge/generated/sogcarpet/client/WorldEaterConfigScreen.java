package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * “世吞小助手”的配置界面（Ctrl+E 打开，只在规则开启后可开）。
 *
 * <p>界面分三块：</p>
 * <ul>
 *   <li>顶部：全局“自动含水方块”开关（默认开，等价于原来的行为）、“显示被覆盖方块”
 *       开关（默认开，被上方方块完全盖住的补挖方块也照常标注，幽灵原位埋在实心方块里
 *       时会自动抬到空位显示）、恢复默认、完成；</li>
 *   <li>中间：方块白名单列表（{@link WorldEaterBlockList}），勾选框决定标不标，
 *       色块点一下换分类，“含水”开关决定可含水方块的含水状态标不标（标成含水的方块
 *       渲染时外面会套一层半透明水壳），“×”移除；</li>
 *   <li>底部：输入方块 id 点“添加方块”加进白名单，支持省略 {@code minecraft:} 前缀。</li>
 * </ul>
 *
 * <p>原来的渲染逻辑完全保留：不改任何设置时，标注的方块与原实现一致。这里只是把
 * “标哪些方块”交出来给玩家。改动即时生效并写入 {@code config/sog_carpet-world_eater.txt}。</p>
 */
public final class WorldEaterConfigScreen extends Screen {
    private WorldEaterBlockList list;
    private EditBox searchBox;
    private EditBox liftBox;
    private Button autoButton;
    private Button hiddenButton;
    private Button heightLimitButton;
    private Button distanceButton;
    private int left;
    private int listWidth = 420;
    private String filter = "";
    private Component status;
    private int statusColor = 0xFF9AD17A;
    private final Screen parent;

    public WorldEaterConfigScreen() {
        this(null);
    }

    public WorldEaterConfigScreen(Screen parent) {
        super(Component.translatable("sog_carpet.ui.world_eater.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        WorldEaterConfig.ensureLoaded();
        this.listWidth = Math.max(200, Math.min(this.width - 16, 420));
        this.left = Math.max(8, (this.width - this.listWidth) / 2);
        int gap = 6;
        // 三行控件全部按 listWidth 动态分配宽度，窄屏时一起收缩，保证互不重叠。
        int doneWidth = Math.max(48, Math.min(64, this.listWidth / 6));
        int resetWidth = Math.max(48, Math.min(76, this.listWidth / 5));
        int autoWidth = Math.max(70, this.listWidth - doneWidth - resetWidth - 2 * gap);

        this.autoButton = Button.builder(autoLabel(), button -> {
            WorldEaterConfig.setAutoWaterlogged(!WorldEaterConfig.isAutoWaterlogged());
            button.setMessage(autoLabel());
            this.list.refresh();
        }).bounds(this.left, 36, autoWidth, 20).build();
        addRenderableWidget(this.autoButton);

        // 被上方方块完全盖住的补挖方块要不要照常标出来（默认开：世吞的机器就压在上面；
        // 幽灵原位如果还埋在实心方块里，渲染时会自动抬到最近的空位并置顶）。
        int hiddenWidth = Math.max(80, this.listWidth - doneWidth - gap);
        this.hiddenButton = Button.builder(hiddenLabel(), button -> {
            WorldEaterConfig.setMarkHidden(!WorldEaterConfig.isMarkHidden());
            button.setMessage(hiddenLabel());
        }).bounds(this.left, 60, hiddenWidth, 20).build();
        addRenderableWidget(this.hiddenButton);

        // 高度限制：抬得太高（≥ 阈值）的幽灵方块不画。默认开、阈值 8 格，可以手动输入。
        int valueWidth = Math.max(28, Math.min(42, this.listWidth / 8));
        int applyWidth = Math.max(38, Math.min(48, this.listWidth / 7));
        int heightWidth = Math.max(60,
                this.listWidth - valueWidth - applyWidth - 2 * gap);
        this.heightLimitButton = Button.builder(heightLimitLabel(), button -> {
            WorldEaterConfig.setHeightLimit(!WorldEaterConfig.isHeightLimit());
            button.setMessage(heightLimitLabel());
            if (this.liftBox != null) {
                this.liftBox.active = WorldEaterConfig.isHeightLimit();
            }
            setStatus(Component.translatable(WorldEaterConfig.isHeightLimit()
                    ? "sog_carpet.ui.world_eater.height_on_done"
                    : "sog_carpet.ui.world_eater.height_off_done"), 0xFF9AD17A);
        }).bounds(this.left, 84, heightWidth, 20).build();
        addRenderableWidget(this.heightLimitButton);

        this.liftBox = new EditBox(this.font, this.left + heightWidth + gap, 84, valueWidth, 20,
                Component.translatable("sog_carpet.ui.world_eater.height_value"));
        this.liftBox.setMaxLength(3);
        this.liftBox.setValue(Integer.toString(WorldEaterConfig.maxGhostLift()));
        this.liftBox.setHint(Component.translatable("sog_carpet.ui.world_eater.height_value"));
        // 输入合法数字就立即生效；不合法时回填当前值，避免留在框里误导。
        this.liftBox.setResponder(text -> {
            int value = WorldEaterConfig.parseGhostLift(text);
            if (value > 0) {
                WorldEaterConfig.setMaxGhostLift(value);
            }
        });
        this.liftBox.active = WorldEaterConfig.isHeightLimit();
        addRenderableWidget(this.liftBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.world_eater.height_apply"), button -> {
                            int value = WorldEaterConfig.parseGhostLift(this.liftBox.getValue());
                            if (value <= 0) {
                                this.liftBox.setValue(
                                        Integer.toString(WorldEaterConfig.maxGhostLift()));
                                setStatus(Component.translatable(
                                        "sog_carpet.ui.world_eater.height_invalid"), 0xFFFF8080);
                                return;
                            }
                            WorldEaterConfig.setMaxGhostLift(value);
                            this.liftBox.setValue(Integer.toString(value));
                            setStatus(Component.translatable(
                                    "sog_carpet.ui.world_eater.height_saved", value), 0xFF9AD17A);
                        })
                .bounds(this.left + heightWidth + gap + valueWidth + gap, 84, applyWidth, 20)
                .build());

        // 真方块 / 映射方块距离标注（默认关闭）：单独一行，避免和上面三行挤在一起。
        this.distanceButton = Button.builder(distanceLabel(), button -> {
            WorldEaterConfig.setDistanceShown(!WorldEaterConfig.isDistanceShown());
            button.setMessage(distanceLabel());
            setStatus(Component.translatable(WorldEaterConfig.isDistanceShown()
                    ? "sog_carpet.ui.world_eater.distance_on_done"
                    : "sog_carpet.ui.world_eater.distance_off_done"), 0xFF9AD17A);
        }).bounds(this.left, 108, Math.min(220, this.listWidth), 20).build();
        addRenderableWidget(this.distanceButton);

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.world_eater.reset"), button -> {
                            WorldEaterConfig.resetDefaults();
                            this.list.refresh();
                            this.autoButton.setMessage(autoLabel());
                            this.hiddenButton.setMessage(hiddenLabel());
                            if (this.distanceButton != null) {
                                this.distanceButton.setMessage(distanceLabel());
                            }
                            if (this.heightLimitButton != null) {
                                this.heightLimitButton.setMessage(heightLimitLabel());
                            }
                            if (this.liftBox != null) {
                                this.liftBox.setValue(
                                        Integer.toString(WorldEaterConfig.maxGhostLift()));
                                this.liftBox.active = WorldEaterConfig.isHeightLimit();
                            }
                            setStatus(Component.translatable("sog_carpet.ui.world_eater.reset_done"),
                                    0xFF9AD17A);
                        })
                .bounds(this.left + autoWidth + gap, 36, resetWidth, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.world_eater.done"), button -> onClose())
                .bounds(this.left + this.listWidth - doneWidth, 36, doneWidth, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.color.button"),
                        button -> VisualizerState.openColorScreen("worldEaterHelper", this))
                .bounds(this.left + this.listWidth - doneWidth, 60, doneWidth, 20)
                .build());

        int listTop = 134;
        int listBottom = this.height - 52;
        this.list = new WorldEaterBlockList(this.left, listTop, this.listWidth,
                Math.max(40, listBottom - listTop), this::onListChanged);
        this.list.setFilter(this.filter);
        addRenderableWidget(this.list);

        this.searchBox = new EditBox(this.font, this.left, this.height - 46,
                Math.max(60, this.listWidth - 96), 18,
                Component.translatable("sog_carpet.ui.world_eater.search"));
        this.searchBox.setHint(Component.translatable("sog_carpet.ui.world_eater.search_hint"));
        this.searchBox.setValue(this.filter);
        this.searchBox.setResponder(value -> {
            this.filter = value;
            this.list.setFilter(value);
        });
        addRenderableWidget(this.searchBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.world_eater.add"), button -> addFromSearch())
                .bounds(this.left + this.listWidth - 92, this.height - 46, 92, 18)
                .build());
    }

    private static Component autoLabel() {
        return Component.translatable(WorldEaterConfig.isAutoWaterlogged()
                ? "sog_carpet.ui.world_eater.auto_on"
                : "sog_carpet.ui.world_eater.auto_off");
    }

    private static Component heightLimitLabel() {
        return Component.translatable(WorldEaterConfig.isHeightLimit()
                ? "sog_carpet.ui.world_eater.height_on"
                : "sog_carpet.ui.world_eater.height_off");
    }

    private static Component distanceLabel() {
        return Component.translatable(WorldEaterConfig.isDistanceShown()
                ? "sog_carpet.ui.world_eater.distance_on"
                : "sog_carpet.ui.world_eater.distance_off");
    }

    private static Component hiddenLabel() {
        return Component.translatable(WorldEaterConfig.isMarkHidden()
                ? "sog_carpet.ui.world_eater.hidden_on"
                : "sog_carpet.ui.world_eater.hidden_off");
    }

    private void onListChanged() {
        // 列表自身已经改好数据，这里只需要保持顶部开关的文案同步。
        if (this.autoButton != null) {
            this.autoButton.setMessage(autoLabel());
        }
        if (this.hiddenButton != null) {
            this.hiddenButton.setMessage(hiddenLabel());
        }
    }

    private void addFromSearch() {
        String text = this.searchBox == null ? "" : this.searchBox.getValue();
        Identifier id = WorldEaterConfig.parseId(text);
        if (id == null) {
            setStatus(Component.translatable("sog_carpet.ui.world_eater.invalid"), 0xFFFF8080);
            return;
        }
        if (WorldEaterConfig.blockOf(id) == null) {
            setStatus(Component.translatable("sog_carpet.ui.world_eater.unknown", text.trim()),
                    0xFFFF8080);
            return;
        }
        WorldEaterConfig.addEntry(id);
        this.searchBox.setValue("");
        this.filter = "";
        this.list.setFilter("");
        this.list.highlight(id);
        setStatus(Component.translatable("sog_carpet.ui.world_eater.added",
                WorldEaterConfig.displayName(id)), 0xFF9AD17A);
    }

    private void setStatus(Component message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
            float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xB0101014);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
        String hint = Component.translatable("sog_carpet.ui.world_eater.hint").getString();
        if (this.font != null && this.font.width(hint) > this.listWidth) {
            hint = this.font.plainSubstrByWidth(hint, Math.max(0, this.listWidth - 6)) + "…";
        }
        extractor.text(this.font, Component.literal(hint),
                this.left, 20, 0xFFA8B0B8, false);

        // 表格列头：跟列表的实际列位置严格对齐，避免出现“文字压在一起”的观感。
        int headerY = this.list == null ? 62 : this.list.getY() - 11;
        extractor.text(this.font, Component.translatable("sog_carpet.ui.world_eater.col_render"),
                WorldEaterBlockList.checkColumnX(this.left), headerY, 0xFF90C8E8, false);
        extractor.text(this.font, Component.translatable("sog_carpet.ui.world_eater.col_block"),
                WorldEaterBlockList.nameColumnX(this.left), headerY, 0xFF90C8E8, false);
        extractor.text(this.font, Component.translatable("sog_carpet.ui.world_eater.col_category"),
                WorldEaterBlockList.catColumnX(this.left, this.listWidth), headerY, 0xFF90C8E8,
                false);
        extractor.text(this.font, Component.translatable("sog_carpet.ui.world_eater.col_water"),
                WorldEaterBlockList.waterColumnX(this.left, this.listWidth), headerY, 0xFF90C8E8,
                false);

        Component footer = this.status != null
                ? this.status
                : Component.translatable("sog_carpet.ui.world_eater.footer");
        extractor.text(this.font, footer, this.left, this.height - 16,
                this.status != null ? this.statusColor : 0xFF808A92, false);
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停世界：配置的同时还能看着世界里的幽灵方块实时变化。
        return false;
    }

    @Override
    public void removed() {
        WorldEaterConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        WorldEaterConfig.save();
        if (this.parent != null && this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        } else {
            super.onClose();
        }
    }
}

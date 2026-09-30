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
 *   <li>顶部：全局“自动含水方块”开关（默认开，等价于原来的行为）、恢复默认、完成；</li>
 *   <li>中间：方块白名单列表（{@link WorldEaterBlockList}），勾选框决定标不标，
 *       色块点一下换分类，“含水”开关决定可含水方块的含水状态标不标，“×”移除；</li>
 *   <li>底部：输入方块 id 点“添加方块”加进白名单，支持省略 {@code minecraft:} 前缀。</li>
 * </ul>
 *
 * <p>原来的渲染逻辑完全保留：不改任何设置时，标注的方块与原实现一致。这里只是把
 * “标哪些方块”交出来给玩家。改动即时生效并写入 {@code config/sog_carpet-world_eater.txt}。</p>
 */
public final class WorldEaterConfigScreen extends Screen {
    private WorldEaterBlockList list;
    private EditBox searchBox;
    private Button autoButton;
    private int left;
    private int listWidth = 420;
    private String filter = "";
    private Component status;
    private int statusColor = 0xFF9AD17A;

    public WorldEaterConfigScreen() {
        super(Component.translatable("sog_carpet.ui.world_eater.title"));
    }

    @Override
    protected void init() {
        WorldEaterConfig.ensureLoaded();
        this.listWidth = Math.min(this.width - 40, 420);
        this.left = (this.width - this.listWidth) / 2;

        this.autoButton = Button.builder(autoLabel(), button -> {
            WorldEaterConfig.setAutoWaterlogged(!WorldEaterConfig.isAutoWaterlogged());
            button.setMessage(autoLabel());
            this.list.refresh();
        }).bounds(this.left, 36, 130, 20).build();
        addRenderableWidget(this.autoButton);

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.world_eater.reset"), button -> {
                            WorldEaterConfig.resetDefaults();
                            this.list.refresh();
                            this.autoButton.setMessage(autoLabel());
                            setStatus(Component.translatable("sog_carpet.ui.world_eater.reset_done"),
                                    0xFF9AD17A);
                        })
                .bounds(this.left + 136, 36, 76, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.world_eater.done"), button -> onClose())
                .bounds(this.left + this.listWidth - 64, 36, 64, 20)
                .build());

        int listTop = 74;
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

    private void onListChanged() {
        // 列表自身已经改好数据，这里只需要保持顶部开关的文案同步。
        if (this.autoButton != null) {
            this.autoButton.setMessage(autoLabel());
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
        extractor.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        extractor.text(this.font, Component.translatable("sog_carpet.ui.world_eater.hint"),
                this.left, 22, 0xFFA8B0B8, false);

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
        super.onClose();
    }
}

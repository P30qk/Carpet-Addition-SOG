package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * “敌对生物仇恨范围可视化”的配置界面（从 Ctrl+V 总控里点本规则的“配置”进入）。
 *
 * <p>中间是可滚动的逐生物开关列表（{@link AggroRangeMobList}），顶部有搜索框，输入名字或
 * 实体 ID 即可过滤；每一行点一下就能单独开关这个生物的仇恨范围球。默认全部开启，
 * 改动即时生效并写入 {@code config/sog_carpet-aggro-range.txt}，不暂停世界。</p>
 */
public final class AggroRangeConfigScreen extends Screen {
    private AggroRangeMobList list;
    private EditBox searchBox;
    private int left;
    private int listWidth = 380;
    private String filter = "";
    private final Screen parent;

    public AggroRangeConfigScreen() {
        this(null);
    }

    public AggroRangeConfigScreen(Screen parent) {
        super(Component.translatable("sog_carpet.ui.aggro.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AggroRangeConfig.ensure();
        this.listWidth = Math.max(200, Math.min(this.width - 16, 380));
        this.left = Math.max(8, (this.width - this.listWidth) / 2);
        int buttonHeight = 20;
        // 第一行：恢复默认 + 颜色；第二行：模式 + Done。这样窄屏时 Done 不会压在模式按钮上。
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.aggro.reset"), button -> {
                            AggroRangeConfig.resetDefaults();
                            if (this.list != null) {
                                this.list.refresh();
                            }
                        })
                .bounds(this.left, 36, 90, buttonHeight)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.color.button"),
                        button -> VisualizerState.openColorScreen("aggroRangeVisualizer", this))
                .bounds(this.left + 96, 36, 70, buttonHeight)
                .build());

        int modeWidth = Math.max(80, this.listWidth - 74);
        addRenderableWidget(Button.builder(modeLabel(), button -> {
            boolean next = !VisualizerConfig.option("aggroRangeVisualizer", "selectMode", false);
            VisualizerConfig.setOption("aggroRangeVisualizer", "selectMode", next);
            button.setMessage(modeLabel());
        }).bounds(this.left, 60, modeWidth, buttonHeight).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.aggro.done"), button -> onClose())
                .bounds(this.left + this.listWidth - 70, 60, 70, buttonHeight)
                .build());

        int listTop = 86;
        int listBottom = this.height - 46;
        this.list = new AggroRangeMobList(this.left, listTop, this.listWidth,
                Math.max(40, listBottom - listTop), () -> {
                });
        this.list.setFilter(this.filter);
        addRenderableWidget(this.list);

        this.searchBox = new EditBox(this.font, this.left, this.height - 40,
                this.listWidth, 18,
                Component.translatable("sog_carpet.ui.aggro.search"));
        this.searchBox.setHint(Component.translatable("sog_carpet.ui.aggro.search_hint"));
        this.searchBox.setValue(this.filter);
        this.searchBox.setResponder(value -> {
            this.filter = value;
            this.list.setFilter(value);
        });
        addRenderableWidget(this.searchBox);
    }

    @Override
    public void render(GuiGraphics extractor, int mouseX, int mouseY,
            float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xB0101014);
        super.render(extractor, mouseX, mouseY, partialTick);
        extractor.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
        extractor.drawString(this.font, Component.translatable("sog_carpet.ui.aggro.hint"),
                this.left, 20, 0xFFA8B0B8, false);
    }

    /** 模式按钮文字：全部模式 / 选择模式。 */
    private static Component modeLabel() {
        return Component.translatable(VisualizerConfig.option("aggroRangeVisualizer", "selectMode", false)
                ? "sog_carpet.ui.aggro.mode_select"
                : "sog_carpet.ui.aggro.mode_all");
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
        // 不暂停世界：开关某个生物时能立刻看到世界里圆球的变化。
        return false;
    }
}

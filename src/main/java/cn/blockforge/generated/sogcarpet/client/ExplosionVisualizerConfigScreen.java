package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * “爆炸可视化”的配置界面（从 Ctrl+V 总控里点本规则的“配置”进入）。
 *
 * <p>一列开关，与 lucidity 的 ExplosionVisualizerConfigs 一一对应，末尾多一个本模组新增的
 * “渲染圆球”开关。改动即时生效并写入 {@code config/sog_carpet-explosion.txt}，不暂停世界。</p>
 */
public final class ExplosionVisualizerConfigScreen extends Screen {
    private static final int COLUMN_WIDTH = 280;
    private static final int ROW = 24;
    private static final int TOP = 44;

    private int left;
    private int columnWidth = COLUMN_WIDTH;
    private int rowHeight = ROW;
    private int buttonHeight = 20;
    private final Screen parent;

    public ExplosionVisualizerConfigScreen() {
        this(null);
    }

    public ExplosionVisualizerConfigScreen(Screen parent) {
        super(Component.translatable("sog_carpet.ui.explosion.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ExplosionVisualizerConfig.ensure();
        int panelWidth = Math.min(COLUMN_WIDTH, Math.max(200, this.width - 16));
        this.columnWidth = panelWidth;
        this.left = Math.max(8, (this.width - panelWidth) / 2);
        int top = this.height < 240 ? 36 : TOP;
        this.rowHeight = Math.max(12, Math.min(ROW, (this.height - top - 26) / 7));
        this.buttonHeight = Math.max(14, Math.min(20, this.rowHeight));
        int y = top;
        addToggle(y, ExplosionVisualizerConfig::blockDestruction,
                ExplosionVisualizerConfig::setBlockDestruction, "sog_carpet.ui.explosion.block_destruction");
        y += this.rowHeight;
        addToggle(y, ExplosionVisualizerConfig::blockRayCast,
                ExplosionVisualizerConfig::setBlockRayCast, "sog_carpet.ui.explosion.block_ray_cast");
        y += this.rowHeight;
        addToggle(y, ExplosionVisualizerConfig::entitySamplePoints,
                ExplosionVisualizerConfig::setEntitySamplePoints,
                "sog_carpet.ui.explosion.entity_sample_points");
        y += this.rowHeight;
        addToggle(y, ExplosionVisualizerConfig::samplePointRayCast,
                ExplosionVisualizerConfig::setSamplePointRayCast,
                "sog_carpet.ui.explosion.sample_point_ray_cast");
        y += this.rowHeight;
        addToggle(y, ExplosionVisualizerConfig::explosionCenter,
                ExplosionVisualizerConfig::setExplosionCenter, "sog_carpet.ui.explosion.explosion_center");
        y += this.rowHeight;
        addToggle(y, ExplosionVisualizerConfig::explodeTimer,
                ExplosionVisualizerConfig::setExplodeTimer, "sog_carpet.ui.explosion.explode_timer");
        y += this.rowHeight;
        addToggle(y, ExplosionVisualizerConfig::sphere,
                ExplosionVisualizerConfig::setSphere, "sog_carpet.ui.explosion.sphere");

        // 颜色与 Done 并在最底行，避免第二个按钮压住最后一个开关。
        int half = Math.max(60, (this.columnWidth - 6) / 2);
        int buttonY = Math.min(top + this.rowHeight * 7 + 3, this.height - this.buttonHeight);
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.color.button"),
                        button -> VisualizerState.openColorScreen("explosionVisualizer", this))
                .bounds(this.left, buttonY, half, this.buttonHeight)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.explosion.done"), button -> onClose())
                .bounds(this.left + this.columnWidth - half, buttonY, half, this.buttonHeight)
                .build());
    }

    private void addToggle(int y, BoolSupplier getter, BoolConsumer setter, String key) {
        Button button = Button.builder(toggleLabel(key, getter.get()), b -> {
            setter.accept(!getter.get());
            b.setMessage(toggleLabel(key, getter.get()));
        }).bounds(this.left, y, this.columnWidth, this.buttonHeight).build();
        addRenderableWidget(button);
    }

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key).append(": ").append(Component.translatable(value
                ? "sog_carpet.ui.explosion.on"
                : "sog_carpet.ui.explosion.off"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
            float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xB0101014);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
        extractor.text(this.font, Component.translatable("sog_carpet.ui.explosion.hint"),
                this.left, 20, 0xFFA8B0B8, false);
    }

    @Override
    public void onClose() {
        if (this.parent != null && this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停世界：调显示项时能立刻看到世界里爆炸预览的变化。
        return false;
    }

    private interface BoolSupplier {
        boolean get();
    }

    private interface BoolConsumer {
        void accept(boolean value);
    }
}

package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * “速度/动量/加速度可视化”的配置界面（从 Ctrl+V 总控里点本规则的“配置”进入）。
 *
 * <p>项目较多，用左右两栏摆：左栏是模式/坐标系与各矢量开关，右栏是每个轴夹角、仰角、方位角、
 * 实体俯仰偏航的开关。改动即时生效并写入 {@code config/sog_carpet-motion.txt}，不暂停世界。</p>
 */
public final class MotionVisualizerConfigScreen extends Screen {
    private static final int COLUMN_WIDTH = 250;
    private static final int COLUMN_GAP = 16;
    private static final int PANEL_WIDTH = COLUMN_WIDTH * 2 + COLUMN_GAP;
    private static final int ROW = 24;
    private static final int TOP = 44;

    private int left;
    private int columnWidth = COLUMN_WIDTH;
    private int rowHeight = ROW;
    private int buttonHeight = 20;
    private final Screen parent;

    public MotionVisualizerConfigScreen() {
        this(null);
    }

    public MotionVisualizerConfigScreen(Screen parent) {
        super(Component.translatable("sog_carpet.ui.motion.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MotionVisualizerConfig.ensureLoaded();
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(260, this.width - 16));
        int gap = panelWidth >= 420 ? COLUMN_GAP : 8;
        this.columnWidth = Math.max(100, (panelWidth - gap) / 2);
        this.left = Math.max(8, (this.width - panelWidth) / 2);
        int top = this.height < 240 ? 36 : TOP;
        this.rowHeight = Math.max(12, Math.min(ROW, (this.height - top - 26) / 7));
        this.buttonHeight = Math.max(14, Math.min(20, this.rowHeight));
        int rightCol = this.left + this.columnWidth + gap;
        int y = top;

        // 左栏：模式与坐标系（各一个会翻转文字的按钮）。
        addRenderableWidget(Button.builder(modeLabel(), button -> {
            MotionVisualizerConfig.setMomentum(!MotionVisualizerConfig.momentum());
            button.setMessage(modeLabel());
        }).bounds(this.left, y, this.columnWidth, this.buttonHeight).build());
        y += this.rowHeight;
        addRenderableWidget(Button.builder(axisLabel(), button -> {
            MotionVisualizerConfig.setSpherical(!MotionVisualizerConfig.spherical());
            button.setMessage(axisLabel());
        }).bounds(this.left, y, this.columnWidth, this.buttonHeight).build());
        y += this.rowHeight;

        addToggle(this.left, y, MotionVisualizerConfig::axes, MotionVisualizerConfig::setAxes,
                "sog_carpet.ui.motion.axes");
        y += this.rowHeight;
        addToggle(this.left, y, MotionVisualizerConfig::axisVectors,
                MotionVisualizerConfig::setAxisVectors, "sog_carpet.ui.motion.axis_vectors");
        y += this.rowHeight;
        addToggle(this.left, y, MotionVisualizerConfig::forces, MotionVisualizerConfig::setForces,
                "sog_carpet.ui.motion.forces");
        y += this.rowHeight;
        addToggle(this.left, y, MotionVisualizerConfig::external,
                MotionVisualizerConfig::setExternal, "sog_carpet.ui.motion.external");
        y += this.rowHeight;
        addToggle(this.left, y, MotionVisualizerConfig::resultant,
                MotionVisualizerConfig::setResultant, "sog_carpet.ui.motion.resultant");

        // 右栏：各夹角/角度与实体姿态开关。
        int ry = top;
        addToggle(rightCol, ry, MotionVisualizerConfig::angleX,
                MotionVisualizerConfig::setAngleX, "sog_carpet.ui.motion.angle_x");
        ry += this.rowHeight;
        addToggle(rightCol, ry, MotionVisualizerConfig::angleY,
                MotionVisualizerConfig::setAngleY, "sog_carpet.ui.motion.angle_y");
        ry += this.rowHeight;
        addToggle(rightCol, ry, MotionVisualizerConfig::angleZ,
                MotionVisualizerConfig::setAngleZ, "sog_carpet.ui.motion.angle_z");
        ry += this.rowHeight;
        addToggle(rightCol, ry, MotionVisualizerConfig::elevation,
                MotionVisualizerConfig::setElevation, "sog_carpet.ui.motion.elevation");
        ry += this.rowHeight;
        addToggle(rightCol, ry, MotionVisualizerConfig::azimuth,
                MotionVisualizerConfig::setAzimuth, "sog_carpet.ui.motion.azimuth");
        ry += this.rowHeight;
        addToggle(rightCol, ry, MotionVisualizerConfig::pitchYaw,
                MotionVisualizerConfig::setPitchYaw, "sog_carpet.ui.motion.pitch_yaw");

        // 颜色与 Done 并排放在最底行：不再上下叠两个按钮压住最后一个开关。
        int buttonY = Math.min(top + this.rowHeight * 7 + 3, this.height - this.buttonHeight);
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.color.button"),
                        button -> VisualizerState.openColorScreen("motionVisualizer", this))
                .bounds(this.left, buttonY, this.columnWidth, this.buttonHeight)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.motion.done"), button -> onClose())
                .bounds(rightCol, buttonY, this.columnWidth, this.buttonHeight)
                .build());
    }

    /** 一个纯开关按钮：getter 读当前值，setter 翻转，label 前缀用固定文案。 */
    private void addToggle(int x, int y, BoolSupplier getter, BoolConsumer setter, String key) {
        Button button = Button.builder(toggleLabel(key, getter.get()), b -> {
            setter.accept(!getter.get());
            b.setMessage(toggleLabel(key, getter.get()));
        }).bounds(x, y, this.columnWidth, this.buttonHeight).build();
        addRenderableWidget(button);
    }

    private static Component modeLabel() {
        return Component.translatable(MotionVisualizerConfig.momentum()
                ? "sog_carpet.ui.motion.mode_momentum"
                : "sog_carpet.ui.motion.mode_velocity");
    }

    private static Component axisLabel() {
        return Component.translatable(MotionVisualizerConfig.spherical()
                ? "sog_carpet.ui.motion.axis_spherical"
                : "sog_carpet.ui.motion.axis_xyz");
    }

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key).append(": ").append(Component.translatable(value
                ? "sog_carpet.ui.motion.on"
                : "sog_carpet.ui.motion.off"));
    }

    @Override
    public void render(GuiGraphics extractor, int mouseX, int mouseY,
            float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xB0101014);
        super.render(extractor, mouseX, mouseY, partialTick);
        extractor.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
        extractor.drawString(this.font, Component.translatable("sog_carpet.ui.motion.hint"),
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
        // 不暂停世界：调显示项时能立刻看到世界里矢量的变化。
        return false;
    }

    /** 读一个布尔显示项。 */
    private interface BoolSupplier {
        boolean get();
    }

    /** 写一个布尔显示项。 */
    private interface BoolConsumer {
        void accept(boolean value);
    }
}

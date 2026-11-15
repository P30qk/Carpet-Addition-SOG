package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.net.ItemFlowActionPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * “物品流向可视化”的面板（从 Ctrl+V 总控里点本规则的“配置”进入）。
 *
 * <p>取代了原来的 {@code /ift} 指令：这里提供“标记手持物品”“清除全部”“刷新列表”三个按钮，
 * 外加颜色循环与“记录路径”开关。所有操作通过 {@link ItemFlowActionPayload} 发给服务端执行，
 * 服务端把会话列表用 {@code ItemFlowStatusPayload} 发回来，面板直接显示。</p>
 */
public final class ItemFlowConfigScreen extends Screen {
    private static final String RULE = "itemFlowTracker";
    private static final int PANEL_WIDTH = 380;

    /** 颜色循环下标（在全部染料颜色间切换）。 */
    private static int colorIndex;

    private int left;
    private final Screen parent;

    public ItemFlowConfigScreen() {
        this(null);
    }

    public ItemFlowConfigScreen(Screen parent) {
        super(Component.translatable("sog_carpet.ui.itemflow.title"));
        this.parent = parent;
    }

    private static int currentRgb() {
        DyeColor[] values = DyeColor.values();
        int index = Math.floorMod(colorIndex, values.length);
        return values[index].getTextureDiffuseColor() & 0xFFFFFF;
    }

    private static String currentColorName() {
        DyeColor[] values = DyeColor.values();
        return values[Math.floorMod(colorIndex, values.length)].getName();
    }

    private static int pathInterval() {
        // 每 2 tick 采一次样，轨迹足够顺滑，又不会在刷石机这种场合产生过多显示实体。
        return VisualizerConfig.option(RULE, "trackPath", true) ? 2 : 0;
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(240, Math.min(PANEL_WIDTH, this.width - 16));
        this.left = Math.max(8, (this.width - panelWidth) / 2);
        int btnY = 36;
        int w = Math.max(60, (panelWidth - 8) / 3);

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.itemflow.mark"), b -> send(ItemFlowActionPayload.MARK_HELD))
                .bounds(this.left, btnY, w, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.itemflow.clear"), b -> send(ItemFlowActionPayload.CLEAR))
                .bounds(this.left + w + 4, btnY, w, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.itemflow.refresh"), b -> send(ItemFlowActionPayload.LIST))
                .bounds(this.left + 2 * (w + 4), btnY, Math.max(60, panelWidth - 2 * (w + 4)), 20).build());

        addRenderableWidget(Button.builder(colorLabel(), b -> {
            colorIndex++;
            b.setMessage(colorLabel());
        }).bounds(this.left, btnY + 24, w, 20).build());

        addRenderableWidget(Button.builder(pathLabel(), b -> {
            VisualizerConfig.setOption(RULE, "trackPath", !VisualizerConfig.option(RULE, "trackPath", true));
            b.setMessage(pathLabel());
        }).bounds(this.left + w + 4, btnY + 24, w, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("sog_carpet.ui.color.done"), b -> onClose())
                .bounds(this.left + 2 * (w + 4), btnY + 24,
                        Math.max(60, panelWidth - 2 * (w + 4)), 20).build());

        // 打开面板即刷新一次会话列表。
        send(ItemFlowActionPayload.LIST);
    }

    private static Component colorLabel() {
        return Component.translatable("sog_carpet.ui.itemflow.color", currentColorName());
    }

    private static Component pathLabel() {
        return Component.translatable(VisualizerConfig.option(RULE, "trackPath", true)
                ? "sog_carpet.ui.itemflow.path_on"
                : "sog_carpet.ui.itemflow.path_off");
    }

    private void send(int action) {
        ClientPlayNetworking.send(new ItemFlowActionPayload(action, currentRgb(), pathInterval()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xB0101014);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
        extractor.text(this.font, Component.translatable("sog_carpet.ui.itemflow.hint"),
                this.left, 20, 0xFFA8B0B8, false);

        extractor.text(this.font, Component.translatable("sog_carpet.ui.itemflow.list_header"),
                this.left, 90, 0xFFB8C0C8, false);
        List<String> lines = ItemFlowPanelState.lines();
        if (lines.isEmpty()) {
            extractor.text(this.font, Component.translatable("sog_carpet.ui.itemflow.empty"),
                    this.left + 6, 106, 0xFF909898, false);
            return;
        }
        int y = 106;
        for (String line : lines) {
            if (y > this.height - 16) {
                break;
            }
            if ("rule_off".equals(line)) {
                extractor.text(this.font, Component.translatable("sog_carpet.ui.itemflow.rule_off"),
                        this.left + 6, y, 0xFFE08080, false);
            } else {
                String[] parts = line.split("\\|");
                String text = parts.length >= 3 ? parts[0] + "  " + parts[1] + "/" + parts[2] : line;
                extractor.text(this.font, Component.literal(text), this.left + 6, y, 0xFFE0E6EC, false);
            }
            y += 12;
        }
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
        // 不暂停世界：标记/清除后能立刻看到世界里的高亮变化。
        return false;
    }
}

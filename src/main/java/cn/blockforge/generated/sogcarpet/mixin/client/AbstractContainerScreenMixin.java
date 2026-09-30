package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.SogFakeRender;
import cn.blockforge.generated.sogcarpet.SogTools;
import cn.blockforge.generated.sogcarpet.client.FakeRenderHelpWidget;
import cn.blockforge.generated.sogcarpet.client.SogFakeRenderState;
import cn.blockforge.generated.sogcarpet.client.SogFakeToolsState;
import cn.blockforge.generated.sogcarpet.client.VerticalDistanceSlider;
import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeRenderSetPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsSetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 GCA 假人背包界面左侧加入渲染距离/模拟距离两条竖向滑条。
 *
 * <p>识别方式：服务端在该背包菜单构造时下发 {@link FakeRenderInfoPayload}，
 * 客户端用“菜单容器 id == 包里的 syncId”+ “标题里带该假人名字”确认身份
 * （客户端拿到的是原版 6 行箱子，不是 GCA 的菜单类）。拖动即时发 C2S 包，
 * 服务端把值应用到这个假人身上。</p>
 *
 * <p>这里不读 Carpet 规则：纯客户端连远程服务器时，本模组的规则字段不会被解析，
 * 客户端拿到的一直是编译期默认值；真正决定“要不要显示”的是服务端是否下发这个包。</p>
 *
 * <p>26.2 里 {@code Screen.init(int,int)} 是 final 的壳，真正被重写的是
 * {@code AbstractContainerScreen.init()}，所以注入点是它的尾部。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    // 目标里是 final 字段：Mixin 要求 shadow 必须同时标注 @Final（这里只读）。
    @Shadow
    @Final
    protected int imageWidth;
    @Shadow
    @Final
    protected int imageHeight;
    @Shadow
    public abstract T getMenu();

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void sog$addFakeRenderSliders(CallbackInfo ci) {
        try {
            sog$buildSliders();
            sog$buildToolColumn();
        } catch (Throwable ignored) {
            // 附加界面属于锦上添花：任何意外都不该让箱子界面打不开。
        }
    }

    /**
     * 在 GCA 假人背包界面<b>右侧</b>加一列工具开关（规则 {@code fakePlayerToolsOnly} 开启时
     * 服务端才会下发 {@link FakeToolsInfoPayload}）。每格对应一类工具，亮着代表这类会被捡起，
     * 点一下切换并发 C2S 包写给这个假人。
     */
    private void sog$buildToolColumn() {
        T menu = this.getMenu();
        FakeToolsInfoPayload info = SogFakeToolsState.current();
        if (info == null || menu == null || info.syncId() != menu.containerId) {
            return;
        }
        int columnWidth = 64;
        int x = this.leftPos + this.imageWidth + 6;
        if (x + columnWidth > this.width - 2) {
            // 屏幕放不下就退到界面左边，宁可压一点界面也不要跑到屏幕外。
            x = Math.max(2, this.leftPos - columnWidth - 6);
        }
        int y = this.topPos;
        // 表头做成不可点的按钮，纯展示。
        Button header = Button.builder(Component.translatable("sog_carpet.ui.tools.header"), button -> {
        }).bounds(x, y - 13, columnWidth, 11).build();
        header.active = false;
        this.addRenderableWidget(header);

        // 类别有 11 种（剑斧镐铲锄弓弩矛锤竿剪），行距压到 13 像素让整列仍放得下。
        int[] mask = {info.mask()};
        for (int i = 0; i < SogTools.CATEGORY_COUNT; i++) {
            final int category = i;
            Button button = Button.builder(sog$toolLabel(category, mask[0]), pressed -> {
                mask[0] ^= 1 << category;
                ClientPlayNetworking.send(new FakeToolsSetPayload(info.fake(), mask[0]));
                pressed.setMessage(sog$toolLabel(category, mask[0]));
            }).bounds(x, y + i * 13, columnWidth, 12).build();
            this.addRenderableWidget(button);
        }
    }

    /** 工具开关上的文字：短名字 + 勾/叉。 */
    private static Component sog$toolLabel(int category, int mask) {
        boolean on = (mask & (1 << category)) != 0;
        return Component.translatable(on ? "sog_carpet.ui.tools.cell_on" : "sog_carpet.ui.tools.cell_off",
                SogTools.categoryShortName(category));
    }

    private void sog$buildSliders() {
        T menu = this.getMenu();
        FakeRenderInfoPayload info = SogFakeRenderState.current();
        // 只对得上号的界面上画滑条：syncId 与该背包菜单的 containerId 一致，
        // 避免把上一个假人的缓存画到新界面上。
        if (info == null || menu == null || info.syncId() != menu.containerId) {
            return;
        }
        if (!SogFakeRenderState.isGcaFakeMenu(menu, this.getTitle())) {
            return;
        }
        // -1 表示跟随服务器默认值：手柄停在默认值处，并标记为“跟随”。
        int render = info.render() >= 0 ? info.render() : info.defaultRender();
        int simulation = info.simulation() >= 0 ? info.simulation() : info.defaultSimulation();
        boolean renderFollow = info.render() < 0;
        boolean simulationFollow = info.simulation() < 0;

        // 滑条下面还要画数字与说明文字，先给文字留出约 80 像素。
        int sliderHeight = Math.max(70, this.imageHeight - 100);
        int top = this.topPos + 8;
        // 截图里绿色（模拟距离）在左、红色（渲染距离）在右。
        int xSimulation = Math.max(4, this.leftPos - 44);
        int xRender = Math.max(4, this.leftPos - 22);

        // 最大值、最小值跟随原版游戏设置：渲染距离 2-32、模拟距离 5-32。
        int renderMin = SogFakeRender.SOG_MIN_RENDER_DISTANCE;
        int renderMax = SogFakeRender.SOG_MAX_RENDER_DISTANCE;
        int simulationMin = SogFakeRender.SOG_MIN_SIMULATION_DISTANCE;
        int simulationMax = SogFakeRender.SOG_MAX_SIMULATION_DISTANCE;

        VerticalDistanceSlider[] pair = new VerticalDistanceSlider[2];
        pair[0] = new VerticalDistanceSlider(xRender, top, 16, sliderHeight, renderMin, renderMax,
                render, renderFollow, Component.translatable("sog_carpet.ui.render_distance"),
                0xFFC4524A, value -> sog$send(info, pair, value, true));
        pair[1] = new VerticalDistanceSlider(xSimulation, top, 16, sliderHeight, simulationMin,
                simulationMax, simulation, simulationFollow,
                Component.translatable("sog_carpet.ui.simulation_distance"), 0xFF4FB37E,
                value -> sog$send(info, pair, value, false));
        this.addRenderableWidget(pair[0]);
        this.addRenderableWidget(pair[1]);

        // 滑条底下：当前数字（含范围）、服务器游戏设置、操作与独立性说明。
        // 每个假人的这块面板只读自己那两条滑条，天然与其他假人、玩家各自独立。
        this.addRenderableWidget(new FakeRenderHelpWidget(xSimulation - 6, top + sliderHeight + 8,
                info.fake(), pair[0], pair[1], info.defaultRender(), info.defaultSimulation()));
    }

    private void sog$send(FakeRenderInfoPayload info, VerticalDistanceSlider[] pair, int value,
            boolean isRender) {
        int render = isRender ? value : pair[0] == null ? info.render() : pair[0].sog$getValue();
        int simulation = isRender ? pair[1] == null ? info.simulation() : pair[1].sog$getValue() : value;
        ClientPlayNetworking.send(new FakeRenderSetPayload(info.fake(), render, simulation));
    }
}

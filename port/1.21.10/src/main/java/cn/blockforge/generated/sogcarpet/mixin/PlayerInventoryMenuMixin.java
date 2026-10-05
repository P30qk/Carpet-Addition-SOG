package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogFakeRender;
import cn.blockforge.generated.sogcarpet.SogFakeRenderService;
import cn.blockforge.generated.sogcarpet.SogFakeTools;
import cn.blockforge.generated.sogcarpet.SogPlayerContainerAccess;
import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GCA 假人背包打开时，向查看者下发该假人的渲染设置与工具类别开关（S2C），
 * 客户端据此在背包界面左侧画滑条、右侧画工具列。
 *
 * <p>目标类与构造器签名已对照 GCA 26.2（v2.12.8 build.97）核实：
 * {@code PlayerInventoryMenu(int, Inventory, Container)}。这里不继承目标类，
 * 只做构造器尾部注入，避免绑死 GCA 的父类实现。</p>
 *
 * <p>本 mixin 属于条件配置 {@code sog_carpet.gca.mixins.json}：只有当 GCA
 * （mod id {@code gca}）存在时才实际生效，且配置 required=false、defaultRequire=0，
 * GCA 缺失或改版时静默跳过，不影响本模组其余功能。</p>
 */
@Mixin(targets = "dev.dubhe.gugle.carpet.tools.player.PlayerInventoryMenu")
public abstract class PlayerInventoryMenuMixin {
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/world/Container;)V", at = @At("TAIL"))
    private void sog$sendRenderInfo(int syncId, Inventory viewerInventory, Container container,
            CallbackInfo ci) {
        try {
            if (!(container instanceof SogPlayerContainerAccess access)) {
                return;
            }
            ServerPlayer fake = access.sog$getFakePlayer();
            // Inventory.player 的静态类型是 Player；服务端菜单构造时它一定是 ServerPlayer。
            if (!(viewerInventory.player instanceof ServerPlayer viewer)) {
                return;
            }
            if (SogSettings.fakePlayerRenderSettings && fake instanceof SogFakeRender settings) {
                // 客户端没装本模组（或还没声明该通道）时不要发，免得自定义负载被拒。
                if (ServerPlayNetworking.canSend(viewer, FakeRenderInfoPayload.TYPE)) {
                    FakeRenderInfoPayload info = SogFakeRenderService.infoOf(syncId, fake, settings,
                            ((ServerLevel) viewer.level()).getServer());
                    ServerPlayNetworking.send(viewer, info);
                }
            }
            if (SogSettings.fakePlayerToolsOnly && fake instanceof SogFakeTools tools) {
                if (ServerPlayNetworking.canSend(viewer, FakeToolsInfoPayload.TYPE)) {
                    ServerPlayNetworking.send(viewer,
                            SogFakeRenderService.toolsInfoOf(syncId, fake, tools));
                }
            }
        } catch (Throwable ignored) {
            // 只是给界面加两块控件：任何意外都不该让“打开假人背包”失败。
        }
    }
}

package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.FlexiblePlacementCorners;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “Tweakeroo 灵活放置四角三角”规则的显示部分：接管灵活放置覆盖层的绘制。
 *
 * <p>Tweakeroo 的 {@code RenderHandler.renderOverlays} 只做一件事——调用 malilib 的
 * {@code RenderUtils.renderBlockTargetingOverlay} 画“如何放置”界面。规则开启时这里直接取消
 * 那次调用，改由 {@link FlexiblePlacementCorners} 在本模组的渲染回调里重画同一套几何，
 * 四个角因此能被当作三角形高亮（malilib 只能按主导轴把角并进某条梯形）。</p>
 *
 * <p>只有在“规则开着 + 覆盖层确实该显示 + 上一帧我方已成功画过”三个条件同时成立时才取消，
 * 所以反射失败、渲染异常等情况下覆盖层会退回 Tweakeroo 自己画，不会凭空消失。</p>
 *
 * <p>目标类用字符串写，编译期不依赖 Tweakeroo；配置 {@code sog_carpet.tweakeroo.mixins.json}
 * 是 required=false / defaultRequire=0，没装 Tweakeroo 时静默跳过。</p>
 */
@Mixin(targets = "fi.dy.masa.tweakeroo.event.RenderHandler")
public abstract class FlexiblePlacementOverlayMixin {
    @Inject(
            method = "renderOverlays(Lnet/minecraft/util/profiling/ProfilerFiller;"
                    + "Lnet/minecraft/client/Minecraft;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void sog$skipFlexibleOverlay(ProfilerFiller profiler, Minecraft client, CallbackInfo ci) {
        if (FlexiblePlacementCorners.shouldReplaceOverlay(client)) {
            ci.cancel();
        }
    }
}

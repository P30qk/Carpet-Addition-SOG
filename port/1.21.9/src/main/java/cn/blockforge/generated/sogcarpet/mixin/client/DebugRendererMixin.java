package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.SogRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.9 的 Fabric API 没有公开世界渲染事件，这里注入到原版 {@link DebugRenderer#render} 的开头，
 * 用它拿到的 {@link PoseStack} 与 {@code MultiBufferSource} 触发本模组自己的渲染回调，
 * 效果与 1.21.10 的 {@code WorldRenderEvents.BEFORE_DEBUG_RENDER} 一致。
 */
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void sog$beforeDebugRender(PoseStack poseStack, Frustum frustum,
            MultiBufferSource.BufferSource buffers, double camX, double camY, double camZ,
            boolean renderDebug, CallbackInfo ci) {
        // 原版一帧里会调两次 render：主渲染通道传 false、调试叠加通道传 true。
        // Fabric 1.21.10 的 BEFORE_DEBUG_RENDER 注入的是主通道，这里同样只在 false
        // 时触发，确保每帧只画一次。
        if (!renderDebug) {
            SogRenderEvents.fire(poseStack, buffers);
        }
    }
}

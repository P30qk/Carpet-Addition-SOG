package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.DropCountVisualizer;
import cn.blockforge.generated.sogcarpet.client.ItemRetentionVisualizer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 掉落物数量标签的渲染挂钩（移植自 drop-count）。
 *
 * <p>渲染方式与原模组一致：注入 {@code LevelRenderer#submitEntities} 的末尾，把标签以世界空间
 * 文字的方式交给同一个提交收集器 {@link SubmitNodeCollector}，因此标签随距离缩放、被地形遮挡
 * 的表现和原模组完全相同。是否真的绘制由 {@link DropCountVisualizer} 自己按规则/总控判断。</p>
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void sog$submitDropCountLabels(PoseStack poseStack, LevelRenderState levelRenderState,
            SubmitNodeCollector output, CallbackInfo ci) {
        DropCountVisualizer.render(poseStack, output, levelRenderState.cameraRenderState);
        ItemRetentionVisualizer.render(poseStack, output, levelRenderState.cameraRenderState);
    }
}

package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.CameraSnapshot;
import cn.blockforge.generated.sogcarpet.client.DropCountVisualizer;
import cn.blockforge.generated.sogcarpet.client.ItemRetentionVisualizer;
import cn.blockforge.generated.sogcarpet.client.RenderSafety;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
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
 *
 * <p>1.21.11 的相机数据在 {@code LevelRenderState#cameraRenderState} 里，但只带位置与朝向，
 * 所以这里先包成 {@link CameraSnapshot} 再交给两个标签可视化。</p>
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void sog$submitDropCountLabels(PoseStack poseStack, LevelRenderState levelRenderState,
            SubmitNodeCollector output, CallbackInfo ci) {
        // 整段包进安全网：任何异常都不许冒到原版渲染循环里（那会让整帧坏掉 = 黑屏）。
        RenderSafety.guard("labels", () -> {
            CameraSnapshot camera = CameraSnapshot.of(levelRenderState.cameraRenderState);
            if (camera == null) {
                return;
            }
            DropCountVisualizer.render(poseStack, output, camera);
            ItemRetentionVisualizer.render(poseStack, output, camera);
        });
    }
}

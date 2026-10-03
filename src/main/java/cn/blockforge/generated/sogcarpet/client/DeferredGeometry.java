package cn.blockforge.generated.sogcarpet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 把一段自定义几何推迟到帧末、按指定 {@link RenderType} 提交。
 *
 * <p>移植自 lucidity 的 {@code DeferredGeometry}：原版提交节点收集器只提供
 * {@code submitCustomGeometry}，而它回调给我们的是一份 {@link PoseStack.Pose} 而不是
 * {@link PoseStack}。这里新建一个 {@link PoseStack} 并把这份 pose 拷进去，再交给绘制回调，
 * 于是上层可以像在原版渲染循环里一样 push / translate / pop，逐 quad 地画方块模型。</p>
 */
public final class DeferredGeometry {
    private DeferredGeometry() {
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
            BiConsumer<PoseStack, VertexConsumer> draw) {
        collector.order(0).submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            PoseStack local = new PoseStack();
            local.last().set(pose);
            draw.accept(local, consumer);
        });
    }
}

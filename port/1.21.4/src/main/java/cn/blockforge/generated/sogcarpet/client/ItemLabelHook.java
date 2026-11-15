package cn.blockforge.generated.sogcarpet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 掉落物标签（数量 + 留存时长）的渲染挂钩。
 *
 * <p>1.21.3 里没有 {@code LevelRenderer#submitEntities} 那层新管线，也就没有
 * {@code SubmitNodeCollector} 可用；这两个标签改成在
 * {@link WorldRenderEvents#BEFORE_DEBUG_RENDER} 里直接往当前的
 * {@code PoseStack} + {@code MultiBufferSource} 写世界空间文字，
 * 也就是原版 {@code DebugRenderer} 的写法。距离缩放、隔墙可见、遮挡关系都由文字渲染器自己处理。</p>
 */
public final class ItemLabelHook {
    private ItemLabelHook() {
    }

    /** 客户端入口调用一次。 */
    public static void init() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ItemLabelHook::render);
    }

    private static void render(WorldRenderContext context) {
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }
        CameraSnapshot camera = CameraSnapshot.capture(context);
        if (camera == null) {
            return;
        }
        // 两个标签可视化各自都有自己的规则判断；这里再包一层安全网 + 主动收批，
        // 保证任何异常或未冲掉的文字批次都不会影响原版这一帧。
        RenderSafety.guard("labels", () -> {
            DropCountVisualizer.render(poseStack, consumers, camera);
            ItemRetentionVisualizer.render(poseStack, consumers, camera);
            RenderSafety.flush(consumers);
        });
    }
}

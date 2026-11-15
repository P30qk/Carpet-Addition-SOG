package cn.blockforge.generated.sogcarpet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * 世界几何提交阶段的上下文（与 1.21.10 Fabric API 的同名接口形状一致）。
 *
 * <p>{@link #matrices()} 是<b>相机相对</b>的姿态栈：原版提交实体/调试几何时都会自己减去
 * 相机位置，所以调用方传进来的世界坐标要先减一次相机位置，这一点由
 * {@code client/gizmo/Gizmos} 统一处理。</p>
 */
public interface WorldRenderContext extends AbstractWorldRenderContext {
    /** 相机相对的姿态栈。 */
    PoseStack matrices();

    /** 立即模式批次（原版 renderBuffers 的 BufferSource）。 */
    MultiBufferSource consumers();

    /** 本帧的延迟几何提交收集器。 */
    SubmitNodeCollector commandQueue();
}

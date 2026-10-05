package cn.blockforge.generated.sogcarpet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 1.21.9 的 Fabric API 还没提供 {@code WorldRenderEvents}（该版本原版渲染大改，
 * 世界渲染钩子到 1.21.10 才补回）。本模组自己用 {@link cn.blockforge.generated.sogcarpet.mixin.client.DebugRendererMixin}
 * 注入到原版调试渲染入口，构造这个轻量上下文交给各可视化器使用。
 */
public final class SogWorldRenderContext {
    private final PoseStack matrices;
    private final MultiBufferSource consumers;

    public SogWorldRenderContext(PoseStack matrices, MultiBufferSource consumers) {
        this.matrices = matrices;
        this.consumers = consumers;
    }

    public PoseStack matrices() {
        return this.matrices;
    }

    public MultiBufferSource consumers() {
        return this.consumers;
    }
}

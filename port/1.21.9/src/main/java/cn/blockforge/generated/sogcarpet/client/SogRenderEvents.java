package cn.blockforge.generated.sogcarpet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 1.21.9 专用的自定义“世界渲染”事件：由 {@code DebugRendererMixin} 在每帧调试渲染入口触发，
 * 替代该版本尚不存在的 Fabric {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}。
 */
public final class SogRenderEvents {
    private static final List<Consumer<SogWorldRenderContext>> LISTENERS = new ArrayList<>();

    private SogRenderEvents() {
    }

    public static void register(Consumer<SogWorldRenderContext> listener) {
        LISTENERS.add(listener);
    }

    public static void fire(PoseStack matrices, MultiBufferSource consumers) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        SogWorldRenderContext context = new SogWorldRenderContext(matrices, consumers);
        for (Consumer<SogWorldRenderContext> listener : LISTENERS) {
            listener.accept(context);
        }
    }
}

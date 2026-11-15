package cn.blockforge.generated.sogcarpet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import java.util.function.Consumer;

/**
 * 世界渲染事件（1.21.9 本地实现）。
 *
 * <p>Fabric API 在 1.21.9 上把整套 {@code rendering.v1.world} 事件撤掉了（1.21.10 才以新
 * 形状回归），而 MC 1.21.9 的渲染管线已经是 {@code LevelRenderState} +
 * {@code SubmitNodeCollector} 那一套。这里保留同名事件与同名方法，并给出与 1.21.10 相同的
 * 触发时机：{@code mixin/client/LevelRendererMixin} 在 {@code LevelRenderer#submitEntities}
 * 末尾先发 {@link #END_EXTRACTION}、再发 {@link #BEFORE_DEBUG_RENDER}。</p>
 *
 * <p>事件类型用 Fabric 的 {@link Event}，所以各处 {@code WorldRenderEvents.XXX.register(...)}
 * 的写法与 1.21.10 完全一致。</p>
 */
public final class WorldRenderEvents {
    /** 提取阶段结束（本实现里在提交阶段开头分发，见类注释）。 */
    public static final Event<Consumer<WorldExtractionContext>> END_EXTRACTION =
            EventFactory.createArrayBacked(Consumer.class, listeners -> context -> {
                for (Consumer<WorldExtractionContext> listener : listeners) {
                    listener.accept(context);
                }
            });

    /**
     * 调试几何提交之前：所有世界空间可视化都在这里提交。
     *
     * <p>回调抛出的异常会被吞掉，避免一条可视化把整帧渲染打断。</p>
     */
    public static final Event<Consumer<WorldRenderContext>> BEFORE_DEBUG_RENDER =
            EventFactory.createArrayBacked(Consumer.class, listeners -> context -> {
                for (Consumer<WorldRenderContext> listener : listeners) {
                    try {
                        listener.accept(context);
                    } catch (RuntimeException ignored) {
                        // 单条可视化出错不影响同一帧的其他可视化。
                    }
                }
            });

    private WorldRenderEvents() {
    }

    /** 由 {@code LevelRendererMixin} 调用：把本帧上下文分发给上面两个事件。 */
    public static void dispatch(PoseStack matrices, SubmitNodeCollector commandQueue) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.gameRenderer == null) {
            return;
        }
        var camera = client.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return;
        }
        Frustum frustum = null;
        if (client.levelRenderer != null) {
            frustum = client.levelRenderer.getCapturedFrustum();
        }
        MultiBufferSource consumers = client.renderBuffers().bufferSource();
        Implementation context = new Implementation(client, camera, client.level, frustum, matrices,
                consumers, commandQueue);
        END_EXTRACTION.invoker().accept(context);
        BEFORE_DEBUG_RENDER.invoker().accept(context);
    }

    /** 同一份数据同时满足“提取”与“提交”两种上下文（调用方会做 instanceof 判断）。 */
    private static final class Implementation implements WorldRenderContext, WorldExtractionContext {
        private final Minecraft client;
        private final net.minecraft.client.Camera camera;
        private final net.minecraft.client.multiplayer.ClientLevel level;
        private final Frustum frustum;
        private final PoseStack matrices;
        private final MultiBufferSource consumers;
        private final SubmitNodeCollector commandQueue;

        private Implementation(Minecraft client, net.minecraft.client.Camera camera,
                net.minecraft.client.multiplayer.ClientLevel level, Frustum frustum,
                PoseStack matrices, MultiBufferSource consumers, SubmitNodeCollector commandQueue) {
            this.client = client;
            this.camera = camera;
            this.level = level;
            this.frustum = frustum;
            this.matrices = matrices;
            this.consumers = consumers;
            this.commandQueue = commandQueue;
        }

        @Override
        public net.minecraft.client.DeltaTracker tickCounter() {
            return this.client.getDeltaTracker();
        }

        @Override
        public net.minecraft.client.Camera camera() {
            return this.camera;
        }

        @Override
        public net.minecraft.client.multiplayer.ClientLevel world() {
            return this.level;
        }

        @Override
        public Frustum frustum() {
            return this.frustum;
        }

        @Override
        public PoseStack matrices() {
            return this.matrices;
        }

        @Override
        public MultiBufferSource consumers() {
            return this.consumers;
        }

        @Override
        public SubmitNodeCollector commandQueue() {
            return this.commandQueue;
        }
    }
}

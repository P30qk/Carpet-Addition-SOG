package cn.blockforge.generated.sogcarpet.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * 世界渲染回调共享的那部分上下文。
 *
 * <p>1.21.9 的 Fabric API（fabric-rendering-v1 16.0.1）里没有
 * {@code net.fabricmc.fabric.api.client.rendering.v1.world} 这套世界渲染事件，MC 1.21.9
 * 又已经在用 {@code SubmitNodeCollector} 那套新渲染管线。所以这里照 1.21.10 的 Fabric
 * 接口形状做一份本地实现，让可视化代码一字不改地编译；事件本身由
 * {@code mixin/client/LevelRendererMixin} 在 {@code LevelRenderer#submitEntities} 末尾分发。</p>
 */
public interface AbstractWorldRenderContext {
    /** 本帧的 tick 计时器（取插值用）。 */
    DeltaTracker tickCounter();

    /** 主相机。 */
    Camera camera();

    /** 当前客户端世界。 */
    ClientLevel world();

    /** 本帧视锥体；取不到时为 {@code null}（调用方据此跳过错视锥剔除）。 */
    Frustum frustum();
}

package cn.blockforge.generated.sogcarpet.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 一帧渲染要用到的相机快照。
 *
 * <p>1.21.11 的渲染管线还没有 26.x 才引入的 {@code CameraRenderState}（那时它挂在
 * {@code LevelRenderState} 上一起下发给渲染阶段）。这个版本里相机仍然是
 * {@link net.minecraft.client.Camera} 对象，视锥体由 {@code WorldRenderContext} 提供，
 * 所以这里把“掉落物标签”用到的四项——相机位置、视锥体、朝向四元数、俯仰角——在渲染回调
 * 开头统一取一次，之后两个标签可视化只读这一份快照。</p>
 */
public final class CameraSnapshot {
    final Vec3 pos;
    final Frustum cullFrustum;
    final Quaternionf orientation;
    final float xRot;

    private CameraSnapshot(Vec3 pos, Frustum cullFrustum, Quaternionf orientation, float xRot) {
        this.pos = pos;
        this.cullFrustum = cullFrustum;
        this.orientation = orientation;
        this.xRot = xRot;
    }

    /**
     * 取当前帧的相机快照；世界或相机还没准备好时返回 {@code null}。
     *
     * <p>视锥体优先用事件上下文里的那份（{@code WorldExtractionContext#frustum()}），拿不到时
     * 退回 {@code null}——调用方据此跳过错视锥剔除，只按距离筛选，不会画错东西。</p>
     */
    public static CameraSnapshot capture(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return null;
        }
        var camera = mc.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return null;
        }
        // 渲染阶段的事件上下文只保证 AbstractWorldRenderContext 的方法，视锥体在提取阶段
        // 才暴露；能拿到就用，拿不到就让调用方只按距离过滤。
        Frustum frustum = context instanceof net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext extraction
                ? extraction.frustum()
                : null;
        return new CameraSnapshot(camera.position(), frustum,
                new Quaternionf(camera.rotation()), camera.getXRot());
    }

    /** 该包围盒在这一帧的视锥体里吗；没有视锥体时一律当作可见。 */
    public boolean isVisible(net.minecraft.world.phys.AABB box) {
        return this.cullFrustum == null || this.cullFrustum.isVisible(box);
    }

    /**
     * 从 1.21.11 的 {@code LevelRenderState#cameraRenderState} 构造快照。
     *
     * <p>这个版本的相机渲染状态只有位置与朝向（没有视锥体、也没有俯仰角字段），俯仰角回退到
     * 客户端主相机的值；视锥体留空，于是标签只按距离筛选——多提交的几个标签本来就会被渲染器
     * 本身剔除，不影响画面。</p>
     */
    public static CameraSnapshot of(net.minecraft.client.renderer.state.CameraRenderState state) {
        if (state == null || !state.initialized) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        float pitch = mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null
                ? 0.0F
                : mc.gameRenderer.getMainCamera().getXRot();
        return new CameraSnapshot(state.pos, null, new Quaternionf(state.orientation), pitch);
    }
}

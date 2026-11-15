package cn.blockforge.generated.sogcarpet.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 一帧渲染要用到的相机快照。
 *
 * <p>1.21.3 里相机仍然是 {@link net.minecraft.client.Camera} 对象，视锥体由
 * {@code WorldRenderContext} 提供，所以这里把“掉落物标签”用到的四项——相机位置、视锥体、
 * 朝向四元数、俯仰角——在渲染回调开头统一取一次，之后两个标签可视化只读这一份快照。</p>
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
     * <p>视锥体优先用事件上下文里的那份，拿不到时退回 {@code null}——调用方据此跳过错视锥
     * 剔除，只按距离筛选，不会画错东西。</p>
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
        Frustum frustum = context == null ? null : context.frustum();
        return new CameraSnapshot(camera.getPosition(), frustum,
                new Quaternionf(camera.rotation()), camera.getXRot());
    }

    /** 该包围盒在这一帧的视锥体里吗；没有视锥体时一律当作可见。 */
    public boolean isVisible(net.minecraft.world.phys.AABB box) {
        return this.cullFrustum == null || this.cullFrustum.isVisible(box);
    }
}

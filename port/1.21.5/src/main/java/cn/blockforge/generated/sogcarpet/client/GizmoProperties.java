package cn.blockforge.generated.sogcarpet.client;

/**
 * 1.21.7~1.21.10 用的 {@code net.minecraft.gizmos.GizmoProperties} 替代。
 *
 * <p>旧版本没有原版的帧末几何收集机制，绘制是即时完成的，所以这里只保留
 * {@link #setAlwaysOnTop()} 这个调用点（透墙显示的语义暂不支持）。</p>
 */
public final class GizmoProperties {
    public GizmoProperties setAlwaysOnTop() {
        return this;
    }
}

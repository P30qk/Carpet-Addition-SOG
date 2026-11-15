package cn.blockforge.generated.sogcarpet.client.gizmo;

/** 与 1.21.11+ {@code net.minecraft.gizmos.GizmoProperties} 对齐的链式属性。 */
public final class GizmoProperties {
    private final Gizmos.Entry entry;

    GizmoProperties(Gizmos.Entry entry) {
        this.entry = entry;
    }

    public GizmoProperties setAlwaysOnTop() {
        this.entry.alwaysOnTop = true;
        return this;
    }

    public GizmoProperties persistForMillis(int millis) {
        // 1.21.10 兼容层没有跨帧保留，调用方本身已经在客户端每帧重画。
        return this;
    }

    public GizmoProperties fadeOut() {
        return this;
    }
}

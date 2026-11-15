package cn.blockforge.generated.sogcarpet.client.gizmo;

/**
 * 1.21.10 的本地 Gizmo 兼容层。
 *
 * <p>26.x / 1.21.11 才把 {@code net.minecraft.gizmos} 接进原版渲染管线，1.21.10 没有这套
 * API。这里保留同名、同签名的一小层，让可视化代码不用重写；真正的绘制由
 * {@link Gizmos} 收集后经 {@code WorldRenderEvents.BEFORE_DEBUG_RENDER} 用原版
 * {@code RenderType.lines()} / {@code debugFilledBox()} 提交。</p>
 */
public interface Gizmo {
    void emit(GizmoPrimitives primitives, float alphaMultiplier);
}

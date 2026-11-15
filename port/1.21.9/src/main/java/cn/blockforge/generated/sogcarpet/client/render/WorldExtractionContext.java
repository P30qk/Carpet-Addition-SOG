package cn.blockforge.generated.sogcarpet.client.render;

/**
 * 提取阶段的上下文（与 1.21.10 Fabric API 的同名接口形状一致）。
 *
 * <p>1.21.9 没有单独的提取阶段事件，这里由世界渲染分发器在提交阶段一并触发，语义上等价：
 * 调用方只用它读世界、相机、视锥与 tick 计时器。</p>
 */
public interface WorldExtractionContext extends AbstractWorldRenderContext {
}

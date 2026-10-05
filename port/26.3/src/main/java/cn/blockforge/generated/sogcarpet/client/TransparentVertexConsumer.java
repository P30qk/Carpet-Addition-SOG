package cn.blockforge.generated.sogcarpet.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 把顶点颜色的透明度固定为 {@value #ALPHA} 的顶点消费者包装器。
 *
 * <p>移植自 lucidity 的 b36 目的地预览：把原版活塞推动中方块的渲染结果再画一遍，
 * 但所有顶点的 alpha 都被压成 100，于是移动中的方块变成半透明“幽灵”，
 * 能提前看到它会落到哪里。除颜色外的所有写入都原样转发给底层消费者。</p>
 */
public final class TransparentVertexConsumer implements VertexConsumer {
    /** 预览统一使用的透明度（lucidity b36 渲染原值）。 */
    public static final int ALPHA = 100;

    private final VertexConsumer base;

    public TransparentVertexConsumer(VertexConsumer base) {
        this.base = base;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        return this.base.addVertex(x, y, z);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return this.base.setColor(red, green, blue, ALPHA);
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return this.setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
                (argb >>> 24) & 0xFF);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this.base.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this.base.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this.base.setUv2(u, v);
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        return this.base.setUv3(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return this.base.setNormal(x, y, z);
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this.base.setLineWidth(width);
    }
}

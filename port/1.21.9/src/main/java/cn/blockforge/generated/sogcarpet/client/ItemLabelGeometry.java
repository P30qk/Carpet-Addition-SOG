package cn.blockforge.generated.sogcarpet.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 掉落物标签的几何计算：把“标签挂在物品上方”对齐到物品真正渲染出来的模型顶部，
 * 再为物品顶面的三维透视留出净空，避免文字被方块模型挡住。
 *
 * <p>{@link ItemEntity} 的碰撞箱只有 0.25 高，但方块物品渲染出来接近一整格。以前标签挂在
 * 碰撞箱顶部（脚上约 0.6 格），会落在方块模型中间：文字被物品挡住，看起来像重影，物品上下
 * 浮动时更是不断穿过文字，表现为闪烁/发虚。</p>
 *
 * <p>这里按物品自己的模型包围盒算出真正的渲染顶部；另外，相机俯视时物品的顶面会在屏幕上
 * 向上铺开一段（约等于水平半对角线 × tan(俯角)），所以再按相机俯角把标签往上抬这一段，
 * 保证文字完全落在物品之上——用半对角线而不是半宽，是因为方块从斜侧看过来时最近的顶角
 * 在半对角线距离上，按半宽算净空会不够，文字底边会被顶角切掉一小条而闪烁。</p>
 */
final class ItemLabelGeometry {
    /** 每种物品的模型度量（渲染高度 + 水平半对角线）缓存——同一物品固定，只需解析一次。 */
    private static final Map<Item, Metrics> METRICS = new ConcurrentHashMap<>();

    /** 俯角最多按这个角度留净空；接近正俯视时顶面几乎铺满屏幕，再多抬也没用。 */
    private static final double MAX_PITCH_DEGREES = 75.0;

    private ItemLabelGeometry() {
    }

    /**
     * 物品在当前插值位置的渲染顶部世界 Y 坐标（脚底为 {@code pos.y}），含原版的上下浮动。
     */
    static double modelTopY(ItemEntity entity, Vec3 pos, float partialTick) {
        Metrics metrics = metrics(entity);
        float bob = 0.1F + 0.1F * Mth.sin((entity.tickCount + partialTick) / 10.0F + entity.bobOffs);
        return pos.y + bob + metrics.height();
    }

    /**
     * 物品顶面在屏幕上因相机俯视而向上铺开的高度（世界单位）。
     *
     * <p>用模型水平半对角线而不是半宽：方块从任意水平朝向看过来时，最“近”的顶角都在
     * 半对角线距离上，按半宽算净空会不够，文字底边会被顶角切掉一小条，随物品上下浮动
     * 表现为边缘闪烁。</p>
     */
    static double topFaceClearance(ItemEntity entity, CameraSnapshot camera) {
        double pitch = Math.toRadians(Math.min(MAX_PITCH_DEGREES, Math.abs(camera.xRot)));
        return metrics(entity).halfDiagonal() * Math.tan(pitch);
    }

    private static Metrics metrics(ItemEntity entity) {
        Item item = entity.getItem().getItem();
        Metrics cached = METRICS.get(item);
        if (cached != null) {
            return cached;
        }
        Metrics measured = measure(entity.getItem(), entity);
        if (measured == null) {
            // 模型还没就绪：这次先用保守估计，但不要写进缓存，等量准了再缓存。
            return fallback(entity.getItem());
        }
        METRICS.putIfAbsent(item, measured);
        return measured;
    }

    /**
     * 原版 {@code ItemEntityRenderer} 的垂直摆法：把模型 minY 对齐到 0.0625，所以从脚底算起的
     * 渲染高度 = {@code 0.0625 - minY + maxY}；水平方向取半对角线，覆盖从任意朝向看来的最近顶角。
     *
     * @return 量不出来（模型暂时不可用）时返回 {@code null}，由调用方短暂回退
     */
    private static Metrics measure(ItemStack stack, ItemEntity entity) {
        boolean block = stack.getItem() instanceof BlockItem;
        double minHeight = block ? 0.875D : 0.15D;
        double minHalfSpan = block ? 0.4D : 0.15D;
        try {
            ItemStackRenderState state = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver()
                    .updateForNonLiving(state, stack, ItemDisplayContext.GROUND, entity);
            AABB box = state.getModelBoundingBox();
            // 空模型会给出退化包围盒（min == max），此时必须回退，否则高度会小得离谱。
            if (box.maxY - box.minY > 1.0E-4 || box.getXsize() > 1.0E-4 || box.getZsize() > 1.0E-4) {
                double height = 0.0625D - box.minY + box.maxY;
                double halfDiagonal = Math.hypot(box.getXsize(), box.getZsize()) / 2.0D;
                return new Metrics(Math.max(height, minHeight), Math.max(halfDiagonal, minHalfSpan));
            }
        } catch (Throwable ignored) {
            // 模型暂时取不到：不缓存，下一帧再试。
        }
        return null;
    }

    /** 量不出模型时的保守估计（不缓存，等模型就绪后会重新量）。 */
    private static Metrics fallback(ItemStack stack) {
        boolean block = stack.getItem() instanceof BlockItem;
        return block ? new Metrics(1.0625D, 0.71D) : new Metrics(0.3125D, 0.25D);
    }

    private record Metrics(double height, double halfDiagonal) {
    }
}

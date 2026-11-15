package cn.blockforge.generated.sogcarpet.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 掉落物标签的几何计算：把“标签挂在物品上方”对齐到物品真正渲染出来的模型顶部，
 * 再为物品顶面的三维透视留出净空，避免文字被方块模型挡住。
 *
 * <p>{@link ItemEntity} 的碰撞箱只有 0.25 高，但方块物品渲染出来接近一整格。以前标签挂在
 * 碰撞箱顶部（脚上约 0.6 格），会落在方块模型中间：文字被物品挡住，看起来像重影，物品上下
 * 浮动时更是不断穿过文字，表现为闪烁/发虚。</p>
 *
 * <p>1.21.3 没有 {@code ItemStackRenderState}（1.21.4+ 才有的物品模型渲染状态对象），拿不到
 * 逐物品量出来的模型包围盒，所以这里按“方块物品 / 普通物品”两档保守估值：方块物品按整格
 * （渲染高 1.0625、水平半对角线 0.71），其余按 0.3125 / 0.25。这一组数值与 1.21.10 版在模型
 * 还没就绪时用的回退值完全一致，视觉效果与之一致。</p>
 */
final class ItemLabelGeometry {
    /** 每种物品的模型度量缓存——同一物品固定，只需给一次。 */
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
        ItemStack stack = entity.getItem();
        Item item = stack.getItem();
        Metrics cached = METRICS.get(item);
        if (cached != null) {
            return cached;
        }
        boolean block = stack.getItem() instanceof BlockItem;
        Metrics measured = block ? new Metrics(1.0625D, 0.71D) : new Metrics(0.3125D, 0.25D);
        METRICS.putIfAbsent(item, measured);
        return measured;
    }

    private record Metrics(double height, double halfDiagonal) {
    }
}

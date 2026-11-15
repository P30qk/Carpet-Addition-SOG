package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * 弹射物“动力学”的物理参数（客户端估算），用来把“外力”画成和参考视频一致的分量。
 *
 * <p>参考视频给出的模型：Minecraft 的运动按 tick 离散步进，速度单位是“格/gt”，
 * 每 tick 先按阻力系数 {@code f} 衰减（{@code v ← v·(1-f)}），再减去重力 {@code g}
 * （{@code v.y ← v.y - g}）。重力 {@code g} 直接用实体自己的
 * {@link Entity#getGravity()}（它已经按实体类型返回值，且 {@code noGravity} 时为 0）；
 * 阻力系数 {@code f} 则按视频给出的实体表估算：普通弹射物（箭/三叉戟/雪球/鸡蛋/末影珍珠/
 * 药水等）为 0.01，TNT 为 0.02。</p>
 *
 * <p>这些只是给可视化用的近似值：真正的加速度仍以每 tick 观测到的
 * {@code Δv} 为准，重力/阻力箭头只是把“外力”拆开显示，方便对照视频里的 {@code g} 与
 * {@code f_x}。</p>
 */
public final class MotionPhysics {
    /** 普通弹射物的阻力系数（视频表格：0.01）。 */
    private static final double PROJECTILE_DRAG = 0.01D;
    /** TNT 的阻力系数（视频表格：0.02）。 */
    private static final double TNT_DRAG = 0.02D;

    private MotionPhysics() {
    }

    /** 这个实体每 tick 受到的“重力速度增量”，方向朝下；{@code noGravity} 时为 0。 */
    public static Vec3 gravity(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        double g = entity.getGravity();
        return g == 0.0D ? Vec3.ZERO : new Vec3(0.0D, -g, 0.0D);
    }

    /** 这个实体每 tick 受到的“阻力速度增量”，方向与速度相反：{@code -f·v}。 */
    public static Vec3 drag(Entity entity, Vec3 velocity) {
        if (entity == null || velocity == null) {
            return Vec3.ZERO;
        }
        double f = dragCoefficient(entity);
        return f <= 0.0D ? Vec3.ZERO : velocity.scale(-f);
    }

    /** 阻力系数：按参考视频的实体表估算，未知实体按 0（不画阻力）。 */
    public static double dragCoefficient(Entity entity) {
        if (entity instanceof ThrowableProjectile || entity instanceof AbstractArrow) {
            return PROJECTILE_DRAG;
        }
        if (entity instanceof PrimedTnt) {
            return TNT_DRAG;
        }
        return 0.0D;
    }
}

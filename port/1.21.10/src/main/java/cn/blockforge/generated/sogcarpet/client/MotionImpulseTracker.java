package cn.blockforge.generated.sogcarpet.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * “外部施加的冲量”采集器（客户端）。
 *
 * <p>原版把重力、摩擦都直接写进 {@code deltaMovement}，只有推动、爆炸、多数击退会走
 * {@link Entity#addDeltaMovement(Vec3)}。本类把每个实体在这一 tick 内通过
 * {@code addDeltaMovement} 收到的冲量累加起来，{@link MotionVisualizer} 就能把它作为
 * “外力施加”的那一支单独画出来。每个客户端 tick 开始清空。</p>
 *
 * <p>它只统计客户端真正调用了 {@code addDeltaMovement} 的实体（本地玩家的推动/爆炸预测等）。
 * 远端实体由服务端模拟、客户端一般拿不到这个过程，所以对它们外力通常为空——这是客户端方案的
 * 固有限制，不影响速度/加速度与合矢量的显示。</p>
 */
public final class MotionImpulseTracker {
    private static final Map<Integer, Vec3> IMPULSES = new ConcurrentHashMap<>();

    private MotionImpulseTracker() {
    }

    public static void add(Entity entity, Vec3 delta) {
        if (entity == null || delta == null) {
            return;
        }
        IMPULSES.merge(entity.getId(), delta, Vec3::add);
    }

    /** 这个实体本 tick 收到的外部冲量，没有则返回 {@link Vec3#ZERO}。 */
    public static Vec3 get(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        Vec3 value = IMPULSES.get(entity.getId());
        return value == null ? Vec3.ZERO : value;
    }

    public static void clear() {
        IMPULSES.clear();
    }
}

package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.net.MotionInfoPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * “外部施加冲量”的客户端缓存（服务端 {@code motion_info} 快照）。
 *
 * <p>服务端每隔几 tick 把玩家附近、近期被施加过冲量的实体发来（爆炸击退、推动、珍珠叠加轴速度
 * 等）。这里存下最近一份快照，{@link MotionVisualizer} 优先用它，拿不到（比如单机/本地玩家）时
 * 再退回客户端自己的 {@link MotionImpulseTracker}。快照太久没更新或维度对不上就作废，避免残影。</p>
 */
public final class MotionImpulseState {
    /** 服务端每 2 tick 发一次，这里留足冗余。 */
    private static final long TTL_MILLIS = 1000L;

    private static volatile String dimension = "";
    private static volatile Map<Integer, Vec3> impulses = Map.of();
    private static volatile long lastUpdateMillis;

    private MotionImpulseState() {
    }

    /** 网络包接收器送来的快照（收包线程调用，只做最少的工作）。 */
    public static void accept(MotionInfoPayload payload) {
        if (payload == null) {
            return;
        }
        Map<Integer, Vec3> map = new HashMap<>();
        for (MotionInfoPayload.Entry entry : payload.entries()) {
            map.put(entry.entityId(), new Vec3(entry.x(), entry.y(), entry.z()));
        }
        impulses = map;
        dimension = payload.dimension() == null ? "" : payload.dimension();
        lastUpdateMillis = System.currentTimeMillis();
    }

    /** 这个实体最近受到的外部冲量；没有有效数据时返回 null（调用方退回本地采集）。 */
    public static Vec3 get(Entity entity) {
        if (entity == null || System.currentTimeMillis() - lastUpdateMillis > TTL_MILLIS) {
            return null;
        }
        if (!entity.level().dimension().location().toString().equals(dimension)) {
            return null;
        }
        return impulses.get(entity.getId());
    }

    public static void clear() {
        impulses = Map.of();
        dimension = "";
        lastUpdateMillis = 0L;
    }
}

package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.MotionInfoPayload;
import cn.blockforge.generated.sogcarpet.net.MotionWantPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * “速度/动量可视化”的服务端辅助：把**外部施加的冲量**下发给客户端。
 *
 * <p>为什么需要它：外部冲量发生在服务端——爆炸击退、实体推动、以及本模组珍珠规则给珍珠叠加的
 * 轴速度——客户端默认拿不到，之前只能靠客户端自己的 {@code addDeltaMovement} 采集，对远端实体
 * 基本无效。这里照“仇恨可视化”的模式补一条服务端快照通道：</p>
 * <ul>
 *   <li><b>没人看就不记</b>：规则关闭时 {@link #record} 直接返回、tick 回调也清空；</li>
 *   <li><b>玩家自己关了就不发</b>：客户端用 {@link MotionWantPayload} 说明还要不要，逐玩家清单里
 *       这条可视化关掉的玩家也被排除；</li>
 *   <li><b>短期保留</b>：冲量是瞬时事件，记录下来保留 {@value #LIFETIME_TICKS} tick 再丢，
 *       让下一次下发（每 {@value #INTERVAL_TICKS} tick 一次）一定能带上；</li>
 *   <li><b>只发玩家附近</b>：按记录里的维度 + 距离过滤，{@value #MAX_ENTRIES} 条封顶。</li>
 * </ul>
 *
 * <p>重力和阻力不在这里发：它们能由客户端按实体自身的 {@code getGravity()} 和速度直接算出来，
 * 只有“被施加的冲量”才是服务端独有的信息。</p>
 *
 * <p><b>谁在用这条通道</b>：除了“速度/动量可视化”画外力矢量，还有“投掷物轨迹可视化”的轨迹
 * 预览——悬停的远传珍珠速度是 0，它下一步往哪飞完全由服务端累积的待施加速度决定，客户端只有
 * 拿到这份冲量才能提前画出落点。因此记录与下发的门控是「两条规则任一开启」；逐玩家过滤也一样
 * （玩家只开了其中一条就发）。包格式没有变，老客户端/老服务端仍互相兼容。</p>
 */
public final class SogMotionSync {
    /** 显式说“不要”的玩家（默认要）。 */
    private static final Set<UUID> OPT_OUT = ConcurrentHashMap.newKeySet();
    private static final int OPT_OUT_PRUNE_THRESHOLD = 256;

    /** 只发玩家周围这个距离（格）以内的冲量。 */
    private static final double RANGE = 96.0D;
    private static final double RANGE_SQR = RANGE * RANGE;
    /** 每隔多少服务端 tick 下发一次。 */
    private static final int INTERVAL_TICKS = 2;
    /** 一条冲量记录保留多少 tick。 */
    private static final int LIFETIME_TICKS = 6;
    /** 本规则在逐玩家渲染清单里的名字。 */
    private static final String RULE = "motionVisualizer";
    /**
     * 同样需要这条冲量通道的规则：“投掷物轨迹可视化”的预览要用悬停珍珠的待施加速度画落点。
     */
    private static final String PREVIEW_RULE = "projectileTrajectoryVisualizer";
    /** 实体号 → 近期冲量。 */
    private static final Map<Integer, Record> RECORDS = new ConcurrentHashMap<>();

    private static int tickCounter;

    private SogMotionSync() {
    }

    /** 只要有一条用到冲量的可视化开着，就值得采集/下发。 */
    private static boolean trackingEnabled() {
        return SogSettings.motionVisualizer || SogSettings.projectileTrajectoryVisualizer;
    }

    /** 这个玩家还要不要收（逐玩家清单里两条规则任一为开）。 */
    private static boolean wants(UUID player) {
        return SogVisualizerPrefs.isRenderEnabled(player, RULE)
                || SogVisualizerPrefs.isRenderEnabled(player, PREVIEW_RULE);
    }

    /** 记录一次外部冲量（服务端线程调用）。冲量是“速度增量”，单位格/tick。 */
    public static void record(Entity entity, Vec3 delta) {
        if (!trackingEnabled() || entity == null || delta == null
                || delta.lengthSqr() < 1.0E-9D) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        int id = entity.getId();
        RECORDS.compute(id, (key, record) -> {
            if (record == null || record.level != level) {
                record = new Record(level);
            }
            record.impulse = record.impulse.add(delta);
            record.age = 0;
            return record;
        });
    }

    /**
     * 持续登记一个“正在累积”的冲量（覆盖而非累加），重置老化计时。悬停的远传珍珠每 tick 调一次，
     * 客户端就能一直看到它当前累积的待施加速度（而不是每次攻击闪一下）。冲量为零时清掉记录。
     */
    public static void setHeld(Entity entity, Vec3 impulse) {
        if (!trackingEnabled() || entity == null) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        int id = entity.getId();
        if (impulse == null || impulse.lengthSqr() < 1.0E-9D) {
            RECORDS.remove(id);
            return;
        }
        RECORDS.compute(id, (key, record) -> {
            if (record == null || record.level != level) {
                record = new Record(level);
            }
            record.impulse = impulse;
            record.age = 0;
            return record;
        });
    }

    /** 客户端 C2S：还要不要收冲量快照。 */
    public static void handleWant(MotionWantPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (payload.want()) {
            OPT_OUT.remove(sender.getUUID());
        } else {
            OPT_OUT.add(sender.getUUID());
        }
    }

    /** 挂在 {@code ServerTickEvents.END_SERVER_TICK} 上。 */
    public static void onEndServerTick(MinecraftServer server) {
        if (!trackingEnabled()) {
            if (!RECORDS.isEmpty()) {
                RECORDS.clear();
            }
            return;
        }
        RECORDS.values().forEach(record -> record.age++);
        RECORDS.entrySet().removeIf(entry -> entry.getValue().age > LIFETIME_TICKS);
        if (++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (RECORDS.isEmpty()) {
            return;
        }
        if (OPT_OUT.size() > OPT_OUT_PRUNE_THRESHOLD) {
            OPT_OUT.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (OPT_OUT.contains(player.getUUID())
                    || !ServerPlayNetworking.canSend(player, MotionInfoPayload.TYPE)
                    || !wants(player.getUUID())) {
                continue;
            }
            List<MotionInfoPayload.Entry> entries = collect(player);
            if (!entries.isEmpty()) {
                String dimension = player.level().dimension().identifier().toString();
                ServerPlayNetworking.send(player, new MotionInfoPayload(dimension, entries));
            }
        }
    }

    /** 收集玩家附近、近期被施加过冲量的实体。 */
    private static List<MotionInfoPayload.Entry> collect(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        List<MotionInfoPayload.Entry> entries = new ArrayList<>();
        for (Map.Entry<Integer, Record> mapEntry : RECORDS.entrySet()) {
            if (entries.size() >= MotionInfoPayload.MAX_ENTRIES) {
                break;
            }
            Record record = mapEntry.getValue();
            if (record.level != level) {
                continue;
            }
            Entity entity = level.getEntity(mapEntry.getKey());
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            double dx = entity.getX() - px;
            double dy = entity.getY() - py;
            double dz = entity.getZ() - pz;
            if (dx * dx + dy * dy + dz * dz > RANGE_SQR) {
                continue;
            }
            Vec3 impulse = record.impulse;
            entries.add(new MotionInfoPayload.Entry(entity.getId(), (float) impulse.x,
                    (float) impulse.y, (float) impulse.z));
        }
        return entries;
    }

    /** 服务端停用时清空。 */
    public static void clear() {
        RECORDS.clear();
    }

    /** 一个实体近期的冲量记录。 */
    private static final class Record {
        private final ServerLevel level;
        private Vec3 impulse = Vec3.ZERO;
        private int age;

        private Record(ServerLevel level) {
            this.level = level;
        }
    }
}

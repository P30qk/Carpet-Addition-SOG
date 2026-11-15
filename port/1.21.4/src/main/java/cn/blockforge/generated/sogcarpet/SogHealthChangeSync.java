package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.HealthChangePayload;
import cn.blockforge.generated.sogcarpet.net.HealthChangeWantPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * “生命数值变化可视化”的服务端数据下发：受伤与回血两条路都从这里采集。
 *
 * <p>两条路各自取的是“真正落到血量上的那一份”：</p>
 * <ul>
 *   <li><b>受伤</b>：Fabric 的 {@link ServerLivingEntityEvents#AFTER_DAMAGE}——它在原版伤害结算
 *       完成、血量真的被扣掉之后触发，回调里的 {@code damageTaken} 正是我们想要的真实伤害
 *       （护甲、附魔、盾牌格挡、吸收都已算完），记成负数下发。</li>
 *   <li><b>回血</b>：{@code LivingEntity#heal} 是所有治疗路径的唯一出口（瞬间治疗药水、再生、
 *       进食与自然回血、金苹果、其它模组的治疗都走它），{@link cn.blockforge.generated.sogcarpet.mixin.LivingEntityHealMixin}
 *       在治疗前后各取一次血量，差值就是这次真正回复的生命值，记成正数下发。</li>
 * </ul>
 *
 * <p>发送策略：</p>
 * <ul>
 *   <li><b>没人看就不采集</b>：规则关闭时两条采集入口直接返回，一个对象都不建；</li>
 *   <li><b>玩家自己关了就不发</b>：客户端用 {@link HealthChangeWantPayload} 说明还要不要，
 *       逐玩家渲染清单里这一条关掉的玩家也会被排除；</li>
 *   <li><b>只发附近</b>：每一服务端 tick 结算一次，只把玩家 {@value #RANGE} 格以内的事件发给他，
 *       单包上限 {@link HealthChangePayload#MAX_ENTRIES} 条。</li>
 * </ul>
 *
 * <p>没装本模组的客户端由 {@link ServerPlayNetworking#canSend} 直接跳过。规则关闭后，已经采集但
 * 还没发出去的事件会在下一次 tick 结算时丢掉，不会残留到重新开启之后。</p>
 */
public final class SogHealthChangeSync {
    /** 显式说“不要”的玩家。 */
    private static final Set<UUID> OPT_OUT = ConcurrentHashMap.newKeySet();
    private static final int OPT_OUT_PRUNE_THRESHOLD = 256;

    /** 只下发玩家周围这个距离（格）以内的事件。 */
    private static final double RANGE = 48.0D;
    /** 这一条在逐玩家渲染清单里的名字。 */
    private static final String RULE = "healthChangeVisualizer";
    /**
     * 待下发事件的硬上限：伤害/治疗事件是“刷屏型”数据（刷怪塔、爆炸、群体伤害、再生药水一 tick
     * 就能产生上千条），超过上限就丢弃多余部分，先保住服务端 tick 时间。
     */
    private static final int MAX_PENDING = 1024;
    /** 小于这个变化量不算数（浮点噪声），避免刷出 +0.0 / -0.0 这种数字。 */
    private static final float MIN_DELTA = 0.05F;

    /** 还没结算的事件；采集与结算都在服务端线程，加锁只为兜住极端情况的并发调用。 */
    private static final List<Change> PENDING = new ArrayList<>();

    private SogHealthChangeSync() {
    }

    /**
     * 一条待下发的变化：所在维度 + 实体号 + 带符号的变化量（负=掉血，正=回血）+ 事件发生处的
     * 锚点坐标（取实体包围盒顶部，与客户端画数字的位置一致）。
     */
    private record Change(ServerLevel level, int entityId, float delta, double x, double y, double z) {
    }

    /** 受伤：挂在 {@link ServerLivingEntityEvents#AFTER_DAMAGE} 上（服务端伤害结算完成之后）。 */
    public static void onAfterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken,
            float damageTaken, boolean blocked) {
        if (!SogSettings.healthChangeVisualizer || blocked || entity == null
                || damageTaken < MIN_DELTA) {
            return;
        }
        record(entity, -damageTaken);
    }

    /**
     * 回血：由 {@code LivingEntityHealMixin} 在 {@code heal} 前后调用。
     *
     * @param entity 被治疗的实体
     * @param before 治疗前的血量（{@code heal} 的 HEAD 处取的）
     */
    public static void onHeal(LivingEntity entity, float before) {
        if (!SogSettings.healthChangeVisualizer || entity == null) {
            return;
        }
        float delta = entity.getHealth() - before;
        if (delta < MIN_DELTA) {
            // 血量已满时的空治疗、以及被上限截断后一点没回的场合，都不该出数字。
            return;
        }
        record(entity, delta);
    }

    /** 把一条变化记进待下发队列（游戏内实体与主世界之外一律忽略）。 */
    private static void record(LivingEntity entity, float delta) {
        if (entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) {
            // 客户端侧的实体不算：那条路要走纯客户端估算，免得两边重复出数字。
            return;
        }
        synchronized (PENDING) {
            if (PENDING.size() >= MAX_PENDING) {
                return;
            }
            Vec3 pos = entity.position();
            PENDING.add(new Change(level, entity.getId(), delta,
                    pos.x, pos.y + entity.getBbHeight(), pos.z));
        }
    }

    /** 客户端 C2S：还要不要收生命数值变化事件。 */
    public static void handleWant(HealthChangeWantPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (payload.want()) {
            OPT_OUT.remove(sender.getUUID());
        } else {
            OPT_OUT.add(sender.getUUID());
        }
    }

    /** 挂在 {@code ServerTickEvents.END_SERVER_TICK} 上：把这一 tick 采集到的事件发给附近玩家。 */
    public static void onEndServerTick(MinecraftServer server) {
        List<Change> changes;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            changes = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        if (!SogSettings.healthChangeVisualizer) {
            // 规则已经关掉：这一批直接丢掉。
            return;
        }
        if (OPT_OUT.size() > OPT_OUT_PRUNE_THRESHOLD) {
            OPT_OUT.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (OPT_OUT.contains(player.getUUID())
                    || !ServerPlayNetworking.canSend(player, HealthChangePayload.TYPE)
                    || !SogVisualizerPrefs.isRenderEnabled(player.getUUID(), RULE)) {
                continue;
            }
            List<HealthChangePayload.Entry> entries = collect(player, changes);
            if (!entries.isEmpty()) {
                String dimension = player.level().dimension().location().toString();
                ServerPlayNetworking.send(player, new HealthChangePayload(dimension, entries));
            }
        }
    }

    /** 取出这名玩家附近的变化事件（最多一包的量）。 */
    private static List<HealthChangePayload.Entry> collect(ServerPlayer player, List<Change> changes) {
        ServerLevel level = (ServerLevel) player.level();
        double rangeSqr = RANGE * RANGE;
        List<HealthChangePayload.Entry> entries = new ArrayList<>();
        for (Change change : changes) {
            if (entries.size() >= HealthChangePayload.MAX_ENTRIES) {
                break;
            }
            if (change.level() != level) {
                continue;
            }
            double dx = change.x() - player.getX();
            double dy = change.y() - player.getY();
            double dz = change.z() - player.getZ();
            if (dx * dx + dy * dy + dz * dz > rangeSqr) {
                continue;
            }
            entries.add(new HealthChangePayload.Entry(change.entityId(), change.delta(),
                    change.x(), change.y(), change.z()));
        }
        return entries;
    }

    /** 服务端停下时清掉还没发出去的事件与玩家偏好，避免下一个世界残留。 */
    public static void clear() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        OPT_OUT.clear();
    }
}

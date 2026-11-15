package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.AggroInfoPayload;
import cn.blockforge.generated.sogcarpet.net.AggroWantPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;

/**
 * “仇恨可视化”的服务端数据下发。
 *
 * <p>生物的锁定目标（{@code Mob#getTarget()}）与坚守者的“被声音吸引”状态都只存在于服务端，
 * 客户端拿不到，所以这条可视化需要一个服务端快照通道。做法与刷怪游走一致，但更省：</p>
 * <ul>
 *   <li><b>没人看就不扫</b>：规则关闭时整个 tick 回调直接返回；</li>
 *   <li><b>玩家自己关了就不发</b>：客户端用 {@link AggroWantPayload} 说明还要不要，
 *       逐玩家渲染清单里这条可视化关掉的玩家也会被排除；</li>
 *   <li><b>每 {@value #INTERVAL_TICKS} tick 扫一次</b>：仇恨关系不需要逐 tick 精度，
 *       降频可以把大群生物时的开销再砍一大截；</li>
 *   <li><b>只扫玩家附近 {@value #RANGE} 格</b>：用原版实体分区索引取一个 AABB 内的生物，
 *       远处的锁定关系看不到、也没必要发；</li>
 *   <li><b>每包有上限</b>：{@link AggroInfoPayload#MAX_ENTRIES} 条关系封顶。</li>
 * </ul>
 *
 * <p>没装本模组的客户端由 {@link ServerPlayNetworking#canSend} 直接跳过，不会因为拒收
 * 自定义负载而掉线。</p>
 */
public final class SogAggroSync {
    /** 显式说“不要”的玩家（默认要，避免丢包导致彻底看不到）。 */
    private static final Set<UUID> OPT_OUT = ConcurrentHashMap.newKeySet();
    private static final int OPT_OUT_PRUNE_THRESHOLD = 256;

    /** 只扫描玩家周围这个距离（格）以内的生物。 */
    private static final double RANGE = 64.0D;
    /** 每隔多少服务端 tick 扫一次（降频以减小压力）。 */
    private static final int INTERVAL_TICKS = 4;
    /** 本规则在逐玩家渲染清单里的名字。 */
    private static final String RULE = "aggroVisualizer";

    private static int tickCounter;

    private SogAggroSync() {
    }

    /** 客户端 C2S：还要不要收仇恨快照。 */
    public static void handleWant(AggroWantPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (payload.want()) {
            OPT_OUT.remove(sender.getUUID());
        } else {
            OPT_OUT.add(sender.getUUID());
        }
    }

    /** 挂在 {@code ServerTickEvents.END_SERVER_TICK} 上。 */
    public static void onEndServerTick(MinecraftServer server) {
        if (!SogSettings.aggroVisualizer) {
            return;
        }
        if (++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (OPT_OUT.size() > OPT_OUT_PRUNE_THRESHOLD) {
            OPT_OUT.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (OPT_OUT.contains(player.getUUID())
                    || !ServerPlayNetworking.canSend(player, AggroInfoPayload.TYPE)
                    || !SogVisualizerPrefs.isRenderEnabled(player.getUUID(), RULE)) {
                continue;
            }
            List<AggroInfoPayload.Entry> entries = collect(player);
            if (!entries.isEmpty()) {
                String dimension = player.level().dimension().location().toString();
                ServerPlayNetworking.send(player, new AggroInfoPayload(dimension, entries));
            }
        }
    }

    /** 扫玩家附近的生物，收集锁定关系与坚守者的疑问状态。 */
    private static List<AggroInfoPayload.Entry> collect(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB area = player.getBoundingBox().inflate(RANGE);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area);
        List<AggroInfoPayload.Entry> entries = new ArrayList<>();
        for (Mob mob : mobs) {
            if (entries.size() >= AggroInfoPayload.MAX_ENTRIES) {
                break;
            }
            if (mob.isRemoved()) {
                continue;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && !target.isRemoved()) {
                entries.add(new AggroInfoPayload.Entry(mob.getId(), target.getId(),
                        AggroInfoPayload.FLAG_TARGET, null));
            } else if (mob instanceof Warden warden) {
                // 坚守者没锁定目标时，如果脑里还留着“干扰位置”（被声音吸引后要去看的地方），
                // 就当成疑问状态发给客户端。
                Optional<BlockPos> disturbance =
                        warden.getBrain().getMemory(MemoryModuleType.DISTURBANCE_LOCATION);
                if (disturbance.isPresent()) {
                    entries.add(new AggroInfoPayload.Entry(warden.getId(), -1,
                            AggroInfoPayload.FLAG_QUESTION, disturbance.get()));
                }
            }
        }
        return entries;
    }
}

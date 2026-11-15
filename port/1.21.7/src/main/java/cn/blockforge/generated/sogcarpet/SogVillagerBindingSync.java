package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.VillagerBindingPayload;
import cn.blockforge.generated.sogcarpet.net.VillagerBindingWantPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * “村民功能方块绑定可视化”的服务端数据下发。
 *
 * <p>村民绑定的床与工作方块（{@code Brain} 里的 {@code HOME} / {@code JOB_SITE} 记忆）只在
 * 服务端维护，客户端拿不到，所以这条可视化需要一个服务端快照通道，做法与
 * {@link SogAggroSync} 一致：</p>
 * <ul>
 *   <li><b>没人看就不扫</b>：规则关闭时整个 tick 回调直接返回；</li>
 *   <li><b>玩家自己关了就不发</b>：客户端用 {@link VillagerBindingWantPayload} 说明还要不要，
 *       逐玩家渲染清单里这条可视化关掉的玩家也会被排除；</li>
 *   <li><b>每 {@value #INTERVAL_TICKS} tick 扫一次</b>，只扫玩家附近 {@value #RANGE} 格；</li>
 *   <li><b>每包有上限</b>：{@link VillagerBindingPayload#MAX_ENTRIES} 条封顶。</li>
 * </ul>
 *
 * <p>没装本模组的客户端由 {@link ServerPlayNetworking#canSend} 直接跳过。</p>
 */
public final class SogVillagerBindingSync {
    /** 显式说“不要”的玩家。 */
    private static final Set<UUID> OPT_OUT = ConcurrentHashMap.newKeySet();
    private static final int OPT_OUT_PRUNE_THRESHOLD = 256;

    /** 只扫描玩家周围这个距离（格）以内的村民。 */
    private static final double RANGE = 48.0D;
    /** 每隔多少服务端 tick 扫一次。 */
    private static final int INTERVAL_TICKS = 8;
    /** 本规则在逐玩家渲染清单里的名字。 */
    private static final String RULE = "villagerBindingVisualizer";

    private static int tickCounter;

    private SogVillagerBindingSync() {
    }

    /** 客户端 C2S：还要不要收村民绑定快照。 */
    public static void handleWant(VillagerBindingWantPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (payload.want()) {
            OPT_OUT.remove(sender.getUUID());
        } else {
            OPT_OUT.add(sender.getUUID());
        }
    }

    /** 挂在 {@code ServerTickEvents.END_SERVER_TICK} 上。 */
    public static void onEndServerTick(MinecraftServer server) {
        if (!SogSettings.villagerBindingVisualizer) {
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
                    || !ServerPlayNetworking.canSend(player, VillagerBindingPayload.TYPE)
                    || !SogVisualizerPrefs.isRenderEnabled(player.getUUID(), RULE)) {
                continue;
            }
            List<VillagerBindingPayload.Entry> entries = collect(player);
            if (!entries.isEmpty()) {
                String dimension = player.level().dimension().location().toString();
                ServerPlayNetworking.send(player, new VillagerBindingPayload(dimension, entries));
            }
        }
    }

    /** 扫玩家附近的村民，收集它们的床 / 工作方块绑定。 */
    private static List<VillagerBindingPayload.Entry> collect(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB area = player.getBoundingBox().inflate(RANGE);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        List<VillagerBindingPayload.Entry> entries = new ArrayList<>();
        for (Villager villager : villagers) {
            if (entries.size() >= VillagerBindingPayload.MAX_ENTRIES) {
                break;
            }
            if (villager.isRemoved()) {
                continue;
            }
            BlockPos bed = resolve(level, villager.getBrain().getMemory(MemoryModuleType.HOME));
            BlockPos job = resolve(level, villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
            if (bed == null && job == null) {
                continue;
            }
            int flags = 0;
            if (bed != null) {
                flags |= VillagerBindingPayload.FLAG_BED;
            }
            if (job != null) {
                flags |= VillagerBindingPayload.FLAG_JOB;
            }
            entries.add(new VillagerBindingPayload.Entry(villager.getId(), flags, bed, job));
        }
        return entries;
    }

    /** 把一个全局位置解析成同一维度的方块坐标；不在本维度或为空时返回 null。 */
    private static BlockPos resolve(ServerLevel level, Optional<GlobalPos> memory) {
        if (memory == null || memory.isEmpty()) {
            return null;
        }
        GlobalPos global = memory.get();
        if (!level.dimension().equals(global.dimension())) {
            return null;
        }
        return global.pos();
    }
}

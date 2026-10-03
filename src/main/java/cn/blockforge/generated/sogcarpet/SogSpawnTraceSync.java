package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.SpawnTracePayload;
import cn.blockforge.generated.sogcarpet.net.SpawnTraceWantPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * “刷怪游走可视化”的服务端下发。
 *
 * <p><b>本轮起服务端只下发“出生事件”，不再下发游走路线。</b> 轨迹由客户端盯着生物的真实
 * 移动自己采样，所以服务端每个 tick 最多只做一件小事：把新刷出生物的
 * {@code 实体号 + 出生坐标} 按玩家所在维度、按距离打包发出去。刷怪塔里一 tick 刷出上千只，
 * 服务端的分配量也比以前（要复制整条候选点列表）小得多。</p>
 *
 * <p><b>服务器压力</b>：</p>
 * <ul>
 *   <li><b>没人看就不采集</b>：先统计在线且能收包的玩家，一个都没有时通过
 *       {@link SogSpawnTrace#setRecording(boolean)} 把采集整个关掉，连出生事件都不记；</li>
 *   <li><b>玩家自己关了就不发</b>：客户端把本地总开关/渲染开关状态用
 *       {@link SpawnTraceWantPayload} 发过来，明确说不要的玩家不会再收到出生事件包；</li>
 *   <li><b>按距离过滤</b>：只把落在玩家 {@link SpawnTracePayload#MAX_DISTANCE} 格以内的
 *       出生事件发给该玩家（原版刷怪本来就在玩家附近，远处出生点没有观看价值）；</li>
 *   <li><b>每 tick 一批、每包有上限</b>：每 tick 每个玩家最多
 *       {@link SpawnTracePayload#MAX_EVENTS} 条，多出来的丢掉。</li>
 * </ul>
 *
 * <p>没装本模组的客户端由 {@link ServerPlayNetworking#canSend} 直接跳过，
 * 不会因为拒收自定义负载而掉线。</p>
 */
public final class SogSpawnTraceSync {
    /**
     * 显式说“不要”的玩家。
     *
     * <p>默认是要：这样即使这个 C2S 包偶尔没到，玩家也不会彻底看不到轨迹。客户端在加入、
     * 收到规则同步、改动本地可视化开关时把最新状态发过来，说不要的玩家就会被排除在下发
     * 名单外，不会再白收每 tick 的出生事件包。离线玩家的残留项不影响行为，数量大时顺手清一次。</p>
     */
    private static final Set<UUID> OPT_OUT = ConcurrentHashMap.newKeySet();
    /** 残留项超过这个数就清理一次离线玩家。 */
    private static final int OPT_OUT_PRUNE_THRESHOLD = 256;

    private SogSpawnTraceSync() {
    }

    /** 客户端 C2S：还要不要收刷怪出生事件。 */
    public static void handleWant(SpawnTraceWantPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (payload.want()) {
            OPT_OUT.remove(sender.getUUID());
        } else {
            OPT_OUT.add(sender.getUUID());
        }
    }

    /** 挂在 {@code ServerTickEvents.END_SERVER_TICK} 上，每个服务端 tick 调一次。 */
    public static void onEndServerTick(MinecraftServer server) {
        if (!SogSettings.mobSpawnVisualizer) {
            // 规则关闭时不采集也不下发；队列由规则观察器在关闭那一刻清空。
            SogSpawnTrace.setRecording(false);
            SogSpawnTrace.drain();
            return;
        }
        if (OPT_OUT.size() > OPT_OUT_PRUNE_THRESHOLD) {
            OPT_OUT.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        }
        List<ServerPlayer> listeners = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 三重门槛：装了客户端模组能收包、没有用 C2S 明确说不要、且逐玩家渲染清单里
            // 这条可视化是开的（命令给没装模组的玩家配的清单同样在这里生效）。
            if (!OPT_OUT.contains(player.getUUID())
                    && ServerPlayNetworking.canSend(player, SpawnTracePayload.TYPE)
                    && SogVisualizerPrefs.isRenderEnabled(player.getUUID(), "mobSpawnVisualizer")) {
                listeners.add(player);
            }
        }
        // 没有任何装了本模组的在线玩家时，服务端连采集都省掉（下一 tick 生效）。
        SogSpawnTrace.setRecording(!listeners.isEmpty());
        List<SogSpawnTrace.SpawnEvent> drained = SogSpawnTrace.drain();
        if (drained.isEmpty() || listeners.isEmpty()) {
            return;
        }
        for (ServerPlayer player : listeners) {
            ResourceKey<Level> dimension = player.level().dimension();
            BlockPos playerPos = player.blockPosition();
            List<SogSpawnTrace.SpawnEvent> events = new ArrayList<>();
            for (SogSpawnTrace.SpawnEvent event : drained) {
                if (!dimension.equals(event.dimension())) {
                    continue;
                }
                if (playerPos.distSqr(event.spawnPos()) > SpawnTracePayload.MAX_DISTANCE_SQR) {
                    continue;
                }
                events.add(event);
                if (events.size() >= SpawnTracePayload.MAX_EVENTS) {
                    break;
                }
            }
            if (!events.isEmpty()) {
                ServerPlayNetworking.send(player, new SpawnTracePayload(
                        dimension.identifier().toString(), events));
            }
        }
    }
}

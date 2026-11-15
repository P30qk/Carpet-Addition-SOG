package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.SculkTriggerPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * “触发判定线”的服务端下发。
 *
 * <p>每 tick 把 {@link SogSculkTrigger} 里新记下的振动触发按玩家过滤后发出去：只发给装了本模组、
 * 逐玩家渲染清单里开着 {@code sculkRangeVisualizer} 的玩家，且只发检测体落在玩家
 * {@value #MAX_DISTANCE} 格以内的触发；没有这样的在线玩家时连采集都关掉。</p>
 */
public final class SogSculkTriggerSync {
    /** 触发事件的下发半径（格）。 */
    public static final double MAX_DISTANCE = 64.0D;
    public static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;
    private static final String RULE = "sculkRangeVisualizer";

    private SogSculkTriggerSync() {
    }

    /** 挂在 {@code ServerTickEvents.END_SERVER_TICK} 上。 */
    public static void onEndServerTick(MinecraftServer server) {
        if (!SogSettings.sculkRangeVisualizer) {
            SogSculkTrigger.setRecording(false);
            SogSculkTrigger.drain();
            return;
        }
        List<ServerPlayer> listeners = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, SculkTriggerPayload.TYPE)
                    && SogVisualizerPrefs.isRenderEnabled(player.getUUID(), RULE)) {
                listeners.add(player);
            }
        }
        SogSculkTrigger.setRecording(!listeners.isEmpty());
        List<SogSculkTrigger.Event> drained = SogSculkTrigger.drain();
        if (drained.isEmpty() || listeners.isEmpty()) {
            return;
        }
        for (ServerPlayer player : listeners) {
            ResourceKey<Level> dimension = player.level().dimension();
            BlockPos playerPos = player.blockPosition();
            List<SculkTriggerPayload.Entry> entries = new ArrayList<>();
            for (SogSculkTrigger.Event event : drained) {
                if (!dimension.equals(event.dimension())) {
                    continue;
                }
                if (playerPos.distSqr(event.receiver()) > MAX_DISTANCE_SQR) {
                    continue;
                }
                entries.add(new SculkTriggerPayload.Entry(event.receiver(), event.source()));
                if (entries.size() >= SculkTriggerPayload.MAX_ENTRIES) {
                    break;
                }
            }
            if (!entries.isEmpty()) {
                ServerPlayNetworking.send(player,
                        new SculkTriggerPayload(dimension.identifier().toString(), entries));
            }
        }
    }
}

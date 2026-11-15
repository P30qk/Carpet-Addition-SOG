package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.ItemCountPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 掉落物数量可视化“按 Tab 显示总物品数”的服务端统计。
 *
 * <p>装了本模组的客户端会收到这个总数并在长按 Tab 时显示；纯客户端（对面没装本模组）则由
 * 客户端自己统计本地已同步的掉落物。规则关掉、或玩家把这条可视化关掉后不再发送。</p>
 */
public final class SogItemCountSync {
    /** 统计与下发间隔（服务端 tick），约一秒一次。 */
    private static final int INTERVAL_TICKS = 20;

    private static int tickCounter;

    private SogItemCountSync() {
    }

    public static void onEndServerTick(MinecraftServer server) {
        if (!SogSettings.dropCountVisualizer) {
            tickCounter = 0;
            return;
        }
        if (++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, ItemCountPayload.TYPE)
                    || !SogVisualizerPrefs.isRenderEnabled(player.getUUID(), "dropCountVisualizer")
                    || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            int total = 0;
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item && !item.getItem().isEmpty()) {
                    total += item.getItem().getCount();
                }
            }
            ServerPlayNetworking.send(player, new ItemCountPayload(total));
        }
    }

    public static void clear() {
        tickCounter = 0;
    }
}

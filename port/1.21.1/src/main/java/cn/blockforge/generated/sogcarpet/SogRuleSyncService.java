package cn.blockforge.generated.sogcarpet;

import carpet.api.settings.CarpetRule;
import cn.blockforge.generated.sogcarpet.net.VisualizerRulesPayload;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * 把可视化规则（以及客户端需要知道开关的规则，比如 Tweakeroo 灵活放置四角三角）的
 * 开关状态同步给装了本模组的客户端。
 *
 * <p>两个时机：玩家加入时发一次；相关规则被改动后再广播一次。同步是纯“服务端说、
 * 客户端听”，客户端不需要回包；没装本模组（或没声明通道）的玩家会被 {@code canSend}
 * 直接跳过，不会因为拒收自定义负载而掉线。</p>
 */
public final class SogRuleSyncService {
    private SogRuleSyncService() {
    }

    /** 规则观察器挂在 carpet 核心 manager 上，这里按“需要同步给客户端”的规则名过滤。 */
    public static void onRuleChanged(CommandSourceStack source, CarpetRule<?> rule, String value) {
        if (!SogSettings.VISUALIZER_RULES.contains(rule.name())
                && !SogSettings.CLIENT_SYNC_RULES.contains(rule.name())) {
            return;
        }
        MinecraftServer server = source.getServer();
        if (server != null) {
            broadcast(server);
        }
    }

    /** 玩家加入时补发一次当前状态。 */
    public static void onJoin(ServerGamePacketListenerImpl handler, PacketSender sender,
            MinecraftServer server) {
        send(handler.getPlayer());
        // 规则是全局的，渲染清单是逐玩家的：紧跟着把“这个玩家自己”的清单也发一遍。
        SogVisualizerPrefs.sendTo(handler.getPlayer());
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    private static void send(ServerPlayer player) {
        if (player == null || !ServerPlayNetworking.canSend(player, VisualizerRulesPayload.TYPE)) {
            return;
        }
        StringBuilder sb = new StringBuilder(256);
        for (String name : SogSettings.VISUALIZER_RULES) {
            sb.append(name).append('=').append(SogSettings.visualizerRuleValue(name) ? '1' : '0')
                    .append(';');
        }
        // 非可视化的客户端规则跟在后面：客户端解析时按名字取用，不认识的名字直接忽略。
        sb.append("flexiblePlacementCorners=")
                .append(SogSettings.flexiblePlacementCorners ? '1' : '0').append(';');
        ServerPlayNetworking.send(player, new VisualizerRulesPayload(sb.toString()));
    }
}

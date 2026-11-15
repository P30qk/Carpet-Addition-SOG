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
        // 能不能改规则（OP）与哪些规则是永久开启的，也一并告诉他。
        SogRuleControl.sendTo(handler.getPlayer());
        // 配方书：原版只在玩家加入时发一次展示条目，这里把“规则已开启”的本模组配方
        // 补解锁一次，于是即使玩家在规则打开之前就拿到了材料，也能在配方书里看到。
        SogRecipeBook.syncOnJoin(handler.getPlayer());
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
        // 规则改动后「哪些是永久开启」也可能变了（面板里切三态），顺手把状态一起刷新。
        SogRuleControl.broadcast(server);
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
        // “紫颂花可催熟”是服务端逻辑，但客户端骨粉右键预测要按同一个开关判断。
        sb.append("chorusFlowerBonemeal=")
                .append(SogSettings.chorusFlowerBonemeal ? '1' : '0').append(';');
        // “自定义信标效果”也是服务端逻辑，但客户端的信标界面要据此决定画不画“+”按钮。
        sb.append("customBeaconEffects=")
                .append(SogSettings.customBeaconEffects ? '1' : '0').append(';');
        // 「随身信标」：客户端要据此决定 Ctrl+右键要不要拦下来（规则关闭时保持原版右键行为）。
        sb.append("portableBeacon=")
                .append(SogSettings.portableBeacon ? '1' : '0').append(';');
        // 黑白名单：客户端信标界面的自定义槽位按它显示可选项/锁定状态，并且要在 Op 修改后立刻刷新。
        // 黑名单与白名单各自一份、互不覆盖；名单可能很长，所以各占一行，模式用关键字而不是布尔值。
        sb.append("beaconEffectFilterMode=")
                .append(SogBeaconFilter.mode().name()).append(';');
        sb.append("beaconEffectBlacklist=")
                .append(SogBeaconFilter.normalize(SogSettings.beaconEffectBlacklist)).append(';');
        sb.append("beaconEffectWhitelist=")
                .append(SogBeaconFilter.normalize(SogSettings.beaconEffectWhitelist)).append(';');
        ServerPlayNetworking.send(player, new VisualizerRulesPayload(sb.toString()));
    }
}

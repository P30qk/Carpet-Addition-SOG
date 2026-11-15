package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：这个玩家能不能改规则、以及哪些规则已经被「永久开启」。
 *
 * <p>调控面板要把每条可视化规则显示成「关闭 / 暂时开启 / 永久开启」三态之一，其中「开没开」
 * 已经从 {@link VisualizerRulesPayload} 拿到了，唯独「是不是永久」只存在于 carpet 的配置文件里，
 * 所以单独补一个包：</p>
 *
 * <pre>
 * op=1;perm:sculkRangeVisualizer=1;perm:dropCountVisualizer=1;
 * </pre>
 *
 * <p>{@code op=1} 表示这个玩家有 OP 权限（{@code COMMANDS_GAMEMASTER}），面板才显示可点的三态按钮；
 * 没有 OP 的玩家照旧只看到只读状态。玩家加入、规则被改动、以及面板里改完之后都会重发一次。</p>
 */
public record RuleStatePayload(String data) implements CustomPacketPayload {
    /** 同步串的长度上限：每条可视化规则最多几十个字符，留足余量。 */
    private static final int MAX_LENGTH = 8192;

    public static final Type<RuleStatePayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:rule_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RuleStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_LENGTH), RuleStatePayload::data,
                    RuleStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

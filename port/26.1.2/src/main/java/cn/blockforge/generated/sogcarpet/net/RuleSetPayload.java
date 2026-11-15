package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：在可视化调控面板里把某条规则切成「关闭 / 暂时开启 / 永久开启」。
 *
 * <p>面板只能改本模组自己的可视化规则（{@link cn.blockforge.generated.sogcarpet.SogSettings#VISUALIZER_RULES}，
 * 全是布尔规则），所以这里只带规则名 + 目标模式，值固定为 true/false。服务端会先校验发包者
 * 是 OP（{@code COMMANDS_GAMEMASTER}），再用 carpet 自己的指令通道去改规则——于是持久化、
 * 观察器广播、{@code carpet.conf} 的写法全部沿用 carpet 的实现，不在这里另造一套。</p>
 *
 * <p>模式：</p>
 * <ul>
 *   <li>{@link #MODE_OFF}：关闭（同时清掉持久项，重启后仍然是关）；</li>
 *   <li>{@link #MODE_TEMPORARY_ON}：暂时开启（本次运行有效，不写 carpet.conf）；</li>
 *   <li>{@link #MODE_PERMANENT_ON}：永久开启（写入 carpet.conf，重启后仍然开）。</li>
 * </ul>
 */
public record RuleSetPayload(String rule, int mode) implements CustomPacketPayload {
    /** 关闭：规则设为 false，并移除持久项。 */
    public static final int MODE_OFF = 0;
    /** 暂时开启：规则设为 true，不写配置。 */
    public static final int MODE_TEMPORARY_ON = 1;
    /** 永久开启：规则设为 true，并写入配置。 */
    public static final int MODE_PERMANENT_ON = 2;

    /** 规则名的长度上限（carpet 规则名都很短，这里只是防空串/超长包）。 */
    private static final int MAX_RULE_LENGTH = 64;

    public static final Type<RuleSetPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:rule_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RuleSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_RULE_LENGTH), RuleSetPayload::rule,
                    ByteBufCodecs.VAR_INT, RuleSetPayload::mode,
                    RuleSetPayload::new);

    public RuleSetPayload {
        if (mode < MODE_OFF || mode > MODE_PERMANENT_ON) {
            mode = MODE_OFF;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

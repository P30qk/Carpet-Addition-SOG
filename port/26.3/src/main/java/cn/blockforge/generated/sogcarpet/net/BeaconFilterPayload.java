package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：Op 在效果选择界面里锁定/解锁某个效果，或切换黑/白名单模式。
 *
 * <p>两个字段：{@code action} + {@code value}。</p>
 * <ul>
 *   <li>{@code action = "add"} / {@code "remove"}：{@code value} 是 {@code 模式|效果 id}，
 *       把该效果加入 / 移出<u>指定模式那一份</u>名单。客户端按自己看到的勾选状态直接给出
 *       “加”还是“删”，服务端不再“翻转一次”——这样即使某一刻客户端与服务端的名单暂时不同步，
 *       操作结果也一定是玩家想要的那个方向；</li>
 *   <li>{@code action = "toggle"}：旧格式（只带效果 id），保留兼容，等价于按当前模式翻转；</li>
 *   <li>{@code action = "mode"}：{@code value} 是 {@code BLACKLIST} / {@code WHITELIST}。</li>
 * </ul>
 *
 * <p>服务端校验发包者是 Op（{@code COMMANDS_GAMEMASTER}），然后用 carpet 自己的
 * {@code /carpet setDefault} 通道改规则——持久化、规则广播、客户端同步全部沿用 carpet，
 * 不需要另造一套配置格式。</p>
 */
public record BeaconFilterPayload(String action, String value) implements CustomPacketPayload {
    /** 切换某个效果是否可选（旧格式，按当前模式翻转）。 */
    public static final String ACTION_TOGGLE = "toggle";
    /** 把某个效果加入指定名单。 */
    public static final String ACTION_ADD = "add";
    /** 把某个效果移出指定名单。 */
    public static final String ACTION_REMOVE = "remove";
    /** {@code value} 里「模式」与「效果 id」的分隔符（效果 id 自身不含 {@code |}）。 */
    public static final String SEPARATOR = "|";
    /** 切换黑/白名单模式。 */
    public static final String ACTION_MODE = "mode";

    private static final int MAX_ACTION_LENGTH = 16;
    private static final int MAX_VALUE_LENGTH = 128;

    public static final Type<BeaconFilterPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:beacon_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeaconFilterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_ACTION_LENGTH), BeaconFilterPayload::action,
                    ByteBufCodecs.stringUtf8(MAX_VALUE_LENGTH), BeaconFilterPayload::value,
                    BeaconFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

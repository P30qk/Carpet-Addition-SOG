package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：这个玩家的<b>逐玩家可视化渲染清单</b>。
 *
 * <p>和 {@link VisualizerRulesPayload}（carpet 规则开关，全局）不同，这条包发的是
 * “这个玩家自己要不要画某条可视化”。服务端为每个玩家单独保存一份，因此同一台服务器上
 * 玩家之间互不影响；没装本模组的玩家收不到这条包，但服务端仍然为它保存着清单，
 * 可以由管理员用 {@code /sogcarpet visualizer} 配置。</p>
 *
 * @param configured 服务端有没有这个玩家的记录。false 时客户端保留本地文件里的设置；
 *                   true 时以 {@code data} 为准。
 * @param data       {@code 规则名=0/1;规则名=0/1;} 形式的清单。
 */
public record VisualizerPrefsPayload(boolean configured, String data) implements CustomPacketPayload {
    public static final Type<VisualizerPrefsPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:visualizer_prefs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VisualizerPrefsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, VisualizerPrefsPayload::configured,
                    ByteBufCodecs.STRING_UTF8, VisualizerPrefsPayload::data,
                    VisualizerPrefsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

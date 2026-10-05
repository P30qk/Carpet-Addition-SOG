package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：覆盖上报者自己的可视化渲染清单。
 *
 * <p>客户端在 Ctrl+V 里改动任一“渲染”开关后，把整份清单
 * （{@code 规则名=0/1;…}）发上来，服务端存在该玩家名下。包本身不带玩家身份——
 * 服务端一律写进发包者自己的 {@link java.util.UUID}，所以客户端无法改别人的清单；
 * 要改别人得走 {@code /sogcarpet visualizer}，由管理员权限把关。</p>
 */
public record VisualizerPrefsSetPayload(String data) implements CustomPacketPayload {
    public static final Type<VisualizerPrefsSetPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:visualizer_prefs_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VisualizerPrefsSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, VisualizerPrefsSetPayload::data,
                    VisualizerPrefsSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

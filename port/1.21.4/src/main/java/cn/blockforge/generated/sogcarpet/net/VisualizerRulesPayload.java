package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：把所有可视化规则的开关状态同步给玩家。
 *
 * <p>carpet 规则存在服务端，专用服务器的客户端读不到自己的静态字段，Ctrl+V 界面上就显示
 * 不出“规则是否开启”，可视化本身也会因为读不到规则而完全不画。这里把状态编成
 * {@code 规则名=0/1;规则名=0/1;} 的短字符串一次性发过来。</p>
 */
public record VisualizerRulesPayload(String data) implements CustomPacketPayload {
    public static final Type<VisualizerRulesPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:visualizer_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VisualizerRulesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, VisualizerRulesPayload::data,
                    VisualizerRulesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

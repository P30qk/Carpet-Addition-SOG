package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：物品流向可视化面板要显示的会话列表文本。
 *
 * <p>每行一个会话：{@code 标签|已用预算|总预算}，行间以 {@code '\n'} 分隔；规则关闭时只有一行
 * {@code rule_off}。面板把它直接画出来即可。</p>
 */
public record ItemFlowStatusPayload(String data) implements CustomPacketPayload {
    public static final Type<ItemFlowStatusPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:item_flow_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFlowStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ItemFlowStatusPayload::data,
                    ItemFlowStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

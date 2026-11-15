package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：全服/当前维度掉落物总数（掉落物数量可视化按 Tab 显示用）。
 *
 * <p>装了本模组的客户端在按 Tab 时显示这个由服务端统计的总数；纯客户端（对面没装本模组）
 * 则退回本地统计已同步的掉落物数量之和。</p>
 */
public record ItemCountPayload(int total) implements CustomPacketPayload {
    public static final Type<ItemCountPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:item_count"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemCountPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ItemCountPayload::total,
                    ItemCountPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

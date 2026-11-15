package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：物品流向可视化面板上的一次操作。
 *
 * <p>面板按钮取代了原来的 {@code /ift} 指令。{@code action} 取
 * {@link #MARK_HELD}（标记手持物品）、{@link #CLEAR}（清除全部）或 {@link #LIST}（刷新列表）；
 * 标记时带上颜色（{@code rgb}）与路径记录间隔（{@code pathInterval}，0 为不记录路径）。</p>
 */
public record ItemFlowActionPayload(int action, int rgb, int pathInterval) implements CustomPacketPayload {
    /** 标记玩家主手上的物品。 */
    public static final int MARK_HELD = 0;
    /** 清除全部追踪会话与高亮。 */
    public static final int CLEAR = 1;
    /** 只请求一份最新会话列表。 */
    public static final int LIST = 2;

    public static final Type<ItemFlowActionPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:item_flow_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFlowActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ItemFlowActionPayload::action,
                    ByteBufCodecs.VAR_INT, ItemFlowActionPayload::rgb,
                    ByteBufCodecs.VAR_INT, ItemFlowActionPayload::pathInterval,
                    ItemFlowActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

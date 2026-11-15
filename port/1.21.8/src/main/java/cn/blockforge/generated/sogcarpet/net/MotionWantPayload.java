package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：这个客户端还要不要收“外部施加冲量”快照（速度/动量可视化用）。
 *
 * <p>和刷怪游走、仇恨可视化的 want 包同理：服务端只看得到 carpet 规则开关，看不到玩家在
 * Ctrl+V 里把这条可视化的渲染开关关掉了。玩家关掉后服务端就没必要再扫附近实体、发冲量；
 * {@code want=false} 的玩家不再收到，规则对其他玩家照常生效。服务端按“默认要、显式不要才排除”。</p>
 */
public record MotionWantPayload(boolean want) implements CustomPacketPayload {
    public static final Type<MotionWantPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:motion_want"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MotionWantPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, MotionWantPayload::want,
                    MotionWantPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

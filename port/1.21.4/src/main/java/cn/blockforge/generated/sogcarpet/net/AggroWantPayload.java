package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：这个客户端还要不要收“仇恨/目标”快照。
 *
 * <p>和刷怪游走的 want 包同理：服务端只看得到 carpet 规则开关，看不到玩家在 Ctrl+V 里把
 * 总开关或这条可视化关掉了。如果玩家已经关掉，服务端再每隔几 tick 给他扫生物、发快照就是白费。
 * 客户端因此在加入、收到规则同步、以及每次改动本地开关时把“还要不要”发过来；
 * {@code want=false} 的玩家不再收到快照，规则对其他玩家照常生效。</p>
 *
 * <p>服务端按“默认要、显式不要才排除”处理，偶尔丢一次也不会让人彻底看不到。</p>
 */
public record AggroWantPayload(boolean want) implements CustomPacketPayload {
    public static final Type<AggroWantPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:aggro_want"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AggroWantPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AggroWantPayload::want,
                    AggroWantPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

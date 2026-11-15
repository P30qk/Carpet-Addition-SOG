package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：这个客户端还要不要收“生命数值变化”事件。
 *
 * <p>与仇恨可视化、村民绑定可视化的 want 包同理：服务端只看得到 carpet 规则开关，看不到玩家
 * 在 Ctrl+V 里把总开关或这一条可视化关掉了。玩家关掉后服务端再采集、再发包就是白费，所以客户端
 * 在加入、收到规则同步、以及每次改动本地开关时把“还要不要”发过来；{@code want=false} 的玩家
 * 不再收到变化事件包。服务端按“默认要、显式不要才排除”处理。</p>
 */
public record HealthChangeWantPayload(boolean want) implements CustomPacketPayload {
    public static final Type<HealthChangeWantPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:health_change_want"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HealthChangeWantPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, HealthChangeWantPayload::want,
                    HealthChangeWantPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

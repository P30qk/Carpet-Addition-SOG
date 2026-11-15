package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：这个客户端还要不要收“村民绑定”快照。
 *
 * <p>和仇恨可视化的 want 包同理：服务端只看得到 carpet 规则开关，看不到玩家在 Ctrl+V 里
 * 把总开关或这条可视化关掉了。玩家关掉后服务端再扫村民、发快照就是白费，所以客户端在加入、
 * 收到规则同步、以及每次改动本地开关时把“还要不要”发过来；{@code want=false} 的玩家不再
 * 收到快照。服务端按“默认要、显式不要才排除”处理。</p>
 */
public record VillagerBindingWantPayload(boolean want) implements CustomPacketPayload {
    public static final Type<VillagerBindingWantPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:villager_binding_want"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerBindingWantPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, VillagerBindingWantPayload::want,
                    VillagerBindingWantPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

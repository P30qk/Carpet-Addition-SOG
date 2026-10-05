package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：这个客户端还要不要收“刷怪游走”轨迹。
 *
 * <p>“刷怪游走可视化”的开关有两层：carpet 规则（服务端，决定有没有数据）与 Ctrl+V
 * 里的本地渲染开关（纯客户端）。服务端只看得到规则开关，看不到玩家自己把渲染关掉了；
 * 如果玩家把总开关或这条可视化关了，服务端再逐 tick 给他发轨迹就是白费上行。
 * 客户端因此在加入、收到规则同步、以及每次改动本地开关时把“还要不要”发过来：
 * {@code want=false} 的玩家会被单独排除在下发名单外，规则对其他玩家照常生效。</p>
 *
 * <p>服务端按“默认要、显式不要才排除”处理，所以这个包偶尔丢一次也不会让人彻底看不到。</p>
 */
public record SpawnTraceWantPayload(boolean want) implements CustomPacketPayload {
    public static final Type<SpawnTraceWantPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:spawn_trace_want"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnTraceWantPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SpawnTraceWantPayload::want,
                    SpawnTraceWantPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

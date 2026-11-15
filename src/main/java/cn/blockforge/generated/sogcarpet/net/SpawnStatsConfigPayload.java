package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：“怪物生成可视化”的统计面板配置。
 *
 * <p>面板写进 Tab 列表页脚，所以由服务端组装；客户端在配置界面里开关“统计面板”或某个统计
 * 分类后，把整份清单（{@code key=0/1;…}）发上来，服务端按发包者自己的 UUID 存下来，之后只发
 * 他勾选的分类。没装本模组的纯客户端不会发这个包，服务端就按默认（全部显示）。</p>
 */
public record SpawnStatsConfigPayload(String data) implements CustomPacketPayload {
    public static final Type<SpawnStatsConfigPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:spawn_stats_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnStatsConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SpawnStatsConfigPayload::data,
                    SpawnStatsConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

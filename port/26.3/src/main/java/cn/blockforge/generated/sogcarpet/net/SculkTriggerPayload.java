package cn.blockforge.generated.sogcarpet.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：一批“声音检测被触发”的事件。
 *
 * <p>幽匿感测体/坚守者收到振动（声音）的位置与来源只在服务端存在，客户端看不到，
 * 所以“触发判定线”需要服务端把每次触发打包下发。每条 {@link Entry} 只有两个方块坐标：
 * 收到振动的检测体（感测体或坚守者）位置，以及引起这次振动的声音源位置。客户端按坐标
 * 把两者用一条线连起来，短暂显示后淡出。</p>
 *
 * <p>只发给附近、装了本模组且开着这条可视化的玩家；一包最多 {@value #MAX_ENTRIES} 条。</p>
 */
public record SculkTriggerPayload(String dimension, List<Entry> entries) implements CustomPacketPayload {
    /** 一包最多携带多少条触发事件。 */
    public static final int MAX_ENTRIES = 256;
    private static final int MAX_DIMENSION_LENGTH = 128;

    public static final Type<SculkTriggerPayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:sculk_trigger"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SculkTriggerPayload> STREAM_CODEC =
            StreamCodec.of(SculkTriggerPayload::encode, SculkTriggerPayload::decode);

    /** 一次触发：检测体位置（receiver）＋声音源位置（source）。 */
    public record Entry(BlockPos receiver, BlockPos source) {
    }

    private static void encode(RegistryFriendlyByteBuf buf, SculkTriggerPayload payload) {
        buf.writeUtf(payload.dimension == null ? "" : payload.dimension, MAX_DIMENSION_LENGTH);
        List<Entry> entries = payload.entries;
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeBlockPos(entry.receiver());
            buf.writeBlockPos(entry.source());
        }
    }

    private static SculkTriggerPayload decode(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalStateException("sculk trigger payload: bad entry count " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos receiver = buf.readBlockPos();
            BlockPos source = buf.readBlockPos();
            entries.add(new Entry(receiver, source));
        }
        return new SculkTriggerPayload(dimension, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

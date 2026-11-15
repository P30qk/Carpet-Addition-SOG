package cn.blockforge.generated.sogcarpet.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：一份“外部施加冲量”快照（速度/动量可视化用）。
 *
 * <p>外部施加的速度改变发生在服务端（爆炸击退、推动、以及本模组的珍珠叠加轴速度），
 * 客户端默认看不到，所以由服务端每隔几 tick 把玩家附近、近期被施加过冲量的实体打包下发。
 * 客户端按实体号找回本地实体，把冲量画成那一支“外力”箭头。</p>
 *
 * <p>每条 {@link Entry} 只有实体号与冲量三分量（单位仍是格/tick）。包按玩家过滤，只发附近实体；
 * 没装模组的客户端由 {@code canSend} 跳过；一条快照最多 {@value #MAX_ENTRIES} 条。</p>
 */
public record MotionInfoPayload(String dimension, List<Entry> entries) implements CustomPacketPayload {
    /** 一包最多携带多少条冲量。 */
    public static final int MAX_ENTRIES = 64;
    /** 维度标识串的长度上限。 */
    private static final int MAX_DIMENSION_LENGTH = 128;

    public static final Type<MotionInfoPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:motion_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MotionInfoPayload> STREAM_CODEC =
            StreamCodec.of(MotionInfoPayload::encode, MotionInfoPayload::decode);

    /** 一个实体近期受到的外部冲量（速度增量，格/tick）。 */
    public record Entry(int entityId, float x, float y, float z) {
    }

    private static void encode(RegistryFriendlyByteBuf buf, MotionInfoPayload payload) {
        buf.writeUtf(payload.dimension == null ? "" : payload.dimension, MAX_DIMENSION_LENGTH);
        List<Entry> entries = payload.entries;
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeVarInt(entry.entityId());
            buf.writeFloat(entry.x());
            buf.writeFloat(entry.y());
            buf.writeFloat(entry.z());
        }
    }

    private static MotionInfoPayload decode(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalStateException("motion payload: bad entry count " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat()));
        }
        return new MotionInfoPayload(dimension, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

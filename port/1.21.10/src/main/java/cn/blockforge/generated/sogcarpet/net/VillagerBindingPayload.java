package cn.blockforge.generated.sogcarpet.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：村民与床 / 工作方块的绑定关系快照。
 *
 * <p>村民的 {@code HOME}（床）与 {@code JOB_SITE}（工作方块）记忆只存在于服务端的
 * {@code Brain} 里，客户端看不到，所以“村民功能方块绑定可视化”必须由服务端把绑定关系
 * 打包下发。服务端每隔几 tick 扫一次玩家附近的村民，把每个村民的实体号、绑定的床方块
 * 和工作方块发过来，客户端再用实体号找回本地村民、画连线与方框。</p>
 *
 * <p>每条 {@link Entry}：村民实体号 + 标志位 + 可选的床 / 工作方块坐标。床用
 * {@link #FLAG_BED}、工作方块用 {@link #FLAG_JOB} 标记；没有绑定的一方不发坐标。</p>
 */
public record VillagerBindingPayload(String dimension, List<Entry> entries) implements CustomPacketPayload {
    /** 这个村民绑定了床（{@code bedPos} 有效）。 */
    public static final int FLAG_BED = 1;
    /** 这个村民绑定了工作方块（{@code jobPos} 有效）。 */
    public static final int FLAG_JOB = 2;
    /** 一包最多携带多少条绑定。 */
    public static final int MAX_ENTRIES = 96;
    /** 维度标识串的长度上限。 */
    private static final int MAX_DIMENSION_LENGTH = 128;

    public static final Type<VillagerBindingPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:villager_binding"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerBindingPayload> STREAM_CODEC =
            StreamCodec.of(VillagerBindingPayload::encode, VillagerBindingPayload::decode);

    /** 一条绑定：村民实体号 + 标志位 + 可选的床 / 工作方块。 */
    public record Entry(int villagerId, int flags, BlockPos bedPos, BlockPos jobPos) {
        public boolean hasBed() {
            return (flags & FLAG_BED) != 0;
        }

        public boolean hasJob() {
            return (flags & FLAG_JOB) != 0;
        }
    }

    private static void encode(RegistryFriendlyByteBuf buf, VillagerBindingPayload payload) {
        buf.writeUtf(payload.dimension == null ? "" : payload.dimension, MAX_DIMENSION_LENGTH);
        List<Entry> entries = payload.entries;
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeVarInt(entry.villagerId());
            buf.writeByte(entry.flags());
            if (entry.hasBed() && entry.bedPos() != null) {
                buf.writeBlockPos(entry.bedPos());
            }
            if (entry.hasJob() && entry.jobPos() != null) {
                buf.writeBlockPos(entry.jobPos());
            }
        }
    }

    private static VillagerBindingPayload decode(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalStateException("villager binding payload: bad entry count " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int villagerId = buf.readVarInt();
            int flags = buf.readByte() & 0xFF;
            BlockPos bedPos = (flags & FLAG_BED) != 0 ? buf.readBlockPos() : null;
            BlockPos jobPos = (flags & FLAG_JOB) != 0 ? buf.readBlockPos() : null;
            entries.add(new Entry(villagerId, flags, bedPos, jobPos));
        }
        return new VillagerBindingPayload(dimension, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package cn.blockforge.generated.sogcarpet.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：一份“仇恨/目标”快照。
 *
 * <p>生物的 {@code Mob#getTarget()} 只在服务端维护，客户端看不到，所以“仇恨可视化”必须由
 * 服务端把每个生物的锁定关系打包下发。服务端每隔几 tick 扫一次玩家附近的生物，把一个
 * 短列表发过来，客户端再用实体号找回本地实体、在它们头顶画标签和连线。</p>
 *
 * <p>每条 {@link Entry} 只有实体号和一个小标志：</p>
 * <ul>
 *   <li>{@link #FLAG_TARGET}：攻击者 {@code attackerId} 锁定了目标 {@code targetId}，
 *       客户端在攻击者头顶画红色感叹号、在目标头顶画红色瞄准环，并用红线相连；</li>
 *   <li>{@link #FLAG_QUESTION}：坚守者被声音吸引但还没锁定目标，{@code soundPos} 是声音源
 *       所在的方块，客户端在坚守者头顶画黄色问号并用黄线连过去。</li>
 * </ul>
 *
 * <p>包按玩家过滤：只把玩家附近生物的关系发给他；没装模组的客户端由 {@code canSend} 跳过。
 * 一条快照最多 {@value #MAX_ENTRIES} 条，避免大群生物时撑爆包体。</p>
 */
public record AggroInfoPayload(String dimension, List<Entry> entries) implements CustomPacketPayload {
    /** 攻击者锁定了一个目标。 */
    public static final int FLAG_TARGET = 1;
    /** 坚守者被声音吸引（尚未锁定）。 */
    public static final int FLAG_QUESTION = 2;
    /** 一包最多携带多少条关系。 */
    public static final int MAX_ENTRIES = 64;
    /** 维度标识串的长度上限（原版 ResourceLocation 远短于此）。 */
    private static final int MAX_DIMENSION_LENGTH = 128;

    public static final Type<AggroInfoPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:aggro_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AggroInfoPayload> STREAM_CODEC =
            StreamCodec.of(AggroInfoPayload::encode, AggroInfoPayload::decode);

    /** 一条关系：攻击者、目标（没有则 -1）、标志、可选的声音源方块。 */
    public record Entry(int attackerId, int targetId, int flags, BlockPos soundPos) {
        public boolean hasTarget() {
            return (flags & FLAG_TARGET) != 0;
        }

        public boolean hasQuestion() {
            return (flags & FLAG_QUESTION) != 0;
        }
    }

    private static void encode(RegistryFriendlyByteBuf buf, AggroInfoPayload payload) {
        buf.writeUtf(payload.dimension == null ? "" : payload.dimension, MAX_DIMENSION_LENGTH);
        List<Entry> entries = payload.entries;
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeVarInt(entry.attackerId());
            buf.writeVarInt(entry.targetId());
            buf.writeByte(entry.flags());
            if (entry.hasQuestion() && entry.soundPos() != null) {
                buf.writeBlockPos(entry.soundPos());
            }
        }
    }

    private static AggroInfoPayload decode(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalStateException("aggro payload: bad entry count " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int attackerId = buf.readVarInt();
            int targetId = buf.readVarInt();
            int flags = buf.readByte() & 0xFF;
            BlockPos soundPos = (flags & FLAG_QUESTION) != 0 ? buf.readBlockPos() : null;
            entries.add(new Entry(attackerId, targetId, flags, soundPos));
        }
        return new AggroInfoPayload(dimension, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package cn.blockforge.generated.sogcarpet.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：一批“某实体的生命数值刚刚变化了多少”的事件。
 *
 * <p>{@link Entry#delta()} 是<b>带符号</b>的变化量：负数表示掉血（受伤），正数表示回血
 * （被治疗）。这个量只在服务端算得出来——受伤那份要用结算进血量的真实伤害（护甲、附魔、
 * 盾牌格挡、吸收都算完），回血那份要治疗前后的血量差——所以由服务端采集后下发。服务端每
 * 一个服务端 tick 把这一 tick 里发生的变化打包，只发给“装了本模组的、明确要收的、而且就在
 * 附近”的玩家；客户端收到后在实体头顶浮出对应的数字（掉血红字带负号、回血绿字带正号）。</p>
 *
 * <p>每条 {@link Entry}：实体号 + 带符号的变化量 + 事件发生时实体的坐标（客户端找不到该实体
 * 时的兜底锚点，例如那一击直接把它打死了的场合）。</p>
 */
public record HealthChangePayload(String dimension, List<Entry> entries)
        implements CustomPacketPayload {
    /** 一包最多携带多少条事件。 */
    public static final int MAX_ENTRIES = 64;
    /** 维度标识串的长度上限。 */
    private static final int MAX_DIMENSION_LENGTH = 128;

    public static final Type<HealthChangePayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:health_change"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HealthChangePayload> STREAM_CODEC =
            StreamCodec.of(HealthChangePayload::encode, HealthChangePayload::decode);

    /** 一条变化事件：实体号 + 带符号的变化量（负=掉血，正=回血）+ 事件发生处的锚点坐标。 */
    public record Entry(int entityId, float delta, double x, double y, double z) {
    }

    private static void encode(RegistryFriendlyByteBuf buf, HealthChangePayload payload) {
        buf.writeUtf(payload.dimension == null ? "" : payload.dimension, MAX_DIMENSION_LENGTH);
        List<Entry> entries = payload.entries;
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeVarInt(entry.entityId());
            buf.writeFloat(entry.delta());
            buf.writeDouble(entry.x());
            buf.writeDouble(entry.y());
            buf.writeDouble(entry.z());
        }
    }

    private static HealthChangePayload decode(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalStateException("health change payload: bad entry count " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readFloat(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble()));
        }
        return new HealthChangePayload(dimension, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

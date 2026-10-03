package cn.blockforge.generated.sogcarpet.net;

import cn.blockforge.generated.sogcarpet.SogSpawnTrace;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 服务端 → 客户端：一个服务端 tick 内新刷出的生物“出生事件”。
 *
 * <p><b>本轮起包里不再有整条游走路线。</b> 以前发的是刷怪器挑位置的候选点列表，
 * 客户端照着画出来的折线跟生物真实走的路毫无关系。现在每个事件只有三样东西：
 * 维度（整个包一个）、生物的网络实体号、出生方块坐标。客户端拿到后在本地把这只生物
 * 的每一步移动采成轨迹，所以“怪物沿着轨迹走”天然成立，服务端也不再为轨迹产生大宗流量。</p>
 *
 * <p>包仍然按玩家过滤：只把落在玩家 {@link #MAX_DISTANCE} 格以内的出生事件发给该玩家，
 * 每包最多 {@value #MAX_EVENTS} 条。刷怪塔里一 tick 刷出上千只时多出来的丢掉——
 * 客户端对没收到出生点的生物会退而用它“第一次看到的位置”当起点，轨迹照样有。</p>
 */
public record SpawnTracePayload(String dimension, List<SogSpawnTrace.SpawnEvent> events)
        implements CustomPacketPayload {

    /** 单个包最多携带多少条出生事件。 */
    public static final int MAX_EVENTS = 256;
    /**
     * 出生事件的下发半径（格）：服务端只把落在这个范围内的出生事件发给该玩家。
     * 刷怪本来只发生在玩家附近（原版刷怪距离 24~128 格），这个半径既保证看得到身边
     * 所有刷怪，又不会把远处的出生点也搬过来，专用服务器上能明显减小上行。
     */
    public static final double MAX_DISTANCE = 192.0D;
    /** {@link #MAX_DISTANCE} 的平方，判定距离时直接比平方省一次开方。 */
    public static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;
    /** 维度标识串的长度上限（原版 Identifier 远短于此）。 */
    private static final int MAX_DIMENSION_LENGTH = 128;

    public static final Type<SpawnTracePayload> TYPE =
            new Type<>(Identifier.parse("sog_carpet:spawn_trace"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnTracePayload> STREAM_CODEC =
            StreamCodec.of(SpawnTracePayload::encode, SpawnTracePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, SpawnTracePayload payload) {
        buf.writeUtf(payload.dimension == null ? "" : payload.dimension, MAX_DIMENSION_LENGTH);
        List<SogSpawnTrace.SpawnEvent> events = payload.events;
        int count = Math.min(events.size(), MAX_EVENTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            SogSpawnTrace.SpawnEvent event = events.get(i);
            buf.writeVarInt(event.entityId());
            buf.writeBlockPos(event.spawnPos());
        }
    }

    private static SpawnTracePayload decode(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_EVENTS) {
            throw new IllegalStateException("spawn trace payload: bad event count " + count);
        }
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION,
                dimension.isEmpty() ? Identifier.parse("minecraft:overworld")
                        : Identifier.parse(dimension));
        List<SogSpawnTrace.SpawnEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int entityId = buf.readVarInt();
            events.add(new SogSpawnTrace.SpawnEvent(dimensionKey, entityId, buf.readBlockPos()));
        }
        return new SpawnTracePayload(dimension, events);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package cn.blockforge.generated.sogcarpet.net;

import cn.blockforge.generated.sogcarpet.SogSpawnStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：自然刷怪的“成功概率”统计快照。
 *
 * <p>服务端把每个 {@code MobCategory} 的尝试次数与成功次数打包发过来，客户端据此算出
 * 成功率（成功 ÷ 尝试）并用 HUD 面板汇总显示，同时在新刷出的生物出生点旁标出当前成功率。
 * 分类只有寥寥几个（MONSTER、CREATURE、WATER_CREATURE 等），所以包很小；每隔约一秒发一次。</p>
 */
public record SpawnStatsPayload(List<SogSpawnStats.CategoryStat> categories)
        implements CustomPacketPayload {

    /** 一包最多携带多少个分类（原版 MobCategory 只有 8 个）。 */
    public static final int MAX_CATEGORIES = 16;

    public static final Type<SpawnStatsPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:spawn_stats"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnStatsPayload> STREAM_CODEC =
            StreamCodec.of(SpawnStatsPayload::encode, SpawnStatsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, SpawnStatsPayload payload) {
        List<SogSpawnStats.CategoryStat> stats = payload.categories;
        int count = Math.min(stats.size(), MAX_CATEGORIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            SogSpawnStats.CategoryStat stat = stats.get(i);
            buf.writeUtf(stat.category() == null ? "" : stat.category(), 64);
            buf.writeVarInt((int) Math.min(stat.attempts(), Integer.MAX_VALUE));
            buf.writeVarInt((int) Math.min(stat.successes(), Integer.MAX_VALUE));
        }
    }

    private static SpawnStatsPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CATEGORIES) {
            throw new IllegalStateException("spawn stats payload: bad category count " + count);
        }
        List<SogSpawnStats.CategoryStat> stats = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String category = buf.readUtf(64);
            int attempts = buf.readVarInt();
            int successes = buf.readVarInt();
            stats.add(new SogSpawnStats.CategoryStat(category, attempts, successes));
        }
        return new SpawnStatsPayload(stats);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

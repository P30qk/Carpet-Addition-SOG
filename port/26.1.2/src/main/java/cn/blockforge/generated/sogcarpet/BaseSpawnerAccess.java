package cn.blockforge.generated.sogcarpet;

/**
 * 由 {@link cn.blockforge.generated.sogcarpet.mixin.BaseSpawnerAccessMixin} 实现在
 * {@code BaseSpawner} 上，用来读取它受保护的刷怪范围字段。
 *
 * <p>{@code BaseSpawner.spawnRange} 是私有字段（NBT 的 {@code SpawnRange}，默认 4），
 * 供“可刷怪方块生成范围可视化”在客户端读出刷怪笼的真实范围——客户端方块实体本来就带着
 * 这个同步下来的值（刷怪笼的更新标签包含 SpawnRange，只剔除了 SpawnPotentials）。</p>
 */
public interface BaseSpawnerAccess {
    /** 读 {@code BaseSpawner} 的私有 {@code spawnRange} 字段。 */
    int sog$getSpawnRange();
}

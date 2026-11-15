package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.BaseSpawnerAccess;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 把 {@code BaseSpawner} 的私有 {@code spawnRange} 字段通过 {@link BaseSpawnerAccess}
 * 暴露出来，供“可刷怪方块生成范围可视化”读取刷怪笼真实的刷怪范围（NBT 的 SpawnRange）。
 *
 * <p>字段在 {@code BaseSpawner} 自身声明，所以这里的 {@code @Shadow} 能正常解析；
 * 调用方直接强转这个接口即可，不需要把 mixin 包里的类引进客户端代码。</p>
 */
@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerAccessMixin implements BaseSpawnerAccess {
    @Shadow
    private int spawnRange;

    @Override
    public int sog$getSpawnRange() {
        return this.spawnRange;
    }
}

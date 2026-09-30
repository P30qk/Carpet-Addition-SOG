package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.SogSpawnTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “刷怪游走可视化”的服务端采集。
 *
 * <p>目标方法是原版真正干活的六参重载
 * {@code NaturalSpawner#spawnCategoryForPosition(MobCategory, ServerLevel, ChunkAccess, BlockPos, SpawnPredicate, AfterSpawnCallback)}：</p>
 * <ul>
 *   <li>{@code HEAD} 记下起始点；{@code RETURN} 清理线程局部状态（提前 return 也能清）；</li>
 *   <li>每次候选点都被写进同一个 {@code BlockPos.MutableBlockPos pos}，所以直接
 *       {@code @Redirect} 这次 {@code set(III)}：先按原样执行，再把落点交给采集器。
 *       这样不用依赖脆弱的局部变量序数（{@code @Local}）或 {@code LocalCapture}；</li>
 *   <li>{@code spawnCallback.run(mob, chunk)} 紧跟在生物进世界之后、且原版/Carpet
 *       都没有在这个调用点上做重定向，所以 {@code @Redirect} 它，先原样回调，
 *       再按生物实际方块坐标归档一条轨迹。这里刻意不去重定向 Carpet 已经占用的
 *       {@code addFreshEntityWithPassengers}，避免同优先级双重重定向冲突。</li>
 * </ul>
 *
 * <p>只在规则开启时记录；规则关掉后注入点仍在，但采集器里 {@code CURRENT} 为空，
 * 全部是空操作。</p>
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    /** 六参重载的完整描述符（Mixin 用它区分另一个三参重载）。 */
    private static final String SPAWN_CATEGORY_FOR_POSITION =
            "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;"
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
                    + "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V";

    @Inject(method = SPAWN_CATEGORY_FOR_POSITION, at = @At("HEAD"))
    private static void sog$beginTrace(MobCategory category, ServerLevel level, ChunkAccess chunk,
            BlockPos start, NaturalSpawner.SpawnPredicate predicate,
            NaturalSpawner.AfterSpawnCallback callback, CallbackInfo ci) {
        if (SogSettings.mobSpawnVisualizer) {
            SogSpawnTrace.begin(level.dimension(), start);
        }
    }

    @Redirect(method = SPAWN_CATEGORY_FOR_POSITION, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;"))
    private static BlockPos.MutableBlockPos sog$captureStep(BlockPos.MutableBlockPos pos, int x, int y, int z) {
        BlockPos.MutableBlockPos moved = pos.set(x, y, z);
        if (SogSettings.mobSpawnVisualizer) {
            SogSpawnTrace.step(moved);
        }
        return moved;
    }

    @Redirect(method = SPAWN_CATEGORY_FOR_POSITION, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;run(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/chunk/ChunkAccess;)V"))
    private static void sog$captureSpawn(NaturalSpawner.AfterSpawnCallback callback, Mob mob,
            ChunkAccess chunk) {
        callback.run(mob, chunk);
        if (SogSettings.mobSpawnVisualizer) {
            SogSpawnTrace.spawned(mob.blockPosition());
        }
    }

    @Inject(method = SPAWN_CATEGORY_FOR_POSITION, at = @At("RETURN"))
    private static void sog$endTrace(MobCategory category, ServerLevel level, ChunkAccess chunk,
            BlockPos start, NaturalSpawner.SpawnPredicate predicate,
            NaturalSpawner.AfterSpawnCallback callback, CallbackInfo ci) {
        SogSpawnTrace.end();
    }
}

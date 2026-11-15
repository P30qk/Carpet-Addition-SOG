package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSpawnStats;
import cn.blockforge.generated.sogcarpet.SogSpawnTrace;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 刷怪（怪物生成）的服务端采集，供“随机游走可视化”与“怪物生成可视化”使用。
 *
 * <p>采集三类东西：</p>
 * <ul>
 *   <li><b>出生事件</b>：生物真正进入世界那一刻记一条（实体号 + 出生坐标 + 本次游走路径），
 *       供客户端画生物真实轨迹、刷怪游走路径与 Y 高度柱；</li>
 *   <li><b>成功率统计</b>：按 MobCategory 记尝试/成功；</li>
 *   <li><b>每个检查环节的成功率</b>：按原版刷怪流程的环节记“到达/通过”次数——
 *       排除红石导体、距离过近、生物存在、位置、生成势、距离过远/障碍。</li>
 * </ul>
 *
 * <p>只在有人看时记录（{@link SogSpawnTrace#isRecording()} / {@link SogSpawnStats#isRecording()}）。</p>
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

    /** 检查环节下标：0 红石导体、1 距离、2 生物存在、3 位置、4 生成势、5 距离过远/障碍。 */
    private static final int CHECK_REDSTONE = 0;
    private static final int CHECK_DISTANCE = 1;
    private static final int CHECK_MOB_EXISTS = 2;
    private static final int CHECK_POSITION = 3;
    private static final int CHECK_SPAWN_POTENTIAL = 4;
    private static final int CHECK_OBSTRUCTION = 5;

    @WrapOperation(method = SPAWN_CATEGORY_FOR_POSITION, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;run(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/chunk/ChunkAccess;)V"))
    private static void sog$captureSpawn(NaturalSpawner.AfterSpawnCallback callback, Mob mob,
            ChunkAccess chunk, Operation<Void> original) {
        original.call(callback, mob, chunk);
        if (SogSpawnTrace.isRecording()) {
            SogSpawnTrace.spawned(mob.level().dimension(), mob.getId(), mob.blockPosition());
        }
        if (SogSpawnStats.isRecording()) {
            SogSpawnStats.success(mob.getType().getCategory());
        }
    }

    /** 检查〇 排除红石导体：起始点循环入口就是这一步。 */
    @Inject(method = SPAWN_CATEGORY_FOR_POSITION, at = @At("HEAD"))
    private static void sog$beginSpawnTrace(MobCategory category, ServerLevel level, ChunkAccess chunk,
            BlockPos start, NaturalSpawner.SpawnPredicate predicate,
            NaturalSpawner.AfterSpawnCallback callback, CallbackInfo ci) {
        if (SogSpawnTrace.isRecording()) {
            SogSpawnTrace.beginTrace(start);
        }
        if (SogSpawnStats.isRecording()) {
            SogSpawnStats.checkReached(CHECK_REDSTONE);
            BlockState state = chunk.getBlockState(start);
            if (!state.isRedstoneConductor(chunk, start)) {
                SogSpawnStats.checkPassed(CHECK_REDSTONE);
            }
        }
    }

    /** 一次刷怪位置循环结束：清掉线程本地的候选路径。 */
    @Inject(method = SPAWN_CATEGORY_FOR_POSITION, at = @At("RETURN"))
    private static void sog$endSpawnTrace(MobCategory category, ServerLevel level, ChunkAccess chunk,
            BlockPos start, NaturalSpawner.SpawnPredicate predicate,
            NaturalSpawner.AfterSpawnCallback callback, CallbackInfo ci) {
        SogSpawnTrace.endTrace();
    }

    /**
     * 记录随机游走的<b>每一步</b>：原版每随机偏移一次都会 {@code pos.set(x, yStart, z)}，这里包装
     * 这个调用，把每次偏移点都记进游走路径——于是路径是完整的随机游走（不像只在距离检查处记，
     * 会因附近没玩家而漏步、看起来只有一两个点）。
     */
    @WrapOperation(method = SPAWN_CATEGORY_FOR_POSITION, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;"))
    private static BlockPos.MutableBlockPos sog$recordWalkStep(BlockPos.MutableBlockPos pos, int x, int y,
            int z, Operation<BlockPos.MutableBlockPos> original) {
        BlockPos.MutableBlockPos result = original.call(pos, x, y, z);
        SogSpawnTrace.addRoamStep(result);
        return result;
    }

    /** 检查一 距离过近：游走的每一步都会调用它。 */
    @Inject(method = "isRightDistanceToPlayerAndSpawnPoint", at = @At("HEAD"))
    private static void sog$distanceReached(ServerLevel level, ChunkAccess chunk,
            BlockPos.MutableBlockPos pos, double nearestPlayerDistanceSqr,
            CallbackInfoReturnable<Boolean> cir) {
        if (SogSpawnStats.isRecording()) {
            SogSpawnStats.checkReached(CHECK_DISTANCE);
        }
    }

    /** 检查一结果。 */
    @Inject(method = "isRightDistanceToPlayerAndSpawnPoint", at = @At("RETURN"))
    private static void sog$distanceResult(ServerLevel level, ChunkAccess chunk,
            BlockPos.MutableBlockPos pos, double nearestPlayerDistanceSqr,
            CallbackInfoReturnable<Boolean> cir) {
        if (SogSpawnStats.isRecording() && Boolean.TRUE.equals(cir.getReturnValue())) {
            SogSpawnStats.checkPassed(CHECK_DISTANCE);
        }
    }

    /** 检查二 生物存在：按群系权重挑一种生物，挑不到就整组结束。 */
    @Inject(method = "getRandomSpawnMobAt", at = @At("HEAD"))
    private static void sog$mobExistsReached(ServerLevel level, StructureManager structureManager,
            ChunkGenerator generator, MobCategory category, net.minecraft.util.RandomSource random,
            BlockPos pos, CallbackInfoReturnable<Optional<MobSpawnSettings.SpawnerData>> cir) {
        if (SogSpawnStats.isRecording()) {
            SogSpawnStats.checkReached(CHECK_MOB_EXISTS);
        }
    }

    /** 检查二结果。 */
    @Inject(method = "getRandomSpawnMobAt", at = @At("RETURN"))
    private static void sog$mobExistsResult(ServerLevel level, StructureManager structureManager,
            ChunkGenerator generator, MobCategory category, net.minecraft.util.RandomSource random,
            BlockPos pos, CallbackInfoReturnable<Optional<MobSpawnSettings.SpawnerData>> cir) {
        if (SogSpawnStats.isRecording() && cir.getReturnValue() != null
                && cir.getReturnValue().isPresent()) {
            SogSpawnStats.checkPassed(CHECK_MOB_EXISTS);
        }
    }

    /** 检查三 位置：能否在此刷这种生物，同时记一次“尝试”，作为整体成功率的分子/分母。 */
    @Inject(method = "isValidSpawnPostitionForType", at = @At("HEAD"))
    private static void sog$positionReached(ServerLevel level, MobCategory category,
            StructureManager structureManager, ChunkGenerator generator,
            MobSpawnSettings.SpawnerData spawnerData, BlockPos.MutableBlockPos pos,
            double nearestPlayerDistanceSqr, CallbackInfoReturnable<Boolean> cir) {
        if (SogSpawnStats.isRecording()) {
            SogSpawnStats.attempt(category);
            SogSpawnStats.checkReached(CHECK_POSITION);
        }
    }

    /** 检查三结果。 */
    @Inject(method = "isValidSpawnPostitionForType", at = @At("RETURN"))
    private static void sog$positionResult(ServerLevel level, MobCategory category,
            StructureManager structureManager, ChunkGenerator generator,
            MobSpawnSettings.SpawnerData spawnerData, BlockPos.MutableBlockPos pos,
            double nearestPlayerDistanceSqr, CallbackInfoReturnable<Boolean> cir) {
        if (SogSpawnStats.isRecording() && Boolean.TRUE.equals(cir.getReturnValue())) {
            SogSpawnStats.checkPassed(CHECK_POSITION);
        }
    }

    /** 检查四 生成势：通过位置校验后，包装 extraTest.test（自然刷怪里就是生成势能检查）。 */
    @WrapOperation(method = SPAWN_CATEGORY_FOR_POSITION, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;test(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/ChunkAccess;)Z"))
    private static boolean sog$spawnPotential(NaturalSpawner.SpawnPredicate predicate, EntityType<?> type,
            ServerLevel level, BlockPos pos, ChunkAccess chunk, Operation<Boolean> original) {
        boolean recording = SogSpawnStats.isRecording();
        if (recording) {
            SogSpawnStats.checkReached(CHECK_SPAWN_POTENTIAL);
        }
        boolean result = original.call(predicate, type, level, pos, chunk);
        if (recording && result) {
            SogSpawnStats.checkPassed(CHECK_SPAWN_POTENTIAL);
        }
        return result;
    }

    /** 检查五 距离过远&障碍。 */
    @Inject(method = "isValidPositionForMob", at = @At("HEAD"))
    private static void sog$obstructionReached(ServerLevel level, Mob mob,
            double nearestPlayerDistanceSqr, CallbackInfoReturnable<Boolean> cir) {
        if (SogSpawnStats.isRecording()) {
            SogSpawnStats.checkReached(CHECK_OBSTRUCTION);
        }
    }

    /** 检查五结果。 */
    @Inject(method = "isValidPositionForMob", at = @At("RETURN"))
    private static void sog$obstructionResult(ServerLevel level, Mob mob,
            double nearestPlayerDistanceSqr, CallbackInfoReturnable<Boolean> cir) {
        if (SogSpawnStats.isRecording() && Boolean.TRUE.equals(cir.getReturnValue())) {
            SogSpawnStats.checkPassed(CHECK_OBSTRUCTION);
        }
    }
}

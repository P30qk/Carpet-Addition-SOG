package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铜生锈控制的唯一收口点。
 *
 * <p>所有铜系方块（铜块/切制铜/台阶/楼梯/门/活板门/格栅/栏杆/锁链/灯笼/避雷针/铜箱等）
 * 的 randomTick 都会调用 {@code ChangeOverTimeBlock#changeOverTime}，
 * 在这里拦掉即可一次覆盖全部变体，而不必逐个方块写注入。</p>
 */
@Mixin(ChangeOverTimeBlock.class)
public interface ChangeOverTimeBlockMixin {
    @Inject(method = "changeOverTime", at = @At("HEAD"), cancellable = true)
    private void sogcarpet$disableNaturalOxidation(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        if (SogSettings.copperOxidationDisabled) {
            ci.cancel();
        }
    }
}

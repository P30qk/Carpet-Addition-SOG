package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁冰融雪：霜冰（冰霜行者踩出的冰）不再按计划刻消退。
 */
@Mixin(FrostedIceBlock.class)
public abstract class FrostedIceBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sogcarpet$disableFrostedIceMelt(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        if (SogSettings.iceSnowMeltDisabled) {
            ci.cancel();
        }
    }
}

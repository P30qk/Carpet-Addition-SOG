package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁冰融雪：雪层不再因光照/生物群系随机融化，已有雪保留。
 */
@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void sogcarpet$disableSnowMelt(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        if (SogSettings.iceSnowMeltDisabled) {
            ci.cancel();
        }
    }
}

package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁冰融雪 + 可调强制解冻（随机刻路径）+ 冰即刻融化。
 *
 * <p>即刻融化：IceBlock 本身没有声明 neighborChanged/onPlace，这里以“向目标类
 * 合并新方法”的方式补上两个覆写点——方块亮度不低于 forceThawLightLevel 的冰，
 * 在放置或被邻居方块变化波及的瞬间立即融化，不再等随机刻。
 * 禁冰融雪规则优先：它开启时即刻融化不生效。</p>
 */
@Mixin(IceBlock.class)
public abstract class IceBlockMixin {
    @Shadow
    protected abstract void melt(BlockState state, Level level, BlockPos pos);

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void sogcarpet$controlIceMelt(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        if (SogSettings.iceSnowMeltDisabled) {
            ci.cancel();
            return;
        }
        int threshold = SogSettings.forceThawLightLevel;
        if (threshold < 0) {
            threshold = 0;
        } else if (threshold > 15) {
            threshold = 15;
        }
        if (level.getBrightness(LightLayer.BLOCK, pos) >= threshold) {
            this.melt(state, level, pos);
            ci.cancel();
        }
    }

    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            Orientation orientation, boolean movedByPiston) {
        if (sogcarpet$shouldInstantMelt(level, pos)) {
            this.melt(state, level, pos);
        }
    }

    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
            boolean movedByPiston) {
        if (sogcarpet$shouldInstantMelt(level, pos)) {
            this.melt(state, level, pos);
        }
    }

    private boolean sogcarpet$shouldInstantMelt(Level level, BlockPos pos) {
        if (level.isClientSide() || !SogSettings.iceInstantMelt || SogSettings.iceSnowMeltDisabled) {
            return false;
        }
        return level.getBrightness(LightLayer.BLOCK, pos) >= SogSettings.forceThawLightLevel;
    }
}

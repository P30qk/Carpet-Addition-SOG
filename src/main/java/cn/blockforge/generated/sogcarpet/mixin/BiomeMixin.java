package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 禁新雪层生成：直接取消 {@code Biome#shouldSnow(LevelReader, BlockPos)} 的返回。
 *
 * <p>26.2 里该方法的调用方只有两处——{@code ServerLevel#tickPrecipitation} 的降雪累积和
 * {@code SnowAndFreezeFeature} 的雪层铺放，两处都是“形成新雪层”，与规划一致；
 * 结冰走 {@code Biome#shouldFreeze}，完全不受影响。</p>
 *
 * <p>用方法级 {@code @Inject} 而不是 {@code @Redirect}，是为了绕开 mixinextras 0.5.4
 * 解析 26.2 工具链编出的数组形态 {@code @Redirect.at} 时崩溃的兼容问题（详见
 * {@link ServerLevelMixin} 注释）。</p>
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @Inject(method = "shouldSnow", at = @At("HEAD"), cancellable = true)
    private void sogcarpet$disableSnowFormation(LevelReader level, BlockPos pos,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (SogSettings.snowGenerationDisabled) {
            cir.setReturnValue(false);
        }
    }
}

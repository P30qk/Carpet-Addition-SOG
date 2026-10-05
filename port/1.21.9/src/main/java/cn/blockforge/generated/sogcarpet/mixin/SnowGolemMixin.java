package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.world.entity.animal.SnowGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 禁雪傀儡产雪：雪傀儡 {@code aiStep} 里铺雪段的四宫格循环上界是 26.2 方法内唯一的
 * {@code iconst_4}（{@code for i in 0..3} 逐个试探落点），规则开启时把它压成 0，
 * 整个铺雪循环（含 {@code setBlockAndUpdate} 与 {@code BLOCK_PLACE} 游戏事件）一步不执行；
 * 循环之前的融化伤害与 {@code mobGriefing} 检查都在循环外，保持原版。
 *
 * <p>不用 {@code @Redirect} 拦 {@code Level#setBlockAndUpdate}，是为绕开
 * mixinextras 0.5.4 对 26.2 工具链数组形态 {@code @Redirect.at} 的兼容崩溃
 * （详见 {@link ServerLevelMixin} 注释）。</p>
 */
@Mixin(SnowGolem.class)
public abstract class SnowGolemMixin {
    @ModifyConstant(method = "aiStep", constant = @Constant(intValue = 4))
    private int sogcarpet$disableGolemSnow(int trailProbeCount) {
        if (SogSettings.snowGolemSnowDisabled) {
            return 0;
        }
        return trailProbeCount;
    }
}

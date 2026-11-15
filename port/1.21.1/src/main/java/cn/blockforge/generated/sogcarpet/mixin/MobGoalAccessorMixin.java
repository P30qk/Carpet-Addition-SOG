package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.MobGoalAccess;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 把 {@code Mob} 的两个目标选择器通过 {@link MobGoalAccess} 暴露出来。
 *
 * <p>字段在 {@code Mob} 自身声明，所以这里的 {@code @Shadow} 装在 {@code Mob} 上能正常解析；
 * 巨人 AI（{@code GiantAiMixin}）用不到自己的 {@code @Shadow}，直接强转这个接口即可。</p>
 */
@Mixin(Mob.class)
public abstract class MobGoalAccessorMixin implements MobGoalAccess {
    @Shadow
    @Final
    protected GoalSelector goalSelector;

    @Shadow
    @Final
    protected GoalSelector targetSelector;

    @Override
    public GoalSelector sog$goalSelector() {
        return this.goalSelector;
    }

    @Override
    public GoalSelector sog$targetSelector() {
        return this.targetSelector;
    }
}

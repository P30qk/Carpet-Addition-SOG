package cn.blockforge.generated.sogcarpet;

import net.minecraft.world.entity.ai.goal.GoalSelector;

/**
 * 由 {@link cn.blockforge.generated.sogcarpet.mixin.MobGoalAccessorMixin} 实现在
 * {@code Mob} 上，用来读取它受保护的目标选择器。
 *
 * <p>{@code Mob.goalSelector} / {@code targetSelector} 在 {@code Mob} 里声明、为
 * {@code protected final}，子类（如 {@code Giant}）里 {@code @Shadow} 取不到（Mixin 只会
 * 在目标类自身找字段，不会沿继承树解析）。所以在 {@code Mob} 自己身上开一个访问器接口，
 * 巨人 AI 通过 {@code (MobGoalAccess) mob} 取用。</p>
 */
public interface MobGoalAccess {
    /** 日常目标选择器。 */
    GoalSelector sog$goalSelector();

    /** 目标（攻击对象）选择器。 */
    GoalSelector sog$targetSelector();
}

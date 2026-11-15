package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.GiantAiAccess;
import cn.blockforge.generated.sogcarpet.MobGoalAccess;
import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 巨人旧版 AI（14w50a）。
 *
 * <p>26.2 的 {@code Giant} 只重写了属性和 {@code getWalkTargetValue}，没有任何 AI 目标，
 * 所以巨人一动不动。这里通过 {@link GiantAiAccess} 暴露一个幂等方法，由
 * {@code ServerEntityEvents.ENTITY_LOAD}（新召唤 / 区块加载）和规则观察器（规则开启时的
 * 存量巨人）调用，把僵尸那一套“会移动、会攻击”的目标装上：</p>
 * <ul>
 *   <li>{@code FloatGoal}：不会淹死；</li>
 *   <li>{@code MeleeAttackGoal}：近战攻击（用巨人自身的高攻属性）；</li>
 *   <li>{@code WaterAvoidingRandomStrollGoal}：随机游走；</li>
 *   <li>{@code LookAtPlayerGoal} / {@code RandomLookAroundGoal}：看向玩家 / 四处张望；</li>
 *   <li>目标选择器：被打了还手（{@code HurtByTargetGoal}），并主动锁定玩家、村民、铁傀儡。</li>
 * </ul>
 *
 * <p>两个选择器从 {@link MobGoalAccess}（在 {@code Mob} 上实现的访问器）取，避免在巨人自己身上
 * {@code @Shadow} 继承字段（Mixin 不会沿继承树解析 {@code @Shadow} 字段）。</p>
 */
@Mixin(Giant.class)
public abstract class GiantAiMixin implements GiantAiAccess {
    /** 是否已装过 AI，保证幂等。 */
    @Unique
    private boolean sog$aiApplied;

    @Override
    public void sog$ensureOldAi() {
        if (sog$aiApplied || !SogSettings.giantOldAi) {
            return;
        }
        sog$aiApplied = true;
        PathfinderMob mob = (PathfinderMob) (Object) this;
        MobGoalAccess access = (MobGoalAccess) (Object) this;
        access.sog$goalSelector().addGoal(1, new FloatGoal(mob));
        access.sog$goalSelector().addGoal(3, new MeleeAttackGoal(mob, 1.0D, false));
        access.sog$goalSelector().addGoal(7, new WaterAvoidingRandomStrollGoal(mob, 1.0D));
        access.sog$goalSelector().addGoal(8, new LookAtPlayerGoal(mob, Player.class, 16.0F));
        access.sog$goalSelector().addGoal(8, new RandomLookAroundGoal(mob));
        access.sog$targetSelector().addGoal(1, new HurtByTargetGoal(mob));
        access.sog$targetSelector().addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
        access.sog$targetSelector().addGoal(2, new NearestAttackableTargetGoal<>(mob, Villager.class, true));
        access.sog$targetSelector().addGoal(2, new NearestAttackableTargetGoal<>(mob, IronGolem.class, true));
    }
}

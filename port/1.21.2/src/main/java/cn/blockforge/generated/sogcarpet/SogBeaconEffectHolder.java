package cn.blockforge.generated.sogcarpet;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * 由 mixin {@code BeaconBlockEntityMixin} 注入到
 * {@code net.minecraft.world.level.block.entity.BeaconBlockEntity} 上，
 * 供静态的 {@code tick} / {@code applyEffects} 注入点读写信标保存的主/副效果，
 * 以及“这次效果是不是用下界之星开的”。
 *
 * <p>{@code tick} 与 {@code applyEffects} 都是静态方法，注入处理器拿不到实例字段的 shadow，
 * 只能把传进来的信标实例转成这个接口再操作。接口必须放在 mixin 包之外：mixin 包里的类被直接
 * 引用会抛 {@code IllegalClassLoadError}。</p>
 */
public interface SogBeaconEffectHolder {
    Holder<MobEffect> sog$getPrimaryEffect();

    Holder<MobEffect> sog$getSecondaryEffect();

    void sog$setPrimaryEffect(Holder<MobEffect> effect);

    void sog$setSecondaryEffect(Holder<MobEffect> effect);

    /** 标脏，让清空后的信标数据落盘并同步。 */
    void sog$markChanged();

    /** 这个信标的效果是不是用下界之星开的（是的话主/副效果等级各 +1）。 */
    boolean sog$isNetherStarPaid();

    void sog$setNetherStarPaid(boolean value);
}

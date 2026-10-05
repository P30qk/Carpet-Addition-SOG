package cn.blockforge.generated.sogcarpet;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * 由 mixin {@code BeaconBlockEntityMixin} 注入到
 * {@code net.minecraft.world.level.block.entity.BeaconBlockEntity} 上，
 * 供静态的 {@code tick} 注入点读写信标已存的附加效果。
 *
 * <p>{@code tick} 是静态方法，注入处理器拿不到实例字段的 shadow，只能把传进来的
 * 信标实例转成这个接口再操作。接口必须放在 mixin 包之外：mixin 包里的类被直接
 * 引用会抛 {@code IllegalClassLoadError}。</p>
 */
public interface SogBeaconEffectHolder {
    Holder<MobEffect> sog$getPrimaryEffect();

    Holder<MobEffect> sog$getSecondaryEffect();

    void sog$setPrimaryEffect(Holder<MobEffect> effect);

    void sog$setSecondaryEffect(Holder<MobEffect> effect);

    /** 标脏，让清空后的信标数据落盘并同步。 */
    void sog$markChanged();
}

package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogHealthChangeSync;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “生命数值变化可视化”里<b>回血</b>那一半的采集点。
 *
 * <p>回血没有像受伤那样的 Fabric 事件，而 {@code LivingEntity#heal(float)} 是所有治疗路径的唯一
 * 出口：瞬间治疗药水、再生效果、进食与自然回血、金苹果，以及沿用原版治疗的模组都走它
 * （{@code Player}/{@code ServerPlayer} 都没有覆写它，所以玩家回血同样能收到）。因此在这里
 * 挂钩一次即可全覆盖。</p>
 *
 * <p>{@code heal} 的返回值是 void，治疗量必须用“治疗前 / 治疗后”的血量差来算，而它的实现
 * 只是「读当前血量 → 加 amount 后 {@code setHealth}」，于是用 MixinExtras 的 {@code @Share}
 * 在两个注入点之间传一个浮点引用：HEAD 存入治疗前的血量，RETURN 取出并交给
 * {@link SogHealthChangeSync#onHeal}。血量已满时的空治疗、以及被血量上限截断后一点没回的情形，
 * 差值都不会超过阈值，因此不会刷出无意义的数字。</p>
 *
 * <p>服务端与客户端的实体都走这个方法，但只有服务端实例才需要采集（客户端那条路走纯客户端
 * 估算），判断在 {@link SogHealthChangeSync#onHeal} 里做；规则关闭时它第一层就返回，与原版
 * 逐字节一致。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {
    @Inject(method = "heal(F)V", at = @At("HEAD"))
    private void sogcarpet$beforeHeal(float amount, CallbackInfo ci,
            @Share("sogcarpet$healthBeforeHeal") LocalFloatRef before) {
        before.set(((LivingEntity) (Object) this).getHealth());
    }

    @Inject(method = "heal(F)V", at = @At("RETURN"))
    private void sogcarpet$afterHeal(float amount, CallbackInfo ci,
            @Share("sogcarpet$healthBeforeHeal") LocalFloatRef before) {
        SogHealthChangeSync.onHeal((LivingEntity) (Object) this, before.get());
    }
}

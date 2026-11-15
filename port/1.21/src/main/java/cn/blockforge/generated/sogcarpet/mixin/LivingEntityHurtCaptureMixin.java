package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogHealthChangeSync;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “生命数值变化可视化”里<b>受伤</b>那一半的采集点（MC 1.21 专用）。
 *
 * <p>1.21.1 起 Fabric API 提供 {@code ServerLivingEntityEvents.AFTER_DAMAGE}，回调里直接带
 * “真实扣掉的血量”；但 1.21 能用的 fabric-api 0.102.0+1.21 里<b>没有</b>这个事件
 * （1.21.1 线才补上），所以这一版改为直接挂在原版的伤害结算出口
 * {@code LivingEntity#actuallyHurt(DamageSource, float)} 上：它在护甲、附魔、盾牌格挡、
 * 伤害吸收全部算完之后才真正改血量，正是我们想要的“真实伤害”。</p>
 *
 * <p>取法与回血那一半对称：HEAD 存下结算前的血量，RETURN 用差值算这次实际掉了多少。
 * 被完全挡下的攻击（掉血 0）差值为 0，不会出数字；血量已见底时的溢出伤害同样按实际扣掉的算。
 * 只在服务端实例上采集（{@link SogHealthChangeSync#onDamage} 里判断），规则关闭时它在第一层
 * 就返回，与原版逐字节一致。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtCaptureMixin {
    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"))
    private void sogcarpet$beforeHurt(DamageSource source, float amount, CallbackInfo ci,
            @Share("sogcarpet$healthBeforeHurt") LocalFloatRef before) {
        before.set(((LivingEntity) (Object) this).getHealth());
    }

    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("RETURN"))
    private void sogcarpet$afterHurt(DamageSource source, float amount, CallbackInfo ci,
            @Share("sogcarpet$healthBeforeHurt") LocalFloatRef before) {
        LivingEntity self = (LivingEntity) (Object) this;
        SogHealthChangeSync.onDamage(self, before.get() - self.getHealth());
    }
}

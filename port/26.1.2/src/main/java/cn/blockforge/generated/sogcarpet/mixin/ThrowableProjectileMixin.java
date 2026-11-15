package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogPearlTeleport;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “简易珍珠远距传送”的悬停实现：在 {@code ThrowableProjectile.tick} 开头拦一下，
 * 命名过的远传珍珠（末影珍珠/雪球）在放飞前每 tick 清零速度并保持无重力。
 *
 * <p>放在公共 mixin 里，因为客户端也要跟着悬停（看 {@link SogPearlTeleport#onProjectileTick}：
 * 客户端只在 {@code noGravity} 已同步为 true 时清零速度）。规则关闭时该方法直接返回，无副作用。</p>
 */
@Mixin(ThrowableProjectile.class)
public abstract class ThrowableProjectileMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void sog$hoverFarPearl(CallbackInfo ci) {
        SogPearlTeleport.onProjectileTick((ThrowableProjectile) (Object) this);
    }
}

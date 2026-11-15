package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogPearlTeleport;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让“远传珍珠/雪球”可以被准星选中。
 *
 * <p>原版里 {@link Projectile#isPickable()} 只对 {@code redirectable_projectile} 标签里的投掷物
 * 返回 true（火球、风弹等），末影珍珠、雪球都不在其中。后果是：客户端准星根本不会瞄准悬停的珍珠，
 * 于是既不会发攻击包、也不会发交互包，服务端的 {@code AttackEntityCallback} /
 * {@code UseEntityCallback} 永远收不到——表现为“无法用矛叠加轴速度、无法空手右键放飞”。
 * 这里对命名过的远传珍珠/雪球返回 true，恢复可选中；其它投掷物保持原样。</p>
 *
 * <p>放在公共 mixin 里：客户端需要它来产生准星命中，服务端也需要它保持一致。</p>
 */
@Mixin(Projectile.class)
public abstract class ProjectilePickableMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void sog$farPearlPickable(CallbackInfoReturnable<Boolean> cir) {
        if (SogPearlTeleport.isFarPearl((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}

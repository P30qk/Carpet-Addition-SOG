package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogPearlTeleport;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.10 的“简易珍珠远距传送”攻击入口。
 *
 * <p>这个版本没有长矛/STAB 动作，只能走普通近战。实测 {@code AttackEntityCallback} 在
 * 1.21.10 的攻击包路径上并不可靠（客户端能选中珍珠，但服务端事件没有触发），所以这里直接
 * 注入服务端真正执行的 {@link Player#attack(Entity)}：无论攻击包、Carpet 假人的
 * {@code attack} 动作，还是其它模组直接调用 {@code player.attack}，都会经过这里。</p>
 *
 * <p>只在服务端生效；客户端侧由 {@link SogPearlTeleport#onAttack} 自己按
 * {@code level().isClientSide()} 忽略。</p>
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void sog$farPearlAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        SogPearlTeleport.onAttack(self, target);
    }
}

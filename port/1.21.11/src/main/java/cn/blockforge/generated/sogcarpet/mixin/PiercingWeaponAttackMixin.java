package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogPearlTeleport;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.PiercingWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “服务器端支持”的关键钩子：长矛突刺。
 *
 * <p>26.2 里长矛攻击走 {@code PiercingWeapon.attack}（服务端由 {@code handlePlayerAction} 的
 * STAB 分支调用），它内部只会命中 {@code LivingEntity}，<b>不会命中末影珍珠等投掷物</b>。
 * 所以在服务端这里、在原版突刺逻辑之后再补一次我们自己的判定：按玩家的主手矛与副手数字纸，
 * 从眼睛沿视线向前找悬停中的远传珍珠并叠加轴速度。</p>
 *
 * <p>放在公共 mixin 里：原版客户端拿矛攻击同样会发出 STAB 包，服务端一样能识别，因此
 * 叠加冲量完全不依赖客户端是否装本模组；客户端装与不装都正常。</p>
 */
@Mixin(PiercingWeapon.class)
public abstract class PiercingWeaponAttackMixin {
    @Inject(
            method = "attack(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("TAIL"))
    private void sog$pearlStab(LivingEntity attacker, EquipmentSlot slot, CallbackInfo ci) {
        SogPearlTeleport.onStab(attacker);
    }
}

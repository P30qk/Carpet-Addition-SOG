package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogPearlTeleport;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在玩家投出“远传珍珠/雪球”时，于投掷物<b>被加入世界之前</b>就把悬停状态设好。
 *
 * <p>为什么要在构造阶段做：`noGravity` 与实体自定义名都是同步数据，如果等到服务端第一 tick 才设，
 * 生成包里带的还是投掷初速度，客户端就会先按初速度飞一小段、随后才被纠正——表现为“先停一下、
 * 又飞一小段、然后才停”。在构造末尾设置后，生成包直接带上“无重力 + 悬停标记”，客户端第一 tick
 * 就悬停。只在服务端（玩家投掷走的是这个构造器）生效，客户端实体由生成包构造，不走这里。</p>
 */
@Mixin(ThrowableItemProjectile.class)
public abstract class ThrowableItemProjectileInitMixin {
    // 1.21.x 的投掷物构造器只有 (EntityType, LivingEntity, Level) 三个参数
    // （ItemStack 参数是 1.21.2+ 才加进来的），所以按这个描述符注入。
    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/EntityType;"
                    + "Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/level/Level;)V",
            at = @At("TAIL"))
    private void sog$setupFarPearl(EntityType<?> type, LivingEntity owner, Level level,
            CallbackInfo ci) {
        if (!level.isClientSide()) {
            SogPearlTeleport.onProjectileConstructed((ThrowableItemProjectile) (Object) this);
        }
    }
}

package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 鞘翅不消耗耐久。
 *
 * <p>鞘翅的耐久只在滑翔时被原版 {@code LivingEntity} 周期性地调用
 * {@link ItemStack#hurtAndBreak} 扣掉，但入口和剪刀一样集中在 {@code hurtAndBreak}
 * 的三个重载上（服务端玩家、装备槽、交互手）。这里照搬剪刀的做法，在每个重载的 HEAD
 * 处判断一次：只要“规则开启 + 这叠物品是鞘翅”就直接取消，鞘翅永远不掉耐久。</p>
 *
 * <p>只对 {@code Items.ELYTRA} 生效，其它工具、盔甲、武器完全不受影响；规则默认关闭时
 * 该方法的第一层判断就是 false，与原版逐字节一致。</p>
 */
@Mixin(ItemStack.class)
public abstract class ElytraDurabilityMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("HEAD"), cancellable = true)
    private void sogcarpet$elytraNoDurabilitySlot(
            int amount, LivingEntity entity, EquipmentSlot slot, CallbackInfo ci) {
        if (SogSettings.elytraNoDurability && ((ItemStack) (Object) this).is(Items.ELYTRA)) {
            ci.cancel();
        }
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void sogcarpet$elytraNoDurabilityServer(
            int amount, ServerLevel level, ServerPlayer player, Consumer<Item> onBreak, CallbackInfo ci) {
        if (SogSettings.elytraNoDurability && ((ItemStack) (Object) this).is(Items.ELYTRA)) {
            ci.cancel();
        }
    }
}

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
 * 剪刀不消耗耐久。
 *
 * <p>剪刀掉耐久的地方不止一处（剪羊毛、剪蜂巢取蜜、剪蘑菇、挖树叶、剪哞菇、
 * 甚至当武器打人），逐个拦 {@code ShearsItem} 与各类交互太容易漏。这里选所有
 * 掉耐久的公共出口 {@link ItemStack#hurtAndBreak}：它共有三个重载（服务端玩家、
 * 装备槽、交互手，后两者最终也走服务端那条），在每个重载的 HEAD 处判断一次，
 * 只要“规则开启 + 这叠物品是剪刀”就直接取消，等于剪刀永远不掉耐久。</p>
 *
 * <p>只对 {@code Items.SHEARS} 生效，其它工具、盔甲、武器完全不受影响；规则默认
 * 关闭时该方法的第一层判断就是 false，与原版逐字节一致。</p>
 */
@Mixin(ItemStack.class)
public abstract class ShearsDurabilityMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("HEAD"), cancellable = true)
    private void sogcarpet$shearsNoDurabilitySlot(
            int amount, LivingEntity entity, EquipmentSlot slot, CallbackInfo ci) {
        if (SogSettings.shearsNoDurability && ((ItemStack) (Object) this).is(Items.SHEARS)) {
            ci.cancel();
        }
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void sogcarpet$shearsNoDurabilityServer(
            int amount, ServerLevel level, ServerPlayer player, Consumer<Item> onBreak, CallbackInfo ci) {
        if (SogSettings.shearsNoDurability && ((ItemStack) (Object) this).is(Items.SHEARS)) {
            ci.cancel();
        }
    }
}

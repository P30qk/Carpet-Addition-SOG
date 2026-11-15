package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「自定义信标效果」：规则开启时，信标的支付格也接受下界之星。
 *
 * <p>只放宽“能不能放”这一处；扣物品、标脏、同步都沿用原版 {@code BeaconMenu#updateEffects}
 * 的路径。规则关闭时下界之星放不进去，与原版一致。</p>
 */
@Mixin(targets = "net.minecraft.world.inventory.BeaconMenu$PaymentSlot")
public abstract class BeaconMenuPaymentSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void sog$acceptNetherStar(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SogSettings.customBeaconEffects && stack != null && stack.is(Items.NETHER_STAR)) {
            cir.setReturnValue(true);
        }
    }
}

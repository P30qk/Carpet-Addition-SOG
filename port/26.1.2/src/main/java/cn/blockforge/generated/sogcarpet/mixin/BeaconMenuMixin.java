package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogBeaconEffectHolder;
import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「自定义信标效果」的菜单侧接线：下界之星作为支付物，以及把“这次用了下界之星”记到信标上。
 *
 * <p>支付槽是菜单的第 0 格（{@code PAYMENT_SLOT}），这里不去 shadow 那个包私有的内部类，
 * 直接用公开的 {@code getSlot(0)} 拿。流程：</p>
 * <ol>
 *   <li>{@code updateEffects} 头部记下“支付格现在放的是不是下界之星”；</li>
 *   <li>原版逻辑照常跑：校验效果 → 写主/副效果 → 扣掉 1 个支付物；</li>
 *   <li>返回 true（真的生效了）时，把“本次支付物是不是下界之星”写到信标方块实体上：是下界之星
 *       就记下 +1 级标记，是普通支付物就把旧标记清掉（避免以前用星开过的信标一直多吃一级），
 *       {@code saveAdditional} 会持久化它，下次生效时 {@code applyEffects} 才会按标记提升等级。</li>
 * </ol>
 *
 * <p>是否接受下界之星由 {@link BeaconMenuPaymentSlotMixin} 控制（规则关闭时仍然只认原版支付物）。</p>
 */
@Mixin(BeaconMenu.class)
public abstract class BeaconMenuMixin {
    @Shadow
    @Final
    private ContainerLevelAccess access;

    /** 这一次 {@code updateEffects} 的支付物是不是下界之星（**头部记、尾部用**）。 */
    @Unique
    private boolean sog$paidWithStar;

    /** 进入 {@code updateEffects} 时支付槽里有没有东西（26.1.2 没有返回值可判断）。 */
    @Unique
    private boolean sog$hadPayment;

    @Inject(method = "updateEffects", at = @At("HEAD"))
    private void sog$capturePayment(java.util.Optional<Holder<MobEffect>> primary,
            java.util.Optional<Holder<MobEffect>> secondary, CallbackInfo ci) {
        ItemStack stack = ((BeaconMenu) (Object) this).getSlot(0).getItem();
        this.sog$hadPayment = ((BeaconMenu) (Object) this).hasPayment();
        this.sog$paidWithStar = SogSettings.customBeaconEffects && stack.is(Items.NETHER_STAR);
    }

    @Inject(method = "updateEffects", at = @At("RETURN"))
    private void sog$rememberNetherStar(java.util.Optional<Holder<MobEffect>> primary,
            java.util.Optional<Holder<MobEffect>> secondary, CallbackInfo ci) {
        boolean paidWithStar = this.sog$paidWithStar;
        boolean hadPayment = this.sog$hadPayment;
        this.sog$paidWithStar = false;
        this.sog$hadPayment = false;
        // 26.1.2 的 updateEffects 是 void：原版只在支付槽有东西时才真正生效，
        // 所以用进入方法时记下的 hasPayment 判断这次有没有成功。
        if (!hadPayment) {
            return;
        }
        // 成功更新时一定要把标记写成“本次支付物是不是下界之星”：
        // 只写 true 不写 false 的话，用下界之星开过一次后，以后每次用铁锭/绿宝石重开都会
        // 白白继承那 +1 级，直到规则关闭或重进世界。
        this.access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof SogBeaconEffectHolder holder
                    && holder.sog$isNetherStarPaid() != paidWithStar) {
                holder.sog$setNetherStarPaid(paidWithStar);
                holder.sog$markChanged();
            }
        });
    }
}

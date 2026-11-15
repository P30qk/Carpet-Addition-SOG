package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogCopperScrape;
import cn.blockforge.generated.sogcarpet.SogDispenserBerryHarvest;
import cn.blockforge.generated.sogcarpet.SogDispenserPlant;
import cn.blockforge.generated.sogcarpet.SogDispenserTill;
import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.SogTools;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 发射器四条“可做某事”规则共用的行为接管点，挂在
 * {@code DispenserBlock#getDispenseMethod} 的返回处。
 *
 * <p>规则开启且物品匹配时，把原版查表得来的发射器行为换成我们的实现：</p>
 * <ul>
 *   <li>{@code dispenserCopperScrape}（发射器可除锈/除蜡）：斧头
 *       （{@link SogTools} 的判定：#minecraft:axes 标签，标签未绑定时按注册名
 *       {@code _axe} 后缀兜底，支持任意材质与模组斧头）→
 *       {@link SogCopperScrape#INSTANCE}；</li>
 *   <li>{@code dispenserTilling}（发射器可耕地）：锄头（{@code #minecraft:hoes} 标签，
 *       任意材质，覆盖沿用原版耕作逻辑的模组锄头）→ {@link SogDispenserTill#INSTANCE}；</li>
 *   <li>{@code dispenserPlanting}（发射器可种植作物）：作物物品（#minecraft:crops 标签，
 *       含紫颂花）→ {@link SogDispenserPlant#INSTANCE}；</li>
 *   <li>{@code dispenserBerryHarvest}（发射器可采集浆果）：剪刀（{@link SogTools} 的
 *       SHEARS 判定）→ {@link SogDispenserBerryHarvest#INSTANCE}，它是原版剪刀发射器
 *       行为的子类，没有浆果时会退回原版取蜜/剪实体逻辑。</li>
 * </ul>
 *
 * <p>前三条（以及采集浆果对“原本没有专属行为的剪刀”的情况）只在原版查表结果是
 * {@link #DEFAULT_BEHAVIOR}（26.2 里“没有专属行为”的兜底结果，即原版本来只会把物品
 * 直接弹出；同时也兼容旧版语义的 {@link DispenseItemBehavior#NOOP}）时才接管——别的模组
 * 给这些物品注册过专属行为时保持原样；采集浆果对原版剪刀也在查表结果是原版
 * {@link ShearsDispenseItemBehavior} 时接管。物品被功能开关禁用时原版会退回
 * {@code DEFAULT_BEHAVIOR}（把物品弹出），这里同样不动。</p>
 *
 * <p>四条规则都只发生在服务端：不是客户端侧（{@code level.isClientSide()}）才处理，
 * 客户端保持原样，纯客户端/单机/专用服务器表现一致，不需要任何客户端同步。</p>
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
    /**
     * 原版兜底发射行为：26.2 中 {@code getDispenseMethod} 对“没有专属发射行为”的物品
     * （如斧头、锄头、作物物品）返回的就是它——默认实现是直接把物品弹出。旧版本这里返回
     * {@link DispenseItemBehavior#NOOP}，判断时两者都算“原版没有专属行为”。
     */
    @Shadow
    @Final
    private static DefaultDispenseItemBehavior DEFAULT_BEHAVIOR;

    @Inject(method = "getDispenseMethod(Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/item/ItemStack;)"
            + "Lnet/minecraft/core/dispenser/DispenseItemBehavior;",
            at = @At("RETURN"), cancellable = true)
    private void sogcarpet$dispenserRules(Level level, ItemStack stack,
            CallbackInfoReturnable<DispenseItemBehavior> cir) {
        if (level.isClientSide()) {
            return;
        }
        DispenseItemBehavior current = cir.getReturnValue();
        // 功能开关被关掉的物品保持原版行为（原版会退回 DEFAULT_BEHAVIOR 把物品弹出）。
        if (!stack.isItemEnabled(level.enabledFeatures())) {
            return;
        }
        int mask = SogTools.categoryMask(stack);
        // 1) 发射器可采集浆果：把原版剪刀行为（或原本没有专属行为的模组剪刀的兜底结果）
        //    换成我们的子类；没有浆果时它会退回原版取蜜/剪实体逻辑。
        //    别的模组给剪刀注册过专属行为时保持原样。
        if (SogSettings.dispenserBerryHarvest && (mask & (1 << SogTools.SHEARS)) != 0
                && (current instanceof ShearsDispenseItemBehavior
                        || current == DEFAULT_BEHAVIOR || current == DispenseItemBehavior.NOOP)) {
            cir.setReturnValue(SogDispenserBerryHarvest.INSTANCE);
            return;
        }
        // 2) 其余三条只在“原版没有专属行为、本来会直接弹出物品”的兜底结果上接管：
        //    26.2 的 getDefaultDispenseMethod 返回 DEFAULT_BEHAVIOR，旧版是 NOOP。
        //    装备/硫方块吞噬/刷怪蛋等原版特例（返回各自的行为实例）保持原样。
        if (current != DEFAULT_BEHAVIOR && current != DispenseItemBehavior.NOOP) {
            return;
        }
        // 发射器可除锈/除蜡：任意材质的斧头（#minecraft:axes 标签，含模组斧头）。
        if (SogSettings.dispenserCopperScrape && (mask & (1 << SogTools.AXE)) != 0) {
            cir.setReturnValue(SogCopperScrape.INSTANCE);
            return;
        }
        // 发射器可耕地：任意材质的锄头（#minecraft:hoes 标签）。
        if (SogSettings.dispenserTilling && stack.is(ItemTags.HOES)) {
            cir.setReturnValue(SogDispenserTill.INSTANCE);
            return;
        }
        // 发射器可种植作物：作物物品（#minecraft:crops 标签）与紫颂花。
        if (SogSettings.dispenserPlanting && SogDispenserPlant.isPlantable(stack)) {
            cir.setReturnValue(SogDispenserPlant.INSTANCE);
        }
    }
}

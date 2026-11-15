package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 木头去皮配方的“斧头保留”实现（1.21.x 版）。
 *
 * <p>原版合成只会把物品栏里那叠材料减一，然后按 {@code getRemainingItemsFor} 把“容器残留”
 * （如空桶）放回原位；斧头本身没有 crafting remainder，所以在原版配方里会被直接吃掉。
 * 本模组的去皮配方都叫 {@code sog_carpet:stripping_*}，这里让它们合成后斧头仍然留在配方格里、
 * 且不掉耐久。</p>
 *
 * <p><b>与 1.21.2+ 的差别</b>：那个版本里“残留物品”是在 {@code ResultSlot#getRemainingItems}
 * 里算的，可以在 TAIL 补一格；1.21.x 的 {@code ResultSlot} 没有这个方法，残留物品由
 * {@code ResultSlot#onTake} 内联算（{@code getRemainingItemsFor} → 逐格删除 → 回填）。所以这里
 * 改成在 {@code onTake} 的 HEAD/TAIL 之间传一个“斧头在哪一格、原来是什么”的快照：</p>
 * <ul>
 *   <li>HEAD：确认当前匹配的是本模组的去皮配方，并记下斧头所在的格子与它的副本；</li>
 *   <li>TAIL：原版已经把材料减一了，把斧头补回去——格子空了就整叠放回，还留着同一把斧头
 *       就补回 1 个（与 1.21.2+ 走 {@code getRemainingItems} 时原版 {@code grow(1)} 的结果一致），
 *       格子被其它残留占住时退回背包，不凭空吞掉玩家的斧头。</li>
 * </ul>
 *
 * <p>只在服务端处理（合成以服务端为准），规则关闭时 {@code RecipeManagerMixin} 已经让这些配方
 * 取不到，这里也会因为规则判断直接返回。</p>
 */
@Mixin(ResultSlot.class)
public abstract class StripRecipeRemainderMixin {
    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void sogcarpet$beforeTake(Player player, ItemStack stack, CallbackInfo ci,
            @Share("sogcarpet$axeSlot") LocalIntRef axeSlot,
            @Share("sogcarpet$axeStack") LocalRef<ItemStack> axeStack) {
        axeSlot.set(-1);
        axeStack.set(ItemStack.EMPTY);
        if (!SogSettings.woodStrippingRecipe || player == null || player.level().isClientSide()) {
            return;
        }
        CraftingInput input = this.craftSlots.asPositionedCraftInput().input();
        RecipeHolder<CraftingRecipe> matched = player.level().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, player.level()).orElse(null);
        if (matched == null) {
            return;
        }
        ResourceLocation id = matched.id();
        if (!"sog_carpet".equals(id.getNamespace()) || !id.getPath().startsWith("stripping_")) {
            return;
        }
        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            ItemStack slotStack = this.craftSlots.getItem(i);
            if (!slotStack.isEmpty() && slotStack.is(ItemTags.AXES)) {
                axeSlot.set(i);
                axeStack.set(slotStack.copy());
                return;
            }
        }
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void sogcarpet$restoreAxe(Player player, ItemStack stack, CallbackInfo ci,
            @Share("sogcarpet$axeSlot") LocalIntRef axeSlot,
            @Share("sogcarpet$axeStack") LocalRef<ItemStack> axeStack) {
        int slot = axeSlot.get();
        ItemStack original = axeStack.get();
        if (slot < 0 || original == null || original.isEmpty()) {
            return;
        }
        ItemStack current = this.craftSlots.getItem(slot);
        if (current.isEmpty()) {
            this.craftSlots.setItem(slot, original);
        } else if (ItemStack.isSameItemSameComponents(current, original)) {
            // 同一把斧头还剩着：原版刚减掉一个，补回来即可（不掉耐久、不被吃掉）。
            current.grow(1);
        } else {
            // 格子被别的残留占了：把斧头退回背包，宁可不保留也不能吞掉玩家的东西。
            player.getInventory().placeItemBackInInventory(original);
        }
    }
}

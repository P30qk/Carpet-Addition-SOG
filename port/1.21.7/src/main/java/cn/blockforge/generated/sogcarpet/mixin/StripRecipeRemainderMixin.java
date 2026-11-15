package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 木头去皮配方的“斧头保留”实现。
 *
 * <p>原版合成配方只会把物品栏里那叠材料减一，然后按 {@code CraftingRecipe#getRemainingItems}
 * 把“容器残留”（如空桶）放回原位；斧头本身没有 crafting remainder，所以在原版配方里会被直接吃掉。
 * 本模组的去皮配方都叫 {@code sog_carpet:stripping_*}，这里在 {@link ResultSlot} 计算残留物品的
 * 收口点（私有方法 {@code getRemainingItems(CraftingInput, Level)}）的 TAIL 处补一刀：
 * 如果当前匹配到的正是本模组的去皮配方，就把配方格里的斧头原样放回残留表对应格，
 * 于是斧头合成后仍留在配方栏中、且不掉耐久。</p>
 *
 * <p>只在服务端 {@link ServerLevel} 上处理（合成以服务端为准），客户端与其它配方完全不受影响；
 * 规则关闭时 {@code RecipeManagerMixin} 已经让这些配方取不到，这里也会因为规则判断直接返回。</p>
 */
@Mixin(ResultSlot.class)
public abstract class StripRecipeRemainderMixin {
    @Inject(method = "getRemainingItems(Lnet/minecraft/world/item/crafting/CraftingInput;"
            + "Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;",
            at = @At("TAIL"), cancellable = true)
    private void sogcarpet$keepAxe(CraftingInput input, Level level,
            CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        if (!SogSettings.woodStrippingRecipe || !(level instanceof ServerLevel server)) {
            return;
        }
        var matched = server.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, server);
        if (matched.isEmpty()) {
            return;
        }
        ResourceLocation id = matched.get().id().location();
        if (!"sog_carpet".equals(id.getNamespace()) || !id.getPath().startsWith("stripping_")) {
            return;
        }
        NonNullList<ItemStack> remaining = cir.getReturnValue();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.AXES)) {
                remaining.set(i, stack.copy());
            }
        }
        cir.setReturnValue(remaining);
    }
}

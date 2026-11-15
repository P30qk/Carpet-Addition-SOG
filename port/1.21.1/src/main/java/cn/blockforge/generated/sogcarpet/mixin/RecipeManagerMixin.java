package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogRecipeBook;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 简易配方的规则门控。
 *
 * <p>本模组自带的数据包 JSON（遮光玻璃、鞘翅、雪块出雪、去皮）始终注册；这里在工作台取配方的
 * 收口点（{@code getRecipeFor}）按配方名逐条判断：对应规则关闭时返回空结果，
 * 等于该配方不存在。这些配方的产物互不相同，但都挂在 {@code sog_carpet} 命名
 * 空间下，因此必须看 {@code getPath()} 才能分别归属到各自的规则。</p>
 *
 * <p><b>与 1.21.2+ 的差别</b>：1.21.x 的 {@code RecipeManager} 没有
 * {@code listDisplaysForRecipe}（那套“一条配方 → 若干展示条目”的逐条下发是 1.21.2 才加的），
 * 配方书侧的门控改在 {@link ServerRecipeBookAddMixin} 里做；这里只保留
 * “能不能合出来”这一个入口，两处共用 {@link SogRecipeBook#isEnabled(ResourceLocation)}，
 * 所以“配方书里有没有”和“工作台能不能合”永远一致。</p>
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;"
            + "Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)"
            + "Ljava/util/Optional;", at = @At("TAIL"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void sog$gateEasyRecipes(
            RecipeType<T> type, I input, Level level,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        Optional<RecipeHolder<T>> matched = cir.getReturnValue();
        if (matched.isEmpty()) {
            return;
        }
        // 1.21.x 的 RecipeHolder#id() 直接就是 ResourceLocation（1.21.2+ 才换成 ResourceKey）。
        ResourceLocation id = matched.get().id();
        if (!"sog_carpet".equals(id.getNamespace())) {
            return;
        }
        if (!SogRecipeBook.isEnabled(id.getPath())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}

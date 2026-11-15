package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogRecipeBook;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 简易配方的规则门控。
 *
 * <p>本模组自带的数据包 JSON（遮光玻璃、鞘翅、雪块出雪）始终注册；这里在工作台取配方的
 * 收口点（{@code getRecipeFor}）按配方名逐条判断：对应规则关闭时返回空结果，
 * 等于该配方不存在。这些配方的产物互不相同，但都挂在 {@code sog_carpet} 命名
 * 空间下，因此必须看 {@code getPath()} 才能分别归属到各自的规则。</p>
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
        ResourceLocation id = matched.get().id().location();
        if (!"sog_carpet".equals(id.getNamespace())) {
            return;
        }
        if (!sog$isEnabled(id.getPath())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    /**
     * 配方名 → 启用它的规则。
     *
     * <p>判断逻辑统一放在 {@link SogRecipeBook#isEnabled(String)}：{@link SogRecipeBook} 在
     * 规则变化时会用它决定给玩家补解锁哪些配方，两处必须完全一致，否则会出现“配方书里有、
     * 工作台合不出来”或反过来的矛盾。</p>
     */
    private static boolean sog$isEnabled(String path) {
        return SogRecipeBook.isEnabled(path);
    }

    /**
     * 配方书（含 EasierCrafting 这类读取配方书的模组）的规则门控。
     *
     * <p>26.2 的配方书不再下发完整配方表，而是让服务端把这些玩家<b>已解锁</b>的配方逐条解析成
     * {@code RecipeDisplayEntry} 再发过去；{@link RecipeManager#listDisplaysForRecipe} 就是
     * 那个“一条配方 → 若干展示条目”的唯一出口（配方书首包、后补解锁都走它）。本模组自带的
     * 配方由 {@code data/sog_carpet/advancement/recipes/*.json} 解锁，这里在规则关闭时干脆
     * 不投递任何条目，于是原版配方书与 EasierCrafting 都看不到这条配方——不会出现“界面里能点、
     * 放进工作台却合不出来”的矛盾。</p>
     */
    @Inject(method = "listDisplaysForRecipe", at = @At("HEAD"), cancellable = true)
    private void sog$gateRecipeBookDisplays(
            net.minecraft.resources.ResourceKey<Recipe<?>> recipe,
            java.util.function.Consumer<RecipeDisplayEntry> consumer,
            CallbackInfo ci) {
        ResourceLocation id = recipe.location();
        if ("sog_carpet".equals(id.getNamespace()) && !sog$isEnabled(id.getPath())) {
            ci.cancel();
        }
    }
}

package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
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
        ResourceLocation id = matched.get().id();
        if (!"sog_carpet".equals(id.getNamespace())) {
            return;
        }
        if (!sog$isEnabled(id.getPath())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    /** 配方名 → 启用它的规则。规则默认全部关闭。 */
    private static boolean sog$isEnabled(String path) {
        return switch (path) {
            case "tinted_glass_easy" -> SogSettings.tintedGlassEasyCraft;
            case "simple_elytra" -> SogSettings.easyElytraCraft;
            case "snow_from_block" -> SogSettings.snowCraftable;
            default -> true;
        };
    }
}

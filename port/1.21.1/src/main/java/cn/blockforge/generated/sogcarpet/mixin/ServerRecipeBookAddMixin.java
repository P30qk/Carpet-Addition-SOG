package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogRecipeBook;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 配方书侧的规则门控（1.21.x 专用）。
 *
 * <p>1.21.2+ 才把配方书拆成“服务端只发<b>已解锁</b>配方 → 逐条解析成
 * {@code RecipeDisplayEntry}”那套，门控点在 {@code RecipeManager#listDisplaysForRecipe}；
 * 1.21.x 的配方书还是老形态：服务端 {@link ServerRecipeBook} 里存一份
 * “已解锁的配方 id 集合”，加入时整份下发、之后靠 ADD/REMOVE 包增删。</p>
 *
 * <p>所以这里拦在唯一的新增入口 {@link ServerRecipeBook#addRecipes} 上：本模组命名空间下、
 * 规则已关闭的配方直接剔除，不让它进“已解锁集合”。否则只要有一条
 * {@code data/sog_carpet/advancement/recipes/*.json} 被触发（拿到材料就会解锁），
 * 配方书里就会出现一条点了却合不出来的配方——与 {@code RecipeManagerMixin} 里
 * “能不能合”的判断矛盾。</p>
 *
 * <p>规则中途切换由 {@link SogRecipeBook#refresh} 负责把两个方向都对齐：
 * 关掉的用 {@code removeRecipes} 撤掉，打开的用 {@code addRecipes} 补上。</p>
 */
@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookAddMixin {
    /**
     * 在 {@code addRecipes} 入口替换掉入参集合。
     *
     * <p>{@code argsOnly = true} 只匹配方法入参，避免误改方法体里的局部变量；
     * 类型擦除后是 {@code java.util.Collection}，与目标方法的第一个参数一致。</p>
     */
    @ModifyVariable(method = "addRecipes", at = @At("HEAD"), argsOnly = true)
    private Collection<RecipeHolder<?>> sog$dropDisabledRecipes(
            Collection<RecipeHolder<?>> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return recipes;
        }
        boolean anyDisabled = false;
        for (RecipeHolder<?> holder : recipes) {
            if (SogRecipeBook.isDisabled(holder.id())) {
                anyDisabled = true;
                break;
            }
        }
        // 没有命中被禁配方时原样返回，不额外分配集合。
        if (!anyDisabled) {
            return recipes;
        }
        List<RecipeHolder<?>> kept = new ArrayList<>(recipes.size());
        for (RecipeHolder<?> holder : recipes) {
            if (!SogRecipeBook.isDisabled(holder.id())) {
                kept.add(holder);
            }
        }
        return kept;
    }
}

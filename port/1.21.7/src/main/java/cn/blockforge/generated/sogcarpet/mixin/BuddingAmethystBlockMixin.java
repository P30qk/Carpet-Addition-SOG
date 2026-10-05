package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 紫水晶芽/簇生长限制的唯一收口点。
 *
 * <p>紫水晶的成长完全由紫水晶母岩（{@link BuddingAmethystBlock}）的 {@code randomTick}
 * 驱动：每次随机刻挑一个方向，把该方向的空气/水变成小芽，或把已有的小芽→中芽→大芽→簇
 * 逐级替换。这里包装它最后写方块的那次 {@code ServerLevel.setBlockAndUpdate}：
 * 若被升级的方块正好处在规则选定的阶段，就放弃这次升级（返回 false，方块保持原样），
 * 其余阶段照原版继续。这样不必复刻原版生长逻辑，也不会影响母岩的其他行为。</p>
 *
 * <p>选定阶段时：处在选定阶段的芽停止升级（如 medium 时中芽不再变大芽），
 * 更小的芽仍会长上来（小芽→中芽），已存在的更大芽也仍会继续生长（大芽→簇）。
 * 规则为 off 时不介入。</p>
 *
 * <p><b>为什么用 {@code @WrapOperation} 而不是原版 {@code @Redirect}：</b>
 * 26.2 工具链（sponge-mixin 0.17.4）把 {@code @Redirect.at()} 改成数组签名，
 * mixinextras 0.5.4 的 {@code FactoryRedirectWrapperMixinTransformer} 仍按单个
 * {@code AnnotationNode} 强转，目标类一加载就抛 {@code ClassCastException}；
 * {@code @WrapOperation} 是 mixinextras 自带的注入器，不存在该兼容问题
 * （详见 {@link ServerLevelMixin} 注释）。</p>
 */
@Mixin(BuddingAmethystBlock.class)
public abstract class BuddingAmethystBlockMixin {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean sogcarpet$limitAmethystBudGrowth(ServerLevel level, BlockPos pos, BlockState newState,
            Operation<Boolean> original) {
        SogSettings.AmethystGrowthLimit limit = SogSettings.amethystBudGrowthLimit;
        if (limit != SogSettings.AmethystGrowthLimit.OFF
                && SogSettings.amethystStage(level.getBlockState(pos)) == limit) {
            return false;
        }
        return original.call(level, pos, newState);
    }
}

package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogChorusBonemeal;
import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * “紫颂花可催熟”的另一半：让紫颂植株也能被骨粉“点名”——对着植株用骨粉时，
 * 在相连的紫颂结构里找最近一朵还能继续生长的紫颂花来催熟（找不到就当作无效）。
 *
 * <p>规则关闭时与原版完全一致（骨粉对植株无效）。查找只读方块，客户端右键预测时也能安全调用；
 * 真正催熟在服务端 {@code performBonemeal} 里执行。</p>
 */
@Mixin(ChorusPlantBlock.class)
public abstract class ChorusPlantBlockMixin implements BonemealableBlock {
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return SogSettings.chorusFlowerBonemeal && SogChorusBonemeal.findNearestGrowableFlower(level, pos) != null;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return SogSettings.chorusFlowerBonemeal && SogChorusBonemeal.findNearestGrowableFlower(level, pos) != null;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (!SogSettings.chorusFlowerBonemeal) {
            return;
        }
        BlockPos flower = SogChorusBonemeal.findNearestGrowableFlower(level, pos);
        if (flower != null) {
            SogChorusBonemeal.grow(level, random, flower);
        }
    }
}
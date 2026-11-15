package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogChorusBonemeal;
import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * “紫颂花可催熟”：让紫颂花实现 {@link BonemealableBlock}，于是原版骨粉的两条路径都会自动生效——
 * 玩家右键（{@code BoneMealItem.useOn} → {@code growCrop}）与发射器（原版骨粉发射行为同样调用
 * {@code growCrop}），消耗、音效与“绿色星星”粒子全部沿用原版，无需改任何发射器代码。
 *
 * <p>规则关闭时三方法都返回“无效/空操作”，行为与原版完全一致（骨粉对紫颂花无效）。
 * {@code isValidBonemealTarget} 在客户端也会被调用（右键预测），这里只读方块、不写世界；
 * 真正的世界修改只在服务端触发的 {@code performBonemeal} 里做。</p>
 */
@Mixin(ChorusFlowerBlock.class)
public abstract class ChorusFlowerBlockMixin implements BonemealableBlock {
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return SogSettings.chorusFlowerBonemeal && SogChorusBonemeal.canGrow(level, pos);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return SogSettings.chorusFlowerBonemeal && SogChorusBonemeal.canGrow(level, pos);
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (SogSettings.chorusFlowerBonemeal) {
            SogChorusBonemeal.grow(level, random, pos);
        }
    }
}
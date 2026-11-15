package cn.blockforge.generated.sogcarpet;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * “紫颂花可催熟”的共用逻辑：骨粉（玩家右键或发射器）让紫颂花执行一次与原版随机刻
 * （{@code ChorusFlowerBlock.randomTick}）完全相同的生长判定。
 *
 * <p>每次成功使用（与自然生长逐步一致，含“是否向上”的随机骰子）：</p>
 * <ol>
 *   <li><b>向上长</b>：上方留有两格空间、且原版的向下判定通过时（花所依附的方块是末地石、
 *       紫颂植株或空气；依附在长杆上时按原版掷“是否继续向上”的骰子），原位置变成紫颂植株、
 *       上方长出一朵年龄不变的新花；</li>
 *   <li><b>横向分叉</b>：上不去、花的年龄还没到上限（age&lt;4）时，按原版在侧面随机尝试
 *       若干次，分叉出一朵或多朵大一岁的新花；</li>
 *   <li><b>枯萎</b>：真的无处可长时（分叉尝试全部落空，或花已到 age=4 且上不去），按原版
 *       把这朵花变成枯萎的死花（age=5，带原版的枯萎事件号 1034）。</li>
 * </ol>
 *
 * <p>因此骨粉只能把紫颂催到自然生长的极限为止：花会像自然生长那样边走边分叉、迟早枯萎，
 * 枯萎的花不再是可以催熟的目标——“可以无限催熟却永不枯萎”的状态不会再出现。唯一
 * “什么都不发生”的情形是花的上方被方块占住（原版随机刻同样不做任何事），此时骨粉无效、
 * 不消耗、不改变方块。</p>
 *
 * <p>对着<b>紫颂植株</b>用骨粉时，会在相连的紫颂结构里找最近一朵还能继续生长的紫颂花来
 * 催熟（找不到就视为无效）。所有布局判断都只读方块，可以在客户端安全调用（右键预测需要）；
 * 真正改方块的部分只在服务端执行（{@code performBonemeal} 只由服务端触发）。</p>
 */
public final class SogChorusBonemeal {
    /** 写入新花/植株/死花时使用的写回旗标（1|2：更新邻居 + 同步客户端），让结构与连接立即刷新。 */
    private static final int GROWTH_FLAGS = 3;
    /** 植株找花搜索的上限（相连结构通常很小，这里只是防御异常数据）。 */
    private static final int MAX_SEARCH_BLOCKS = 512;

    private SogChorusBonemeal() {
    }

    /** 这朵紫颂花现在能不能被催熟（骨粉是否有效）：上方留空且还没枯萎，就能做一次生长判定。 */
    public static boolean canGrow(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChorusFlowerBlock)) {
            return false;
        }
        if (state.getValue(ChorusFlowerBlock.AGE) >= ChorusFlowerBlock.DEAD_AGE) {
            return false;
        }
        BlockPos above = pos.above();
        return level.isEmptyBlock(above) && insideBuildHeight(level, above);
    }

    /**
     * 对某朵紫颂花执行一次与原版随机刻一致的生长判定（向上/分叉/枯萎，含随机骰子）。
     *
     * <p>返回 false 只表示“上方被占住、原版也不会做任何事”，此时没有动过世界；
     * 其余情况（包括枯萎）都返回 true——那也算一次生效的催熟使用。</p>
     */
    public static boolean grow(ServerLevel level, RandomSource random, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChorusFlowerBlock)) {
            return false;
        }
        int age = state.getValue(ChorusFlowerBlock.AGE);
        if (age >= ChorusFlowerBlock.DEAD_AGE) {
            return false;
        }
        BlockPos above = pos.above();
        if (!level.isEmptyBlock(above) || !insideBuildHeight(level, above)) {
            return false;
        }
        // —— 与原版 ChorusFlowerBlock#randomTick 相同的判定流程 ——
        boolean growUpwards = false;
        boolean pillarOnSupportBlock = false;
        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.is(Blocks.END_STONE)) {
            growUpwards = true;
        } else if (belowState.is(Blocks.CHORUS_PLANT)) {
            int height = 1;
            for (int i = 0; i < 4; i++) {
                BlockState testState = level.getBlockState(pos.below(height + 1));
                if (!testState.is(Blocks.CHORUS_PLANT)) {
                    if (testState.is(Blocks.END_STONE)) {
                        pillarOnSupportBlock = true;
                    }
                    break;
                }
                height++;
            }
            if (height < 2 || height <= random.nextInt(pillarOnSupportBlock ? 5 : 4)) {
                growUpwards = true;
            }
        } else if (belowState.isAir()) {
            growUpwards = true;
        }
        if (growUpwards && allNeighborsEmpty(level, above, null) && level.isEmptyBlock(pos.above(2))) {
            // 先放新花、再把原位置换成植株：这样植株与上方新花的连接属性一次就写对。
            placeGrownFlower(level, above, age);
            becomePlant(level, pos);
            return true;
        }
        if (age < 4) {
            int attempts = random.nextInt(4);
            if (pillarOnSupportBlock) {
                attempts++;
            }
            boolean createdBranch = false;
            for (int i = 0; i < attempts; i++) {
                Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                if (isBranchTarget(level, pos, direction)) {
                    placeGrownFlower(level, pos.relative(direction), age + 1);
                    createdBranch = true;
                }
            }
            if (createdBranch) {
                becomePlant(level, pos);
            } else {
                placeDeadFlower(level, pos);
            }
            return true;
        }
        placeDeadFlower(level, pos);
        return true;
    }

    /**
     * 从一块紫颂植株出发，在相连的紫颂结构里找“最近一朵还能继续生长的紫颂花”。
     * 找不到（没有花、或所有花都枯萎/上方被占）返回 {@code null}。
     */
    public static @Nullable BlockPos findNearestGrowableFlower(LevelReader level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_SEARCH_BLOCKS) {
            BlockPos current = queue.poll();
            visited++;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (!seen.add(neighbor)) {
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.getBlock() instanceof ChorusFlowerBlock) {
                    if (canGrow(level, neighbor)) {
                        double dist = neighbor.distSqr(start);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = neighbor;
                        }
                    }
                    // 花也是结构的一部分：从花还能连向其它分支。
                    queue.add(neighbor);
                } else if (neighborState.getBlock() instanceof ChorusPlantBlock) {
                    queue.add(neighbor);
                }
            }
        }
        return best;
    }

    /** 位置是否在构建高度范围内（LevelReader 没有现成的 isInsideBuildHeight，这里按 minY+height 自算）。 */
    private static boolean insideBuildHeight(LevelReader level, BlockPos pos) {
        return pos.getY() >= level.getMinY() && pos.getY() < level.getMinY() + level.getHeight();
    }

    /** 与原版一致的分叉落点检查：落点与落点下方都是空气，落点的其它水平邻居也都是空气。 */
    private static boolean isBranchTarget(LevelReader level, BlockPos pos, Direction direction) {
        BlockPos target = pos.relative(direction);
        return level.isEmptyBlock(target)
                && level.isEmptyBlock(target.below())
                && allNeighborsEmpty(level, target, direction.getOpposite());
    }

    /** 六个方向之外的判断：水平四邻（可忽略一个方向）是否全为空气。 */
    private static boolean allNeighborsEmpty(LevelReader level, BlockPos pos, @Nullable Direction ignore) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != ignore && !level.isEmptyBlock(pos.relative(direction))) {
                return false;
            }
        }
        return true;
    }

    /** 放一朵新的紫颂花（声音/粒子事件与原版自然生长一致，事件号 1033）。 */
    private static void placeGrownFlower(ServerLevel level, BlockPos pos, int age) {
        level.setBlock(pos, Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, age),
                GROWTH_FLAGS);
        level.levelEvent(1033, pos, 0);
    }

    /** 把这朵花变成枯萎的死花（与原版自然生长一致，事件号 1034）。 */
    private static void placeDeadFlower(ServerLevel level, BlockPos pos) {
        level.setBlock(pos,
                Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, ChorusFlowerBlock.DEAD_AGE),
                GROWTH_FLAGS);
        level.levelEvent(1034, pos, 0);
    }

    /** 把原位置的紫颂花变成紫颂植株，并算好与周围花朵/植株的连接。 */
    private static void becomePlant(ServerLevel level, BlockPos pos) {
        level.setBlock(pos,
                ChorusPlantBlock.getStateWithConnections(level, pos, Blocks.CHORUS_PLANT.defaultBlockState()),
                GROWTH_FLAGS);
    }
}
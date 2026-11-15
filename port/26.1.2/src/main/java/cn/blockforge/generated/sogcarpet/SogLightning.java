package cn.blockforge.generated.sogcarpet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 雷击相关的公共定义：影响范围与「粘土 → 陶瓦」转化（服务端与客户端共用）。
 *
 * <p><b>范围</b>：原版闪电对实体的判定盒是 {@code AABB(x-3, y-3, z-3, x+3, y+6, z+3)}
 * （水平半径 3 格、向下 3 格、向上 6 格）。这里按整方块对齐成
 * <b>水平 ±3 格、竖直 -3～+6 格</b>，也就是 7×10×7 的一整块区域，
 * 既是「雷击粘土转化」实际转化的范围，也正好是可视化里画出来的
 * 「雷击影响范围」——两边用同一个 {@link #impactBox}，所见即所得。</p>
 *
 * <p>只做纯方块替换（粘土 → 陶瓦），不触发额外音效或粒子：闪电本身的视觉、声音、火焰
 * 与铜除锈都还是原版行为。</p>
 */
public final class SogLightning {
    /** 影响范围：水平半径（格）。 */
    public static final int HORIZONTAL_RADIUS = 3;
    /** 影响范围：落雷点向下几格。 */
    public static final int VERTICAL_BELOW = 3;
    /** 影响范围：落雷点向上几格。 */
    public static final int VERTICAL_ABOVE = 6;

    private SogLightning() {
    }

    /**
     * 落雷点对应的「雷击影响范围」：与 {@link #convertClay} 遍历的方块区域完全一致
     * （{@code x±3}、{@code y-3..y+6}、{@code z±3}），因此可视化画出来的方块范围就是
     * 真正会被转化/影响的那些方块。
     */
    public static AABB impactBox(BlockPos strike) {
        return new AABB(
                strike.getX() - HORIZONTAL_RADIUS,
                strike.getY() - VERTICAL_BELOW,
                strike.getZ() - HORIZONTAL_RADIUS,
                strike.getX() + HORIZONTAL_RADIUS + 1,
                strike.getY() + VERTICAL_ABOVE + 1,
                strike.getZ() + HORIZONTAL_RADIUS + 1);
    }

    /**
     * 把雷击影响范围内的粘土全部换成陶瓦，返回替换的方块数。
     *
     * <p>只处理 {@link Blocks#CLAY} 本身（带水的粘土在 26.2 里就是同一个方块，含水状态会
     * 随方块改变一起丢掉，正好符合“被雷击干烧成陶瓦”的直觉）；其它方块一律不动。</p>
     */
    public static int convertClay(Level level, BlockPos strike) {
        if (level == null || strike == null || level.isClientSide()) {
            return 0;
        }
        BlockState terracotta = Blocks.TERRACOTTA.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                for (int dy = -VERTICAL_BELOW; dy <= VERTICAL_ABOVE; dy++) {
                    cursor.set(strike.getX() + dx, strike.getY() + dy, strike.getZ() + dz);
                    if (!level.isOutsideBuildHeight(cursor)
                            && level.getBlockState(cursor).is(Blocks.CLAY)) {
                        if (level.setBlockAndUpdate(cursor, terracotta)) {
                            changed++;
                        }
                    }
                }
            }
        }
        return changed;
    }

    /** 从闪电实体坐标取落雷方块（与原版 {@code LightningBolt#getStrikePosition} 完全一致）。 */
    public static BlockPos strikePosition(double x, double y, double z) {
        return BlockPos.containing(x, y - 1.0E-6D, z);
    }
}

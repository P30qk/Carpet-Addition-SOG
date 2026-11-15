package cn.blockforge.generated.sogcarpet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * “发射器可种植作物”的发射器行为——让发射器把作物种到正前方。
 *
 * <p>支持两类物品：方块标签 {@code #minecraft:crops} 里的作物（小麦种子、甜菜种子、
 * 胡萝卜、土豆、西瓜/南瓜种子、火把花种子、瓶子草荚果；模组把自定义作物加进同一标签
 * 后同样可用）与紫颂花。规则开启且发射器取到这样的物品时，由
 * {@code DispenserBlockMixin} 在原版没有专属发射行为的前提下把 {@link #INSTANCE} 换进去。
 * 每次发射：</p>
 * <ol>
 *   <li>确定“支撑方块”：正前方本身就是支撑（作物=耕地、紫颂花=末地石），或者正前方是
 *       空位、它下方的方块是支撑（即对着它们正上方的空位）；</li>
 *   <li>在支撑方块顶面构造一个 {@link BlockPlaceContext}（没有玩家），落点即支撑方块
 *       上方那一格；</li>
 *   <li>直接调用原版 {@link BlockItem#place}：能否种（位置可替换、光照、能否成活、
 *       有没有实体挡路）全部按原版判定，种下后原版会消耗 1 个物品并播放原版放置音效；
 *       紫颂花、瓶子草等特殊形态（如双层植株只放下半）也沿用原版放置逻辑。</li>
 * </ol>
 *
 * <p>没有可放置的落点时什么都不做（不消耗物品、失败咔哒声）。所有逻辑都发生在服务端，
 * 纯客户端/单机/专用服务器下行为一致，且不需要任何客户端同步。</p>
 */
public final class SogDispenserPlant extends OptionalDispenseItemBehavior {
    /** 行为唯一实例，由 mixin 在“作物/紫颂花 + 原版无行为 + 规则开启”时替换返回。 */
    public static final SogDispenserPlant INSTANCE = new SogDispenserPlant();

    private SogDispenserPlant() {
    }

    /** 这个物品是不是本规则支持的“作物/紫颂花”（作物按 #minecraft:crops 标签识别）。 */
    public static boolean isPlantable(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.is(BlockTags.CROPS) || state.is(Blocks.CHORUS_FLOWER);
    }

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        this.setSuccess(false);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return stack;
        }
        BlockState plantState = blockItem.getBlock().defaultBlockState();
        boolean crop = plantState.is(BlockTags.CROPS);
        boolean chorus = plantState.is(Blocks.CHORUS_FLOWER);
        if (!crop && !chorus) {
            return stack;
        }
        ServerLevel level = source.level();
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        BlockPos facingPos = source.pos().relative(facing);
        // 支撑方块 = 正前方本身（对着耕地/末地石），或正前方空位下方的方块（对着正上方空位）。
        BlockPos supportPos = null;
        if (isSupport(level.getBlockState(facingPos), crop)) {
            supportPos = facingPos;
        } else if (isSupport(level.getBlockState(facingPos.below()), crop)) {
            supportPos = facingPos.below();
        }
        if (supportPos == null) {
            return stack;
        }
        // 完全走原版放置流程：点击点固定在支撑方块顶面，落点 = 支撑方块上方那一格。
        BlockPlaceContext context = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack,
                clickOnTop(supportPos));
        InteractionResult result = blockItem.place(context);
        if (result.consumesAction()) {
            this.setSuccess(true);
        }
        return stack;
    }

    /** 该方块能不能作为目标作物的支撑（作物=#minecraft:supports_crops，紫颂花=#minecraft:supports_chorus_flower）。 */
    private static boolean isSupport(BlockState state, boolean crop) {
        return crop ? state.is(BlockTags.SUPPORTS_CROPS) : state.is(BlockTags.SUPPORTS_CHORUS_FLOWER);
    }

    /** 在支撑方块顶面构造一个点击点（供原版放置逻辑读取点击位置与点击面）。 */
    private static BlockHitResult clickOnTop(BlockPos supportPos) {
        return new BlockHitResult(Vec3.atCenterOf(supportPos).add(0.0D, 0.5D, 0.0D),
                Direction.UP, supportPos, false);
    }
}
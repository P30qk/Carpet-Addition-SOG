package cn.blockforge.generated.sogcarpet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * “发射器可耕地”的发射器行为——让发射器里的锄头做玩家手里锄头能做的事。
 *
 * <p>规则开启且发射器取到的物品是锄头（原版 {@link HoeItem} 及其子类，任意材质）时，由
 * {@code DispenserBlockMixin} 在本方块原版没有任何发射器行为（DEFAULT_BEHAVIOR/NOOP）的
 * 前提下把 {@link #INSTANCE} 换进去。每次发射：</p>
 * <ol>
 *   <li>取发射器正前方（{@link DispenserBlock#FACING}）那一格，构造一个“玩家站在发射器
 *       位置、点击目标方块朝向发射器那一面”的 {@link UseOnContext}（没有玩家）；</li>
 *   <li>直接调用原版 {@link HoeItem#useOn}：泥土、草方块、土径耕成耕地，粗泥耕成泥土，
 *       缠根泥土耕成泥土并掉落垂根——判定、音效、游戏事件与玩家用锄头逐行一致
 *       （含“目标上方需要为空”等原版限制）；</li>
 *   <li>成功时按原版锄头的结算补上耐久消耗（{@code hurtAndBreak(1)}，耐久耗尽时锄头照常
 *       损坏消失；原版在“没有玩家”时不结算耐久，这里与玩家用锄头对齐）。</li>
 * </ol>
 *
 * <p>无论成功还是失败都留在发射器里（不弹出物品）：失败时沿用
 * {@link OptionalDispenseItemBehavior} 的“失败咔哒声”，不消耗耐久、不碰方块。
 * 所有逻辑都发生在服务端（{@link BlockSource} 只由服务端构建），纯客户端/单机/
 * 专用服务器下行为一致，且不需要任何客户端同步。</p>
 */
public final class SogDispenserTill extends OptionalDispenseItemBehavior {
    /** 行为唯一实例，由 mixin 在“锄头 + 原版无行为 + 规则开启”时替换返回。 */
    public static final SogDispenserTill INSTANCE = new SogDispenserTill();

    private SogDispenserTill() {
    }

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        this.setSuccess(false);
        if (!(stack.getItem() instanceof HoeItem hoe)) {
            // mixin 只会在物品是 HoeItem 时替换进来，这里纯属防御。
            return stack;
        }
        ServerLevel level = source.level();
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        BlockPos targetPos = source.pos().relative(facing);
        // 与“玩家站在发射器所在的位置、右键正前方方块”等价的使用上下文：
        // 点击面取朝向发射器的那一面，没有玩家（原版自己的耐久结算因此不会触发，
        // 成功与否也不消耗物品）。
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, stack,
                clickOn(targetPos, facing.getOpposite()));
        InteractionResult result = hoe.useOn(context);
        if (!result.consumesAction()) {
            return stack;
        }
        // 与玩家用锄头一致：每成功耕一次，消耗 1 点耐久（耐久耗尽时照常损坏消失）。
        stack.hurtAndBreak(1, level, null, item -> { });
        this.setSuccess(true);
        return stack;
    }

    /** 在目标方块的指定面上构造一个点击点（供原版逻辑读取点击位置与点击面）。 */
    private static BlockHitResult clickOn(BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos)
                .add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
        return new BlockHitResult(point, face, pos, false);
    }
}

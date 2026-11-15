package cn.blockforge.generated.sogcarpet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * “发射器可采集浆果”的发射器行为——让发射器里的剪刀采集正前方的浆果。
 *
 * <p>本类直接继承原版 {@link ShearsDispenseItemBehavior}：规则开启且发射器取到的物品是
 * 剪刀时，由 {@code DispenserBlockMixin} 把原版剪刀行为换成 {@link #INSTANCE}。每次发射：</p>
 * <ol>
 *   <li>取发射器正前方（{@link DispenserBlock#FACING}）那一格；</li>
 *   <li>若是成熟的甜浆果丛或长有发光浆果的洞穴藤蔓，就走原版 {@code useWithoutItem}
 *       采摘路径：甜浆果丛按原版掉落表掉落甜浆果、回到 age=1 再重新结果；洞穴藤蔓
 *       掉 1 个发光浆果并转为未结果状态；音效、游戏事件与手动采摘完全一致；</li>
 *   <li>采集成功时按原版剪刀发射器的方式消耗 1 点耐久（“剪刀不消耗耐久”规则开启时会被
 *       那边的拦截取消，剪刀因此不损耗）。</li>
 * </ol>
 *
 * <p>正前方没有可采集的浆果时，直接 {@code super.execute} 退回原版剪刀发射器行为
 * （蜂巢取蜜、剪实体等原样保留），不消耗耐久。所有逻辑都发生在服务端，
 * 纯客户端/单机/专用服务器下行为一致，且不需要任何客户端同步。</p>
 */
public final class SogDispenserBerryHarvest extends ShearsDispenseItemBehavior {
    /** 行为唯一实例，由 mixin 在“剪刀 + 规则开启”时替换原版剪刀行为。 */
    public static final SogDispenserBerryHarvest INSTANCE = new SogDispenserBerryHarvest();

    private SogDispenserBerryHarvest() {
    }

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        ServerLevel level = source.level();
        BlockPos targetPos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        if (harvest(level, targetPos)) {
            stack.hurtAndBreak(1, level, null, item -> { });
            this.setSuccess(true);
            return stack;
        }
        // 没有可采集的浆果：退回原版剪刀发射器逻辑（同时保持它自己的成功/失败状态与音效）。
        return super.execute(source, stack);
    }

    /** 以“空手采摘”的原版交互采集浆果；正前方不是可采集的浆果时返回 false。 */
    private static boolean harvest(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SweetBerryBushBlock) && !(state.getBlock() instanceof CaveVines)) {
            return false;
        }
        // 甜浆果丛：只在 age > 1（结着果）时给出掉落；洞穴藤蔓：只在有发光浆果时给出掉落。
        // 两者的“没有果子”分支都会返回 PASS，这里据 consumesAction 判断是否真的采到了。
        InteractionResult result = state.useWithoutItem(level, null,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return result.consumesAction();
    }
}
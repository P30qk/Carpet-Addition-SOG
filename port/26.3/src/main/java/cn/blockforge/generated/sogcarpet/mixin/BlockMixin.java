package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 精准采集扩展。
 *
 * <p>强化深板岩与可疑的沙子/沙砾在原版里根本没有产出（战利品表是空的），
 * 即便精准采集也挖不到本体。这里在玩家破坏方块时补一次掉落：
 * 只有“正确的工具类型 + 精准采集 + 对应规则开启”三者同时满足才生效，
 * 因此不会和原版掉落重叠，也不会影响刷子刷可疑的沙子。</p>
 */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "playerDestroy", at = @At("HEAD"))
    private void sogcarpet$silkTouchBonusDrops(ServerLevel level, ServerPlayer player, BlockPos pos,
            BlockState state, BlockEntity blockEntity, ItemStack tool, CallbackInfo ci) {
        if (player.isCreative()) {
            return;
        }
        Block block = state.getBlock();
        Item drop;
        if (block == Blocks.REINFORCED_DEEPSLATE) {
            if (!SogSettings.silkTouchReinforcedDeepslate) {
                return;
            }
            drop = Items.REINFORCED_DEEPSLATE;
        } else if (block == Blocks.SUSPICIOUS_SAND || block == Blocks.SUSPICIOUS_GRAVEL) {
            if (!SogSettings.silkTouchSuspicious) {
                return;
            }
            drop = block == Blocks.SUSPICIOUS_SAND ? Items.SUSPICIOUS_SAND : Items.SUSPICIOUS_GRAVEL;
        } else {
            return;
        }
        if (!tool.isCorrectToolForDrops(state)) {
            return;
        }
        if (!hasSilkTouch(tool)) {
            return;
        }
        Block.popResource(level, pos, new ItemStack(drop));
    }

    private static boolean hasSilkTouch(ItemStack stack) {
        for (Holder<Enchantment> holder : stack.getEnchantments().keySet()) {
            if (holder.is(Enchantments.SILK_TOUCH)) {
                return true;
            }
        }
        return false;
    }
}

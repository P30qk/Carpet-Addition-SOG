package cn.blockforge.generated.sogcarpet.mixin;

import carpet.patches.EntityPlayerMPFake;
import cn.blockforge.generated.sogcarpet.SogFakeTools;
import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.SogTools;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “假人只拾取工具”：carpet 假人碰到物品实体时，非工具类物品不再捡起。
 *
 * <p>原版物品拾取的唯一入口是 {@link ItemEntity#playerTouch(Player)}：
 * 它先看拾取延迟与归属者，再调用 {@code player.getInventory().add(stack)}。
 * 在 HEAD 直接取消，物品实体原样留在地上（不会被吞掉、也不会触发统计）。
 * 判定条件为“规则开启 + 目标玩家是假人 + 物品不属于假人允许的工具类别”，三者同时成立才取消：
 * 真人玩家、以及规则关闭时都走回原版逻辑。</p>
 *
 * <p>“允许的类别”由假人自己携带（{@link SogFakeTools}），玩家可以在 GCA 假人背包界面
 * 右侧那一列开关里逐类切换；没设置过的假人默认全选，行为与原实现一致。</p>
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void sog$toolsOnlyPickup(Player player, CallbackInfo ci) {
        if (!SogSettings.fakePlayerToolsOnly) {
            return;
        }
        if (!(player instanceof EntityPlayerMPFake)) {
            return;
        }
        int mask = player instanceof SogFakeTools tools ? tools.sog$getToolMask() : SogTools.ALL;
        ItemStack stack = ((ItemEntity) (Object) this).getItem();
        if (!SogTools.isTool(stack, mask)) {
            ci.cancel();
        }
    }
}

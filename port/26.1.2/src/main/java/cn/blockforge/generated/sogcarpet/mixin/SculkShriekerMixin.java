package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幽匿尖啸等级可调 / 锁定 / 禁止坚守者生成。
 *
 * <p>26.2 里尖啸等级的全部写入点只有两处：</p>
 * <ul>
 *   <li>{@code tryShriek} 开头把等级清 0（{@code warningLevel = 0}，方法内唯一的
 *       {@code iconst_0}），锁定规则开启时改回当前值，即“不重置”；</li>
 *   <li>{@code lambda$tryToWarn$0}：尖啸成功后把玩家警告等级同步进方块实体，
 *       锁定规则取消这次写入（不升级），可调规则（1-4）把等级直接置为该值。</li>
 * </ul>
 *
 * <p>坚守者召唤只从 {@code trySummonWarden}（等级 >= 4 才尝试）发出，
 * 规则开启时在 HEAD 直接返回 false；尖啸动画与黑暗效果保持原版。</p>
 */
@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerMixin {
    @Shadow
    private int warningLevel;

    @ModifyConstant(method = "tryShriek", constant = @Constant(intValue = 0))
    private int sog$lockLevelReset(int resetValue) {
        if (SogSettings.sculkShriekerLevelLocked) {
            return this.warningLevel;
        }
        return resetValue;
    }

    @Inject(method = "lambda$tryToWarn$0", at = @At("HEAD"), cancellable = true)
    private void sog$overrideLevel(int trackerLevel, CallbackInfo ci) {
        if (SogSettings.sculkShriekerLevelLocked) {
            ci.cancel();
            return;
        }
        int forced = SogSettings.sculkShriekerLevel;
        if (forced > 0) {
            this.warningLevel = forced;
            ci.cancel();
        }
    }

    @Inject(method = "trySummonWarden", at = @At("HEAD"), cancellable = true)
    private void sog$blockWardenSpawn(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (SogSettings.wardenSpawnDisabled) {
            cir.setReturnValue(false);
        }
    }
}

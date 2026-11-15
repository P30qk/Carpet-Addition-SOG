package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSculkTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幽匿感测体（含校频感测体）收到振动时的采集点，用于“触发判定线”。
 *
 * <p>感测体的振动接收收口在 {@code SculkSensorBlockEntity$VibrationUser#onReceiveVibration}：
 * 第二个参数就是引起振动的声音源坐标，用户对象里的 {@code blockPos} 是感测体自身坐标。
 * 在 HEAD 处记一条（检测体坐标 ＋ 声音源坐标）即可，其余原版逻辑完全不动。校频感测体的
 * 振动用户继承自同一个类且没重写这个方法，所以也一并覆盖。</p>
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.SculkSensorBlockEntity$VibrationUser")
public abstract class SculkSensorReceiveMixin {
    @Shadow
    @Final
    protected BlockPos blockPos;

    @Inject(method = "onReceiveVibration(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("HEAD"))
    private void sogcarpet$recordTrigger(ServerLevel level, BlockPos sourcePos,
            Holder<GameEvent> event, Entity sourceEntity, Entity projectileOwner, float distance,
            CallbackInfo ci) {
        SogSculkTrigger.received(level, this.blockPos, sourcePos);
    }
}

package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogMotionSync;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端采集 {@code addDeltaMovement} 的冲量，交给 {@link SogMotionSync} 下发给客户端，
 * 供“速度/动量可视化”显示“外力施加”的一支。
 *
 * <p>只统计逻辑服务端的调用（客户端那一侧由客户端自带的采集负责），所以这里显式判断
 * {@code !level().isClientSide()}；规则关闭时 {@link SogMotionSync#record} 会直接返回。</p>
 */
@Mixin(Entity.class)
public abstract class EntityDeltaMovementRecordMixin {
    @Inject(method = "addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void sog$recordImpulse(Vec3 delta, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide()) {
            SogMotionSync.record(self, delta);
        }
    }
}

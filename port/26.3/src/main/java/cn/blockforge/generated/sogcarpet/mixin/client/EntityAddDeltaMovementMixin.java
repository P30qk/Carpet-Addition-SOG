package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.MotionImpulseTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 采集 {@code addDeltaMovement} 的冲量，供“速度/动量/加速度可视化”显示“外力施加”的一支。
 *
 * <p>只挂在客户端 mixin 配置里，所以只在物理客户端生效；采集到的数据每个客户端 tick 清空，
 * 由 {@link MotionImpulseTracker} 保存。规则关闭时这个 mixin 仍然记录，但没人读，代价极小。</p>
 */
@Mixin(Entity.class)
public abstract class EntityAddDeltaMovementMixin {
    @Inject(method = "addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void sog$trackExternalImpulse(Vec3 delta, CallbackInfo ci) {
        MotionImpulseTracker.add((Entity) (Object) this, delta);
    }
}

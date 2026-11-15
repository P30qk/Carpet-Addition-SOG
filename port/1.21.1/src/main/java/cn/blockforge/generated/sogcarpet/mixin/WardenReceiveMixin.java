package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSculkTrigger;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 坚守者收到振动时的采集点，用于“触发判定线”。
 *
 * <p>坚守者也是振动监听者，收口同样是它内部振动用户的 {@code onReceiveVibration}：第二个参数
 * 是声音源坐标，用户对象里的 {@code positionSource} 给出坚守者自身位置。因为 {@code VibrationUser}
 * 是包级私有内部类，这里用 {@code targets} 指过去，并阴影出 {@code positionSource} 取自身坐标。</p>
 */
@Mixin(targets = "net.minecraft.world.entity.monster.warden.Warden$VibrationUser")
public abstract class WardenReceiveMixin {
    @Shadow
    @Final
    private PositionSource positionSource;

    @Inject(method = "onReceiveVibration(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("HEAD"))
    private void sogcarpet$recordTrigger(ServerLevel level, BlockPos sourcePos,
            Holder<GameEvent> event, Entity sourceEntity, Entity projectileOwner, float distance,
            CallbackInfo ci) {
        Optional<Vec3> self = this.positionSource.getPosition(level);
        if (self.isEmpty()) {
            return;
        }
        Vec3 pos = self.get();
        SogSculkTrigger.received(level, BlockPos.containing(pos.x, pos.y, pos.z), sourcePos);
    }
}

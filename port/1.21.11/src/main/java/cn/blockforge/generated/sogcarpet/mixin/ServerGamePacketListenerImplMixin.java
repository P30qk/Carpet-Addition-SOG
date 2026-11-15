package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogPearlTeleport;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 的长矛突刺入口。
 *
 * <p>这个版本把长矛挥击放在 {@code ServerGamePacketListenerImpl#handlePlayerAction} 里处理：
 * 客户端发的是 {@code ServerboundPlayerActionPacket.Action.STAB}，不会像普通近战那样发
 * ATTACK 交互包，所以 {@code AttackEntityCallback} 永远收不到这次攻击，悬停珍珠也就叠不上速度。
 * 原生 {@code PiercingWeapon.attack} 只对 {@code LivingEntity} 生效、投掷物不在候选里，同样
 * 靠不住。这里在服务端动作处理完后按 STAB 调一次 {@link SogPearlTeleport#onStab}，由它自己
 * 从玩家视线里找最近的悬停珍珠；{@code STAB_COOLDOWN} 会防止同一次突刺重复计数。</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("TAIL"))
    private void sog$farPearlStab(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet != null && packet.getAction() == ServerboundPlayerActionPacket.Action.STAB) {
            SogPearlTeleport.onStab(this.player);
        }
    }
}

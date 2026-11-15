package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogLightning;
import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「雷击粘土转化」的服务端接线。
 *
 * <p>原版 {@code LightningBolt#tick} 在 {@code life == 2} 的那一帧、且只在服务端，依次做
 * 点燃火焰、避雷针通电、铜除锈与 {@code LIGHTNING_STRIKE} 游戏事件。这里在同一个时机（HEAD，
 * 用同一个 {@code life == 2} + 服务端条件）把落雷点周围的粘土换成陶瓦，于是：</p>
 * <ul>
 *   <li>只触发一次（{@code life} 每 tick 递减，只有第一帧等于 2）；</li>
 *   <li>只发生在服务端（客户端闪电实体只是视觉，改方块由服务端同步）；</li>
 *   <li>{@code visualOnly}（例如只播放电击特效的闪电）不改变世界，和原版一致；</li>
 *   <li>范围与可视化用的是同一份 {@link SogLightning#impactBox} 定义，画出来的就是真正转化的区域。</li>
 * </ul>
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {
    /** 闪电剩余寿命：原版在 {@code life == 2} 的那一帧执行所有服务端效果。 */
    @Shadow
    private int life;

    /** 只播放视觉效果、不改世界的闪电（原版字段）。 */
    @Shadow
    private boolean visualOnly;

    @Inject(method = "tick", at = @At("HEAD"))
    private void sog$turnsClayIntoTerracotta(CallbackInfo ci) {
        if (!SogSettings.lightningTurnsClay || this.life != 2 || this.visualOnly) {
            return;
        }
        LightningBolt bolt = (LightningBolt) (Object) this;
        Level level = bolt.level();
        if (level.isClientSide()) {
            return;
        }
        BlockPos strike = SogLightning.strikePosition(bolt.getX(), bolt.getY(), bolt.getZ());
        SogLightning.convertClay(level, strike);
    }
}

package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.CreakingHeartVisualizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.creaking.Creaking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把“绑着嘎吱之心的嘎吱怪被攻击”这一刻交给 {@link CreakingHeartVisualizer}。
 *
 * <p>原版 {@code Creaking.hurtServer} 在怪被打中、且它绑着嘎吱之心时会设置无敌帧动画
 * 并 {@code broadcastEntityEvent(this, (byte) 66)}；服务端发的这个实体事件到客户端由
 * {@code Level} 回放进 {@code Creaking.handleEntityEvent}，所以注入点就是它。
 * 只有客户端会走这条路（服务端只发不收），本 mixin 也只挂在客户端配置里。</p>
 *
 * <p>注入点取 HEAD：事件 66 命中时客户端刚设好动画，此时读取同步来的家坐标
 * （即嘎吱之心方块，见 {@code HOME_POS} 同步数据）一定是最新的。</p>
 */
@Mixin(Creaking.class)
public abstract class CreakingHurtMixin {
    @Inject(method = "handleEntityEvent(B)V", at = @At("HEAD"))
    private void sog$onEntityEvent(byte event, CallbackInfo ci) {
        // 66 = 原版“绑着嘎吱之心的嘎吱怪被打中”的实体事件。
        if (event != 66) {
            return;
        }
        Creaking self = (Creaking) (Object) this;
        BlockPos heart = self.getHomePos();
        if (heart != null) {
            CreakingHeartVisualizer.onCreakingHurt(heart, self.getId());
        }
    }
}

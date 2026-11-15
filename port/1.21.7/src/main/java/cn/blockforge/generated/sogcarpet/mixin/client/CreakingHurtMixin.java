package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.CreakingHeartVisualizer;
import net.minecraft.world.entity.monster.creaking.Creaking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把“绑着嘎吱之心的嘎吱怪被攻击”这一刻交给 {@link CreakingHeartVisualizer}。
 *
 * <p>原版 {@code Creaking.hurtServer} 在怪被打中、且它绑着嘎吱之心时会设置无敌帧动画
 * 并 {@code broadcastEntityEvent(this, (byte) 66)}。这个广播是<b>原版行为</b>，所以即使服务端
 * 没装本模组（甚至就是个原版服务器），客户端也收得到——本可视化在纯客户端下同样可用。</p>
 *
 * <p>注入点取 HEAD：事件 66 命中时客户端刚收到同步数据，这里只把实体 id 投递出去；
 * 嘎吱之心的方块坐标由 {@link CreakingHeartVisualizer} 每次 tick 从
 * {@link Creaking#getHomePos()} 重新读取（HOME_POS 才是权威值）。</p>
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
        if (self.getHomePos() != null) {
            CreakingHeartVisualizer.onCreakingHurt(self.getId());
        }
    }
}

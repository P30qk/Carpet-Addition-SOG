package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.CreakingHeartVisualizer;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.creaking.CreakingTransient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把“绑着嘎吱之心的嘎吱怪被攻击”这一刻交给 {@link CreakingHeartVisualizer}。
 *
 * <p>1.21.2/1.21.3 里绑定嘎吱之心的实体是 {@link CreakingTransient}：它自己处理实体事件
 * 66，不再调用父类 {@code Creaking.handleEntityEvent}，且家坐标存在包私有字段
 * {@code homePos}（1.21.4 起才提升成 {@code Creaking.HOME_POS} 同步数据并有 getter）。
 * 所以这两个版本直接把 mixin 挂在 {@code CreakingTransient} 上，用反射读取家坐标。</p>
 */
@Mixin(CreakingTransient.class)
public abstract class CreakingHurtMixin {
    @Inject(method = "handleEntityEvent(B)V", at = @At("HEAD"))
    private void sog$onEntityEvent(byte event, CallbackInfo ci) {
        // 66 = 原版“绑着嘎吱之心的嘎吱怪被打中”的实体事件。
        if (event != 66) {
            return;
        }
        CreakingTransient self = (CreakingTransient) (Object) this;
        BlockPos heart = sog$homePos(self);
        if (heart != null) {
            CreakingHeartVisualizer.onCreakingHurt(heart, self.getId());
        }
    }

    private static BlockPos sog$homePos(CreakingTransient self) {
        try {
            Field field = CreakingTransient.class.getDeclaredField("homePos");
            field.setAccessible(true);
            return (BlockPos) field.get(self);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

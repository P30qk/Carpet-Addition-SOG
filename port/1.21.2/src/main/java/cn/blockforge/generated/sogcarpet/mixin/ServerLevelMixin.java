package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 天气的服务端收口点（1.21~1.21.4）。
 *
 * <p>1.21.5 起原版把雷雨生成单独拆成了 {@code ServerLevel#tickThunder(LevelChunk)}；
 * 这几个版本还没有它，雷雨逻辑内联在 {@code ServerLevel#tickChunk(LevelChunk, int)} 里，
 * 由唯一的一次 {@code isThundering()} 判断进入（其后是骷髅马陷阱与闪电生成，唯一入口）。
 * 这里包装 {@code tickChunk} 里对 {@code isThundering()} 的调用：规则开启时返回 false，
 * 整段雷雨逻辑被跳过，效果与 1.21.5+ 直接取消 {@code tickThunder} 一致。</p>
 *
 * <p>不用 {@code @Redirect} 的理由见 {@link ServerLevelMixin} 系列注释（mixinextras 0.5.4
 * 对数组形态 {@code at} 的兼容问题），{@code @WrapOperation} 不受影响。</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @WrapOperation(method = "tickChunk",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z"))
    private boolean sogcarpet$disableLightning(ServerLevel level, Operation<Boolean> original) {
        if (SogSettings.thunderDisabled) {
            return false;
        }
        return original.call(level);
    }
}

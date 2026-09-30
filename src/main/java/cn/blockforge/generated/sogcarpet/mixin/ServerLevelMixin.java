package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 天气的服务端收口点。
 *
 * <p>{@code tickThunder} 是雷雨期间生成闪电（以及骷髅马陷阱）的唯一入口，整段取消即可禁落雷。</p>
 *
 * <p>注意：本类（以及整个模组）刻意不使用原版 {@code @Redirect}。26.2 工具链
 * （sponge-mixin 0.17.4）里 {@code @Redirect.at()} 是数组签名，编出的字节码把 {@code at}
 * 写成 {@code List}；用户环境里 fabric-loader 0.19.3 自带的 mixinextras 0.5.4 在
 * {@code FactoryRedirectWrapperMixinTransformer} 里把该值强转单个 {@code AnnotationNode}，
 * 目标类（如 {@code ServerLevel}）一被转换就抛 {@code ClassCastException} 导致启动崩溃。
 * 雪生成因此改在 {@link BiomeMixin} 里以 {@code @Inject} 取消 {@code Biome#shouldSnow}。</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
    private void sogcarpet$disableLightning(LevelChunk chunk, CallbackInfo ci) {
        if (SogSettings.thunderDisabled) {
            ci.cancel();
        }
    }
}

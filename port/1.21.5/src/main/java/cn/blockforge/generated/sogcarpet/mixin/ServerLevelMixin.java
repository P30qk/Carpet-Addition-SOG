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
 * <p><b>关于 {@code @Redirect}（重要，改注入点时先读这段）：</b>26.2 工具链
 * （sponge-mixin 0.17.4）里 {@code @Redirect.at()} 是数组签名，编出的字节码把 {@code at}
 * 写成 {@code List}；fabric-loader 0.19.3 自带的 mixinextras 0.5.4 在
 * {@code FactoryRedirectWrapperMixinTransformer} 里把该值强转单个 {@code AnnotationNode}，
 * 目标类一被转换就抛 {@code ClassCastException}（表现为 “Exception ticking world” /
 * 启动崩溃）。<b>因此本模组一律不要用原版 {@code @Redirect}</b>：</p>
 * <ul>
 *   <li>能整段取消/改写结果的，用 {@code @Inject}（如本类与 {@link BiomeMixin}）；</li>
 *   <li>能改常量的，用 {@code @ModifyConstant}（如 {@link ZombieMixin}、{@link SnowGolemMixin}）；</li>
 *   <li>确实要替换一次调用的，用 mixinextras 的 {@code @WrapOperation}——它是 mixinextras
 *       自己的注入器注解，0.5.4 就是按数组形态解析 {@code at} 的，没有这个兼容问题
 *       （如 {@code NaturalSpawnerMixin}）。注意 mixinextras 的 {@code @Local}、{@code @Share}、
 *       {@code @Expression} 在 0.5.4 里同样有单值强转的问题，也别用。</li>
 * </ul>
 * <p>雪生成因此改在 {@link BiomeMixin} 里以 {@code @Inject} 取消 {@code Biome#shouldSnow}。</p>
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

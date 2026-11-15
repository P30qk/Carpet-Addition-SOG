package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 天气的服务端收口点。
 *
 * <p>雷雨期间生成闪电（以及骷髅马陷阱）的唯一入口在 {@code ServerLevel#tickChunk} 里：
 * 判定条件成立后创建 {@link LightningBolt} 并 {@code addFreshEntity}。1.21.3 没有更后面的版本
 * 才拆出来的 {@code tickThunder(LevelChunk)} 方法，所以这里直接钩住那次
 * {@code addFreshEntity}：规则开启时把闪电实体吞掉，其它实体照常加入世界。</p>
 *
 * <p><b>关于 {@code @Redirect}（重要，改注入点时先读这段）：</b>26.2 工具链
 * （sponge-mixin 0.17.4）里 {@code @Redirect.at()} 是数组签名，编出的字节码把 {@code at}
 * 写成 {@code List}；fabric-loader 0.19.3 自带的 mixinextras 0.5.4 在
 * {@code FactoryRedirectWrapperMixinTransformer} 里把该值强转单个 {@code AnnotationNode}，
 * 目标类一被转换就抛 {@code ClassCastException}（表现为 “Exception ticking world” /
 * 启动崩溃）。<b>因此本模组一律不要用原版 {@code @Redirect}</b>：</p>
 * <ul>
 *   <li>能整段取消/改写结果的，用 {@code @Inject}（如 {@link BiomeMixin}）；</li>
 *   <li>能改常量的，用 {@code @ModifyConstant}（如 {@link ZombieMixin}、{@link SnowGolemMixin}）；</li>
 *   <li>确实要替换一次调用的，用 mixinextras 的 {@code @WrapOperation}——它是 mixinextras
 *       自己的注入器注解，0.5.4 就是按数组形态解析 {@code at} 的，没有这个兼容问题。
 *       注意 mixinextras 的 {@code @Local}、{@code @Share}、{@code @Expression} 在 0.5.4 里
 *       同样有单值强转的问题，也别用。</li>
 * </ul>
 * <p>雪生成因此改在 {@link BiomeMixin} 里以 {@code @Inject} 取消 {@code Biome#shouldSnow}。</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @WrapOperation(method = "tickChunk",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;"
                            + "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean sogcarpet$disableLightning(ServerLevel level, Entity entity,
            Operation<Boolean> original) {
        if (SogSettings.thunderDisabled && entity instanceof LightningBolt) {
            return false;
        }
        return original.call(level, entity);
    }
}

package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogPlayerContainerAccess;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * GCA PlayerContainer 的访问器（条件配置，仅 GCA 在时装载）。
 *
 * <p>接口 {@link SogPlayerContainerAccess} 特意放在 mixin 包之外：mixin 包里的类
 * 不能被直接引用，否则 GCA 的容器类会在加载时炸掉、玩家无法进入世界。</p>
 */
@Mixin(targets = "dev.dubhe.gugle.carpet.tools.player.PlayerContainer")
public abstract class PlayerContainerAccessorMixin implements SogPlayerContainerAccess {
    @Override
    @Accessor("player")
    public abstract ServerPlayer sog$getFakePlayer();
}

package cn.blockforge.generated.sogcarpet;

import net.minecraft.server.level.ServerPlayer;

/**
 * 由 mixin {@code PlayerContainerAccessorMixin} 注入到 GCA
 * {@code dev.dubhe.gugle.carpet.tools.player.PlayerContainer} 上，
 * 用来读出容器持有的假人（GCA 的 {@code PlayerContainer.player} 字段）。
 *
 * <p><b>必须放在 mixin 包之外。</b>Mixin 会把配置里声明的 package
 * （这里是 {@code cn.blockforge.generated.sogcarpet.mixin}）整体视为“mixin 包”，
 * 该包内的类一旦被别的类直接引用，就会抛
 * {@code IllegalClassLoadError: ... is in a defined mixin package ... and cannot be
 * referenced directly}，进而让被注入的 GCA 容器类加载失败——表现为玩家加入时
 * “无效的玩家数据 / Invalid player data”。</p>
 */
public interface SogPlayerContainerAccess {
    ServerPlayer sog$getFakePlayer();
}

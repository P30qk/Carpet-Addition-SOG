package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.DropCountVisualizer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把掉落物总数接进玩家的 Tab 列表（玩家数面板）页脚，而不是另开一个独立面板。
 *
 * <p>这里只在 {@code setFooter} 里记下服务端写来的页脚文本；{@link DropCountVisualizer} 每个
 * 客户端 tick 把“服务端页脚 + 一行掉落物总数”写回去。于是纯客户端（对面没装本模组、没有页脚）
 * 也能显示本地统计的总数，装了本模组的服务器则由服务端下发总数叠加显示。</p>
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Inject(method = "setFooter", at = @At("TAIL"))
    private void sog$rememberFooter(Component footer, CallbackInfo ci) {
        DropCountVisualizer.setTabFooter(footer);
    }
}

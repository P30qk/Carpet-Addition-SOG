package cn.blockforge.generated.sogcarpet.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 <b>Ctrl+O</b> 从原版“好友界面”手里让给可视化总开关。
 *
 * <p>26.2 里 {@code O} 是原版 {@code key.friends} 的默认键，而且好友界面是在
 * {@code Minecraft.handleGlobalKeyPress} 里<b>按键按下的瞬间</b>直接切换的——比客户端
 * tick 早得多，所以光靠 tick 里 {@code consumeClick()} 根本拦不住。这里在
 * {@code handleGlobalKeyPress} 的开头就把 Ctrl+O 吃掉：只有按下 Ctrl 时才拦，
 * 单独按 O 仍然正常打开好友界面。返回 false 后按键继续走 {@code KeyMapping.click}，
 * 于是 {@code VisualizerState} 的 Ctrl+O 总开关照常收到这次点击。</p>
 */
@Mixin(Minecraft.class)
public abstract class FriendsKeyGuardMixin {
    @Inject(method = "handleGlobalKeyPress", at = @At("HEAD"), cancellable = true)
    private void sog$keepCtrlOForVisualizer(InputConstants.Key key, boolean controlDown,
            CallbackInfoReturnable<Boolean> cir) {
        if (!controlDown) {
            return;
        }
        Minecraft client = (Minecraft) (Object) this;
        if (client.level != null && client.options != null
                && client.options.keyFriends.matches(key)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}

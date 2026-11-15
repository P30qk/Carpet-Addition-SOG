package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.WorldEaterHelperVisualizer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把 <b>Ctrl+E</b> 从“物品栏界面”手里抢回来，注入点在<b>按键源头</b>。
 *
 * <p><b>为什么不在界面上拦</b>：物品栏是
 * {@code InventoryScreen → AbstractRecipeBookScreen → AbstractContainerScreen}，
 * 而 {@code AbstractRecipeBookScreen#keyPressed} 先问配方书，配方书（含搜索框 {@code EditBox}）
 * 吃掉按键就直接 return，<b>根本不会调用 super</b>；创造模式物品栏同样重写了 {@code keyPressed}。
 * 所以在 {@code AbstractContainerScreen#keyPressed} 上做 HEAD 注入，遇到“物品栏 + 配方书/搜索框”
 * 这条最常见的路径永远收不到 Ctrl+E —— 这就是“Ctrl+E 仍被物品栏阻挡”的确切原因。</p>
 *
 * <p><b>这里改成拦第一站</b>：{@link KeyboardHandler#keyPress} 是所有按键进入游戏的入口，
 * 界面、搜索框、快捷键分发都还没参与。命中 Ctrl+E 时直接取消这次按键，于是
 * 物品栏不会打开、搜索框也不会收到字符；真正的 {@code setScreen} 交给
 * {@code WorldEaterHelperVisualizer} 在下一个客户端 tick 执行（不在 GLFW 回调栈里重入界面）。</p>
 *
 * <p>只处理 {@code action == 1}（GLFW_PRESS）：松开与自动重复原样放行，
 * 不会让 {@code KeyMapping} 的按下状态卡住。</p>
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    /** GLFW_PRESS。 */
    private static final int PRESS = 1;

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void sog$ctrlEAtSource(long window, int key, int scanCode, int action, int modifiers,
            CallbackInfo ci) {
        if (action != PRESS) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null
                || !WorldEaterHelperVisualizer.sog$isCtrlDown(client)) {
            return;
        }
        if (!WorldEaterHelperVisualizer.sog$matchesConfigKey(key, scanCode)) {
            return;
        }
        WorldEaterHelperVisualizer.sog$queueOpen();
        ci.cancel();
    }
}

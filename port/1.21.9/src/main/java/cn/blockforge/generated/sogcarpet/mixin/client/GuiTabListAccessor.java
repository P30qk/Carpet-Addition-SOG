package cn.blockforge.generated.sogcarpet.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 还没有 26.2 引入的 {@code Gui#hud} 中间层，玩家列表（Tab 栏）直接挂在
 * {@link Gui} 的私有字段 {@code tabList} 上，也没有公开的 getter。
 *
 * <p>“掉落物数量”可视化需要读 Tab 栏的页脚（{@code DropCountVisualizer} 往那里写总数），
 * 所以这里用一个 accessor mixin 把这一个字段暴露出来：只读，不改任何原版行为。</p>
 */
@Mixin(Gui.class)
public interface GuiTabListAccessor {
    @Accessor("tabList")
    PlayerTabOverlay sog$tabList();
}
